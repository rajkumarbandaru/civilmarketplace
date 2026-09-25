# support-service opens tenants' AI provider credentials and nothing else.
path "transit/decrypt/ai-tenant-*" {
  capabilities = ["update"]
}
