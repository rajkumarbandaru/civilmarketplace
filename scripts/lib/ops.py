"""Shared helpers for the operations tooling (tenant backup and point-in-time restore).

Runs on the Docker host: reaches each MySQL cluster's container with `docker exec`, as root, the
way an operator's runbook would. Nothing here goes through the platform's APIs except pausing a
tenant's writes, which is tenant-service's to do.
"""
import gzip, json, os, re, subprocess

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
VAULT = os.environ.get("BACKUP_VAULT", os.path.join(ROOT, "backups"))
CONTAINERS = {"mysql": "civil_mysql", "mysql-b": "civil_mysql_b"}
PREFIXES = ["admin_db", "civil_engineer_audit", "civil_engineer_auth", "civil_engineer_bookings", "civil_engineer_media",
            "civil_engineer_messaging", "civil_engineer_notifications", "civil_engineer_payments",
            "civil_engineer_procurement", "civil_engineer_projects", "civil_engineer_reviews", "civil_engineer_support",
            "civil_engineer_users"]
IDENT = re.compile(r"^[A-Za-z0-9_]{1,64}$")


def ident(name):
    if not IDENT.match(name):
        raise ValueError(f"unsafe identifier: {name}")
    return name


OPS_IMAGE = "civil-ops:8.0"
NETWORK = os.environ.get("PLATFORM_NETWORK", "rajkumar_civil-network")


def ensure_ops_image():
    """The toolbox with mysqlbinlog (docker/ops); built on first use."""
    if subprocess.run(["docker", "image", "inspect", OPS_IMAGE], capture_output=True).returncode != 0:
        subprocess.run(["docker", "build", "-t", OPS_IMAGE, os.path.join(ROOT, "docker", "ops")], check=True,
                       capture_output=True)


def toolbox(container, script):
    """Runs a bash script in the toolbox on the platform network, as the cluster's root, with pipefail:
    a failing step fails the whole script instead of feeding an empty stream to the next one."""
    ensure_ops_image()
    r = subprocess.run(["docker", "run", "--rm", "--network", NETWORK, "-e", f"MYSQL_PWD={root_pw(container)}", OPS_IMAGE,
                        "bash", "-o", "pipefail", "-c", script], capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(r.stderr.strip() or f"toolbox exited {r.returncode}")
    return r.stdout


def root_pw(container):
    return subprocess.run(["docker", "exec", container, "printenv", "MYSQL_ROOT_PASSWORD"],
                          capture_output=True, text=True, check=True).stdout.strip()


def mysql(container, q, database=None, no_binlog=False):
    """Runs SQL as root; returns stdout. no_binlog keeps restore traffic out of the binlog (and CDC)."""
    args = ["docker", "exec", container, "mysql", "-uroot", f"-p{root_pw(container)}", "-N", "-B"]
    if no_binlog:
        args.append("--init-command=SET SESSION sql_log_bin=0")
    if database:
        args.append(database)
    r = subprocess.run(args + ["-e", q], capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(r.stderr.replace(root_pw(container), "***").strip())
    return r.stdout


def placement(tenant_key):
    """(cluster id, host, container) the tenant's schemas are on, from the control plane."""
    row = mysql("civil_mysql", "SELECT t.db_cluster_id, c.host FROM civil_engineer_tenants.tenants t "
                               f"JOIN civil_engineer_tenants.db_clusters c ON c.cluster_id = t.db_cluster_id "
                               f"WHERE t.tenant_key = '{ident(tenant_key)}'").split()
    if not row:
        raise SystemExit(f"no tenant '{tenant_key}'")
    return row[0], row[1], CONTAINERS[row[1]]


def tenant_schemas(container, tenant_key):
    wanted = {p + "_" + ident(tenant_key) for p in PREFIXES}
    return sorted(s for s in mysql(container, f"SHOW DATABASES LIKE '%\\\\_{tenant_key}'").split() if s in wanted)


def tables(container, schema):
    return mysql(container, f"SELECT table_name FROM information_schema.tables WHERE table_schema='{ident(schema)}' "
                            "AND table_type='BASE TABLE' ORDER BY table_name").split()


def row_counts(container, schema):
    counts = {}
    for t in tables(container, schema):
        counts[t] = int(mysql(container, f"SELECT COUNT(*) FROM `{schema}`.`{ident(t)}`").strip())
    return counts


def backups(tenant_key):
    base = os.path.join(VAULT, tenant_key)
    found = []
    for d in sorted(os.listdir(base)) if os.path.isdir(base) else []:
        manifest = os.path.join(base, d, "manifest.json")
        if os.path.exists(manifest):
            found.append(json.load(open(manifest)))
    return found


def write_gz(path, data):
    with gzip.open(path, "wb") as f:
        f.write(data)
