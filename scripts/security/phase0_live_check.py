"""Phase 0 isolation and hardening checks, run against a live local stack.

Unit tests cover each mechanism in isolation; this proves they hold together in the running
system: published ports, forged and unsigned identity, tenant-scoped Redis keys, OTP handling,
cross-tenant token use and workspace resolution. It needs the dev seed accounts (DevUserSeeder)
and the `acme` tenant, and talks to Docker directly, so run it on the host:

    docker compose -f docker-compose.yml -f docker-compose.lean.yml up -d   # from docker/
    python3 scripts/security/phase0_live_check.py

Exit status 0 means every check passed.
"""
import json, socket, subprocess, sys, urllib.request, urllib.error

import os

GW = os.environ.get("GATEWAY_URL", "http://localhost:8080") + "/api/v1"
ok = True


def check(name, cond, detail=""):
    global ok
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else f"  -> {detail}"))
    ok = ok and cond


def call(method, path, body=None, headers=None, host=None):
    h = {"Content-Type": "application/json"}
    h.update(headers or {})
    if host: h["Host"] = host
    req = urllib.request.Request(GW + path, method=method, data=None if body is None else json.dumps(body).encode(), headers=h)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, json.loads(r.read() or b"null")
    except urllib.error.HTTPError as e:
        try: return e.code, json.loads(e.read())
        except Exception: return e.code, None


def redis(*args):
    return subprocess.run(["docker", "exec", "civil_redis", "redis-cli", *args], capture_output=True, text=True).stdout.strip()


PUBLISHED = subprocess.run(["docker", "ps", "--filter", "name=civil_", "--format", "{{.Ports}}"],
                           capture_output=True, text=True).stdout


def port_open(p):
    """Published by this stack's containers (other host programs may hold the same port)."""
    return f":{p}->" in PUBLISHED


# Item 3: only the public entry points are published
for p, name in [(3306, "MySQL"), (6381, "Redis"), (8761, "Eureka"), (8888, "config-server"), (8093, "auth-service"),
                (8094, "user-service"), (8100, "media-service"), (29093, "Kafka"), (9001, "MinIO console")]:
    check(f"{name} ({p}) not published", not port_open(p))
for p, name in [(8080, "gateway"), (3000, "frontend"), (9000, "MinIO API")]:
    check(f"{name} ({p}) still published", port_open(p))

# Item 2: refresh keys carry the tenant
s, d = call("POST", "/auth/login", {"email": "customer@civileng.test", "password": "Password123!"})
check("login works", s == 200, s)
uid = d["user"]["id"]
check("refresh token stored under its tenant",
      bool(redis("--scan", "--pattern", f"refresh_token:platform:{uid}:*")), redis("--scan", "--pattern", "refresh_token:*"))

# Item 2: OTP keys carry the tenant, the code is not logged, and guessing is capped
redis("DEL", *(redis("--scan", "--pattern", "otp:*").split() or ["none"]))
s, d = call("POST", "/auth/otp/send", {"email": "customer@civileng.test"})
check("OTP sent", s == 200, (s, d))
keys = [k for k in redis("--scan", "--pattern", "otp:platform:*").split() if not k.startswith(("otp:cooldown", "otp:attempts"))]
check("OTP key is tenant-scoped", len(keys) == 1, redis("--scan", "--pattern", "otp:*"))
code = redis("GET", keys[0]).strip('"') if keys else ""
logs = subprocess.run(["docker", "logs", "--since", "2m", "civil_auth_service"], capture_output=True, text=True)
check("OTP code not in the logs", code and code not in logs.stdout + logs.stderr, "code found in logs")
wrong = "000000" if code != "000000" else "111111"
for _ in range(5):
    call("POST", "/auth/otp/verify", {"email": "customer@civileng.test", "otp": wrong})
s, d = call("POST", "/auth/otp/verify", {"email": "customer@civileng.test", "otp": code})
check("right code refused after 5 wrong guesses", s in (400, 401), (s, d))

# Item 1: forged identity headers are dropped at the gateway (visible in its log)
call("GET", "/catalogue/services", headers={"X-User-Role": "SUPER_ADMIN", "X-User-Id": "1"})
glog = subprocess.run(["docker", "logs", "--since", "1m", "civil_api_gateway"], capture_output=True, text=True)
check("gateway drops forged X-User-* on a public route", "Dropped client-supplied identity headers" in glog.stdout + glog.stderr)

# Item 4: inside the network, a service refuses identity the gateway did not sign
def inside(url, *headers):
    args = ["docker", "exec", "civil_api_gateway", "wget", "-S", "-q", "-O", "-"]
    for h in headers: args += ["--header", h]
    r = subprocess.run(args + [url], capture_output=True, text=True)
    return r.stdout + r.stderr

out = inside("http://user-service:8082/api/v1/users/portfolio", "X-Tenant-Id: platform", "X-User-Id: 1", "X-User-Role: SUPER_ADMIN")
check("direct call with forged identity refused (401)", "401" in out, out[-300:])
out = inside("http://user-service:8082/api/v1/users/portfolio", "X-Tenant-Id: platform", "X-User-Id: 1",
             "X-Internal-Signature: v1:1790000000:forged")
check("direct call with a made-up signature refused (401)", "401" in out, out[-300:])
out = inside("http://user-service:8082/actuator/health")
check("health probe with no identity still served", "UP" in out, out[-300:])
out = inside("http://tenant-service:8099/api/v1/tenants", "X-Tenant-Id: platform", "X-User-Id: 1", "X-User-Role: SUPER_ADMIN")
check("tenant-service (tenant runtime off) also refuses forged operator headers", "401" in out, out[-300:])

# Cross-tenant: a token is only good on the workspace it was issued for
s, d = call("POST", "/auth/login", {"email": "customer@civileng.test", "password": "Password123!"})
token = d["accessToken"]
s, _ = call("GET", "/users/portfolio", headers={"Authorization": "Bearer " + token})
check("token works on its own workspace", s == 200, s)
s, _ = call("GET", "/users/portfolio", headers={"Authorization": "Bearer " + token}, host="acme.localhost")
check("platform token refused on acme's address (403)", s == 403, s)

# Workspace bootstrap resolves by Host, exactly as every other request
s, w = call("GET", "/tenant-resolution/current")
check("bootstrap resolves this address's workspace", s == 200 and w.get("tenantKey") == "platform", (s, w))
s, w = call("GET", "/tenant-resolution/current", host="acme.localhost")
check("bootstrap resolves acme by its subdomain", s == 200 and w.get("tenantKey") == "acme", (s, w))
check("bootstrap exposes no private tenant fields", s == 200 and not w.get("contactEmail") and not w.get("plan"), w)

print("\nALL PASSED" if ok else "\nSOME FAILED")
sys.exit(0 if ok else 1)
