"""Workspace self-service, live: every tenant runs on the platform's provider accounts by default,
and its own admins can change that, switch modules and add users — for their workspace only.

A fresh tenant is published. Its owner (invited by email) opens Workspace settings: switches a
module off and on within the plan (the gateway follows), cannot take tenant administration, moves
the AI assistant from the platform's Gemini to Anthropic with a model, switches SMS off and back
to the platform default. Payments with no account of the tenant's own run on the platform's
merchant account, whose webhook settles only such tenants. The owner adds a colleague as ADMIN by
invitation; the colleague sets a password, signs in and reaches the settings, but cannot make a
Super Admin. The operator workspace and non-admins are refused. The demo tenant is archived.

    python3 scripts/tenancy/workspace_selfservice_check.py
"""
import hashlib, hmac, json, os, re, sys, time, urllib.request, urllib.error
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "lib"))
from live import Checks, call, sql, sign_in_enrolling, reset_mfa, publish_new_tenant, wait_until, OP_EMAIL, OP_PASSWORD, GW

check = Checks()
KEY = "ws" + str(int(time.time()) % 1000000)
HOST = f"{KEY}.localhost"
OWNER = f"owner@{KEY}.example.com"
MATE = f"ravi@{KEY}.example.com"
ENV = dict(l.strip().split("=", 1) for l in open(os.path.join(os.path.dirname(__file__), "..", "..", "docker", ".env"))
           if "=" in l and not l.lstrip().startswith("#"))


def invite_link(email):
    for _ in range(40):
        body = sql(f"SELECT body FROM civil_engineer_notifications_{KEY}.email_log WHERE recipient='{email}' ORDER BY id DESC LIMIT 1")
        m = re.search(r"/invite/([A-Za-z0-9_-]{20,})", body)
        if m:
            return m.group(1)
        time.sleep(2)
    return None


def webhook(body, secret):
    raw = json.dumps(body).encode()
    sig = hmac.new(secret.encode(), raw, hashlib.sha256).hexdigest()
    req = urllib.request.Request(GW.replace("/api/v1", "") + "/webhooks/payments/razorpay/platform", data=raw,
                                 method="POST", headers={"Content-Type": "application/json", "X-Razorpay-Signature": sig})
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return r.status
    except urllib.error.HTTPError as e:
        return e.code


def by_cap(items):
    return {i["capability"]: i for i in items}


reset_mfa(OP_EMAIL)
op = None
try:
    op = sign_in_enrolling(OP_EMAIL, OP_PASSWORD)["accessToken"]
    p = publish_new_tenant(op, KEY, "professional", OWNER)
    check("a new tenant is published", p["step"] == "DONE", p)

    s, items = call("GET", f"/tenants/{KEY}/integrations", token=op)
    caps = by_cap(items)
    check("a new tenant has no payment, SMS or WhatsApp account of its own",
          s == 200 and not any(caps[c]["configured"] for c in ("payment", "sms", "whatsapp")), items)
    s, cat = call("GET", "/tenants/integration-catalog", token=op)
    ai_providers = sorted(by_cap(cat)["ai"]["providers"]) if s == 200 else []
    check("the AI assistant offers Gemini, OpenAI and Anthropic", ai_providers == ["anthropic", "gemini", "openai"], ai_providers)

    link = invite_link(OWNER)
    call("POST", f"/auth/invitations/{link}/accept", {"password": "correct horse battery staple"}, host=HOST)
    owner = sign_in_enrolling(OWNER, "correct horse battery staple", host=HOST)["accessToken"]

    # --- Modules, within the plan
    s, m = call("GET", "/workspace-settings/modules", token=owner, host=HOST)
    mods = {x["key"]: x for x in m["modules"]} if s == 200 else {}
    check("the owner sees their modules and plan", s == 200 and m["planName"] == "Professional" and m["tenantKey"] == KEY, (s, m))
    check("sign-in, users, console and audit are locked on", all(mods[k]["locked"] and mods[k]["chosen"] for k in ("auth", "users", "admin", "audit")), mods)
    optional = [k for k, x in mods.items() if x["chosen"] and not x["locked"]]
    s, m2 = call("PUT", "/workspace-settings/modules", {"modules": [k for k in optional if k != "projects"]}, token=owner, host=HOST)
    check("the owner switches Projects off", s == 200 and not {x["key"]: x for x in m2["modules"]}["projects"]["chosen"], (s, m2))
    check("the gateway closes Projects within 45s",
          wait_until(lambda: call("GET", "/projects", host=HOST)[0] == 404, timeout=45), call("GET", "/projects", host=HOST)[0])
    s, _ = call("PUT", "/workspace-settings/modules", {"modules": optional}, token=owner, host=HOST)
    check("switching it back on reopens Projects",
          s == 200 and wait_until(lambda: call("GET", "/projects", host=HOST)[0] == 401, timeout=45))
    s, r = call("PUT", "/workspace-settings/modules", {"modules": optional + ["tenantadmin"]}, token=owner, host=HOST)
    check("a workspace cannot switch on tenant administration", s == 400, (s, r))
    outside = next(k for k, x in mods.items() if not x["entitled"])
    s, r = call("PUT", "/workspace-settings/modules", {"modules": optional + [outside]}, token=owner, host=HOST)
    check(f"a module outside the plan ({outside}) is refused", s == 400 and "plan" in (r or {}).get("message", ""), (s, r))
    s, all_off = call("PUT", "/workspace-settings/modules", {"modules": []}, token=owner, host=HOST)
    locked_kept = s == 200 and all({x["key"]: x for x in all_off["modules"]}[k]["chosen"] for k in ("auth", "users", "admin", "audit"))
    check("sending no modules still keeps the locked ones", locked_kept, (s, all_off))
    call("PUT", "/workspace-settings/modules", {"modules": optional}, token=owner, host=HOST)

    # --- Provider accounts: platform by default, the workspace's own on request
    s, st = call("GET", "/support/ai/status", token=owner, host=HOST)
    platform_ai = bool(ENV.get("GEMINI_API_KEY"))
    check("the assistant starts on the platform's Gemini",
          s == 200 and (st["provider"] == "gemini" if platform_ai else st["available"] is False), (s, st))
    s, v = call("PUT", "/workspace-settings/integrations/ai", {"mode": "BYO", "provider": "anthropic", "enabled": True,
                "settings": {"model": "claude-sonnet-5"}, "secrets": {"apiKey": "sk-ant-live-check-000000"}}, token=owner, host=HOST)
    check("the owner moves the assistant to Anthropic with a model",
          s == 200 and v["provider"] == "anthropic" and v["settings"] == {"model": "claude-sonnet-5"}
          and v["secretHints"]["apiKey"].startswith("••••") and "sk-ant" not in json.dumps(v), (s, v))
    check("the assistant now answers through Anthropic",
          wait_until(lambda: call("GET", "/support/ai/status", token=owner, host=HOST)[1].get("provider") == "anthropic", timeout=40))
    stored = sql(f"SELECT secrets_ciphertext FROM civil_engineer_tenants.tenant_integrations WHERE tenant_key='{KEY}' AND capability='AI'")
    check("the key is stored sealed, not in the clear", "sk-ant-live-check" not in stored and stored.strip() != "", stored[:60])
    s, _ = call("DELETE", "/workspace-settings/integrations/ai", token=owner, host=HOST)
    check("back to the platform default in one call", s == 204 and wait_until(
        lambda: call("GET", "/support/ai/status", token=owner, host=HOST)[1].get("provider") == ("gemini" if platform_ai else None), timeout=40))

    s, v = call("PUT", "/workspace-settings/integrations/sms", {"mode": "PLATFORM_SHARED", "enabled": False}, token=owner, host=HOST)
    check("SMS can be switched off", s == 200 and v["enabled"] is False, (s, v))
    s, _ = call("DELETE", "/workspace-settings/integrations/sms", token=owner, host=HOST)
    s2, items = call("GET", "/workspace-settings/integrations", token=owner, host=HOST)
    check("…and put back on the platform default", s == 204 and not by_cap(items)["sms"]["configured"], (s, items))
    s, _ = call("DELETE", "/workspace-settings/integrations/whatsapp", token=owner, host=HOST)
    check("resetting what is already the default is harmless", s == 204, s)

    # --- Payments on the platform's merchant account, and its webhook
    secret = ENV.get("RAZORPAY_WEBHOOK_SECRET", "")
    event = lambda tenant: {"event": "payment.captured", "payload": {"payment": {"entity": {
        "id": "pay_live_check", "order_id": "order_live_check_none", "notes": {"tenant_key": tenant}}}}}
    check("the platform webhook accepts an event for a workspace on the platform account",
          bool(secret) and webhook(event(KEY), secret) == 200, "no RAZORPAY_WEBHOOK_SECRET" if not secret else "")
    check("…but not one signed with any other secret", webhook(event(KEY), "not-the-secret") == 401)
    call("PUT", "/workspace-settings/integrations/payment", {"mode": "BYO", "provider": "razorpay", "enabled": True,
         "settings": {"keyId": "rzp_test_own"}, "secrets": {"keySecret": "own-secret-123456", "webhookSecret": "own-wh"}},
         token=owner, host=HOST)
    check("…and not for a workspace with its own merchant account",
          wait_until(lambda: webhook(event(KEY), secret) == 401, timeout=40))
    call("DELETE", "/workspace-settings/integrations/payment", token=owner, host=HOST)

    # --- Team: adding users by invitation
    s, roles = call("GET", "/admin/users/roles", token=owner, host=HOST)
    check("the owner sees the roles a user can be given", s == 200 and "ADMIN" in [r["name"] for r in roles["data"]], (s, roles))
    s, r = call("POST", "/admin/users", {"name": "Ravi", "email": MATE, "role": "ADMIN", "linkBase": f"http://{HOST}:3000",
                "workspaceName": f"Demo {KEY}"}, token=owner, host=HOST)
    check("the owner adds a colleague as ADMIN", s == 200 and r["data"]["role"] == "ADMIN", (s, r))
    s, r = call("POST", "/admin/users", {"name": "Ravi", "email": MATE, "role": "ADMIN", "linkBase": f"http://{HOST}:3000",
                "workspaceName": "x"}, token=owner, host=HOST)
    check("the same email cannot be added twice", s == 400 and "already" in (r or {}).get("message", ""), (s, r))
    mate_link = invite_link(MATE)
    check("the colleague is emailed an invitation to this workspace", mate_link is not None)
    call("POST", f"/auth/invitations/{mate_link}/accept", {"password": "another long passphrase"}, host=HOST)
    s, login = call("POST", "/auth/login", {"email": MATE, "password": "another long passphrase"}, host=HOST)
    mate = (login or {}).get("accessToken")
    check("the colleague signs in to this workspace", s == 200 and mate, (s, login))
    s, _ = call("GET", "/workspace-settings/modules", token=mate, host=HOST)
    check("an ADMIN reaches the workspace settings", s == 200, s)
    s, r = call("POST", "/admin/users", {"email": f"boss@{KEY}.example.com", "role": "SUPER_ADMIN",
                "linkBase": f"http://{HOST}:3000", "workspaceName": "x"}, token=mate, host=HOST)
    check("an ADMIN cannot add a Super Admin", s == 403, (s, r))
    s, _ = call("POST", "/auth/login", {"email": MATE, "password": "another long passphrase"})
    check("the colleague's account exists only in this workspace", s in (400, 401), s)

    # --- Scope
    s, r = call("GET", "/workspace-settings/modules", token=op)
    check("the operator workspace has no workspace settings of its own", s == 400, (s, r))
    check("tenant-wide integration APIs stay closed to the workspace", call("GET", f"/tenants/{KEY}/integrations", token=owner, host=HOST)[0] == 404)
    s, menu = call("GET", "/ui-config/me", token=owner, host=HOST)
    keys = json.dumps(menu)
    check("the owner's console menu has Workspace settings", s == 200 and "admin-workspace-settings" in keys, s)
finally:
    if op:
        call("PATCH", f"/tenants/{KEY}/status", {"status": "ARCHIVED"}, token=op)
    reset_mfa(OP_EMAIL)
    print(f"(demo tenant '{KEY}' archived; operator MFA reset)")

print("\nALL PASSED" if check.ok else "\nSOME FAILED")
sys.exit(0 if check.ok else 1)
