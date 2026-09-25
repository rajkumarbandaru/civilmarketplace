"""Phase 5 exit criterion, live: the hybrid tenant flow (architecture 07, Diagram 14) end to end,
with the B2B extras: contracts, dispatch under e-way bills, notifications and invoice payment.

On the platform workspace, with the seeded dev accounts:

  B2C  a homeowner books work and it is assigned to a contractor;
  B2B  the supplier's existing account becomes an organization (party migration), its published
       material rate its catalogue; it offers the contractor's firm a contract (rates, Net 30,
       credit limit) which the firm accepts. The firm — one organization that is both CONTRACTOR
       and BUYER — raises an RFQ for the booking's materials; the contract caps the supplier's
       quote; the firm accepts, a colleague approves (never whoever raised it), the supplier
       acknowledges and dispatches under e-way bills, the firm receives against each dispatch,
       the three-way match rejects over-billing and over-pricing, the order closes, a second
       order is refused for the credit limit, the invoices are paid through payment-service
       (Razorpay test mode), and once paid the credit is free again.

Also: plan gating at the gateway, provisioning of a new tenant's procurement storage, people
told by in-app notification and email at each step, and an audit trail. Demo data is removed.

    python3 scripts/procurement/phase5_hybrid_flow_check.py
"""
import hashlib, hmac, os, sys, time
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "lib"))
from live import Checks, call, sql, sign_in_enrolling, reset_mfa, publish_new_tenant, wait_until, OP_EMAIL, OP_PASSWORD

check = Checks()
RUN = str(int(time.time()) % 1000000)
KEY = "b2b" + RUN
HOST = f"{KEY}.localhost"
SCHEMA = "civil_engineer_procurement_platform"
PREFIX = "P5 "
PASSWORD = "Password123!"
ENV = dict(l.strip().split("=", 1) for l in open(os.path.join(os.path.dirname(__file__), "..", "..", "docker", ".env"))
           if "=" in l and not l.lstrip().startswith("#"))
created_orgs = []   # organizations this run made from accounts (removed at the end)
published_rates = []


def login(email):
    s, r = call("POST", "/auth/login", {"email": email, "password": PASSWORD})
    assert s == 200 and r.get("accessToken"), (email, s, r)
    return r["accessToken"], r["user"]["id"]


def cleanup(extra_orgs=()):
    ids = sql(f"SELECT id FROM {SCHEMA}.organizations WHERE name LIKE '{PREFIX}%'").split() + [str(i) for i in extra_orgs]
    if not ids:
        return
    orgs = ",".join(ids)
    pos = f"SELECT id FROM {SCHEMA}.purchase_orders WHERE buyer_org_id IN ({orgs}) OR supplier_org_id IN ({orgs})"
    rfqs = f"SELECT id FROM {SCHEMA}.rfqs WHERE buyer_org_id IN ({orgs})"
    for q in [
        f"DELETE FROM {SCHEMA}.supplier_invoices WHERE purchase_order_id IN ({pos})",
        f"DELETE FROM {SCHEMA}.goods_receipts WHERE purchase_order_id IN ({pos})",
        f"DELETE FROM {SCHEMA}.dispatches WHERE purchase_order_id IN ({pos})",
        f"DELETE FROM {SCHEMA}.purchase_orders WHERE id IN (SELECT id FROM ({pos}) x)",
        f"DELETE FROM {SCHEMA}.quotations WHERE rfq_id IN ({rfqs})",
        f"DELETE FROM {SCHEMA}.rfqs WHERE id IN (SELECT id FROM ({rfqs}) x)",
        f"DELETE FROM {SCHEMA}.organizations WHERE id IN ({orgs})",
    ]:
        sql(q)


def org(token, name, caps):
    s, o = call("POST", "/procurement/organizations", {"name": PREFIX + name + " " + RUN, "capabilities": caps}, token=token)
    assert s == 201, (name, s, o)
    return o


def notified(user_id, type_):
    return int(sql(f"SELECT COUNT(*) FROM civil_engineer_notifications_platform.notifications WHERE user_id={user_id} "
                   f"AND type='{type_}' AND created_at > NOW() - INTERVAL 15 MINUTE").strip() or 0) > 0


def emailed(recipient, subject_part):
    return int(sql(f"SELECT COUNT(*) FROM civil_engineer_notifications_platform.email_log WHERE recipient='{recipient}' "
                   f"AND subject LIKE '%{subject_part}%' AND created_at > NOW() - INTERVAL 15 MINUTE").strip() or 0) > 0


cleanup()
reset_mfa(OP_EMAIL)
op = None
deepak = None
try:
    # ------------------------------------------------------------------ platform wiring
    s, _ = call("GET", "/procurement/organizations/mine")
    check("procurement is routed on the platform workspace (401 without sign-in)", s == 401, s)
    op = sign_in_enrolling(OP_EMAIL, OP_PASSWORD)["accessToken"]
    s, plans = call("GET", "/tenants/plans", token=op)
    by_key = {p["key"]: p for p in plans["plans"]}
    check("Professional and Enterprise include procurement; Starter can add it",
          "procurement" in by_key["professional"]["features"] and "procurement" in by_key["enterprise"]["features"]
          and "procurement" not in by_key["starter"]["features"] and any(a["key"] == "procurement" for a in plans["addOns"]))
    p = publish_new_tenant(op, KEY, "starter", f"owner@{KEY}.example.com")
    svc = {x["service"]: x["state"] for x in p["services"]}
    check("a new tenant goes live only after procurement-service built its storage",
          p["step"] == "DONE" and svc.get("procurement-service") == "READY", svc)
    check("on Starter, procurement is refused at the gateway (404)",
          wait_until(lambda: call("GET", "/procurement/rfqs", host=HOST)[0] == 404, timeout=45))
    call("PUT", f"/tenants/{KEY}/subscription", {"plan": "professional", "addOns": []}, token=op)
    check("upgrading routes it within 30s", wait_until(lambda: call("GET", "/procurement/rfqs", host=HOST)[0] == 401, timeout=45))
    s, _ = call("GET", "/payments/internal/orders")
    s2, _ = call("GET", "/users/internal/material-prices/1")
    check("the new internal endpoints are hidden at the gateway (404)", s == 404 and s2 == 404, (s, s2))

    # ------------------------------------------------------------------ people
    homeowner, _ = login("customer@civileng.test")
    admin, _ = login("admin@civileng.test")
    ravi, ravi_id = login("contractor@civileng.test")       # contractor firm: owner, raises orders
    anita, anita_id = login("engineer@civileng.test")       # contractor firm: approver
    suresh, _ = login("worker@civileng.test")               # contractor firm: site staff
    deepak, deepak_id = login("supplier@civileng.test")     # cement supplier (MATERIAL_SUPPLIER account)
    priya, _ = login("architect@civileng.test")             # steel supplier
    vikram, _ = login("surveyor@civileng.test")             # a supplier the firm will not trade with

    # ------------------------------------------------------------------ B2C
    s, booking = call("POST", "/bookings", {"serviceCategory": "Construction", "serviceName": "House extension",
                                             "bookingType": "INSTANT", "city": "Hyderabad",
                                             "description": "Ground-floor extension, 400 sq ft"}, token=homeowner)
    check("B2C: the homeowner books a house extension", s in (200, 201), (s, booking))
    s, assigned = call("POST", f"/bookings/{booking['id']}/assign/{ravi_id}", token=admin)
    check("B2C: the booking is assigned to the contractor", s == 200 and assigned.get("workerId") == ravi_id, (s, assigned))

    # ------------------------------------------------------------------ party migration
    s, rate = call("POST", "/users/materials/my-prices", {"materialItem": {"id": 2}, "price": 395, "city": "Hyderabad",
                                                        "brand": "UltraTech"}, token=deepak)
    check("the supplier has a published material rate (user-service)", s == 201, (s, rate))
    published_rates.append(rate["id"])
    already = sql(f"SELECT id FROM {SCHEMA}.organizations WHERE source_user_id={deepak_id}").strip()
    s, report = call("POST", "/procurement/organizations/migration?dryRun=true", token=admin)
    mine = [e for e in report.get("entries", []) if e["email"] == "supplier@civileng.test"]
    check("staff preview the migration: the supplier account and its rate are listed, nothing written",
          s == 200 and report["dryRun"] and mine and (mine[0]["outcome"] == "ALREADY_MIGRATED" or mine[0]["catalogueItems"] >= 1)
          and sql(f"SELECT COUNT(*) FROM {SCHEMA}.organizations WHERE source_user_id={deepak_id}").strip() == ("1" if already else "0"),
          (s, report))
    s, _ = call("POST", "/procurement/organizations/migration?dryRun=true", token=ravi)
    check("only staff may run the migration (403)", s == 403, s)
    s, cement = call("POST", "/procurement/organizations/from-profile", token=deepak)
    check("the supplier's own account becomes a SUPPLIER organization it owns",
          s == 201 and cement["capabilities"] == ["SUPPLIER"] and cement["myRole"] == "OWNER", (s, cement))
    if not already:
        created_orgs.append(cement["id"])
    s, again = call("POST", "/procurement/organizations/from-profile", token=deepak)
    check("doing it twice returns the same organization", again["id"] == cement["id"], again)
    s, lists = call("GET", "/procurement/price-lists", token=deepak)
    cat = [p for p in lists if not p["contract"] and p["supplier"]["id"] == cement["id"]]
    check("its published rate became its catalogue",
          already or (cat and any(i["description"].startswith("OPC 53 Grade Cement") and i["unitPrice"] == 395
                                  for i in cat[0]["items"])), cat)
    s, _ = call("POST", "/procurement/organizations/from-profile", token=homeowner)
    check("a customer account has no organization to set up (400)", s == 400, s)

    # ------------------------------------------------------------------ organizations and a contract
    buildco = org(ravi, "BuildCo", ["CONTRACTOR", "BUYER"])
    steel = org(priya, "SteelCo", ["SUPPLIER"])
    shady = org(vikram, "ShadyCo", ["SUPPLIER"])
    for email, role in [("engineer@civileng.test", "APPROVER"), ("worker@civileng.test", "MEMBER")]:
        call("POST", f"/procurement/organizations/{buildco['id']}/members", {"email": email, "role": role}, token=ravi)
    call("PUT", f"/procurement/organizations/{buildco['id']}/relationships", {"targetOrgId": shady["id"], "type": "BLOCKED"}, token=ravi)
    call("PUT", f"/procurement/organizations/{buildco['id']}/relationships",
         {"targetOrgId": steel["id"], "type": "PREFERRED_SUPPLIER"}, token=ravi)

    contract_body = {"supplierOrgId": cement["id"], "buyerOrgId": buildco["id"], "name": "Cement & steel 2026-27",
                     "paymentTermsDays": 30, "creditLimit": 450000,
                     "items": [{"description": "OPC 53 cement", "uom": "bag", "unitPrice": 390, "taxPercent": 28},
                               {"description": "TMT bar 12mm", "uom": "tonne", "unitPrice": 64000, "taxPercent": 18}]}
    s, _ = call("POST", "/procurement/price-lists/contracts", contract_body, token=ravi)
    check("only the supplier can offer its contract (403)", s == 403, s)
    s, contract = call("POST", "/procurement/price-lists/contracts", contract_body, token=deepak)
    check("the supplier offers the firm a contract: rates, Net 30, ₹4,50,000 credit", s == 201 and contract["status"] == "PROPOSED",
          (s, contract))
    check("the firm's approvers are told (in-app)",
          wait_until(lambda: notified(anita_id, "PROCUREMENT_CONTRACT_PROPOSED"), timeout=30))
    s, _ = call("POST", f"/procurement/price-lists/contracts/{contract['id']}/accept", token=suresh)
    check("site staff cannot accept a contract (403)", s == 403, s)
    s, contract = call("POST", f"/procurement/price-lists/contracts/{contract['id']}/accept", token=ravi)
    check("the firm accepts: the contract is in force", s == 200 and contract["status"] == "ACTIVE", (s, contract))
    s, directory = call("GET", f"/procurement/organizations/directory?capability=SUPPLIER&asOrg={buildco['id']}", token=ravi)
    ids = [d["id"] for d in directory]
    check("directory: contracted supplier first, then preferred; blocked absent",
          ids[:2] == [cement["id"], steel["id"]] and shady["id"] not in ids,
          [(d["name"], d["contracted"], d["preferred"]) for d in directory])

    # ------------------------------------------------------------------ RFQ under the contract
    rfq_body = {"buyerOrgId": buildco["id"], "title": "Cement and steel for the extension",
                "deliverySite": "Plot 12, Hyderabad", "reference": f"Booking #{booking['id']}",
                "lines": [{"description": "OPC 53 cement", "quantity": 500, "uom": "bag"},
                          {"description": "TMT bar 12mm", "quantity": 2, "uom": "tonne"}],
                "supplierOrgIds": [cement["id"], steel["id"], shady["id"]]}
    s, e = call("POST", "/procurement/rfqs", rfq_body, token=ravi)
    check("a blocked supplier cannot be invited (400)", s == 400, (s, e))
    rfq_body["supplierOrgIds"] = [cement["id"], steel["id"]]
    s, rfq = call("POST", "/procurement/rfqs", rfq_body, token=ravi)
    check("B2B: the contractor raises an RFQ for the booking's materials", s == 201 and rfq["status"] == "OPEN", (s, rfq))
    l1, l2 = [l["id"] for l in rfq["lines"]]
    check("invited suppliers are told (in-app and email)",
          wait_until(lambda: notified(deepak_id, "PROCUREMENT_RFQ_INVITED") and emailed("supplier@civileng.test", "asks for your quotation"),
                     timeout=30))
    s, seen = call("GET", f"/procurement/rfqs/{rfq['id']}", token=deepak)
    hints = {h["rfqLineId"]: (h["source"], h["unitPrice"]) for h in seen["priceHints"]}
    check("the contracted supplier sees its contract rates as the ceiling", hints == {l1: ("CONTRACT", 390.0), l2: ("CONTRACT", 64000.0)}, hints)

    def quote(token, org_id, cement_price, steel_price):
        return call("POST", f"/procurement/rfqs/{rfq['id']}/quotations", {"supplierOrgId": org_id, "lines": [
            {"rfqLineId": l1, "unitPrice": cement_price, "taxPercent": 28},
            {"rfqLineId": l2, "unitPrice": steel_price, "taxPercent": 18}]}, token=token)

    s, e = quote(deepak, cement["id"], 395, 64000)
    check("quoting above the contract rate is refused (400)", s == 400 and "contract rate" in e.get("message", ""), (s, e))
    s, _ = quote(deepak, cement["id"], 390, 64000)
    s2, _ = quote(priya, steel["id"], 395, 64000)
    check("both suppliers quote", s == 200 and s2 == 200, (s, s2))
    check("the buyer hears of each quotation", wait_until(lambda: notified(ravi_id, "PROCUREMENT_QUOTATION_RECEIVED"), timeout=30))
    s, view = call("GET", f"/procurement/rfqs/{rfq['id']}", token=ravi)
    totals = [(q["supplier"]["id"], q["total"]) for q in view["quotations"]]
    # Cement co: 500×390 +28% = 249,600 ; 2×64,000 +18% = 151,040 → 400,640. Steel co: 403,840.
    check("the buyer compares both, cheapest first", totals == [(cement["id"], 400640.0), (steel["id"], 403840.0)], totals)

    # ------------------------------------------------------------------ PO, approval, acknowledgement
    s, po = call("POST", f"/procurement/rfqs/{rfq['id']}/quotations/{view['quotations'][0]['id']}/accept", token=ravi)
    check("the PO carries the contract's Net 30 and awaits approval above ₹1,00,000",
          s == 201 and po["status"] == "PENDING_APPROVAL" and po["paymentTermsDays"] == 30 and po["contractId"] == contract["id"], (s, po))
    pid = po["id"]
    check("the approver is told, in-app and by email",
          wait_until(lambda: notified(anita_id, "PROCUREMENT_PO_APPROVAL_NEEDED")
                     and emailed("engineer@civileng.test", "needs your approval"), timeout=30))
    check("the losing supplier is told", wait_until(lambda: notified(
        int(sql("SELECT id FROM civil_engineer_auth_platform.users WHERE email='architect@civileng.test'").strip()),
        "PROCUREMENT_QUOTATION_DECLINED"), timeout=30))
    s, _ = call("POST", f"/procurement/purchase-orders/{pid}/approve", token=ravi)
    check("whoever raised it cannot approve it (403)", s == 403, s)
    s, po = call("POST", f"/procurement/purchase-orders/{pid}/approve", token=anita)
    check("a second person approves it", s == 200 and po["status"] == "ISSUED", (s, po))
    check("the supplier is told of the new order", wait_until(lambda: notified(deepak_id, "PROCUREMENT_PO_ISSUED"), timeout=30))
    s, _ = call("GET", f"/procurement/purchase-orders/{pid}", token=priya)
    check("the losing supplier cannot see the order (404)", s == 404, s)
    s, _ = call("POST", f"/procurement/purchase-orders/{pid}/dispatches",
                {"vehicleNumber": "TS09AB1234", "lines": [{"poLineId": po["lines"][0]["id"], "quantity": 1}]}, token=deepak)
    check("nothing can be dispatched before acknowledgement (409)", s == 409, s)
    s, po = call("POST", f"/procurement/purchase-orders/{pid}/acknowledge", token=deepak)
    check("the supplier acknowledges", s == 200 and po["status"] == "ACKNOWLEDGED", (s, po))
    p1, p2 = [l["id"] for l in po["lines"]]

    # ------------------------------------------------------------------ dispatch + GRN
    first = {"vehicleNumber": "ts09ab1234", "transporter": "VRL Logistics",
             "lines": [{"poLineId": p1, "quantity": 320}, {"poLineId": p2, "quantity": 2}]}
    s, e = call("POST", f"/procurement/purchase-orders/{pid}/dispatches", first, token=deepak)
    check("goods worth over ₹50,000 cannot move without an e-way bill (400)", s == 400 and "e-way bill" in e.get("message", ""), (s, e))
    first["ewayBillNumber"] = "331000123456"
    s, po = call("POST", f"/procurement/purchase-orders/{pid}/dispatches", first, token=deepak)
    d1 = po["dispatches"][0] if s == 200 else {}
    check("dispatched under e-way bill 331000123456 on TS09AB1234",
          s == 200 and d1["ewayBillNumber"] == "331000123456" and d1["vehicleNumber"] == "TS09AB1234", (s, po))
    check("the buyer is told the goods are on the way", wait_until(lambda: notified(ravi_id, "PROCUREMENT_DISPATCHED"), timeout=30))
    s, po = call("POST", f"/procurement/purchase-orders/{pid}/receipts", {"dispatchId": d1["id"], "lines": [
        {"poLineId": p1, "receivedQty": 320, "rejectedQty": 20}, {"poLineId": p2, "receivedQty": 2, "rejectedQty": 0}],
        "notes": "20 bags wet"}, token=suresh)
    check("site staff receive that delivery: 20 bags rejected, partly received",
          s == 200 and po["status"] == "PARTIALLY_RECEIVED" and po["lines"][0]["acceptedQty"] == 300
          and po["dispatches"][0]["receiptId"] == po["receipts"][0]["id"], (s, po))
    s, _ = call("POST", f"/procurement/purchase-orders/{pid}/receipts", {"dispatchId": d1["id"], "lines": [
        {"poLineId": p1, "receivedQty": 1, "rejectedQty": 0}]}, token=ravi)
    check("a delivery is received once (409)", s == 409, s)

    # ------------------------------------------------------------------ invoices + three-way match
    def invoice(number, cement_qty, cement_price, with_steel=True):
        ls = [{"poLineId": p1, "quantity": cement_qty, "unitPrice": cement_price, "taxPercent": 28}]
        if with_steel:
            ls.append({"poLineId": p2, "quantity": 2, "unitPrice": 64000, "taxPercent": 18})
        return call("POST", f"/procurement/purchase-orders/{pid}/invoices", {"invoiceNumber": number, "lines": ls}, token=deepak)

    s, po = invoice(f"DS-{RUN}-1", 400, 390)
    bad = po["invoices"][-1]
    check("billing 400 bags when 300 were accepted fails the three-way match",
          bad["status"] == "EXCEPTION" and any("only 300" in m for m in bad["matchIssues"]), bad)
    s, _ = call("POST", f"/procurement/purchase-orders/{pid}/invoices/{bad['id']}/approve", token=anita)
    check("a failed match cannot be approved (409)", s == 409, s)
    call("POST", f"/procurement/purchase-orders/{pid}/invoices/{bad['id']}/reject", {"note": "Bill what was accepted"}, token=anita)
    check("the supplier is told its invoice was rejected",
          wait_until(lambda: notified(deepak_id, "PROCUREMENT_INVOICE_REJECTED"), timeout=30))
    s, po = invoice(f"DS-{RUN}-2", 300, 397.8)
    good = po["invoices"][-1]
    check("the corrected invoice, within 2% of the order's price, matches", good["status"] == "MATCHED", good)
    s, _ = invoice(f"DS-{RUN}-2", 1, 390, with_steel=False)
    check("the same invoice number twice is refused (409)", s == 409, s)

    s, po = call("POST", f"/procurement/purchase-orders/{pid}/dispatches", {"vehicleNumber": "TS09CD5678",
                 "ewayBillNumber": "331000123457", "lines": [{"poLineId": p1, "quantity": 200}]}, token=deepak)
    check("the 20 rejected bags and the rest follow on a second dispatch", s == 200 and len(po["dispatches"]) == 2, (s, po))
    s, po = call("POST", f"/procurement/purchase-orders/{pid}/receipts", {"dispatchId": po["dispatches"][1]["id"], "lines": [
        {"poLineId": p1, "receivedQty": 200, "rejectedQty": 0}]}, token=suresh)
    check("received in full", s == 200 and po["status"] == "RECEIVED", (s, po))
    s, po = invoice(f"DS-{RUN}-3", 200, 390, with_steel=False)
    last = po["invoices"][-1]
    s, _ = call("POST", f"/procurement/purchase-orders/{pid}/invoices/{last['id']}/approve", token=suresh)
    check("site staff cannot approve invoices (403)", s == 403, s)
    call("POST", f"/procurement/purchase-orders/{pid}/invoices/{good['id']}/approve", token=anita)
    s, po = call("POST", f"/procurement/purchase-orders/{pid}/invoices/{last['id']}/approve", token=anita)
    due = {i["id"]: i["dueDate"] for i in po["invoices"]}
    today = sql("SELECT CURRENT_DATE + INTERVAL 30 DAY").strip()
    check("the order closes; approved invoices are due in 30 days (Net 30)",
          po["status"] == "CLOSED" and due[good["id"]] == today and due[last["id"]] == today, (po["status"], due, today))

    # ------------------------------------------------------------------ credit limit
    s, rfq2 = call("POST", "/procurement/rfqs", {"buyerOrgId": buildco["id"], "title": "More cement",
                   "lines": [{"description": "OPC 53 cement", "quantity": 200, "uom": "bag"}],
                   "supplierOrgIds": [cement["id"]]}, token=ravi)
    s, r2 = call("POST", f"/procurement/rfqs/{rfq2['id']}/quotations", {"supplierOrgId": cement["id"], "lines": [
        {"rfqLineId": rfq2["lines"][0]["id"], "unitPrice": 390, "taxPercent": 28}]}, token=deepak)
    q2 = r2["quotations"][0]["id"]
    s, e = call("POST", f"/procurement/rfqs/{rfq2['id']}/quotations/{q2}/accept", token=ravi)
    check("another ₹99,840 order is refused: ₹4,00,640 unpaid against a ₹4,50,000 credit limit (409)",
          s == 409 and "credit limit" in e.get("message", ""), (s, e))
    s, still = call("GET", f"/procurement/rfqs/{rfq2['id']}", token=ravi)
    check("…and nothing changed: the RFQ is still open", still["status"] == "OPEN", still["status"])

    # ------------------------------------------------------------------ payment (payment-service, Razorpay test mode)
    def pay(invoice_id):
        s, co = call("POST", f"/procurement/purchase-orders/{pid}/invoices/{invoice_id}/pay", token=anita)
        if s != 200:
            return s, co
        payment_id = "pay_p5" + RUN + str(invoice_id)
        signature = hmac.new(ENV["RAZORPAY_KEY_SECRET"].encode(), f"{co['razorpayOrderId']}|{payment_id}".encode(),
                             hashlib.sha256).hexdigest()
        # What Razorpay Checkout's handler hands the browser after a test-card payment.
        return call("POST", "/payments/verify", {"razorpayOrderId": co["razorpayOrderId"], "razorpayPaymentId": payment_id,
                                                  "razorpaySignature": signature}, token=anita)

    s, _ = call("POST", f"/procurement/purchase-orders/{pid}/invoices/{good['id']}/pay", token=suresh)
    check("site staff cannot pay invoices (403)", s == 403, s)
    s, co = call("POST", f"/procurement/purchase-orders/{pid}/invoices/{good['id']}/pay", token=anita)
    check("paying an approved invoice opens a Razorpay order for exactly its total",
          s == 200 and co["razorpayOrderId"].startswith("order_") and co["amount"] == good["total"], (s, co))
    for inv in (good, last):
        s, done = pay(inv["id"])
        check(f"payment of {inv['invoiceNumber']} verified by payment-service", s == 200 and done["paymentStatus"] == "COMPLETED", (s, done))

    def paid():
        s, o = call("GET", f"/procurement/purchase-orders/{pid}", token=ravi)
        return all(i["status"] == "PAID" for i in o["invoices"] if i["id"] in (good["id"], last["id"]))
    check("procurement hears it over Kafka: both invoices PAID", wait_until(paid, timeout=45))
    s, o = call("GET", f"/procurement/purchase-orders/{pid}", token=deepak)
    refs = [i["paymentReference"] for i in o["invoices"] if i["status"] == "PAID"]
    check("the supplier sees the payment references", len(refs) == 2 and all(r.startswith("pay_p5") for r in refs), refs)
    check("the supplier is told it was paid", wait_until(lambda: notified(deepak_id, "PROCUREMENT_INVOICE_PAID"), timeout=30))
    s, _ = call("POST", f"/procurement/purchase-orders/{pid}/invoices/{good['id']}/pay", token=anita)
    check("a paid invoice cannot be paid again (409)", s == 409, s)
    s, po2 = call("POST", f"/procurement/rfqs/{rfq2['id']}/quotations/{q2}/accept", token=ravi)
    check("paid up, the credit is free: the second order goes through on Net 30",
          s == 201 and po2["paymentTermsDays"] == 30, (s, po2))

    # ------------------------------------------------------------------ the hybrid picture
    s, as_seller = call("GET", "/bookings/worker", token=ravi)
    s, as_buyer = call("GET", "/procurement/purchase-orders", token=ravi)
    check("the contractor is at once the seller of the booking and the buyer of its materials",
          any(b["id"] == booking["id"] for b in as_seller["content"]) and any(o["id"] == pid for o in as_buyer))
    check("every procurement step is in the audit trail", wait_until(lambda: int(sql(
        "SELECT COUNT(*) FROM civil_engineer_audit_platform.audit_events WHERE source_service='procurement-service' "
        "AND recorded_at > NOW() - INTERVAL 15 MINUTE").strip() or 0) >= 25, timeout=30))
finally:
    cleanup(created_orgs)
    if deepak:
        for rid in published_rates:
            call("DELETE", f"/users/materials/my-prices/{rid}", token=deepak)
    if op:
        call("PATCH", f"/tenants/{KEY}/status", {"status": "ARCHIVED"}, token=op)
    reset_mfa(OP_EMAIL)
    print(f"(demo organizations and rates removed; demo tenant '{KEY}' archived; operator MFA reset)")

print("\nALL PASSED" if check.ok else "\nSOME FAILED")
sys.exit(0 if check.ok else 1)
