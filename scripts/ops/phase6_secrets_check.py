"""Phase 6 deliverable, live: the secrets broker (architecture 06 §9) and crypto-shredding.

A fresh tenant is given its own Razorpay account. Its secrets are sealed field by field in Vault
under a key of its own, never stored or shown in clear; payment-service opens them to take a
payment (and Vault's audit log records it); no service can open what its policy does not name —
tenant-service, which writes them, can open nothing. Vault restarts and keeps serving. Finally
the tenant is archived and its keys destroyed: its sealed secrets become unreadable for good,
while another tenant's are untouched.

    python3 scripts/ops/phase6_secrets_check.py
"""
import json, os, re, secrets, subprocess, sys, time
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "lib"))
from live import Checks, call, sql, sign_in_enrolling, reset_mfa, publish_new_tenant, wait_until, OP_EMAIL, OP_PASSWORD

check = Checks()
KEY = "sec" + str(int(time.time()) % 1000000)
HOST = f"{KEY}.localhost"
OWNER = f"owner@{KEY}.example.com"
ENV = dict(l.strip().split("=", 1) for l in open(os.path.join(os.path.dirname(__file__), "..", "..", "docker", ".env"))
           if "=" in l and not l.lstrip().startswith("#"))
WEBHOOK = secrets.token_hex(12)


def row(tenant, capability):
    return sql(f"SELECT secrets_ciphertext FROM civil_engineer_tenants.tenant_integrations "
               f"WHERE tenant_key='{tenant}' AND capability='{capability}'").strip()


def vault_as(service, *args):
    """Runs the vault CLI in the broker's container with that service's token. (exit code, output)"""
    token = ENV[f"VAULT_TOKEN_{service.upper().replace('-SERVICE', '')}_SERVICE"]
    r = subprocess.run(["docker", "exec", "-e", "VAULT_ADDR=http://127.0.0.1:8200", "-e", f"VAULT_TOKEN={token}",
                        "civil_vault", "vault", *args], capture_output=True, text=True)
    return r.returncode, r.stdout + r.stderr


def audit_log():
    return subprocess.run(["docker", "exec", "civil_vault", "cat", "/vault/logs/audit.log"], capture_output=True,
                          text=True).stdout


def owner_token():
    link = None
    for _ in range(30):
        m = re.search(r"/invite/([A-Za-z0-9_-]{20,})", sql(f"SELECT body FROM civil_engineer_notifications_{KEY}.email_log "
                                                            f"WHERE recipient='{OWNER}' ORDER BY id DESC LIMIT 1"))
        if m:
            link = m.group(1)
            break
        time.sleep(2)
    call("POST", f"/auth/invitations/{link}/accept", {"password": "correct horse battery staple"}, host=HOST)
    return sign_in_enrolling(OWNER, "correct horse battery staple", host=HOST)["accessToken"]


def pay(owner):
    """A Razorpay order with the tenant's own account. Retried on a network blip reaching Razorpay —
    the broker is what is under test, not the internet."""
    for _ in range(3):
        s, b = call("POST", "/bookings", {"serviceCategory": "Plumbing", "serviceName": "Leak fix", "bookingType": "INSTANT",
                                          "city": "Pune"}, token=owner, host=HOST)
        s, order = call("POST", "/payments/create-order", {"bookingId": b["id"], "amount": 150}, token=owner, host=HOST)
        if order and order.get("razorpayOrderId"):
            return s, order
        time.sleep(3)
    return s, order or {}


reset_mfa(OP_EMAIL)
op, acme_sms = None, False
try:
    op = sign_in_enrolling(OP_EMAIL, OP_PASSWORD)["accessToken"]
    for svc in ("payment", "notification", "support"):
        env = subprocess.run(["docker", "exec", f"civil_{svc}_service", "printenv", "INTEGRATION_MASTER_KEY"],
                             capture_output=True, text=True).stdout.strip()
        check(f"{svc}-service holds no shared master key any more", env == "", "set")

    p = publish_new_tenant(op, KEY, "professional", OWNER)
    check("a fresh tenant is published", p["step"] == "DONE", p)
    s, v = call("PUT", f"/tenants/{KEY}/integrations/payment", {"mode": "BYO", "provider": "razorpay", "enabled": True,
                "settings": {"keyId": ENV["RAZORPAY_KEY_ID"]},
                "secrets": {"keySecret": ENV["RAZORPAY_KEY_SECRET"], "webhookSecret": WEBHOOK}}, token=op)
    check("its own Razorpay account is saved; the API shows only masked hints",
          s == 200 and ENV["RAZORPAY_KEY_SECRET"] not in json.dumps(v) and v["secretHints"]["keySecret"].startswith("••••"), (s, v))
    stored = row(KEY, "PAYMENT")
    sealed = json.loads(stored)
    check("stored sealed per field under the tenant's own key (vault:v1:…), nothing in clear",
          set(sealed) == {"keySecret", "webhookSecret"} and all(x.startswith("vault:v1:") for x in sealed.values())
          and ENV["RAZORPAY_KEY_SECRET"] not in stored and WEBHOOK not in stored, stored[:120])
    code, keys = vault_as("tenant-service", "list", "transit/keys")
    check(f"the broker holds key payment-tenant-{KEY}", f"payment-tenant-{KEY}" in keys, keys[-300:])

    owner = owner_token()
    s, order = pay(owner)
    check("payment-service opens the tenant's key secret through the broker and takes a payment with the tenant's account",
          s in (200, 201) and (order.get("razorpayOrderId") or "").startswith("order_")
          and order.get("razorpayKeyId") == ENV["RAZORPAY_KEY_ID"], (s, order))
    log = audit_log()
    opens = [l for l in log.splitlines() if f"transit/decrypt/payment-tenant-{KEY}" in l and '"type":"request"' in l]
    check("Vault's audit log records the open, by payment-service",
          opens and all('"display_name":"token-payment-service"' in l for l in opens), len(opens))
    check("…and not a single open by tenant-service", not any('token-tenant-service' in l and "/decrypt/" in l
                                                               for l in log.splitlines()))

    one = sealed["keySecret"]
    code, out = vault_as("tenant-service", "write", f"transit/decrypt/payment-tenant-{KEY}", f"ciphertext={one}")
    check("tenant-service, which writes secrets, is refused when it tries to open one (403)",
          code != 0 and "permission denied" in out, out[-200:])
    code, out = vault_as("notification-service", "write", f"transit/decrypt/payment-tenant-{KEY}", f"ciphertext={one}")
    check("notification-service may not open payment secrets (403)", code != 0 and "permission denied" in out, out[-200:])
    code, out = vault_as("payment-service", "write", f"transit/decrypt/email-tenant-{KEY}", f"ciphertext={one}")
    check("payment-service may not open email secrets (403)", code != 0 and "permission denied" in out, out[-200:])

    s, v = call("PUT", f"/tenants/{KEY}/integrations/payment", {"mode": "BYO", "provider": "razorpay", "enabled": True,
                "settings": {"keyId": ENV["RAZORPAY_KEY_ID"]}, "secrets": {"webhookSecret": secrets.token_hex(12)}}, token=op)
    after = json.loads(row(KEY, "PAYMENT"))
    check("rotating one secret re-seals only it: the other's sealed value is kept byte for byte (write-only)",
          s == 200 and after["keySecret"] == sealed["keySecret"] and after["webhookSecret"] != sealed["webhookSecret"])

    subprocess.run(["docker", "restart", "civil_vault"], capture_output=True)
    healthy = wait_until(lambda: subprocess.run(["docker", "inspect", "-f", "{{.State.Health.Status}}", "civil_vault"],
                                                capture_output=True, text=True).stdout.strip() == "healthy", timeout=90)
    s, order = pay(owner)
    check("the broker restarts (unseals itself, keeps its keys) and payments carry on",
          healthy and s in (200, 201) and (order.get("razorpayOrderId") or "").startswith("order_"), (healthy, s))

    # ------------------------------------------------------------------ crypto-shredding
    s, _ = call("PUT", "/tenants/acme/integrations/sms", {"mode": "BYO", "provider": "twilio", "enabled": False,
                "settings": {"accountSid": "AC" + "0" * 32, "fromNumber": "+15005550006", "senderId": "ACME"},
                "secrets": {"authToken": "acme-twilio-token-" + secrets.token_hex(6)}}, token=op)
    acme_sms = s == 200
    s, _ = call("POST", f"/tenants/{KEY}/crypto-shred", {"confirm": KEY}, token=op)
    check("an active tenant's keys cannot be destroyed (409)", s == 409, s)
    call("PATCH", f"/tenants/{KEY}/status", {"status": "ARCHIVED"}, token=op)
    s, _ = call("POST", f"/tenants/{KEY}/crypto-shred", {"confirm": "wrong"}, token=op)
    check("…nor without typing its key (400)", s == 400, s)
    s, r = call("POST", f"/tenants/{KEY}/crypto-shred", {"confirm": KEY}, token=op)
    check("the archived tenant's keys are destroyed", s == 200 and r["keysDestroyed"] >= 1 and r["integrationsDisabled"] == 1,
          (s, r))
    code, keys = vault_as("tenant-service", "list", "transit/keys")
    check("…gone from the broker", f"-tenant-{KEY}" not in keys, keys[-200:])
    check("its sealed secrets are still in the database…", row(KEY, "PAYMENT").startswith("{"))
    code, out = vault_as("payment-service", "write", f"transit/decrypt/payment-tenant-{KEY}", f"ciphertext={one}")
    check("…and can never be opened again, by anyone", code != 0 and "not found" in out.lower(), out[-200:])
    s, t = call("GET", f"/tenants/{KEY}", token=op)
    check("the tenant records when its keys were destroyed", s == 200 and t.get("keysDestroyedAt"), t.get("keysDestroyedAt"))
    acme_sealed = json.loads(row("acme", "SMS"))["authToken"]
    code, out = vault_as("notification-service", "write", "transit/decrypt/sms-tenant-acme", f"ciphertext={acme_sealed}")
    check("another tenant's secrets are untouched (acme's SMS token still opens for notification-service)",
          code == 0 and "plaintext" in out, out[-200:])
finally:
    if op:
        if acme_sms:
            call("DELETE", "/tenants/acme/integrations/sms", token=op)
        call("PATCH", f"/tenants/{KEY}/status", {"status": "ARCHIVED"}, token=op)
    reset_mfa(OP_EMAIL)
    print(f"(tenant '{KEY}' archived and shredded; acme's test SMS integration removed; drill operator MFA reset)")

print("\nALL PASSED" if check.ok else "\nSOME FAILED")
sys.exit(0 if check.ok else 1)
