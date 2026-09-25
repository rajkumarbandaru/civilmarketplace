"""Per-tenant logical backup (architecture 09 §6: "per-tenant logical dumps" for fast single-tenant
restore). Dumps each of the tenant's schemas on its current cluster with a consistent snapshot and
records the binlog coordinates it corresponds to, so a restore can replay from exactly there.

    python3 scripts/ops/tenant_backup.py <tenantKey>

Written to backups/<tenant>/<id>/ (gitignored); in production the vault is object storage with
object lock, in a separate account (09 §7).
"""
import datetime, json, os, re, subprocess, sys, time
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "lib"))
from ops import VAULT, ident, mysql, placement, root_pw, tenant_schemas, write_gz

# 8.0 writes CHANGE MASTER TO MASTER_LOG_FILE=...; 8.4 writes CHANGE REPLICATION SOURCE TO SOURCE_LOG_FILE=...
POSITION = re.compile(rb"(?:SOURCE|MASTER)_LOG_FILE='([^']+)', (?:SOURCE|MASTER)_LOG_POS=(\d+)")


def backup(tenant_key):
    cluster, host, container = placement(ident(tenant_key))
    schemas = tenant_schemas(container, tenant_key)
    if not schemas:
        raise SystemExit(f"'{tenant_key}' has no schemas on {cluster}")
    backup_id = datetime.datetime.utcnow().strftime("%Y%m%dT%H%M%S")
    folder = os.path.join(VAULT, tenant_key, backup_id)
    os.makedirs(folder, exist_ok=True)
    manifest = {"id": backup_id, "tenant": tenant_key, "cluster": cluster, "host": host, "container": container,
                "takenAt": None, "schemas": {}}
    for schema in schemas:
        dump = subprocess.run(["docker", "exec", container, "mysqldump", "-uroot", f"-p{root_pw(container)}",
                               "--single-transaction", "--source-data=2", "--set-gtid-purged=OFF", "--no-create-db",
                               "--triggers", "--routines=false", "--hex-blob", schema],
                              capture_output=True, check=True).stdout
        m = POSITION.search(dump)
        if not m:
            raise SystemExit(f"{schema}: dump carries no binlog position (is binary logging on?)")
        write_gz(os.path.join(folder, schema + ".sql.gz"), dump)
        manifest["schemas"][schema] = {"binlogFile": m.group(1).decode(), "binlogPos": int(m.group(2)), "bytes": len(dump)}
    # Recorded once every snapshot exists: a restore to any time at or after this starts from data
    # no newer than that time.
    time.sleep(1)
    manifest["takenAt"] = mysql(container, "SELECT DATE_FORMAT(NOW(), '%Y-%m-%d %H:%i:%s')").strip()
    json.dump(manifest, open(os.path.join(folder, "manifest.json"), "w"), indent=2)
    return manifest


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    m = backup(sys.argv[1])
    print(f"backup {m['id']} of '{m['tenant']}' on {m['cluster']} at {m['takenAt']}: {len(m['schemas'])} schemas")
