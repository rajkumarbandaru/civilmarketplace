"""Phase 4, live: reference presets A/B/C applied to real tenants render through the public
bundle, previews carry unpublished themes safely, and the published look is what anonymous
visitors get. The visual matrix itself runs in Playwright (frontend/e2e/experience-matrix.spec.ts).

    python3 scripts/experience/phase4_experience_check.py
"""
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "lib"))
from live import Checks, call, sign_in_enrolling, reset_mfa, OP_EMAIL, OP_PASSWORD

check = Checks()
TENANTS = {"acme": "aurora-marketplace", "bhoomi": "evergreen-corporate", "testing": "onyx-boutique"}
EXPECT = {"aurora-marketplace": ("glass", "marketplace"), "evergreen-corporate": ("material", "corporate"),
          "onyx-boutique": ("luxury", "ecommerce")}
tokens, before = {}, {}
for t in TENANTS: reset_mfa(OP_EMAIL, t)
try:
    for t, preset in TENANTS.items():
        host = f"{t}.localhost"
        tokens[t] = sign_in_enrolling(OP_EMAIL, OP_PASSWORD, host=host)["accessToken"]
        s, rel = call("GET", "/admin/config/releases?scope=PLATFORM", token=tokens[t], host=host)
        before[t] = rel[0]["id"]
        s, presets = call("GET", "/admin/theme/presets", token=tokens[t], host=host)
        values = next(p["values"] for p in presets if p["key"] == preset)
        check(f"[{t}] preset {preset} is offered", True)

        # Preview first: the real app with the preset, unpublished.
        s, pv = call("POST", "/admin/config/preview", values, token=tokens[t], host=host)
        check(f"[{t}] a preview link is issued", s == 200 and pv["path"].startswith("/?preview="), (s, pv))
        s, shown = call("GET", f"/ui-config/public/preview/{pv['token']}", host=host)
        check(f"[{t}] the preview carries the unpublished look", s == 200 and shown["uiStyle"] == EXPECT[preset][0], (s, shown))
        other = next(x for x in TENANTS if x != t)
        s, _ = call("GET", f"/ui-config/public/preview/{pv['token']}", host=f"{other}.localhost")
        check(f"[{t}] the preview link does not open on another workspace", s == 404, s)
        s, live = call("GET", "/ui-config/public/theme", host=host)
        check(f"[{t}] previewing did not publish anything", live["uiStyle"] != EXPECT[preset][0] or live["siteLayout"] != EXPECT[preset][1], live)

        s, _ = call("PUT", "/admin/theme", values, token=tokens[t], host=host)
        check(f"[{t}] preset published", s == 200, s)
        s, live = call("GET", "/ui-config/public/theme", host=host)
        check(f"[{t}] anonymous visitors now get {EXPECT[preset][0]} + {EXPECT[preset][1]}",
              s == 200 and (live["uiStyle"], live["siteLayout"]) == EXPECT[preset], (s, live))

    s, bad = call("POST", "/admin/config/preview", {"mode": "light", "uiStyle": "holographic"}, token=tokens["acme"], host="acme.localhost")
    check("a preview of a style this build cannot render is refused", s == 400, (s, bad))
    s, a = call("GET", "/ui-config/public/theme", host="acme.localhost")
    s, b = call("GET", "/ui-config/public/theme", host="bhoomi.localhost")
    check("three tenants, three different experiences, one build", a["uiStyle"] != b["uiStyle"] and a["siteLayout"] != b["siteLayout"], (a, b))
finally:
    for t, rid in before.items():
        call("POST", f"/admin/config/releases/{rid}/rollback", {"reason": "phase 4 live check"}, token=tokens.get(t), host=f"{t}.localhost")
    for t in TENANTS: reset_mfa(OP_EMAIL, t)
    print("(themes rolled back; admins' MFA reset)")

print("\nALL PASSED" if check.ok else "\nSOME FAILED")
sys.exit(0 if check.ok else 1)
