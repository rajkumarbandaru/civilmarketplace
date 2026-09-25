"""Single-tenant point-in-time restore (architecture 09 §7, "single-tenant data corruption"):

  1. load the tenant's latest backup before the target time into side schemas (<schema>__r<id>);
  2. replay that tenant's changes from the binlog, from the backup's coordinates up to the time;
  3. report, table by table, how the restored data differs from live;
  4. with --swap: pause the tenant's writes (MAINTENANCE), swap the restored tables in — the
     live ones are kept as <schema>__pre<id> for undo — and resume.

Other tenants are never touched: only this tenant's schemas are read, restored and swapped.

    python3 scripts/ops/tenant_restore.py <tenantKey> --to "YYYY-MM-DD HH:MM:SS" [--swap]
    python3 scripts/ops/tenant_restore.py <tenantKey> --cleanup <id>     # drop side / pre schemas
"""
import argparse, gzip, json, os, subprocess, sys, time
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "lib"))
from ops import VAULT, backups, ident, mysql, placement, root_pw, row_counts, tables, tenant_schemas, toolbox


def restore_side(tenant_key, to, backup=None):
    cluster, host, container = placement(tenant_key)
    candidates = [b for b in backups(tenant_key) if b["takenAt"] <= to and (backup is None or b["id"] == backup)]
    if not candidates:
        raise SystemExit(f"no backup of '{tenant_key}' taken before {to}")
    b = candidates[-1]
    if b["cluster"] != cluster:
        raise SystemExit(f"the backup was taken on {b['cluster']} but '{tenant_key}' is now on {cluster}; "
                         "its later changes are in the other cluster's binlog — take a fresh backup after a move")
    rid = time.strftime("%H%M%S")
    pw = root_pw(container)
    replayed = {}
    for schema, pos in b["schemas"].items():
        side = ident(f"{schema}__r{rid}")
        mysql(container, f"CREATE DATABASE `{side}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci", no_binlog=True)
        with gzip.open(os.path.join(VAULT, tenant_key, b["id"], schema + ".sql.gz"), "rb") as dump:
            load = subprocess.run(["docker", "exec", "-i", container, "mysql", "-uroot", f"-p{pw}",
                                   "--init-command=SET SESSION sql_log_bin=0", side], input=dump.read(), capture_output=True)
        if load.returncode != 0:
            raise SystemExit(f"loading {schema}: {load.stderr.decode()}")
        # Read remotely from the cluster through the toolbox (the server image has no mysqlbinlog), from
        # the backup's coordinates to the target time. --rewrite-db is applied before --database, so
        # the filter names the side schema. The replay itself stays out of the binlog.
        toolbox(container, f"mysqlbinlog --read-from-remote-server --host={host} --user=root --to-last-log "
                           f"--start-position={pos['binlogPos']} --stop-datetime='{to}' "
                           f"--rewrite-db='{schema}->{side}' --database={side} {pos['binlogFile']} "
                           f"| mysql --host={host} --user=root --init-command='SET SESSION sql_log_bin=0'")
        replayed[schema] = side
    return {"id": rid, "backup": b["id"], "container": container, "cluster": cluster, "to": to, "schemas": replayed}


def diff(restore):
    report = {}
    for schema, side in restore["schemas"].items():
        live, restored = row_counts(restore["container"], schema), row_counts(restore["container"], side)
        changes = {t: (live.get(t), restored.get(t)) for t in set(live) | set(restored) if live.get(t) != restored.get(t)}
        if changes:
            report[schema] = changes
    return report


def swap(restore, pause, resume):
    """Swaps every restored table in, atomically per schema; the live tables become <schema>__pre<id>."""
    pause()
    try:
        c = restore["container"]
        for schema, side in restore["schemas"].items():
            pre = ident(f"{schema}__pre{restore['id']}")
            mysql(c, f"CREATE DATABASE `{pre}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci", no_binlog=True)
            renames = [f"`{schema}`.`{ident(t)}` TO `{pre}`.`{t}`" for t in tables(c, schema)]
            renames += [f"`{side}`.`{ident(t)}` TO `{schema}`.`{t}`" for t in tables(c, side)]
            mysql(c, "SET FOREIGN_KEY_CHECKS=0; RENAME TABLE " + ", ".join(renames), no_binlog=True)
            mysql(c, f"DROP DATABASE `{side}`", no_binlog=True)
    finally:
        resume()


def cleanup(tenant_key, rid):
    cluster, host, container = placement(tenant_key)
    dropped = []
    for s in mysql(container, f"SHOW DATABASES LIKE '%\\\\_{ident(tenant_key)}\\\\_\\\\_%{ident(rid)}'").split():
        mysql(container, f"DROP DATABASE `{ident(s)}`", no_binlog=True)
        dropped.append(s)
    return dropped


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("tenant")
    ap.add_argument("--to")
    ap.add_argument("--backup")
    ap.add_argument("--swap", action="store_true")
    ap.add_argument("--cleanup")
    a = ap.parse_args()
    if a.cleanup:
        print("dropped", cleanup(a.tenant, a.cleanup))
        sys.exit(0)
    if not a.to:
        raise SystemExit("--to is required")
    r = restore_side(a.tenant, a.to, a.backup)
    print(f"restore {r['id']}: backup {r['backup']} + binlog to {a.to} → {len(r['schemas'])} side schemas")
    print(json.dumps(diff(r), indent=2))
    if a.swap:
        sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "lib"))
        from live import call, sign_in_enrolling, reset_mfa, OP_EMAIL, OP_PASSWORD
        op = sign_in_enrolling(OP_EMAIL, OP_PASSWORD)["accessToken"]
        body = lambda on: {"enabled": on, "reason": f"Point-in-time restore {r['id']} to {a.to}"}
        swap(r, lambda: (call("POST", f"/tenants/{a.tenant}/maintenance", body(True), token=op), time.sleep(6)),
             lambda: call("POST", f"/tenants/{a.tenant}/maintenance", body(False), token=op))
        reset_mfa(OP_EMAIL)
        print(f"swapped in; previous data kept as <schema>__pre{r['id']} (drop with --cleanup {r['id']})")
