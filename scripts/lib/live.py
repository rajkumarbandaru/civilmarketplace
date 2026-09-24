"""Shared helpers for the live checks: gateway calls, TOTP like an authenticator app, MySQL and
the dev operator account. Run the checks from the repository root on the Docker host."""
import base64, hashlib, hmac, json, os, re, struct, subprocess, time, urllib.request, urllib.error

GW = os.environ.get("GATEWAY_URL", "http://localhost:8080") + "/api/v1"
_ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
_SEEDER = open(os.path.join(_ROOT, "backend/auth-service/src/main/java/com/civileng/marketplace/auth/service/DevUserSeeder.java")).read()
OP_EMAIL = re.search(r'SUPER_ADMIN_EMAIL\s*=\s*"([^"]+)"', _SEEDER).group(1)
OP_PASSWORD = re.search(r'SUPER_ADMIN_PASSWORD\s*=\s*"([^"]+)"', _SEEDER).group(1)


class Checks:
    def __init__(self):
        self.ok = True

    def __call__(self, name, cond, detail=""):
        print(("PASS " if cond else "FAIL ") + name + ("" if cond else f"  -> {detail}"))
        self.ok = self.ok and bool(cond)


def call(method, path, body=None, token=None, host=None):
    h = {"Content-Type": "application/json"}
    if token: h["Authorization"] = "Bearer " + token
    if host: h["Host"] = host
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


def sign_in_enrolling(email, password, host=None):
    """Password, then first-time authenticator enrolment (what a Super Admin does). Returns the session."""
    s, r = call("POST", "/auth/login", {"email": email, "password": password}, host=host)
    assert s == 200 and r.get("mfaRequired") and r.get("mfaSetupRequired"), (s, r)
    s, setup = call("POST", "/auth/mfa/setup", {"mfaToken": r["mfaToken"]}, host=host)
    s, sess = call("POST", "/auth/mfa/enable", {"mfaToken": r["mfaToken"], "code": totp(setup["secret"])}, host=host)
    assert s == 200, (s, sess)
    return sess


def reset_mfa(email, tenant="platform"):
    sql(f"UPDATE civil_engineer_auth_{tenant}.users SET two_factor_enabled=0, two_factor_secret=NULL, "
        f"two_factor_last_step=NULL, two_factor_recovery_codes=NULL WHERE email='{email}'")


def publish_new_tenant(op, key, plan, owner_email):
    """Wizard draft -> DRAFT tenant -> publish -> wait until the saga is DONE. Returns the progress."""
    data = {"name": f"Demo {key}", "tenantKey": key, "contactEmail": f"ops@{key}.example.com", "plan": plan,
            "vertical": "CIVIL_MARKETPLACE", "ownerName": "Demo Owner", "ownerEmail": owner_email}
    s, d = call("POST", "/tenants/drafts", data, token=op)
    assert s == 201, (s, d)
    s, t = call("POST", f"/tenants/drafts/{d['id']}/create", token=op)
    assert s == 201, (s, t)
    s, p = call("POST", f"/tenants/{key}/publish", token=op)
    assert s == 202, (s, p)
    for _ in range(300):
        s, p = call("GET", f"/tenants/{key}/provisioning", token=op)
        if p["step"] in ("DONE", "FAILED"):
            return p
        time.sleep(2)
    return p


def wait_until(predicate, timeout=90, every=3):
    end = time.time() + timeout
    while time.time() < end:
        if predicate():
            return True
        time.sleep(every)
    return False
