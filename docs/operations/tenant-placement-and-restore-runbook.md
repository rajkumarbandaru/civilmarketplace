# Runbook — Moving a tenant between clusters, and restoring one tenant to a point in time

Architecture: [02 §6.2 tenant move procedure](../architecture/platform-factory/02-tenant-and-data-architecture.md),
[09 §6–7 backup and single-tenant data corruption](../architecture/platform-factory/09-infrastructure-and-nfr.md).
Both procedures are rehearsed by live drills — run them after any change to placement, routing or
the tooling, and at least quarterly:

```bash
python3 scripts/ops/phase6_move_drill.py      # move cluster-a → cluster-b → back, verified
python3 scripts/ops/phase6_pitr_drill.py      # backup, damage, restore to the second before, swap
python3 -m unittest scripts/ops/test_ops.py   # the tooling's own tests
```

## How placement works

- `civil_engineer_tenants.db_clusters` lists the MySQL clusters (`cluster-a` = `mysql`,
  `cluster-b` = `mysql-b` locally). `tenants.db_cluster_id` says where each tenant's 13 schemas
  (`<service prefix>_<tenantKey>`) are; `placement_epoch` goes up on every move.
- Every data service routes a tenant's connections to its cluster (tenant-common:
  `TenantPlacements` → `ClusterDataSources`, one pool per cluster). It re-reads the map when
  tenant-service announces a change, and every 15 s as a backstop, and acknowledges each change
  on `tenant.placement.acks`.
- `MAINTENANCE` is the tenant's read-only state: the gateway and every service refuse its writes
  with `503 TENANT_MAINTENANCE` + `Retry-After: 5`; reads go on. Other tenants are unaffected.
- The control plane (the tenant registry, `platform`) stays on `cluster-a`.

## A. Move a tenant to another cluster

Console → Tenants → *tenant* → **Data placement** → *Move to* (or `POST /api/v1/tenants/{key}/moves`
with `{"targetClusterId": "cluster-b"}`, SUPER_ADMIN of the operator tenant).

What happens (watch the stepper; `GET /api/v1/tenants/{key}/placement`):

| Step | What | Tenant sees |
|---|---|---|
| COPY | every schema copied to the target while live | nothing |
| FREEZE | tenant → MAINTENANCE; waits until all 13 services confirm writes are paused, + 3 s drain | read-only |
| SYNC | re-copies tables whose checksum changed since COPY | read-only |
| VERIFY | `CHECKSUM TABLE` equal for every table on both sides; else the move fails and resumes on the source | read-only |
| FLIP | placement → target, epoch + 1, announced | read-only |
| AWAIT_ACKS | every service routes to the target | read-only |
| RESUME | tenant → ACTIVE | normal |

Typical pause: 10–20 s. The source copy is kept.

**If it fails before FLIP**: nothing to do — the tenant resumed on the source automatically. Read
`lastError`, fix, start again (the target's partial copy is replaced).

**If it fails after FLIP** (a service did not acknowledge in time): the tenant stays read-only on
purpose — some services may already use the target. Either wait for the service and start a new
move, or **Roll back** (`POST …/moves/{id}/rollback`): placement returns to the source, whose data
is exactly as it was when writes were paused, and writes resume.

**Afterwards**: when satisfied (after a day of normal traffic), **Drop old copy**
(`DELETE …/moves/{id}/source`). Irreversible.

Dedicated database (tier T2): register a cluster with `kind = 'DEDICATED'` in `db_clusters`, then move
the tenant there. It takes one tenant; the tenant's tier becomes `DEDICATED_DB`.

## B. Restore one tenant to a point in time

Use when one tenant's data is damaged (bad import, admin error). Other tenants are never read or
written. Requirements: a backup older than the moment, and the binlog since (binlog is on; kept
30 days — `binlog_expire_logs_seconds`).

1. **Backups** (schedule daily per tenant; before risky operations):
   `python3 scripts/ops/tenant_backup.py <tenantKey>` → `backups/<tenant>/<id>/` — one consistent
   dump per schema, with the binlog coordinates it corresponds to. In production the vault is object
   storage with object lock in a separate account.
2. **Find the moment** just before the damage (audit trail: `civil_engineer_audit_<tenant>.audit_events`,
   times are UTC).
3. **Restore beside live**:
   `python3 scripts/ops/tenant_restore.py <tenantKey> --to "YYYY-MM-DD HH:MM:SS"` — loads the newest
   backup before that time into `<schema>__r<id>` side schemas, replays the tenant's binlog from the
   backup's coordinates to the moment (through the `civil-ops` toolbox image, `docker/ops`), and
   prints, table by table, row counts live vs restored. Nothing live has changed yet.
4. **Decide**:
   - *Selective repair* (usually right when legitimate changes happened after the damage): copy the
     rows you need from the side schemas with SQL, then `--cleanup <id>`.
   - *Full swap*: re-run with `--swap`. The tenant is put in MAINTENANCE, every restored table is
     swapped in atomically per schema (`RENAME TABLE`), the live tables are kept as
     `<schema>__pre<id>`, and writes resume. Changes made after the moment are in `__pre<id>` — this
     is the undo.
5. **Clean up** once verified: `python3 scripts/ops/tenant_restore.py <tenantKey> --cleanup <id>`.

A backup taken on another cluster (before a move) cannot be used: the changes since are in the other
cluster's binlog. Take a fresh backup after every move.
