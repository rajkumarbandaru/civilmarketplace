#!/bin/bash
# Change-data capture (analytics-service): a read-only replication account on every cluster, so the
# warehouse can follow the binlog like a replica. No write privilege anywhere.
mysql -uroot -p"$MYSQL_ROOT_PASSWORD" <<SQL
CREATE USER IF NOT EXISTS 'civil_cdc'@'%' IDENTIFIED BY '${CDC_DB_PASSWORD:-cdc_pass}';
GRANT SELECT, REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'civil_cdc'@'%';
FLUSH PRIVILEGES;
SQL
