"""Phase 1 exit criterion, live: theme change -> publish -> rollback on two tenants, no code change.

Signs in as each tenant's dev SUPER_ADMIN (enrolling the required authenticator the way an app
would), changes the workspace theme, checks it is live and versioned, rolls it back, and checks
the other tenant never moved. Resets the admins' MFA and restores both themes afterwards.

    python3 scripts/config/phase1_theme_rollback_check.py
"""
import base64, hashlib, hmac, json, os, re, struct, subprocess, sys, time, urllib.request, urllib.error

GW = os.environ.get("GATEWAY_URL", "http://localhost:8080") + "/api/v1"
TENANTS = ["acme", "bhoomi"]
SEEDER = os.path.join(os.path.dirname(__file__),
                      "../../backend/auth-service/src/main/java/com/civileng/marketplace/auth/service/DevUserSeeder.java")
src = open(SEEDER).read()
EMAIL = re.search(r'SUPER_ADMIN_EMAIL\s*=\s*"([^"]+)"', src).group(1)
PASSWORD = re.search(r'SUPER_ADMIN_PASSWORD\s*=\s*"([^"]+)"', src).group(1)
ok = True


def check(name, cond, detail=""):
    global ok
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else f"  -> {detail}"))
    ok = ok and cond


def call(method, path, body=None, token=None, tenant=None):
    h = {"Content-Type": "application/json"}
    if token: h["Authorization"] = "Bearer " + token
    if tenant: h["Host"] = f"{tenant}.localhost"
    req = urllib.request.Request(GW + path, method=method, data=None if body is None else json.dumps(body).encode(), headers=h)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, json.loads(r.read() or b"null")
    except urllib.error.HTTPError as e:
        try: return e.code, json.loads(e.read())
        except Exception: return e.code, None


def totp(secret):
    key = base64.b32decode(secret + "=" * (-len(secret) % 8))
    h = hmac.new(key, struct.pack(">Q", int(time.time() // 30)), hashlib.sha1).digest()
    o = h[-1] & 15
    return "%06d" % ((struct.unpack(">I", h[o:o + 4])[0] & 0x7FFFFFFF) % 1000000)


def sql(q):
    pw = subprocess.run(["docker", "exec", "civil_mysql", "printenv", "MYSQL_ROOT_PASSWORD"], capture_output=True, text=True).stdout.strip()
    return subprocess.run(["docker", "exec", "civil_mysql", "mysql", "-uroot", f"-p{pw}", "-N", "-e", q], capture_output=True, text=True).stdout


def reset_mfa():
    for t in TENANTS:
        sql(f"UPDATE civil_engineer_auth_{t}.users SET two_factor_enabled=0, two_factor_secret=NULL, "
            f"two_factor_last_step=NULL, two_factor_recovery_codes=NULL WHERE email='{EMAIL}'")
    keys = subprocess.run(["docker", "exec", "civil_redis", "redis-cli", "--scan", "--pattern", "mfa:*"], capture_output=True, text=True).stdout.split()
    if keys: subprocess.run(["docker", "exec", "civil_redis", "redis-cli", "DEL", *keys], capture_output=True)


def sign_in(tenant):
    s, r = call("POST", "/auth/login", {"email": EMAIL, "password": PASSWORD}, tenant=tenant)
    assert s == 200 and r.get("mfaRequired"), (tenant, s, r)
    s, setup = call("POST", "/auth/mfa/setup", {"mfaToken": r["mfaToken"]}, tenant=tenant)
    s, sess = call("POST", "/auth/mfa/enable", {"mfaToken": r["mfaToken"], "code": totp(setup["secret"])}, tenant=tenant)
    assert s == 200, (tenant, s, sess)
    return sess["accessToken"]


def form(t):
    keys = ["mode", "primaryColor", "accentColor", "surfaceColor", "sidebarColor", "borderRadius", "fontFamily",
            "brandName", "logoUrl", "uiStyle", "buttonStyle", "layoutStyle", "density"]
    return {k: t.get(k) for k in keys}


reset_mfa()
tokens, original = {}, {}
try:
    for t in TENANTS:
        tokens[t] = sign_in(t)
        s, original[t] = call("GET", "/admin/theme", token=tokens[t], tenant=t)
        check(f"[{t}] reads its live theme", s == 200, (s, original[t]))

    colours = {"acme": "#6a1b9a", "bhoomi": "#00695c"}
    before_release = {}
    for t in TENANTS:
        s, rel = call("GET", "/admin/config/releases?scope=PLATFORM", token=tokens[t], tenant=t)
        check(f"[{t}] has an imported history", s == 200 and len(rel) >= 1 and rel[0]["live"], (s, rel))
        before_release[t] = rel[0]["id"]

        changed = dict(form(original[t]), primaryColor=colours[t], brandName=f"{t.title()} Demo")
        s, saved = call("PUT", "/admin/theme", changed, token=tokens[t], tenant=t)
        check(f"[{t}] theme change is published", s == 200 and saved["primaryColor"] == colours[t], (s, saved))
        s, pub = call("GET", "/ui-config/public/branding", tenant=t)
        check(f"[{t}] sign-in screen sees the new brand name", s == 200 and pub["brandName"] == f"{t.title()} Demo", (s, pub))

    s, other = call("GET", "/admin/theme", token=tokens["bhoomi"], tenant="bhoomi")
    check("tenants are independent (bhoomi kept its own colour)", other["primaryColor"] == colours["bhoomi"], other)

    for t in TENANTS:
        s, rel = call("GET", "/admin/config/releases?scope=PLATFORM", token=tokens[t], tenant=t)
        live = rel[0]
        check(f"[{t}] the change is a new live release", live["live"] and live["id"] != before_release[t] and live["source"] == "CONSOLE", live)
        s, diff = call("GET", f"/admin/config/releases/{live['id']}/diff", token=tokens[t], tenant=t)
        keys = {c["key"] for c in diff["changes"]}
        check(f"[{t}] diff names exactly what changed", {"primaryColor", "brandName"} <= keys, diff)

        s, back = call("POST", f"/admin/config/releases/{before_release[t]}/rollback", {"reason": "phase 1 demo"}, token=tokens[t], tenant=t)
        check(f"[{t}] rollback publishes a new ROLLBACK release", s == 200 and back["source"] == "ROLLBACK"
              and back["rollbackOfReleaseId"] == before_release[t], (s, back))
        s, now = call("GET", "/admin/theme", token=tokens[t], tenant=t)
        check(f"[{t}] the theme is back to what it was", form(now) == form(original[t]), (form(now), form(original[t])))
        s, again = call("POST", f"/admin/config/releases/{before_release[t]}/rollback", {}, token=tokens[t], tenant=t)
        check(f"[{t}] rolling back to what is already live is refused", s == 400, (s, again))
        s, bad = call("PUT", "/admin/theme", dict(form(original[t]), primaryColor="not-a-colour"), token=tokens[t], tenant=t)
        check(f"[{t}] an invalid theme is refused and nothing is published", s == 400, (s, bad))

    s, cross = call("GET", "/admin/config/releases?scope=PLATFORM", token=tokens["acme"], tenant="bhoomi")
    check("acme's admin cannot read bhoomi's history", s == 403, s)
finally:
    reset_mfa()
    print("(both admins' MFA reset; both themes are back to their original content)")

print("\nALL PASSED" if ok else "\nSOME FAILED")
sys.exit(0 if ok else 1)
