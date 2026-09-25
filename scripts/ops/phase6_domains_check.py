"""Phase 6 deliverable, live: a tenant's custom domain, from DNS proof to an ACME certificate served
at the edge — and what happens when the proof disappears or the domain is removed.

Locally, Pebble plays Let's Encrypt and challtestsrv plays the tenant's DNS provider: this script
publishes the records through challtestsrv's API, as the tenant would at their registrar.

    python3 scripts/ops/phase6_domains_check.py
"""
import json, os, socket, ssl, subprocess, sys, tempfile, time, urllib.request
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "lib"))
from live import Checks, call, sign_in_enrolling, reset_mfa, wait_until, OP_EMAIL, OP_PASSWORD

check = Checks()
RUN = str(int(time.time()) % 1000000)
HOST = f"www.acme-{RUN}.test"
DNS_API = "http://127.0.0.1:8055"
EDGE_HTTPS = ("127.0.0.1", int(os.environ.get("HOST_PORT_NGINX_SSL", "8443")))
EDGE_HTTP = f"http://127.0.0.1:{os.environ.get('HOST_PORT_NGINX', '8010')}"


def dns(path, body):
    req = urllib.request.Request(DNS_API + path, data=json.dumps(body).encode(), method="POST")
    urllib.request.urlopen(req, timeout=5).read()


def pebble_root():
    ctx = ssl._create_unverified_context()
    return urllib.request.urlopen("https://127.0.0.1:15000/roots/0", context=ctx, timeout=5).read().decode()


def tls(host):
    """Handshake with the edge for `host` (SNI), verifying the chain against Pebble's root. Returns the peer cert."""
    with tempfile.NamedTemporaryFile("w", suffix=".pem", delete=False) as f:
        f.write(pebble_root())
    ctx = ssl.create_default_context(cafile=f.name)
    with socket.create_connection(EDGE_HTTPS, timeout=5) as raw:
        with ctx.wrap_socket(raw, server_hostname=host) as s:
            return s.getpeercert()


def https_get(host, path):
    with tempfile.NamedTemporaryFile("w", suffix=".pem", delete=False) as f:
        f.write(pebble_root())
    ctx = ssl.create_default_context(cafile=f.name)
    with socket.create_connection(EDGE_HTTPS, timeout=10) as raw:
        with ctx.wrap_socket(raw, server_hostname=host) as s:
            s.sendall(f"GET {path} HTTP/1.1\r\nHost: {host}\r\nConnection: close\r\n\r\n".encode())
            data = b""
            while chunk := s.recv(65536):
                data += chunk
    head, _, body = data.partition(b"\r\n\r\n")
    status = int(head.split(b" ")[1])
    if b"transfer-encoding: chunked" in head.lower():
        out, rest = b"", body
        while rest:
            size, _, rest = rest.partition(b"\r\n")
            n = int(size, 16)
            if n == 0:
                break
            out, rest = out + rest[:n], rest[n + 2:]
        body = out
    return status, body


def edge_ip():
    return subprocess.run(["docker", "inspect", "-f", "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}", "civil_nginx"],
                          capture_output=True, text=True).stdout.strip()


def reaches_acme(host):
    """Whether a host resolves to the acme workspace. (Locally, unknown hosts fall back to the platform
    tenant — platform.tenant.fallback, blank in any deployment — so 'not acme' is the local form of 404.)"""
    s, body = call("GET", "/tenant-resolution/current", host=host)
    return s == 200 and body.get("tenantKey") == "acme"


def domain(op, key, did):
    return next(d for d in call("GET", f"/tenants/{key}/domains", token=op)[1] if d["id"] == did)


reset_mfa(OP_EMAIL)
op, did = None, None
try:
    op = sign_in_enrolling(OP_EMAIL, OP_PASSWORD)["accessToken"]
    s, e = call("POST", "/tenants/acme/domains", {"host": "shop.localhost"}, token=op)
    check("a platform zone cannot be claimed (400)", s == 400, (s, e))
    s, d = call("POST", "/tenants/acme/domains", {"host": HOST.upper() + "."}, token=op)
    check("a domain is added, normalised, waiting for DNS", s == 201 and d["host"] == HOST
          and d["status"] == "PENDING_VERIFICATION", (s, d))
    did = d["id"]
    txt = next(r for r in d["records"] if r["type"] == "TXT")
    check("the tenant is told which records to publish (TXT proof, CNAME to the edge)",
          txt["name"] == f"_platform-verify.{HOST}" and len(txt["value"]) == 32
          and any(r["type"] == "CNAME" for r in d["records"]), d["records"])
    s, _ = call("POST", "/tenants/bhoomi/domains", {"host": HOST}, token=op)
    check("nobody else can claim it meanwhile (409)", s == 409, s)

    call("POST", f"/tenants/acme/domains/{did}/check", token=op)
    check("without the TXT record it stays unverified",
          domain(op, "acme", did)["status"] == "PENDING_VERIFICATION")
    check("…and the host does not reach the tenant", not reaches_acme(HOST))

    # ------------------------------------------------------------------ the tenant publishes its records
    dns("/set-txt", {"host": f"_platform-verify.{HOST}.", "value": txt["value"]})
    dns("/add-a", {"host": f"{HOST}.", "addresses": [edge_ip()]})
    started = time.time()
    call("POST", f"/tenants/acme/domains/{did}/check", token=op)
    check("proven, certified by the ACME server (Pebble) over HTTP-01 through the edge, and live",
          wait_until(lambda: domain(op, "acme", did)["status"] == "ACTIVE", timeout=120, every=2),
          domain(op, "acme", did))
    d = domain(op, "acme", did)
    check(f"…in {time.time() - started:.0f}s, certificate from {d['certIssuer']} until {d['certNotAfter']}",
          "Pebble" in (d["certIssuer"] or ""), d)

    cert = tls(HOST)
    sans = [v for k, v in cert.get("subjectAltName", ()) if k == "DNS"]
    check("the edge serves it over TLS with that certificate (verified against the CA, SAN = the host)",
          sans == [HOST], cert)
    s, body = https_get(HOST, "/api/v1/tenant-resolution/current")
    check("https://<domain> reaches the tenant's workspace (acme)", s == 200 and json.loads(body)["tenantKey"] == "acme",
          (s, body[:200]))
    s, page = https_get(HOST, "/")
    check("…and serves the app", s == 200 and b"<div id=\"root\"" in page, (s, page[:120]))
    r = urllib.request.Request(EDGE_HTTP + "/", headers={"Host": HOST})
    try:
        urllib.request.build_opener(type("NoRedirect", (urllib.request.HTTPRedirectHandler,),
                                         {"redirect_request": lambda *a: None})).open(r, timeout=5)
        status = 200
    except urllib.error.HTTPError as e:
        status, location = e.code, e.headers.get("Location")
    check("plain HTTP redirects to HTTPS", status == 301 and location == f"https://{HOST}/", (status, location))
    try:
        tls(f"www.unknown-{RUN}.test")
        unknown = "served"
    except (ssl.SSLError, OSError) as e:
        unknown = "refused"
    check("a host with no certificate gets no TLS at all", unknown == "refused", unknown)
    check("a look-alike host (acme.<elsewhere>) does not resolve to the tenant", not reaches_acme(f"acme.evil-{RUN}.test"))

    # ------------------------------------------------------------------ proof disappears
    dns("/clear-txt", {"host": f"_platform-verify.{HOST}."})
    subprocess.run(["docker", "exec", "civil_mysql", "sh", "-c",
                    "mysql -uroot -p\"$MYSQL_ROOT_PASSWORD\" -e \"UPDATE civil_engineer_tenants.tenant_domains "
                    f"SET last_checked_at = NOW() - INTERVAL 2 DAY WHERE id = {did}\""], capture_output=True)
    check("the daily re-check finds the TXT record gone: DEGRADED, still serving through its grace",
          wait_until(lambda: domain(op, "acme", did)["status"] == "DEGRADED", timeout=30), domain(op, "acme", did))
    check("…the site still answers", https_get(HOST, "/api/v1/tenant-resolution/current")[0] == 200)
    dns("/set-txt", {"host": f"_platform-verify.{HOST}.", "value": txt["value"]})
    call("POST", f"/tenants/acme/domains/{did}/check", token=op)
    subprocess.run(["docker", "exec", "civil_mysql", "sh", "-c",
                    "mysql -uroot -p\"$MYSQL_ROOT_PASSWORD\" -e \"UPDATE civil_engineer_tenants.tenant_domains "
                    f"SET last_checked_at = NOW() - INTERVAL 2 DAY WHERE id = {did}\""], capture_output=True)
    check("the record back: ACTIVE again", wait_until(lambda: domain(op, "acme", did)["status"] == "ACTIVE", timeout=30))

    # ------------------------------------------------------------------ removal
    s, gone = call("DELETE", f"/tenants/acme/domains/{did}", token=op)
    check("removed on request", s == 200 and gone["status"] == "REMOVED", (s, gone))
    did = None
    try:
        tls(HOST)
        after = "served"
    except (ssl.SSLError, OSError):
        after = "refused"
    check("its certificate is withdrawn from the edge immediately", after == "refused", after)
    check("the host stops routing to the tenant (within the gateway cache, ≤30 s)",
          wait_until(lambda: not reaches_acme(HOST), timeout=45))
    s, again = call("POST", "/tenants/bhoomi/domains", {"host": HOST}, token=op)
    check("a removed domain can be claimed again (by its new owner)", s == 201, (s, again))
    call("DELETE", f"/tenants/bhoomi/domains/{again['id']}", token=op)
finally:
    if op and did:
        call("DELETE", f"/tenants/acme/domains/{did}", token=op)
    for path, body in (("/clear-txt", {"host": f"_platform-verify.{HOST}."}), ("/clear-a", {"host": f"{HOST}."})):
        try:
            dns(path, body)
        except Exception:
            pass
    reset_mfa(OP_EMAIL)
    print(f"(domain {HOST} removed; DNS records cleared; operator MFA reset)")

print("\nALL PASSED" if check.ok else "\nSOME FAILED")
sys.exit(0 if check.ok else 1)
