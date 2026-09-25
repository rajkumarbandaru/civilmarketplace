"""Live two-step sign-in check with the dev SUPER_ADMIN (run like phase0_live_check.py), computing codes like an authenticator app. Resets the
account's MFA afterwards so the owner enrols their own phone on next sign-in."""
import os, base64, hashlib, hmac, json, re, struct, subprocess, sys, time, urllib.request, urllib.error

GW = "http://localhost:8080/api/v1"
src = open(os.path.join(os.path.dirname(__file__), "../../backend/auth-service/src/main/java/com/civileng/marketplace/auth/service/DevUserSeeder.java")).read()
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "lib"))
# Never the owner's Super Admin: these checks enrol and reset MFA (see scripts/lib/live.py).
from live import OP_EMAIL as EMAIL, OP_PASSWORD as PASSWORD, ensure_drill_operator
ok = True


def check(name, cond, detail=""):
    global ok
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else f"  -> {detail}"))
    ok = ok and cond


def call(method, path, body=None, token=None):
    h = {"Content-Type": "application/json"}
    if token: h["Authorization"] = "Bearer " + token
    req = urllib.request.Request(GW + path, method=method, data=None if body is None else json.dumps(body).encode(), headers=h)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, json.loads(r.read() or b"null")
    except urllib.error.HTTPError as e:
        try: return e.code, json.loads(e.read())
        except Exception: return e.code, None


def totp(secret, t=None):
    key = base64.b32decode(secret + "=" * (-len(secret) % 8))
    step = int((t or time.time()) // 30)
    h = hmac.new(key, struct.pack(">Q", step), hashlib.sha1).digest()
    o = h[-1] & 15
    return "%06d" % ((struct.unpack(">I", h[o:o + 4])[0] & 0x7FFFFFFF) % 1000000)


def sql(q):
    pw = subprocess.run(["docker", "exec", "civil_mysql", "printenv", "MYSQL_ROOT_PASSWORD"], capture_output=True, text=True).stdout.strip()
    return subprocess.run(["docker", "exec", "civil_mysql", "mysql", "-uroot", f"-p{pw}", "-N", "-e", q],
                          capture_output=True, text=True).stdout.strip()


def reset():
    ensure_drill_operator("platform")
    sql(f"UPDATE civil_engineer_auth_platform.users SET two_factor_enabled=0, two_factor_secret=NULL, two_factor_last_step=NULL, "
        f"two_factor_recovery_codes=NULL, login_attempts=0, locked_until=NULL WHERE email='{EMAIL}'")
    # Only the drill operator's pending enrolment and failure count — never anyone else's.
    for t in ["platform"]:
        uid = sql(f"SELECT id FROM civil_engineer_auth_{t}.users WHERE email='{EMAIL}'").strip()
        for kind in ("pending", "failures"):
            if uid:
                subprocess.run(["docker", "exec", "civil_redis", "redis-cli", "DEL", f"mfa:{kind}:{t}:{uid}"], capture_output=True)


reset()
try:
    # 1. Password alone no longer yields a session for a Super Admin
    s, r = call("POST", "/auth/login", {"email": EMAIL, "password": PASSWORD})
    check("password step returns an enrolment challenge, no tokens",
          s == 200 and r.get("mfaRequired") and r.get("mfaSetupRequired") and not r.get("accessToken"), (s, r))
    ticket = r["mfaToken"]
    s, _ = call("GET", "/tenants/integration-catalog", token=ticket)
    check("the MFA ticket is not an access token at the gateway", s == 401, s)

    s, setup = call("POST", "/auth/mfa/setup", {"mfaToken": ticket})
    check("setup returns a secret and otpauth URI", s == 200 and setup["otpauthUri"].startswith("otpauth://totp/"), (s, setup))
    secret = setup["secret"]
    stored = sql(f"SELECT two_factor_secret FROM civil_engineer_auth_platform.users WHERE email='{EMAIL}'")
    s, bad = call("POST", "/auth/mfa/enable", {"mfaToken": ticket, "code": "000000" if totp(secret) != "000000" else "111111"})
    check("a wrong first code does not enable it", s == 400, (s, bad))
    s, sess = call("POST", "/auth/mfa/enable", {"mfaToken": ticket, "code": totp(secret)})
    check("enrolment completes sign-in with tokens and 8 recovery codes",
          s == 200 and sess.get("accessToken") and len(sess.get("recoveryCodes", [])) == 8, (s, sess))
    stored = sql(f"SELECT two_factor_secret FROM civil_engineer_auth_platform.users WHERE email='{EMAIL}'")
    check("secret is stored encrypted", stored.startswith("v1:") and secret not in stored, stored[:20])
    s, _ = call("GET", "/tenants/integration-catalog", token=sess["accessToken"])
    check("the resulting session works (Super Admin API)", s == 200, s)
    s, _ = call("GET", "/tenants/integration-catalog", token=sess["refreshToken"])
    check("a refresh token is not an access token at the gateway", s == 401, s)
    s, _ = call("POST", "/auth/mfa/enable", {"mfaToken": ticket, "code": totp(secret)})
    check("the setup ticket cannot be reused", s == 401, s)

    # 2. Next sign-in asks for a code; the same code cannot be replayed
    time.sleep(31 - time.time() % 30)  # a fresh 30-second step
    s, r = call("POST", "/auth/login", {"email": EMAIL, "password": PASSWORD})
    check("next sign-in asks for a code", r.get("mfaRequired") and not r.get("mfaSetupRequired"), r)
    code = totp(secret)
    s, v = call("POST", "/auth/mfa/verify", {"mfaToken": r["mfaToken"], "code": code})
    check("a current code signs in", s == 200 and v.get("accessToken"), (s, v))
    s, r2 = call("POST", "/auth/login", {"email": EMAIL, "password": PASSWORD})
    s, v2 = call("POST", "/auth/mfa/verify", {"mfaToken": r2["mfaToken"], "code": code})
    check("the same code cannot be used twice", s == 400, (s, v2))

    # 3. Recovery code, once
    rc = sess["recoveryCodes"][0]
    s, v = call("POST", "/auth/mfa/verify", {"mfaToken": r2["mfaToken"], "code": rc})
    check("a recovery code signs in", s == 200 and v.get("accessToken"), (s, v))
    s, r3 = call("POST", "/auth/login", {"email": EMAIL, "password": PASSWORD})
    s, _ = call("POST", "/auth/mfa/verify", {"mfaToken": r3["mfaToken"], "code": rc})
    check("the recovery code is spent", s == 400, s)

    # 4. Refresh keeps working for an enrolled admin
    s, rf = call("POST", "/auth/refresh", {"refreshToken": v["refreshToken"]})
    check("refresh works after MFA", s == 200 and rf.get("accessToken"), (s, rf))

    # 5. Lockout
    for _ in range(5):
        s, rr = call("POST", "/auth/login", {"email": EMAIL, "password": PASSWORD})
        call("POST", "/auth/mfa/verify", {"mfaToken": rr["mfaToken"], "code": "000000"})
    time.sleep(31 - time.time() % 30)
    s, rr = call("POST", "/auth/login", {"email": EMAIL, "password": PASSWORD})
    s, lk = call("POST", "/auth/mfa/verify", {"mfaToken": rr["mfaToken"], "code": totp(secret)})
    check("after 5 wrong codes even the right one waits (429)", s == 429, (s, lk))

    # 6. Accounts without MFA are unchanged
    s, c = call("POST", "/auth/login", {"email": "customer@civileng.test", "password": "Password123!"})
    check("a customer still signs in in one step", s == 200 and c.get("accessToken") and not c.get("mfaRequired"), (s, c))
finally:
    reset()
    print("(drill operator's MFA reset; the owner's Super Admin was never touched)")

print("\nALL PASSED" if ok else "\nSOME FAILED")
sys.exit(0 if ok else 1)
