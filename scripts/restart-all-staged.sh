#!/bin/bash
set -u
P=civil_
APPS="auth_service user_service tenant_service admin_service project_service booking_service payment_service notification_service messaging_service media_service search_service review_service procurement_service analytics_service audit_service support_service"
wait_healthy(){ for c in "$@"; do for i in $(seq 1 60); do s=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $P$c); [ "$s" = healthy ] || [ "$s" = running ] && break; sleep 5; done; echo "$c: $s"; done; }
echo "== stopping frontend, apps, gateway"
docker stop ${P}frontend $(for a in $APPS; do echo $P$a; done) ${P}api_gateway >/dev/null
echo "== restarting infra"
docker restart ${P}zookeeper ${P}mysql ${P}mysql_b ${P}redis ${P}rabbitmq ${P}vault ${P}elasticsearch ${P}minio ${P}pebble ${P}challtestsrv >/dev/null
sleep 5; docker restart ${P}kafka >/dev/null
wait_healthy mysql mysql_b redis vault minio
echo "== config + registry"
docker restart ${P}config_server >/dev/null; wait_healthy config_server
docker restart ${P}service_registry >/dev/null; wait_healthy service_registry
echo "== gateway"
docker start ${P}api_gateway >/dev/null; sleep 40
set -- $APPS
while [ $# -gt 0 ]; do
  batch="$1 ${2:-} ${3:-} ${4:-}"; shift $(( $# < 4 ? $# : 4 ))
  echo "== starting: $batch"
  docker start $(for a in $batch; do echo $P$a; done) >/dev/null
  sleep 60; uptime
done
echo "== frontend"
docker start ${P}frontend >/dev/null; sleep 5
docker ps -a --filter name=civil_ --format '{{.Names}}\t{{.Status}}'
