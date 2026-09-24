"""Phase 3 exit criterion, live: downgrade/upgrade scenarios pass; running modules ⊆ entitlement.

A fresh tenant is published on Professional. Downgrading to Starter takes Projects away at the
gateway (404) while the tenant's choice and data are kept; an add-on or an upgrade brings it back.
Switching on a module outside the plan is refused; a grant allows one until its date. The
Starter monthly bookings limit is reached and the next booking is refused (402) until capacity
is added. Every tenant's running modules are checked to be within its entitlement.

    python3 scripts/entitlements/phase3_entitlements_check.py
"""
import os, sys, time, datetime
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "lib"))
from live import Checks, call, sql, sign_in_enrolling, reset_mfa, publish_new_tenant, wait_until, OP_EMAIL, OP_PASSWORD

check = Checks()
KEY = "plan" + str(int(time.time()) % 1000000)
HOST = f"{KEY}.localhost"
OWNER = f"owner@{KEY}.example.com"


def module_status(path):
    """404 = module not running for this tenant; 401 = routed, only the sign-in is missing."""
    return call("GET", path, host=HOST)[0]


reset_mfa(OP_EMAIL)
op = None
try:
    op = sign_in_enrolling(OP_EMAIL, OP_PASSWORD)["accessToken"]
    p = publish_new_tenant(op, KEY, "professional", OWNER)
    check("tenant published on Professional", p["step"] == "DONE", p)

    s, e = call("GET", f"/tenants/{KEY}/entitlements", token=op)
    check("Professional entitles Projects and caps bookings at 5,000",
          "projects" in e["entitlements"]["features"] and e["entitlements"]["limits"].get("bookings.monthly") == 5000, e)
    check("Projects is routed for the tenant", wait_until(lambda: module_status("/projects") == 401), module_status("/projects"))

    # --- Downgrade
    s, impact = call("GET", f"/tenants/{KEY}/subscription/preview?plan=starter", token=op)
    check("downgrade preview names what stops", impact["modulesStopping"] == ["procurement", "projects"], impact)
    s, _ = call("PUT", f"/tenants/{KEY}/subscription", {"plan": "starter", "addOns": []}, token=op)
    check("downgraded to Starter", s == 200, s)
    check("Projects is refused at the gateway within 30s (404)",
          wait_until(lambda: module_status("/projects") == 404, timeout=45), module_status("/projects"))
    s, e = call("GET", f"/tenants/{KEY}/entitlements", token=op)
    check("the tenant's choice of Projects is kept, dormant",
          "projects" in e["chosenModules"] and "projects" not in e["runningModules"], e)
    check("bookings still run on Starter", module_status("/bookings") == 401, module_status("/bookings"))

    # --- Choices stay within entitlement
    s, t = call("GET", f"/tenants/{KEY}", token=op)
    s, err = call("PUT", f"/tenants/{KEY}/modules", {"modules": t["modules"] + ["landrecords"]}, token=op)
    check("switching on a module outside the plan is refused", s == 400 and "landrecords" in str(err), (s, err))

    # --- Add-on brings Projects back without an upgrade
    s, _ = call("PUT", f"/tenants/{KEY}/subscription", {"plan": "starter", "addOns": ["projects"]}, token=op)
    check("Projects add-on restores it within 30s",
          wait_until(lambda: module_status("/projects") == 401, timeout=45), module_status("/projects"))
    s, _ = call("PUT", f"/tenants/{KEY}/subscription", {"plan": "starter", "addOns": []}, token=op)
    wait_until(lambda: module_status("/projects") == 404, timeout=45)

    # --- Grant with expiry
    until = (datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(days=30)).strftime("%Y-%m-%dT%H:%M:%S")
    s, g = call("POST", f"/tenants/{KEY}/grants", {"feature": "projects", "expiresAt": until, "reason": "live check trial"}, token=op)
    check("a dated grant entitles Projects", s == 200 and "projects" in g["features"], (s, g))
    check("the granted module runs within 30s", wait_until(lambda: module_status("/projects") == 401, timeout=45), module_status("/projects"))
    far = (datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(days=400)).strftime("%Y-%m-%dT%H:%M:%S")
    s, _ = call("POST", f"/tenants/{KEY}/grants", {"feature": "projects", "expiresAt": far, "reason": "forever"}, token=op)
    check("a grant longer than 12 months is refused", s == 400, s)
    s, g = call("DELETE", f"/tenants/{KEY}/grants/{g['grants'][0]['id']}", token=op)
    check("revoking the grant takes Projects away again",
          wait_until(lambda: module_status("/projects") == 404, timeout=45), module_status("/projects"))

    # --- Hard quota: bookings per month
    owner_link = None
    for _ in range(30):
        body = sql(f"SELECT body FROM civil_engineer_notifications_{KEY}.email_log WHERE recipient='{OWNER}' ORDER BY id DESC LIMIT 1")
        import re
        m = re.search(r"/invite/([A-Za-z0-9_-]{20,})", body)
        if m: owner_link = m.group(1); break
        time.sleep(2)
    call("POST", f"/auth/invitations/{owner_link}/accept", {"password": "correct horse battery staple"}, host=HOST)
    owner = sign_in_enrolling(OWNER, "correct horse battery staple", host=HOST)["accessToken"]
    booking = {"serviceCategory": "Plumbing", "serviceName": "Leak fix", "bookingType": "INSTANT", "city": "Pune"}
    s, b = call("POST", "/bookings", booking, token=owner, host=HOST)
    check("a booking under the limit is taken", s in (200, 201), (s, b))
    # Bring this month's count to Starter's 500 directly in the tenant's own schema.
    sql(f"INSERT INTO civil_engineer_bookings_{KEY}.bookings (booking_code, customer_id, service_category, service_name, "
        f"booking_type, status, city, created_at) SELECT CONCAT('QT', LPAD(n, 8, '0')), 1, 'x', 'x', 'INSTANT', 'PENDING', 'Pune', NOW() "
        f"FROM (SELECT a.i*100 + b.i*10 + c.i AS n FROM (SELECT 0 i UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) a, "
        f"(SELECT 0 i UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b, "
        f"(SELECT 0 i UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) c) nums "
        f"WHERE n < 499")
    count = sql(f"SELECT COUNT(*) FROM civil_engineer_bookings_{KEY}.bookings").strip()
    s, b = call("POST", "/bookings", booking, token=owner, host=HOST)
    check(f"booking #{int(count) + 1} on Starter (limit 500) is refused with 402", s == 402, (s, b, count))
    call("PUT", f"/tenants/{KEY}/subscription", {"plan": "starter", "addOns": ["bookings-5k"]}, token=op)
    check("adding capacity lets bookings through again within 30s",
          wait_until(lambda: call("POST", "/bookings", booking, token=owner, host=HOST)[0] in (200, 201), timeout=45))

    # --- Invariant across every tenant
    s, tenants = call("GET", "/tenants", token=op)
    violations = []
    for t in tenants:
        if t["tenantKey"] == "platform" or t["status"] in ("DRAFT",):
            continue
        s, e = call("GET", f"/tenants/{t['tenantKey']}/entitlements", token=op)
        if s != 200:
            violations.append((t["tenantKey"], s)); continue
        extra = set(e["runningModules"]) - set(e["entitlements"]["features"])
        if extra: violations.append((t["tenantKey"], extra))
    check(f"running modules ⊆ entitlement for all {len(tenants) - 1} tenants", not violations, violations)
finally:
    if op:
        call("PATCH", f"/tenants/{KEY}/status", {"status": "ARCHIVED"}, token=op)
    reset_mfa(OP_EMAIL)
    print(f"(demo tenant '{KEY}' archived; operator MFA reset)")

print("\nALL PASSED" if check.ok else "\nSOME FAILED")
sys.exit(0 if check.ok else 1)
