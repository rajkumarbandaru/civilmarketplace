# payment-service opens tenants' payment credentials (Razorpay) and nothing else.
path "transit/decrypt/payment-tenant-*" {
  capabilities = ["update"]
}
