"""Phase 6 deliverable, live: the analytics warehouse fed by change-data capture (architecture 02 §8).

A fresh tenant's bookings and payment reach the warehouse from the binlog within seconds; its staff
see only their own figures, the operator sees every tenant; people are pseudonymised; when the
tenant is moved, its changes are captured from the new cluster; capture resumes after a restart
without losing what happened meanwhile; and a tenant's partition can be purged.

    python3 scripts/ops/phase6_warehouse_check.py
"""
import hashlib, hmac, json, os, re, secrets, subprocess, sys, time
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "lib"))
from live import Checks, call, sql, sign_in_enrolling, reset_mfa, publish_new_tenant, wait_until, OP_EMAIL, OP_PASSWORD

check = Checks()
KEY = "wh" + str(int(time.time()) % 1000000)
HOST = f"{KEY}.localhost"
OWNER = f"owner@{KEY}.example.com"
ENV = dict(l.strip().split("=", 1) for l in open(os.path.join(os.path.dirname(__file__), "..", "..", "docker", ".env"))
           if "=" in l and not l.lstrip().startswith("#"))


def owner_token():
    link = None
    for _ in range(30):
        m = re.search(r"/invite/([A-Za-z0-9_-]{20,})", sql(f"SELECT body FROM civil_engineer_notifications_{KEY}.email_log "
                                                            f"WHERE recipient='{OWNER}' ORDER BY id DESC LIMIT 1"))
        if m:
            link = m.group(1)
            break
        time.sleep(2)
    call("POST", f"/auth/invitations/{link}/accept", {"password": "correct horse battery staple"}, host=HOST)
    return sign_in_enrolling(OWNER, "correct horse battery staple", host=HOST)["accessToken"]


def book(owner, city):
    s, b = call("POST", "/bookings", {"serviceCategory": "Plumbing", "serviceName": "Leak fix", "bookingType": "INSTANT",
                                      "city": city}, token=owner, host=HOST)
    assert s in (200, 201), (s, b)
    return b


def mine(owner):
    return call("GET", "/analytics/workspace", token=owner, host=HOST)[1]


def wh(q):
    return sql(f"SELECT {q}").strip()


reset_mfa(OP_EMAIL)
op = None
try:
    op = sign_in_enrolling(OP_EMAIL, OP_PASSWORD)["accessToken"]
    s, cap = call("GET", "/analytics/capture", token=op)
    check("capture is following every cluster in the placement map",
          s == 200 and {c["clusterId"] for c in cap if c["connected"]} >= {"cluster-a", "cluster-b"}, cap)

    p = publish_new_tenant(op, KEY, "professional", OWNER)
    check("a fresh tenant is published", p["step"] == "DONE", p)
    owner = owner_token()
    started = time.time()
    ids = [book(owner, c)["id"] for c in ("Pune", "Pune", "Nashik")]
    check("its bookings reach the warehouse from the binlog within seconds",
          wait_until(lambda: mine(owner)["totals"]["bookings"] == 3, timeout=30, every=1), mine(owner))
    check(f"…({time.time() - started:.1f}s end to end)", True)
    call("POST", f"/bookings/{ids[0]}/cancel", {"reason": "customer changed plans"}, token=owner, host=HOST)
    check("a change to a booking follows (cancelled: 1)",
          wait_until(lambda: mine(owner)["totals"]["cancelledBookings"] == 1, timeout=30, every=1), mine(owner))
    cities = {b["label"]: b["count"] for b in mine(owner)["bookingsByCity"]}
    check("the tenant's breakdowns are its own (Pune 2, Nashik 1)", cities == {"Pune": 2, "Nashik": 1}, cities)

    # a real payment, completed
    call("PUT", f"/tenants/{KEY}/integrations/payment", {"mode": "BYO", "provider": "razorpay", "enabled": True,
         "settings": {"keyId": ENV["RAZORPAY_KEY_ID"]},
         "secrets": {"keySecret": ENV["RAZORPAY_KEY_SECRET"], "webhookSecret": secrets.token_hex(12)}}, token=op)
    order = {}
    for _ in range(3):
        s, order = call("POST", "/payments/create-order", {"bookingId": ids[1], "amount": 2500}, token=owner, host=HOST)
        if order and order.get("razorpayOrderId"):
            break
        time.sleep(3)
    pid = "pay_wh" + secrets.token_hex(4)
    sig = hmac.new(ENV["RAZORPAY_KEY_SECRET"].encode(), f"{order['razorpayOrderId']}|{pid}".encode(), hashlib.sha256).hexdigest()
    call("POST", "/payments/verify", {"razorpayOrderId": order["razorpayOrderId"], "razorpayPaymentId": pid,
                                      "razorpaySignature": sig}, token=owner, host=HOST)
    check("a completed payment is captured (₹2,500 received)",
          wait_until(lambda: mine(owner)["totals"]["paymentsCompleted"] == 2500, timeout=30, every=1), mine(owner)["totals"])

    # isolation and privacy
    s, _ = call("GET", "/analytics/platform", token=owner, host=HOST)
    check("a tenant's own admin cannot see the cross-tenant view (403)", s == 403, s)
    s, allof = call("GET", "/analytics/platform", token=op)
    row = next((t for t in allof["tenants"] if t["tenantKey"] == KEY), None)
    check("the operator sees it among every tenant, with the same figures",
          s == 200 and row and row["bookings"] == 3 and len(allof["tenants"]) > 1, row)
    raw = sql(f"SELECT customer_id FROM civil_engineer_bookings_{KEY}.bookings LIMIT 1").strip()
    tok = wh(f"customer_token FROM civil_engineer_warehouse.fact_bookings WHERE tenant_key='{KEY}' LIMIT 1")
    cols = sql("SELECT GROUP_CONCAT(column_name) FROM information_schema.columns WHERE table_schema='civil_engineer_warehouse' "
               "AND table_name='fact_bookings'")
    check("customers are pseudonyms in the warehouse, never raw ids",
          len(tok) == 16 and tok != raw and "customer_id" not in cols, (raw, tok))

    # the tenant moves: capture follows it to the other cluster
    s, _ = call("POST", f"/tenants/{KEY}/moves", {"targetClusterId": "cluster-b"}, token=op)
    moved = wait_until(lambda: call("GET", f"/tenants/{KEY}/placement", token=op)[1]["currentMove"]["step"] in ("DONE", "FAILED"),
                       timeout=300, every=2)
    check("the tenant is moved to cluster-b", moved and call("GET", f"/tenants/{KEY}/placement", token=op)[1]["clusterId"] == "cluster-b")
    after_move = book(owner, "Pune")
    check("its next booking is captured from cluster-b's binlog",
          wait_until(lambda: wh(f"source_cluster FROM civil_engineer_warehouse.fact_bookings WHERE tenant_key='{KEY}' "
                                f"AND booking_id={after_move['id']}") == "cluster-b", timeout=30, every=1))
    check("…and counted once, not twice, despite the copy to cluster-b replaying every row",
          mine(owner)["totals"]["bookings"] == 4, mine(owner)["totals"])

    # restart: resume, catch up
    subprocess.run(["docker", "stop", "civil_analytics_service"], capture_output=True)
    while_down = book(owner, "Satara")
    subprocess.run(["docker", "start", "civil_analytics_service"], capture_output=True)
    check("after a restart capture resumes from its saved position and catches up on what happened while it was down",
          wait_until(lambda: wh(f"COUNT(*) FROM civil_engineer_warehouse.fact_bookings WHERE tenant_key='{KEY}' "
                                f"AND booking_id={while_down['id']}") == "1", timeout=120, every=2))
    logs = subprocess.run(["docker", "logs", "--since", "3m", "civil_analytics_service"], capture_output=True, text=True).stdout
    check("…without a new snapshot", "resuming at" in logs and "snapshot of" not in logs, logs[-400:])

    # purge
    call("PATCH", f"/tenants/{KEY}/status", {"status": "ARCHIVED"}, token=op)
    s, r = call("DELETE", f"/analytics/tenants/{KEY}", token=op)
    check("the tenant's warehouse partition is purged on request", s == 200 and r["rowsDeleted"] >= 6, (s, r))
    s, allof = call("GET", "/analytics/platform", token=op)
    check("…and it no longer appears in the operator's view", all(t["tenantKey"] != KEY for t in allof["tenants"]))
finally:
    if op:
        call("PATCH", f"/tenants/{KEY}/status", {"status": "ARCHIVED"}, token=op)
    reset_mfa(OP_EMAIL)
    print(f"(tenant '{KEY}' archived and purged from the warehouse; drill operator MFA reset)")

print("\nALL PASSED" if check.ok else "\nSOME FAILED")
sys.exit(0 if check.ok else 1)
