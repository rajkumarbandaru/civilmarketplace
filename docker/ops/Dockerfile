# Operations toolbox for the restore runbook (scripts/ops/tenant_restore.py): the MySQL server image
# ships mysqldump but not mysqlbinlog, which point-in-time recovery needs. Ubuntu's MySQL 8.0
# packages carry both. The toolbox runs on the platform network and reads each cluster's binlog
# remotely (--read-from-remote-server), so it works for any cluster without access to its files.
FROM ubuntu:24.04
RUN apt-get update \
 && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends mysql-client-8.0 mysql-server-core-8.0 \
 && rm -rf /var/lib/apt/lists/*
