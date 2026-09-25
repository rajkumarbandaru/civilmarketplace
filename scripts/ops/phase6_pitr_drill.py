"""Phase 6 exit criterion, live: a single-tenant point-in-time restore drill.

A fresh tenant gets three bookings, is backed up, gets two more (only in the binlog), and then an
"admin error" deletes all of its bookings. Its data is restored to the moment before the error:
backup + binlog replay into side schemas, compared with live, then swapped in while the tenant's
writes are paused. Nothing of any other tenant is read or changed; the pre-restore data is kept
for undo until dropped.

    python3 scripts/ops/phase6_pitr_drill.py
"""
import os, re, shutil, sys, time
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "lib"))
sys.path.insert(0, os.path.dirname(__file__))
from live import Checks, call, sql, sign_in_enrolling, reset_mfa, publish_new_tenant, OP_EMAIL, OP_PASSWORD
from ops import VAULT, mysql, row_counts
import tenant_backup, tenant_restore

check = Checks()
KEY = "pitr" + str(int(time.time()) % 1000000)
HOST = f"{KEY}.localhost"
OWNER = f"owner@{KEY}.example.com"
C = "civil_mysql"
BOOKINGS = f"civil_engineer_bookings_{KEY}"


def book(owner, name):
    s, b = call("POST", "/bookings", {"serviceCategory": "Plumbing", "serviceName": name, "bookingType": "INSTANT",
                                      "city": "Pune"}, token=owner, host=HOST)
    assert s in (200, 201), (s, b)
    return b


def names(schema):
    return sorted(mysql(C, f"SELECT service_name FROM `{schema}`.bookings").split("\n")[:-1])


def platform_fingerprint():
    return mysql(C, "CHECKSUM TABLE civil_engineer_bookings_platform.bookings, civil_engineer_users_platform.user_profiles")


reset_mfa(OP_EMAIL)
op = None
restore = None
try:
    op = sign_in_enrolling(OP_EMAIL, OP_PASSWORD)["accessToken"]
    p = publish_new_tenant(op, KEY, "professional", OWNER)
    check("a fresh tenant is published", p["step"] == "DONE", p)
    link = None
    for _ in range(30):
        m = re.search(r"/invite/([A-Za-z0-9_-]{20,})", sql(f"SELECT body FROM civil_engineer_notifications_{KEY}.email_log "
                                                            f"WHERE recipient='{OWNER}' ORDER BY id DESC LIMIT 1"))
        if m:
            link = m.group(1)
            break
        time.sleep(2)
    call("POST", f"/auth/invitations/{link}/accept", {"password": "correct horse battery staple"}, host=HOST)
    owner = sign_in_enrolling(OWNER, "correct horse battery staple", host=HOST)["accessToken"]
    for n in ("A-leak", "B-tap", "C-drain"):
        book(owner, n)

    manifest = tenant_backup.backup(KEY)
    check(f"per-tenant backup: {len(manifest['schemas'])} schemas with binlog coordinates",
          len(manifest["schemas"]) == 13 and all(v["binlogPos"] > 0 for v in manifest["schemas"].values()), manifest)

    for n in ("D-geyser", "E-valve"):
        book(owner, n)
    time.sleep(2)
    good = mysql(C, "SELECT DATE_FORMAT(NOW(), '%Y-%m-%d %H:%i:%s')").strip()
    time.sleep(2)

    before_other = platform_fingerprint()
    mysql(C, f"DELETE FROM `{BOOKINGS}`.bookings")        # the admin error
    time.sleep(1)
    book(owner, "F-after-the-error")                       # a legitimate change after the moment
    check("the damage: only the one booking made after the error is left", names(BOOKINGS) == ["F-after-the-error"],
          names(BOOKINGS))

    # ------------------------------------------------------------------ restore to the moment before
    restore = tenant_restore.restore_side(KEY, good)
    side = restore["schemas"][BOOKINGS]
    check(f"restored to {good}: backup + binlog replay give the five bookings of that moment",
          names(side) == ["A-leak", "B-tap", "C-drain", "D-geyser", "E-valve"], names(side))
    check("…two of which exist only in the binlog (made after the backup)",
          mysql(C, f"SELECT COUNT(*) FROM `{side}`.bookings WHERE service_name IN ('D-geyser','E-valve')").strip() == "2")
    report = tenant_restore.diff(restore)
    check("the diff names exactly what the restore would change: bookings 1 → 5",
          report.get(BOOKINGS, {}).get("bookings") == (1, 5), report)
    check("every one of the tenant's 13 schemas was restored", len(restore["schemas"]) == 13, list(restore["schemas"]))
    check("no other tenant's data was read into or changed (platform checksums identical)",
          platform_fingerprint() == before_other)

    # ------------------------------------------------------------------ swap in, writes paused
    refused = {}

    def pause():
        call("POST", f"/tenants/{KEY}/maintenance", {"enabled": True, "reason": "PITR drill"}, token=op)
        for _ in range(20):
            s, _ = call("POST", "/bookings", {"serviceCategory": "x", "serviceName": "during-swap", "bookingType": "INSTANT",
                                               "city": "Pune"}, token=owner, host=HOST)
            if s == 503:
                refused["write"] = s
                break
            time.sleep(1)
        refused["read"] = call("GET", "/bookings/customer", token=owner, host=HOST)[0]

    tenant_restore.swap(restore, pause,
                        lambda: call("POST", f"/tenants/{KEY}/maintenance", {"enabled": False}, token=op))
    check("while swapping, writes are refused (503) and reads served",
          refused.get("write") == 503 and refused.get("read") == 200, refused)
    check("the live bookings are the restored five", names(BOOKINGS) == ["A-leak", "B-tap", "C-drain", "D-geyser", "E-valve"],
          names(BOOKINGS))
    s, page = call("GET", "/bookings/customer", token=owner, host=HOST)
    check("the app sees them: 5 bookings through the API", s == 200 and page.get("totalElements") == 5, (s, page))
    pre = f"{BOOKINGS}__pre{restore['id']}"
    check("the pre-restore data is kept for undo (it holds the post-error booking)",
          names(pre) == ["F-after-the-error"], names(pre))
    book(owner, "G-after-restore")
    check("writes work after the restore", "G-after-restore" in names(BOOKINGS))
    s, pl = call("GET", f"/tenants/{KEY}/placement", token=op)
    check("the tenant is ACTIVE again", pl["status"] == "ACTIVE", pl)
    check("other tenants never noticed (platform checksums identical)", platform_fingerprint() == before_other)

    dropped = tenant_restore.cleanup(KEY, restore["id"])
    check("undo copies dropped on request", len(dropped) == 13 and not any(
        s.endswith(restore["id"]) for s in mysql(C, f"SHOW DATABASES LIKE '%{KEY}%'").split()), dropped)
finally:
    if restore:
        tenant_restore.cleanup(KEY, restore["id"])
    if op:
        call("PATCH", f"/tenants/{KEY}/status", {"status": "ARCHIVED"}, token=op)
    shutil.rmtree(os.path.join(VAULT, KEY), ignore_errors=True)
    reset_mfa(OP_EMAIL)
    print(f"(drill tenant '{KEY}' archived; its backups removed; operator MFA reset)")

print("\nALL PASSED" if check.ok else "\nSOME FAILED")
sys.exit(0 if check.ok else 1)
