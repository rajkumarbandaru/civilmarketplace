"""Phase 6 exit criterion, live: a tenant moved between MySQL clusters in a drill.

A fresh tenant is published and given data (an owner, bookings). It is moved from cluster-a to
cluster-b while in use: reads keep working throughout, writes are refused (503, Retry-After)
only while it is paused, every table is verified equal on both clusters, and afterwards new
writes land on cluster-b and nowhere else. Other tenants are unaffected. It is then moved back
(epoch 2) and the copy left on cluster-b is dropped.

    python3 scripts/ops/phase6_move_drill.py
"""
import os, re, subprocess, sys, time
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "lib"))
from live import Checks, call, sql, sign_in_enrolling, reset_mfa, publish_new_tenant, wait_until, OP_EMAIL, OP_PASSWORD

check = Checks()
KEY = "mv" + str(int(time.time()) % 1000000)
HOST = f"{KEY}.localhost"
OWNER = f"owner@{KEY}.example.com"
PREFIXES = ["admin_db", "civil_engineer_audit", "civil_engineer_auth", "civil_engineer_bookings", "civil_engineer_media",
            "civil_engineer_messaging", "civil_engineer_notifications", "civil_engineer_payments",
            "civil_engineer_procurement", "civil_engineer_projects", "civil_engineer_reviews", "civil_engineer_support",
            "civil_engineer_users"]


def sql_on(container, q):
    pw = subprocess.run(["docker", "exec", container, "printenv", "MYSQL_ROOT_PASSWORD"], capture_output=True, text=True).stdout.strip()
    return subprocess.run(["docker", "exec", container, "mysql", "-uroot", f"-p{pw}", "-N", "-e", q],
                          capture_output=True, text=True).stdout


def schemas_on(container):
    rows = sql_on(container, f"SHOW DATABASES LIKE '%\\_{KEY}'").split()
    return sorted(r for r in rows if r in {p + "_" + KEY for p in PREFIXES})


def checksums(container):
    sums = {}
    for schema in schemas_on(container):
        tables = sql_on(container, f"SELECT table_name FROM information_schema.tables WHERE table_schema='{schema}' "
                                   "AND table_type='BASE TABLE'").split()
        if tables:
            for line in sql_on(container, "CHECKSUM TABLE " + ", ".join(f"`{schema}`.`{t}`" for t in tables)).strip().split("\n"):
                name, value = line.split("\t")
                sums[name] = value
    return sums


def content(container, qualified):
    """Row count and a hash of every non-timestamp column: the table's data, minus bookkeeping times."""
    schema, table = qualified.split(".")
    cols = sql_on(container, f"SELECT column_name FROM information_schema.columns WHERE table_schema='{schema}' "
                             f"AND table_name='{table}' AND data_type NOT IN ('timestamp','datetime') "
                             "ORDER BY ordinal_position").split()
    expr = "CONCAT_WS('|', " + ", ".join(f"IFNULL(`{c}`,'~')" for c in cols) + ")"
    return sql_on(container, f"SELECT COUNT(*), BIT_XOR(CRC32({expr})) FROM `{schema}`.`{table}`").strip()


def bookings_on(container):
    out = sql_on(container, f"SELECT COUNT(*) FROM civil_engineer_bookings_{KEY}.bookings").strip()
    return int(out) if out.isdigit() else None


def move(op, target):
    s, m = call("POST", f"/tenants/{KEY}/moves", {"targetClusterId": target}, token=op)
    assert s == 202, (s, m)
    return m


reset_mfa(OP_EMAIL)
op = None
try:
    op = sign_in_enrolling(OP_EMAIL, OP_PASSWORD)["accessToken"]
    s, clusters = call("GET", "/tenants/clusters", token=op)
    check("the placement map lists both clusters",
          s == 200 and {c["clusterId"] for c in clusters} >= {"cluster-a", "cluster-b"}, clusters)

    # ------------------------------------------------------------------ a tenant with data
    p = publish_new_tenant(op, KEY, "professional", OWNER)
    check("a fresh tenant is published on cluster-a", p["step"] == "DONE", p)
    s, placement = call("GET", f"/tenants/{KEY}/placement", token=op)
    check("…placed on cluster-a, epoch 0", placement["clusterId"] == "cluster-a" and placement["epoch"] == 0, placement)
    link = None
    for _ in range(30):
        m = re.search(r"/invite/([A-Za-z0-9_-]{20,})", sql(f"SELECT body FROM civil_engineer_notifications_{KEY}.email_log "
                                                            f"WHERE recipient='{OWNER}' ORDER BY id DESC LIMIT 1"))
        if m:
            link = m.group(1)
            break
        time.sleep(2)
    call("POST", f"/auth/invitations/{link}/accept", {"password": "correct horse battery staple"}, host=HOST)
    session = sign_in_enrolling(OWNER, "correct horse battery staple", host=HOST)
    owner = session["accessToken"]
    booking = {"serviceCategory": "Plumbing", "serviceName": "Leak fix", "bookingType": "INSTANT", "city": "Pune"}
    for _ in range(3):
        s, _ = call("POST", "/bookings", booking, token=owner, host=HOST)
    check("the tenant has data: an owner and three bookings", s in (200, 201) and bookings_on("civil_mysql") == 3,
          bookings_on("civil_mysql"))
    before = len(schemas_on("civil_mysql"))
    check(f"its data is {before} schemas on cluster-a and none on cluster-b",
          before == len(PREFIXES) and schemas_on("civil_mysql_b") == [], (before, schemas_on("civil_mysql_b")))

    # ------------------------------------------------------------------ the move, in use
    started = time.time()
    m = move(op, "cluster-b")
    check("the move starts (202)", m["step"] == "COPY", m)
    saw_pause, write_refused, read_served, other_write_ok = False, None, None, None
    while time.time() - started < 300:
        s, pl = call("GET", f"/tenants/{KEY}/placement", token=op)
        step = pl["currentMove"]["step"]
        if pl["status"] == "MAINTENANCE" and not saw_pause:
            saw_pause = True
            ws, wb = call("POST", "/bookings", booking, token=owner, host=HOST)
            write_refused = ws
            read_served = call("GET", "/bookings/customer", token=owner, host=HOST)[0]
            other_write_ok = call("POST", "/auth/login", {"email": "customer@civileng.test", "password": "Password123!"})[0]
        if step in ("DONE", "FAILED", "ROLLED_BACK"):
            break
        time.sleep(0.3)
    s, pl = call("GET", f"/tenants/{KEY}/placement", token=op)
    mv = pl["currentMove"]
    check("the move finishes", mv["step"] == "DONE", mv)
    check("while paused, a write is refused with 503 and the tenant keeps reading", saw_pause and write_refused == 503
          and read_served == 200, (saw_pause, write_refused, read_served))
    check("…while other tenants keep writing (platform sign-in)", other_write_ok == 200, other_write_ok)
    check(f"writes were paused for {mv['freezeMillis'] / 1000:.1f}s (well under a minute)",
          mv["freezeMillis"] is not None and mv["freezeMillis"] < 60000, mv["freezeMillis"])
    report = mv.get("report") or {}
    check(f"copied {mv['schemasCopied']} schemas / {mv['tablesCopied']} tables / {mv['rowsCopied']} rows; "
          f"{report.get('tablesVerified')} tables verified equal, no mismatches",
          mv["schemasCopied"] == len(PREFIXES) and report.get("tablesVerified") and not report.get("mismatches"), mv)
    check("placement: cluster-b, epoch 1, active again",
          pl["clusterId"] == "cluster-b" and pl["epoch"] == 1 and pl["status"] == "ACTIVE", pl)
    check("every data service acknowledged the new cluster", len(mv["acknowledged"]) == len(PREFIXES), mv["acknowledged"])

    a, b = checksums("civil_mysql"), checksums("civil_mysql_b")
    diff = sorted(k for k in a if a.get(k) != b.get(k))
    # A table can differ only by bookkeeping written after writes resumed (the module sync re-stamps
    # updated_at); its data — every other column — must be identical.
    lost = [k for k in diff if content("civil_mysql", k) != content("civil_mysql_b", k)]
    check(f"all {len(b)} tables on cluster-b hold the source's data"
          + (f" ({len(diff)} re-stamped after resuming: {', '.join(diff)})" if diff else ""),
          a.keys() == b.keys() and not lost, lost)

    # ------------------------------------------------------------------ after the move
    s, _ = call("POST", "/bookings", booking, token=owner, host=HOST)
    check("writes work again", s in (200, 201), s)
    check("…and land on cluster-b only (4 there, cluster-a copy still 3)",
          bookings_on("civil_mysql_b") == 4 and bookings_on("civil_mysql") == 3,
          (bookings_on("civil_mysql_b"), bookings_on("civil_mysql")))
    s, mine = call("GET", "/bookings/customer", token=owner, host=HOST)
    total = mine.get("totalElements", len(mine.get("content", []))) if isinstance(mine, dict) else len(mine)
    check("reads come from cluster-b: all 4 bookings", s == 200 and total == 4, (s, total))
    s, refreshed = call("POST", "/auth/refresh", {"refreshToken": session["refreshToken"]}, host=HOST)
    check("sign-in state is intact (auth schema moved too): the session refreshes", s == 200 and refreshed.get("accessToken"),
          (s, refreshed))
    s, _ = call("POST", "/auth/login", {"email": "customer@civileng.test", "password": "Password123!"})
    check("other tenants never noticed (platform sign-in works)", s == 200, s)

    # ------------------------------------------------------------------ and back
    m = move(op, "cluster-a")
    check("moved back to cluster-a", wait_until(lambda: call("GET", f"/tenants/{KEY}/placement", token=op)[1]
                                                ["currentMove"]["step"] in ("DONE", "FAILED"), timeout=300, every=1))
    s, pl = call("GET", f"/tenants/{KEY}/placement", token=op)
    check("…epoch 2, the four bookings with it", pl["clusterId"] == "cluster-a" and pl["epoch"] == 2
          and bookings_on("civil_mysql") == 4, (pl, bookings_on("civil_mysql")))
    s, dropped = call("DELETE", f"/tenants/{KEY}/moves/{m['id']}/source", token=op)
    check("the copy left on cluster-b is dropped on request", s == 200 and schemas_on("civil_mysql_b") == [],
          (s, schemas_on("civil_mysql_b")))
    s, _ = call("GET", "/bookings/customer", token=owner, host=HOST)
    check("the tenant still works after all that", s == 200, s)
finally:
    if op:
        call("PATCH", f"/tenants/{KEY}/status", {"status": "ARCHIVED"}, token=op)
    for schema in schemas_on("civil_mysql_b"):
        sql_on("civil_mysql_b", f"DROP DATABASE `{schema}`")
    reset_mfa(OP_EMAIL)
    print(f"(drill tenant '{KEY}' archived; operator MFA reset)")

print("\nALL PASSED" if check.ok else "\nSOME FAILED")
sys.exit(0 if check.ok else 1)
