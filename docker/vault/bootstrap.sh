#!/bin/sh
# Starts Vault and makes it ready to serve, idempotently, on every start:
#   first start: initialise (1 key share), keep the unseal key and root token in the data volume;
#   every start: unseal, ensure the Transit engine, the audit log, the policies and one token per
#   service (ids from .env, so services are configured before Vault exists).
# LOCAL DEVELOPMENT ONLY: the unseal key lives beside the data it unseals. Production uses KMS
# auto-unseal and short-lived service identities (Kubernetes auth), never a stored root token.
vault server -config=/vault/bootstrap/vault.hcl &
SERVER=$!
export VAULT_ADDR=http://127.0.0.1:8200

while true; do
  vault status >/dev/null 2>&1
  rc=$?
  [ "$rc" -ne 1 ] && break          # 0 unsealed, 2 sealed: the server is answering
  sleep 1
done

STATE=/vault/file/bootstrap
if [ ! -s "$STATE/unseal" ]; then
  mkdir -p "$STATE" && chmod 700 "$STATE"
  vault operator init -key-shares=1 -key-threshold=1 > "$STATE/init.txt"
  sed -n 's/^Unseal Key 1: //p' "$STATE/init.txt" > "$STATE/unseal"
  sed -n 's/^Initial Root Token: //p' "$STATE/init.txt" > "$STATE/root"
  rm "$STATE/init.txt"
  chmod 600 "$STATE/unseal" "$STATE/root"
  echo "vault: initialised"
fi
vault operator unseal "$(cat "$STATE/unseal")" >/dev/null && echo "vault: unsealed"
export VAULT_TOKEN="$(cat "$STATE/root")"

vault secrets list | grep -q '^transit/' || vault secrets enable transit
mkdir -p /vault/logs
vault audit list 2>/dev/null | grep -q '^file/' || vault audit enable file file_path=/vault/logs/audit.log

for svc in tenant-service payment-service notification-service support-service; do
  vault policy write "$svc" "/vault/bootstrap/policies/$svc.hcl" >/dev/null
done

token() {  # token <id> <policy>
  [ -z "$1" ] && { echo "vault: no token id for $2 (set it in .env)"; return; }
  vault token lookup "$1" >/dev/null 2>&1 && return
  vault token create -id="$1" -policy="$2" -display-name="$2" -orphan -ttl=87600h >/dev/null && echo "vault: token for $2 created"
}
token "$VAULT_TOKEN_TENANT_SERVICE" tenant-service
token "$VAULT_TOKEN_PAYMENT_SERVICE" payment-service
token "$VAULT_TOKEN_NOTIFICATION_SERVICE" notification-service
token "$VAULT_TOKEN_SUPPORT_SERVICE" support-service

touch "$STATE/ready"
echo "vault: ready"
wait $SERVER
