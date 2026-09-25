# tenant-service writes tenants' provider secrets and destroys keys (crypto-shredding).
# It can open NOTHING: "no API returns a secret" is enforced here, not just in code.
path "transit/encrypt/*" {
  capabilities = ["create", "update"]   # create: the first seal makes the (tenant, capability) key
}
path "transit/keys" {
  capabilities = ["list"]
}
path "transit/keys/*" {
  capabilities = ["read", "delete"]
}
path "transit/keys/+/config" {
  capabilities = ["update"]             # deletion_allowed, just before a crypto-shred
}
