"""Phase 2 exit criterion, live: create -> publish a tenant end to end in < 10 min, no manual steps.

As the platform operator (the dev SUPER_ADMIN, enrolling the required authenticator like an app
would): a wizard draft is autosaved, turned into a DRAFT tenant, published, and followed until it
is live. Then, as the new owner: the invitation email is read from the tenant's email log (what
their inbox would show), the password is set with its link, and they sign in — enrolling their own
authenticator — into their own workspace. The demo tenant is archived and the operator's MFA reset.

    python3 scripts/factory/phase2_create_publish_check.py
"""
import base64, hashlib, hmac, json, os, re, struct, subprocess, sys, time, urllib.request, urllib.error

GW = os.environ.get("GATEWAY_URL", "http://localhost:8080") + "/api/v1"
SEEDER = os.path.join(os.path.dirname(__file__),
                      "../../backend/auth-service/src/main/java/com/civileng/marketplace/auth/service/DevUserSeeder.java")
src = open(SEEDER).read()
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "lib"))
# Never the owner's Super Admin: these checks enrol and reset MFA (see scripts/lib/live.py).
from live import OP_EMAIL as OP_EMAIL, OP_PASSWORD as OP_PASSWORD, ensure_drill_operator
KEY = "demo" + str(int(time.time()) % 1000000)
OWNER = f"owner@{KEY}.example.com"
OWNER_PASSWORD = "correct horse battery staple"
ok = True


def check(name, cond, detail=""):
    global ok
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else f"  -> {detail}"))
    ok = ok and cond


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


def sign_in_with_new_authenticator(email, password, host=None):
    s, r = call("POST", "/auth/login", {"email": email, "password": password}, host=host)
    assert s == 200 and r.get("mfaRequired") and r.get("mfaSetupRequired"), (s, r)
    s, setup = call("POST", "/auth/mfa/setup", {"mfaToken": r["mfaToken"]}, host=host)
    s, sess = call("POST", "/auth/mfa/enable", {"mfaToken": r["mfaToken"], "code": totp(setup["secret"])}, host=host)
    assert s == 200, (s, sess)
    return sess


def reset_operator_mfa():
    ensure_drill_operator("platform")
    sql(f"UPDATE civil_engineer_auth_platform.users SET two_factor_enabled=0, two_factor_secret=NULL, "
        f"two_factor_last_step=NULL, two_factor_recovery_codes=NULL WHERE email='{OP_EMAIL}'")


reset_operator_mfa()
op = None
try:
    op = sign_in_with_new_authenticator(OP_EMAIL, OP_PASSWORD)["accessToken"]
    started = time.time()

    # --- Operator: wizard draft -> DRAFT tenant -> publish
    data = {"name": f"Demo {KEY}", "tenantKey": KEY, "contactEmail": f"ops@{KEY}.example.com",
            "vertical": "CIVIL_MARKETPLACE", "ownerName": "Demo Owner", "ownerEmail": OWNER,
            "branding": {"primaryColor": "#00695c", "brandName": f"Demo {KEY}"}}
    s, draft = call("POST", "/tenants/drafts", {"name": data["name"]}, token=op)
    check("wizard draft created", s == 201, (s, draft))
    s, draft = call("PUT", f"/tenants/drafts/{draft['id']}", {"version": draft["version"], "data": data}, token=op)
    check("autosave accepted, no outstanding issues", s == 200 and draft["issues"] == [], (s, draft))
    s, stale = call("PUT", f"/tenants/drafts/{draft['id']}", {"version": 0, "data": data}, token=op)
    check("a stale autosave is refused (409)", s == 409, s)

    s, tenant = call("POST", f"/tenants/drafts/{draft['id']}/create", token=op)
    check("draft becomes a DRAFT tenant", s == 201 and tenant["status"] == "DRAFT", (s, tenant))
    s, _ = call("GET", "/tenant-resolution/current", host=f"{KEY}.localhost")
    check("a DRAFT tenant serves no traffic", s == 503, s)

    s, prog = call("POST", f"/tenants/{KEY}/publish", token=op)
    check("publish starts provisioning", s == 202 and prog["status"] == "PROVISIONING", (s, prog))
    s, again = call("POST", f"/tenants/{KEY}/publish", token=op)
    check("publishing twice is refused", s == 400, s)

    saw_active_before_all_ready = False
    deadline = time.time() + 600
    while time.time() < deadline:
        s, prog = call("GET", f"/tenants/{KEY}/provisioning", token=op)
        if prog["status"] == "ACTIVE" and any(x["state"] != "READY" for x in prog["services"]):
            saw_active_before_all_ready = True
        if prog["step"] in ("DONE", "FAILED"):
            break
        time.sleep(2)
    elapsed = time.time() - started
    check(f"provisioned end to end in {elapsed:.0f}s (< 10 min)", prog["step"] == "DONE" and elapsed < 600, prog)
    check("every service acknowledged before it went live", not saw_active_before_all_ready
          and all(x["state"] == "READY" for x in prog["services"]), prog["services"])
    s, t = call("GET", f"/tenants/{KEY}", token=op)
    check("tenant is ACTIVE", t["status"] == "ACTIVE", t)
    s, hist = call("GET", f"/tenants/{KEY}/history", token=op)
    check("lifecycle history recorded", [h["toStatus"] for h in hist][:3] == ["ACTIVE", "PROVISIONING", "DRAFT"], hist)

    # --- The owner: invitation email -> set password -> sign in with MFA
    link = None
    for _ in range(30):
        body = sql(f"SELECT body FROM civil_engineer_notifications_{KEY}.email_log WHERE recipient='{OWNER}' ORDER BY id DESC LIMIT 1")
        m = re.search(r"/invite/([A-Za-z0-9_-]{20,})", body)
        if m:
            link = m.group(1)
            break
        time.sleep(2)
    check("owner's invitation email was sent, in their workspace", link is not None, "no invitation in email_log")
    host = f"{KEY}.localhost"
    s, preview = call("GET", f"/auth/invitations/{link}", host=host)
    check("invitation link is for the owner", s == 200 and preview["email"] == OWNER, (s, preview))
    s, _ = call("POST", f"/auth/invitations/{link}/accept", {"password": OWNER_PASSWORD}, host=host)
    check("owner sets their own password", s == 200, s)
    s, _ = call("POST", f"/auth/invitations/{link}/accept", {"password": OWNER_PASSWORD}, host=host)
    check("the link works only once", s == 404, s)

    sess = sign_in_with_new_authenticator(OWNER, OWNER_PASSWORD, host=host)
    check("owner signs in to their workspace (after enrolling MFA)", sess["user"]["role"] == "SUPER_ADMIN", sess["user"])
    s, theme = call("GET", "/admin/theme", token=sess["accessToken"], host=host)
    check("owner runs their own console, seeded with the onboarding branding",
          s == 200 and theme["primaryColor"] == "#00695c", (s, theme))
    s, _ = call("GET", "/admin/theme", token=sess["accessToken"])
    check("owner's token is refused on the platform's address", s == 403, s)
    s, pub = call("GET", "/ui-config/public/branding", host=host)
    check("sign-in screen shows the new workspace's brand", s == 200 and pub["brandName"] == f"Demo {KEY}", (s, pub))
finally:
    if op:
        # A live demo tenant is archived; one that never went live is discarded (archiving is only
        # for tenants that served traffic — the lifecycle refuses it otherwise).
        s, t = call("GET", f"/tenants/{KEY}", token=op)
        if t and t.get("status") == "ACTIVE":
            call("PATCH", f"/tenants/{KEY}/status", {"status": "ARCHIVED"}, token=op)
        else:
            for _ in range(20):
                s, _ = call("DELETE", f"/tenants/{KEY}", token=op)
                if s == 204: break
                time.sleep(3)
    reset_operator_mfa()
    print(f"(demo tenant '{KEY}' cleaned up; operator MFA reset)")

print("\nALL PASSED" if ok else "\nSOME FAILED")
sys.exit(0 if ok else 1)
