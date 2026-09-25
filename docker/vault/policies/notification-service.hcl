# notification-service opens tenants' email, SMS and WhatsApp credentials and nothing else.
path "transit/decrypt/email-tenant-*" {
  capabilities = ["update"]
}
path "transit/decrypt/sms-tenant-*" {
  capabilities = ["update"]
}
path "transit/decrypt/whatsapp-tenant-*" {
  capabilities = ["update"]
}
