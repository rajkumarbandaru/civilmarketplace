# The secrets broker (architecture 06 §9). File storage on the vault_data volume.
storage "file" {
  path = "/vault/file"
}

listener "tcp" {
  address     = "0.0.0.0:8200"
  tls_disable = 1   # internal network only; production terminates TLS here
}

api_addr          = "http://vault:8200"
disable_mlock     = true
ui                = false
default_lease_ttl = "768h"
max_lease_ttl     = "87600h"
