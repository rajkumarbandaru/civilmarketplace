"""Phase 5 exit criterion, live: the hybrid tenant flow (architecture 07, Diagram 14) end to end.

On the platform workspace, with the seeded dev accounts:

  B2C  a homeowner books work and it is assigned to a contractor;
  B2B  the contractor's firm — one organization that is both CONTRACTOR and BUYER — raises an RFQ
       for that booking's materials to two suppliers (a blocked third cannot be invited), compares
       their quotations, accepts the cheaper, gets the order approved by a colleague (never by
       whoever raised it), and runs it through acknowledgement, partial and final goods receipts
       and supplier invoices, with the three-way match catching an over-billed invoice and the
       order closing once everything received is billed and approved.

Also: the module is gated per plan at the gateway (a Starter tenant gets 404, an upgrade routes
it), a new tenant's procurement storage is provisioned before it goes live, and every step is
audited. Demo organizations are removed afterwards.

    python3 scripts/procurement/phase5_hybrid_flow_check.py
"""
import os, sys, time
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "lib"))
from live import Checks, call, sql, sign_in_enrolling, reset_mfa, publish_new_tenant, wait_until, OP_EMAIL, OP_PASSWORD

check = Checks()
RUN = str(int(time.time()) % 1000000)
KEY = "b2b" + RUN
HOST = f"{KEY}.localhost"
SCHEMA = "civil_engineer_procurement_platform"
PREFIX = "P5 "
PASSWORD = "Password123!"


def login(email):
    s, r = call("POST", "/auth/login", {"email": email, "password": PASSWORD})
    assert s == 200 and r.get("accessToken"), (email, s, r)
    return r["accessToken"], r["user"]["id"]


def cleanup():
    ids = sql(f"SELECT id FROM {SCHEMA}.organizations WHERE name LIKE '{PREFIX}%'").split()
    if not ids:
        return
    orgs = ",".join(ids)
    pos = f"SELECT id FROM {SCHEMA}.purchase_orders WHERE buyer_org_id IN ({orgs}) OR supplier_org_id IN ({orgs})"
    rfqs = f"SELECT id FROM {SCHEMA}.rfqs WHERE buyer_org_id IN ({orgs})"
    for q in [
        f"DELETE FROM {SCHEMA}.supplier_invoices WHERE purchase_order_id IN ({pos})",
        f"DELETE FROM {SCHEMA}.goods_receipts WHERE purchase_order_id IN ({pos})",
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


cleanup()
reset_mfa(OP_EMAIL)
op = None
try:
    # ------------------------------------------------------------------ platform wiring
    s, _ = call("GET", "/procurement/organizations/mine")
    check("procurement is routed on the platform workspace (401 without sign-in)", s == 401, s)

    op = sign_in_enrolling(OP_EMAIL, OP_PASSWORD)["accessToken"]
    s, plans = call("GET", "/tenants/plans", token=op)
    by_key = {p["key"]: p for p in plans["plans"]}
    check("Professional and Enterprise now include procurement; Starter does not",
          "procurement" in by_key["professional"]["features"] and "procurement" in by_key["enterprise"]["features"]
          and "procurement" not in by_key["starter"]["features"], {k: v["features"] for k, v in by_key.items()})
    check("procurement is a Starter add-on", any(a["key"] == "procurement" for a in plans["addOns"]), plans["addOns"])

    p = publish_new_tenant(op, KEY, "starter", f"owner@{KEY}.example.com")
    svc = {x["service"]: x["state"] for x in p["services"]}
    check("a new tenant goes live only after procurement-service built its storage",
          p["step"] == "DONE" and svc.get("procurement-service") == "READY", svc)
    check("its procurement schema exists and starts empty",
          sql(f"SELECT COUNT(*) FROM civil_engineer_procurement_{KEY}.organizations").strip() == "0")
    check("on Starter, procurement is refused at the gateway (404)",
          wait_until(lambda: call("GET", "/procurement/rfqs", host=HOST)[0] == 404, timeout=45),
          call("GET", "/procurement/rfqs", host=HOST)[0])
    call("PUT", f"/tenants/{KEY}/subscription", {"plan": "professional", "addOns": []}, token=op)
    check("upgrading to Professional routes it within 30s (401 = only sign-in missing)",
          wait_until(lambda: call("GET", "/procurement/rfqs", host=HOST)[0] == 401, timeout=45),
          call("GET", "/procurement/rfqs", host=HOST)[0])

    # ------------------------------------------------------------------ people
    homeowner, homeowner_id = login("customer@civileng.test")
    admin, _ = login("admin@civileng.test")
    ravi, ravi_id = login("contractor@civileng.test")       # contractor firm: raises
    anita, _ = login("engineer@civileng.test")              # contractor firm: approves
    suresh, _ = login("worker@civileng.test")               # contractor firm: site staff
    deepak, _ = login("supplier@civileng.test")             # cement supplier
    priya, _ = login("architect@civileng.test")             # steel supplier
    vikram, _ = login("surveyor@civileng.test")             # a supplier the firm will not trade with

    # ------------------------------------------------------------------ B2C: homeowner books the contractor
    s, booking = call("POST", "/bookings", {"serviceCategory": "Construction", "serviceName": "House extension",
                                             "bookingType": "INSTANT", "city": "Hyderabad",
                                             "description": "Ground-floor extension, 400 sq ft"}, token=homeowner)
    check("B2C: the homeowner books a house extension", s in (200, 201), (s, booking))
    s, assigned = call("POST", f"/bookings/{booking['id']}/assign/{ravi_id}", token=admin)
    check("B2C: the booking is assigned to the contractor", s == 200 and assigned.get("workerId") == ravi_id, (s, assigned))
    s, mine = call("GET", "/bookings/worker", token=ravi)
    check("B2C: the contractor sees the job as the seller",
          s == 200 and any(b["id"] == booking["id"] for b in mine["content"]), s)

    # ------------------------------------------------------------------ organizations
    buildco = org(ravi, "BuildCo", ["CONTRACTOR", "BUYER"])
    check("one organization carries both roles: CONTRACTOR (sells to the homeowner) and BUYER",
          set(buildco["capabilities"]) == {"CONTRACTOR", "BUYER"} and buildco["myRole"] == "OWNER", buildco)
    cement = org(deepak, "CementCo", ["SUPPLIER"])
    steel = org(priya, "SteelCo", ["SUPPLIER"])
    shady = org(vikram, "ShadyCo", ["SUPPLIER"])
    s, _ = call("POST", f"/procurement/organizations/{buildco['id']}/members",
                {"email": "engineer@civileng.test", "role": "APPROVER"}, token=ravi)
    check("the owner adds a colleague as approver", s == 200, s)
    call("POST", f"/procurement/organizations/{buildco['id']}/members",
         {"email": "worker@civileng.test", "role": "MEMBER"}, token=ravi)
    s, anita_orgs = call("GET", "/procurement/organizations/mine", token=anita)
    check("the colleague acts for the firm as APPROVER on first visit",
          [(o["id"], o["myRole"]) for o in anita_orgs] == [(buildco["id"], "APPROVER")], anita_orgs)
    s, _ = call("POST", f"/procurement/organizations/{buildco['id']}/members",
                {"email": "x@example.com", "role": "MEMBER"}, token=anita)
    check("an approver cannot manage members (403)", s == 403, s)

    call("PUT", f"/procurement/organizations/{buildco['id']}/relationships",
         {"targetOrgId": shady["id"], "type": "BLOCKED"}, token=ravi)
    call("PUT", f"/procurement/organizations/{buildco['id']}/relationships",
         {"targetOrgId": steel["id"], "type": "PREFERRED_SUPPLIER"}, token=ravi)
    s, directory = call("GET", f"/procurement/organizations/directory?capability=SUPPLIER&asOrg={buildco['id']}", token=ravi)
    ids = [d["id"] for d in directory]
    check("supplier directory: preferred first, blocked and self absent",
          ids and ids[0] == steel["id"] and cement["id"] in ids and shady["id"] not in ids and buildco["id"] not in ids,
          [(d["name"], d["preferred"]) for d in directory])
    s, _ = call("GET", f"/procurement/organizations/directory?capability=SUPPLIER&asOrg={buildco['id']}", token=deepak)
    check("nobody can look through another organization's eyes (403)", s == 403, s)

    # ------------------------------------------------------------------ RFQ
    rfq_body = {"buyerOrgId": buildco["id"], "title": "Cement and steel for the extension",
                "deliverySite": "Plot 12, Hyderabad", "reference": f"Booking #{booking['id']}",
                "lines": [{"description": "OPC 53 cement", "quantity": 500, "uom": "bag"},
                          {"description": "TMT bar 12mm", "quantity": 2, "uom": "tonne"}],
                "supplierOrgIds": [cement["id"], steel["id"], shady["id"]]}
    s, e = call("POST", "/procurement/rfqs", rfq_body, token=ravi)
    check("a blocked supplier cannot be invited (400)", s == 400 and "cannot be invited" in e.get("message", ""), (s, e))
    rfq_body["supplierOrgIds"] = [cement["id"], steel["id"]]
    s, e = call("POST", "/procurement/rfqs", rfq_body, token=deepak)
    check("only a member of the buyer can raise its RFQ (403)", s == 403, s)
    s, rfq = call("POST", "/procurement/rfqs", rfq_body, token=ravi)
    check("B2B: the contractor raises an RFQ for the booking's materials",
          s == 201 and rfq["status"] == "OPEN" and rfq["reference"] == f"Booking #{booking['id']}", (s, rfq))
    l1, l2 = [l["id"] for l in rfq["lines"]]
    s, _ = call("GET", f"/procurement/rfqs/{rfq['id']}", token=vikram)
    check("an uninvited supplier cannot see it (404)", s == 404, s)

    def quote(token, org_id, cement_price, steel_price):
        return call("POST", f"/procurement/rfqs/{rfq['id']}/quotations", {"supplierOrgId": org_id, "lines": [
            {"rfqLineId": l1, "unitPrice": cement_price, "taxPercent": 28},
            {"rfqLineId": l2, "unitPrice": steel_price, "taxPercent": 18}]}, token=token)

    s, e = call("POST", f"/procurement/rfqs/{rfq['id']}/quotations", {"supplierOrgId": cement["id"], "lines": [
        {"rfqLineId": l1, "unitPrice": 400, "taxPercent": 28}]}, token=deepak)
    check("a quotation must price every line (400)", s == 400, (s, e))
    s, qc = quote(deepak, cement["id"], 400, 65000)
    s2, qs = quote(priya, steel["id"], 395, 64000)
    check("both suppliers quote", s == 200 and s2 == 200, (s, s2))
    check("a supplier sees only its own quotation",
          [q["supplier"]["id"] for q in qs["quotations"]] == [steel["id"]], qs["quotations"])
    s, view = call("GET", f"/procurement/rfqs/{rfq['id']}", token=ravi)
    totals = [(q["supplier"]["id"], q["total"]) for q in view["quotations"]]
    # Steel: 500×395 = 197,500 + 28% = 252,800 ; 2×64,000 = 128,000 + 18% = 151,040 → 403,840
    check("the buyer compares both, cheapest first, totals incl. GST",
          totals == [(steel["id"], 403840.0), (cement["id"], 409400.0)], totals)

    # ------------------------------------------------------------------ PO + approval (maker-checker)
    s, po = call("POST", f"/procurement/rfqs/{rfq['id']}/quotations/{view['quotations'][0]['id']}/accept", token=ravi)
    check("accepting raises a PO; above the ₹1,00,000 threshold it awaits approval",
          s == 201 and po["status"] == "PENDING_APPROVAL" and po["total"] == 403840.0 and po["number"].startswith("PO-"), (s, po))
    pid = po["id"]
    s, r2 = call("GET", f"/procurement/rfqs/{rfq['id']}", token=deepak)
    check("the RFQ is awarded and the other supplier is told it lost",
          r2["status"] == "AWARDED" and r2["quotations"][0]["status"] == "REJECTED", r2)
    s, _ = call("POST", f"/procurement/purchase-orders/{pid}/acknowledge", token=priya)
    check("the supplier cannot act before approval (409)", s == 409, s)
    s, e = call("POST", f"/procurement/purchase-orders/{pid}/approve", token=ravi)
    check("whoever raised it cannot approve it (403)", s == 403, (s, e))
    s, po = call("POST", f"/procurement/purchase-orders/{pid}/approve", token=anita)
    check("a second person approves it: issued", s == 200 and po["status"] == "ISSUED", (s, po))
    s, _ = call("GET", f"/procurement/purchase-orders/{pid}", token=deepak)
    check("the losing supplier cannot see the order (404)", s == 404, s)
    s, po = call("POST", f"/procurement/purchase-orders/{pid}/acknowledge", token=priya)
    check("the supplier acknowledges", s == 200 and po["status"] == "ACKNOWLEDGED", (s, po))
    p1, p2 = [l["id"] for l in po["lines"]]

    # ------------------------------------------------------------------ GRN
    s, po = call("POST", f"/procurement/purchase-orders/{pid}/receipts", {"lines": [
        {"poLineId": p1, "receivedQty": 320, "rejectedQty": 20}, {"poLineId": p2, "receivedQty": 2, "rejectedQty": 0}],
        "notes": "First truck; 20 bags wet"}, token=ravi)
    check("first delivery received, 20 bags rejected: partly received",
          s == 200 and po["status"] == "PARTIALLY_RECEIVED" and po["lines"][0]["acceptedQty"] == 300, (s, po))
    s, e = call("POST", f"/procurement/purchase-orders/{pid}/receipts", {"lines": [
        {"poLineId": p1, "receivedQty": 201, "rejectedQty": 0}]}, token=ravi)
    check("more than ordered cannot be accepted (400)", s == 400, (s, e))

    # ------------------------------------------------------------------ invoices + three-way match
    def invoice(number, cement_qty, cement_price, with_steel=True):
        ls = [{"poLineId": p1, "quantity": cement_qty, "unitPrice": cement_price, "taxPercent": 28}]
        if with_steel:
            ls.append({"poLineId": p2, "quantity": 2, "unitPrice": 64000, "taxPercent": 18})
        return call("POST", f"/procurement/purchase-orders/{pid}/invoices", {"invoiceNumber": number, "lines": ls}, token=priya)

    s, po = invoice(f"SC-{RUN}-1", 400, 395)
    bad = po["invoices"][-1]
    check("an invoice billing 400 bags when 300 were accepted fails the three-way match",
          s == 200 and bad["status"] == "EXCEPTION" and any("only 300" in m for m in bad["matchIssues"]), (s, bad))
    s, _ = call("POST", f"/procurement/purchase-orders/{pid}/invoices/{bad['id']}/approve", token=anita)
    check("a failed match cannot be approved (409)", s == 409, s)
    call("POST", f"/procurement/purchase-orders/{pid}/invoices/{bad['id']}/reject", {"note": "Bill what was accepted"}, token=anita)
    s, po = invoice(f"SC-{RUN}-2", 300, 402.9)
    good = po["invoices"][-1]
    check("the corrected invoice, priced within 2% of the order, matches",
          s == 200 and good["status"] == "MATCHED", (s, good))
    s, _ = invoice(f"SC-{RUN}-2", 1, 395, with_steel=False)
    check("the same invoice number twice is refused (409)", s == 409, s)
    s, po = invoice(f"SC-{RUN}-3", 1, 420, with_steel=False)
    check("a price 6% over the order fails the match", po["invoices"][-1]["status"] == "EXCEPTION", po["invoices"][-1])
    call("POST", f"/procurement/purchase-orders/{pid}/invoices/{po['invoices'][-1]['id']}/reject", token=anita)

    s, po = call("POST", f"/procurement/purchase-orders/{pid}/receipts", {"lines": [
        {"poLineId": p1, "receivedQty": 200, "rejectedQty": 0}]}, token=ravi)
    check("the replacement bags arrive: fully received", s == 200 and po["status"] == "RECEIVED", (s, po))
    s, po = invoice(f"SC-{RUN}-4", 200, 395, with_steel=False)
    last = po["invoices"][-1]
    check("the final invoice matches", last["status"] == "MATCHED", last)
    s, _ = call("POST", f"/procurement/purchase-orders/{pid}/invoices/{good['id']}/approve", token=suresh)
    check("site staff (a plain member) cannot approve invoices (403)", s == 403, s)
    call("POST", f"/procurement/purchase-orders/{pid}/invoices/{good['id']}/approve", token=anita)
    s, po = call("POST", f"/procurement/purchase-orders/{pid}/invoices/{last['id']}/approve", token=anita)
    check("everything received is billed and approved: the order closes",
          s == 200 and po["status"] == "CLOSED" and all(l["invoicedQty"] == l["quantity"] for l in po["lines"]), (s, po))

    # ------------------------------------------------------------------ the hybrid picture
    s, as_seller = call("GET", "/bookings/worker", token=ravi)
    s, as_buyer = call("GET", "/procurement/purchase-orders", token=ravi)
    check("the contractor is at once the seller of the booking and the buyer of its materials",
          any(b["id"] == booking["id"] for b in as_seller["content"])
          and any(o["id"] == pid and o["roles"] == ["BUYER"] for o in as_buyer), as_buyer)
    audited = wait_until(lambda: int(sql(
        "SELECT COUNT(*) FROM civil_engineer_audit_platform.audit_events WHERE source_service='procurement-service' "
        f"AND recorded_at > NOW() - INTERVAL 10 MINUTE").strip() or 0) >= 15, timeout=30)
    check("every procurement step is in the audit trail", audited,
          sql("SELECT COUNT(*) FROM civil_engineer_audit_platform.audit_events WHERE source_service='procurement-service'"))
finally:
    cleanup()
    if op:
        call("PATCH", f"/tenants/{KEY}/status", {"status": "ARCHIVED"}, token=op)
    reset_mfa(OP_EMAIL)
    print(f"(demo organizations removed; demo tenant '{KEY}' archived; operator MFA reset)")

print("\nALL PASSED" if check.ok else "\nSOME FAILED")
sys.exit(0 if check.ok else 1)
