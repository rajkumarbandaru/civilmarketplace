#!/usr/bin/env python3
"""Generate the Angular project docs (Markdown + HTML) into ~/RAJKUMAR/project-info/infi/angular."""
import html
import json
import os
import re
from collections import Counter

import markdown

REPO = '/home/aryagami/VIPLine-billing'
DASH = f'{REPO}/aryadash-dashboard'
OUT = '/home/aryagami/RAJKUMAR/project-info/infi/angular'
CFG = f'{DASH}/src/config'

# Hand-written one-line purpose per product, read from each product's menu and screens.
PRODUCTS = {
    'vip-line': ('VIP Line', 'Reseller and admin portal for a private mobile/VoIP operator: SIM cards, activations, calls and SMS, data, DIDs, finances, rate sheets, SIM transfer and reseller management.'),
    'arya-crm': ('Arya CRM', 'Ticketing and customer-care CRM: raise and track tickets, approvals, support and CRM chats, web channels, bot flows, staff rosters, agent break requests and a live agent panel.'),
    'arya-billing-admin': ('Arya Billing Admin (Xmobee)', 'The largest product: full billing back office for an MVNO - MNO, customer, reseller, product, wallet, warehouse and SIM management, finance, dunning, invoices, tax and business-activation reports.'),
    'arya-billing': ('Arya Billing (reseller)', 'Reseller-facing billing portal (infimobile): activation, registration, staff, product and stock requests, e-top-up and voucher sales, returns and recalls.'),
    'arya-connect': ('AryaConnect', 'CPaaS e-mail workspace: accounts, staff, roster, inbox, outbox, folders and mail sync logs.'),
    'arya-spect': ('Arya Spect (IMBIL Telecom)', 'Monitoring / lawful-intercept style console: targets, users, audit events, error logs, tenants and subscriptions.'),
    'bss-metrics': ('MetriXcope (BSS Metrics)', 'Read-only BSS analytics: dashboards for orders, activations, payments, services, recharges and finance.'),
    'daily-reports': ('Daily Reports (Lyca Mobile)', 'Daily operational reporting: reports, data validations and analytics dashboards.'),
    'edr-system': ('EDR System', 'Endpoint / EDR admin console: users, devices, audit logs and settings.'),
    'multi-tenant': ('Multi-Tenant Admin', 'Tenant onboarding and administration for the multi-tenant platform: onboarding, users, audit events, error logs, tenants, subscriptions.'),
    'ocs-monitor': ('OCS Monitor', 'Online Charging System monitor: recharges, rated CDRs, servers, subscriptions, USSD menus and tariffs.'),
    'port-mnp-solutions': ('Portease MNP Solutions', 'Mobile Number Portability portal: port-in, port-out and routing requests.'),
    'projectile': ('Projectile', 'Project delivery tool: accounts, projects, change requests, QA review tool, AI chat, KPIs.'),
    'wirepay': ('Wirepay (Wirecard Solutions Operations)', 'Payments operations console: overview, onboarding, resellers, wallets and float. Uses JWT bearer login and an X-TENANT-ID header.'),
}

FEATURE_KEYS = {
    'access-control': 'Custom role field for access rules',
    'api-config': 'Global extra request headers (header-params)',
    'global-context': 'Global context selector in the header (stored in DataStore, can reload page)',
    'live-panel': 'Live agent panel',
    'break-request': 'Agent break requests (break panel)',
    'float-action': 'Floating action button that opens a modal view',
    'crmchat': 'CRM chat', 'supportchat': 'Support chat', 'infibotchat': 'Bot chat', 'troubleshootchat': 'Troubleshoot chat',
    'change-password': 'Change-password flow', 'user-profile': 'Role-based profile icon',
    'layout': 'Default layout', 'links': 'External links', 'toprightlogo': 'Reseller brand logo from DataStore',
    'clientlogo': 'Client logo', 'summary-gauges': 'Dashboard gauges', 'trend-bars': 'Dashboard trend bars',
    'trend-charts': 'Dashboard trend charts', 'topn-tables': 'Dashboard top-N tables', 'summary-views': 'Dashboard summary views',
    'metrics-summary': 'Dashboard metric tiles',
}


def load_json(path):
    try:
        with open(path) as f:
            return json.load(f)
    except Exception:
        return None


def angular_configs():
    a = load_json(f'{DASH}/angular.json')['projects']['vip-line']['architect']
    serve = {}
    for name, v in a['serve']['configurations'].items():
        build = v['browserTarget'].split(':')[-1]
        serve.setdefault(build, []).append((name, v.get('proxyConfig')))
    return a['build']['configurations'], serve


def product_facts(slug, builds, serves):
    d = f'{CFG}/{slug}'
    g = load_json(f'{d}/global.json') or {}
    files = sorted(f for f in os.listdir(d) if f.endswith('.json') and f != 'global.json')
    subdirs = sorted(x for x in os.listdir(d) if os.path.isdir(f'{d}/{x}'))
    view_types = Counter()
    for f in files:
        j = load_json(f'{d}/{f}')
        if isinstance(j, dict):
            for v in j.values():
                if isinstance(v, dict) and isinstance(v.get('type'), str):
                    view_types[v['type'].lower()] += 1
    page_types = Counter()
    for items in (g.get('pages') or {}).values():
        if isinstance(items, list):
            for it in items:
                if isinstance(it, dict) and it.get('type'):
                    page_types[it['type']] += 1
    routes_src = open(f'{d}/routes.ts').read() if os.path.exists(f'{d}/routes.ts') else ''
    paths = re.findall(r"path:\s*'([^']*)'", routes_src)
    comps = sorted(set(re.findall(r'component:\s*(\w+)', routes_src)))

    build_names = [b for b, v in builds.items()
                   if any(r['with'].startswith(f'src/config/{slug}/') for r in v.get('fileReplacements', []))]
    serve_list, proxies = [], set()
    for b in build_names:
        for name, px in serves.get(b, []):
            serve_list.append(name)
            if px:
                proxies.add(px)
    api_prefixes = set()
    for px in proxies:
        j = load_json(f'{DASH}/{px}')
        if isinstance(j, dict):
            api_prefixes.update(k.rstrip('*').rstrip('/') + '/' for k in j)
    assets = set()
    for b in build_names:
        for a in builds[b].get('assets', []):
            if isinstance(a, dict) and a['input'].startswith('src/assets-'):
                assets.add(a['input'])
    env = ''
    envf = f'{DASH}/src/environments/environment.{slug}.ts'
    if os.path.exists(envf):
        m = re.search(r'\{.*?\}', open(envf).read(), re.S)
        env = re.sub(r'\s+', ' ', m.group(0)) if m else ''

    menu = []
    sub = g.get('submenu') or {}
    for m in g.get('menu', []):
        items = sub.get(m.get('name'))
        if isinstance(items, dict):
            items = [items]
        titles = [i.get('title') for i in (items or []) if isinstance(i, dict)]
        acc = m.get('access')
        menu.append((m.get('title'), m.get('name'), titles, acc))

    lg = g.get('login', {})
    return dict(
        slug=slug, g=g, files=files, subdirs=subdirs, view_types=view_types, page_types=page_types,
        paths=paths, comps=comps, build_names=build_names, serve_list=sorted(set(serve_list)),
        proxies=sorted(proxies), api_prefixes=sorted(api_prefixes), assets=sorted(assets), env=env,
        menu=menu, login=lg,
        features=[k for k in FEATURE_KEYS if k in g],
    )


def access_text(acc):
    if not acc:
        return 'everyone'
    parts = []
    r = acc.get('role')
    if isinstance(r, dict) and 'not' in r:
        parts.append('role not in ' + ', '.join(r['not'] if isinstance(r['not'], list) else [r['not']]))
    elif r:
        parts.append('role = ' + (', '.join(r) if isinstance(r, list) else str(r)))
    if acc.get('list'):
        nm = acc.get('name', {}).get('value') if isinstance(acc.get('name'), dict) else None
        parts.append(f"permission list `{acc['list']}`" + (f" has '{nm}'" if nm else ''))
    if acc.get('mode'):
        parts.append(f"mode {acc['mode']}")
    return '; '.join(parts)


def mm(s):
    """Make a string safe inside a quoted Mermaid label."""
    return re.sub(r'["<>;#{}]', '', str(s))


def product_md(f, idx):
    slug, g, lg = f['slug'], f['g'], f['login']
    name, purpose = PRODUCTS[slug]
    serve = f['serve_list'][0] if f['serve_list'] else None
    auth = lg.get('auth-mode') or 'session cookie'
    login_url = lg.get('login_url') or '-'
    admin_url = lg.get('admin_url') or '-'
    L = []
    L.append(f'# {idx}. {name}')
    L.append('')
    L.append(f'> Product folder: `aryadash-dashboard/src/config/{slug}/` - built from the shared AryaDash engine. '
             '[Back to all projects](../README.md) - [Interview guide](../../00-interview-guide.md)')
    L.append('')
    L.append('## What it is')
    L.append('')
    L.append(purpose)
    L.append('')
    L.append('## Key facts')
    L.append('')
    L.append('| Item | Value |')
    L.append('|---|---|')
    L.append(f"| Browser title | {g.get('title') or '(not set)'} |")
    if g.get('version'):
        L.append(f"| Version in config | {g['version']} |")
    tc = g.get('theme-colors', {})
    if tc:
        L.append(f"| Theme colours | primary `{tc.get('primary')}`, fade `{tc.get('primary-fade1')}` |")
    for k, label in [('timezone', 'Timezone'), ('currency', 'Currency'), ('layout', 'Default layout')]:
        if g.get(k):
            L.append(f'| {label} | {g[k]} |')
    L.append(f"| localStorage prefix | `{g.get('datastore-prefix')}` |")
    L.append(f"| Session cookie | `{lg.get('cookie_name')}` |")
    L.append(f'| Auth mode | {auth} |')
    L.append(f'| Login URL (user / admin) | `{login_url}` / `{admin_url}` |')
    L.append(f"| Menu entries | {len(f['menu'])} |")
    L.append(f"| Pages in global.json | {len(g.get('pages', {}))} |")
    L.append(f"| Screen JSON files | {len(f['files'])} |")
    L.append(f"| Routes | {len(f['paths']) + 1} (login + {len(f['paths'])}) using {', '.join('`'+c+'`' for c in f['comps'])} |")
    if f['env']:
        L.append(f"| Environment | `{f['env']}` |")
    if f['subdirs']:
        L.append(f"| Product-type overrides | {', '.join('`'+s+'/`' for s in f['subdirs'])} (used with `PRODUCT_TYPE=...`) |")
    if f['assets']:
        L.append(f"| Brand assets | {', '.join('`'+a+'`' for a in f['assets'])} |")
    if f['api_prefixes']:
        L.append(f"| Backend API modules (dev proxy) | {', '.join('`'+p+'`' for p in f['api_prefixes'])} |")
    L.append('')

    L.append('## How to run')
    L.append('')
    L.append('```bash')
    L.append('cd ~/VIPLine-billing/aryadash-dashboard')
    if serve:
        L.append('ng serve' + ('' if serve == 'development' else f' --configuration={serve}') + '   # http://localhost:4200')
        for b in f['build_names']:
            L.append(f'ng build --configuration={b}')
        if f['subdirs']:
            L.append(f"PRODUCT_TYPE={f['subdirs'][0]} ng serve --configuration={serve}")
    else:
        L.append('# no serve configuration points at this product yet')
    L.append('```')
    L.append('')

    L.append('## Menu and access')
    L.append('')
    L.append('| Menu | Route | Sub-pages | Who can see it |')
    L.append('|---|---|---|---|')
    for title, nm, subs, acc in f['menu']:
        subtxt = ', '.join(s for s in subs if s) if subs else '-'
        if len(subtxt) > 220:
            subtxt = subtxt[:217] + '...'
        L.append(f'| {title} | `/{nm}` | {subtxt} | {access_text(acc)} |')
    L.append('')

    if f['features']:
        L.append('## Product-specific features (keys in global.json)')
        L.append('')
        for k in f['features']:
            L.append(f'- `{k}` - {FEATURE_KEYS[k]}')
        L.append('')

    L.append('## View types used')
    L.append('')
    if f['page_types']:
        L.append('Page items in `global.json -> pages`: ' + ', '.join(f'`{t}` x{c}' for t, c in f['page_types'].most_common()))
        L.append('')
    if f['view_types']:
        L.append('Definitions inside screen JSON files: ' + ', '.join(f'`{t}` x{c}' for t, c in f['view_types'].most_common()))
        L.append('')

    # Flowchart
    L.append('## Flow')
    L.append('')
    L.append('```mermaid')
    L.append('flowchart TD')
    L.append(f'    B["ng serve --configuration={mm(serve or "?")}"] --> C["global.json + routes.ts of {mm(slug)} baked into bundle"]')
    L.append(f'    C --> LG["Login page - {mm(g.get("title") or name)}"]')
    if auth == 'bearer':
        L.append(f'    LG --> AU["POST {mm(login_url)} - JWT access + refresh token"]')
        L.append(f'    AU --> ME["GET me_url - roles, tenant"]')
        L.append('    ME --> LAND["Landing page (per role)"]')
    else:
        L.append(f'    LG --> AU["POST {mm(login_url)} (admin: {mm(admin_url)}) - SHA-256 password"]')
        L.append(f'    AU --> CK["Cookie {mm(lg.get("cookie_name"))} + store-data in localStorage"]')
        L.append('    CK --> LAND["Landing page (per role)"]')
    L.append('    LAND --> G["AuthGuard + access rules"]')
    L.append('    G --> MENU["Menu"]')
    shown = f['menu'][:8]
    for i, (title, nm, subs, acc) in enumerate(shown):
        L.append(f'    MENU --> M{i}["{mm(title)}"]')
    if len(f['menu']) > len(shown):
        L.append(f'    MENU --> MX["+{len(f["menu"]) - len(shown)} more"]')
    L.append('    M0 --> TV["TableviewComponent / DashboardComponent renders page items"]')
    for i in range(1, len(shown)):
        L.append(f'    M{i} --> TV')
    L.append('    TV --> JS["AppConfig.readConfig(screen JSON)"]')
    api = ', '.join(mm(p) for p in f['api_prefixes'][:4]) or 'backend'
    L.append(f'    JS --> API["RestAPIService -> {api}"]')
    L.append('    API --> UI["Table / form / chart shown to user"]')
    L.append('```')
    L.append('')

    L.append('## Screen JSON files')
    L.append('')
    if f['files']:
        cols = 4
        L.append('| ' + ' | '.join([''] * cols) + ' |')
        L.append('|' + '---|' * cols)
        rows = [f['files'][i:i + cols] for i in range(0, len(f['files']), cols)]
        for r in rows:
            r = r + [''] * (cols - len(r))
            L.append('| ' + ' | '.join(f'`{x}`' if x else '' for x in r) + ' |')
    else:
        L.append('None - this product only has `global.json` (pages are defined inline).')
    L.append('')

    L.append('## Talking points for an interview')
    L.append('')
    tp = [
        f"Built from the **same codebase** as the other products; only `src/config/{slug}/`, its environment file and asset folder differ.",
        f"Adding a screen here = add a JSON file + a `pages`/`menu` entry in `global.json` + a route in `routes.ts` (component `TableviewComponent`).",
    ]
    if auth == 'bearer':
        tp.append('Uses the **bearer-token** login path (`BearerAuthService`): access token in an `Authorization` cookie, refresh 30 s before expiry, principal from `me_url`.')
    else:
        tp.append(f"Uses **session-cookie** login: `sessionKey` from the login response is stored in cookie `{lg.get('cookie_name')}` and sent as the `SESSIONID` header.")
    if any(a for *_, a in f['menu']):
        tp.append('Menu items carry `access` rules, so the menu, the route guard and the page builder all hide what the role may not see.')
    if str(login_url).endswith('!'):
        tp.append("URLs ending in `!` (`" + login_url + "`) tell `RestAPIService.submitData()` to strip the marker and not append a trailing slash.")
    if 'api-config' in g:
        tp.append('`api-config.header-params` adds extra headers (for example a tenant id) to every request.')
    if f['subdirs']:
        tp.append(f"Has a **product-type** variant folder (`{f['subdirs'][0]}/`); `AppConfig.readConfig()` loads it first and falls back to the base file.")
    if 'live-panel' in g or 'break-request' in g:
        tp.append('Agent features (live panel, break requests) come from `live-panel/`, `break-panel/` and `attendance.service.ts`.')
    for t in tp:
        L.append(f'- {t}')
    L.append('')
    return '\n'.join(L)


# ---------------------------------------------------------------------------- HTML
CSS = """
:root{--bg:#f6f7f9;--fg:#1f2933;--muted:#5b6770;--card:#fff;--line:#e3e6ea;--accent:#3730a3;--accent-bg:#eef2ff;--head:#0f172a;--code:#f1f3f6}
@media (prefers-color-scheme:dark){:root{--bg:#0f1115;--fg:#e6e8eb;--muted:#9aa4ad;--card:#171a20;--line:#2a2f37;--accent:#a5b4fc;--accent-bg:#1e2240;--head:#05070b;--code:#1f232b}}
*{box-sizing:border-box}
body{margin:0;font:15px/1.6 system-ui,-apple-system,Segoe UI,sans-serif;background:var(--bg);color:var(--fg)}
header{background:var(--head);color:#fff;padding:18px 16px}
header .in{max-width:1200px;margin:0 auto}
header h1{margin:0;font-size:22px;line-height:1.3} header p{margin:4px 0 0;opacity:.75;font-size:13px}
header a{color:#c7d2fe}
.wrap{max-width:1200px;margin:0 auto;padding:0 16px 60px;display:flex;gap:24px;align-items:flex-start}
nav.toc{position:sticky;top:12px;flex:0 0 250px;max-height:calc(100vh - 24px);overflow:auto;background:var(--card);border:1px solid var(--line);border-radius:10px;padding:12px;margin-top:16px;font-size:13px}
nav.toc ul{list-style:none;margin:0;padding-left:10px} nav.toc>ul{padding-left:0}
nav.toc a{color:var(--accent);text-decoration:none;display:block;padding:2px 0}
main{flex:1;min-width:0}
main h1{display:none}
h2{font-size:20px;margin:32px 0 10px;padding-top:8px;border-top:1px solid var(--line)}
h3{font-size:16px;margin:20px 0 8px}
a{color:var(--accent)}
blockquote{margin:12px 0;padding:10px 14px;background:var(--accent-bg);border-left:4px solid var(--accent);border-radius:6px}
blockquote p{margin:4px 0}
table{border-collapse:collapse;width:100%;background:var(--card);font-size:14px;display:block;overflow-x:auto}
th,td{border:1px solid var(--line);padding:6px 10px;text-align:left;vertical-align:top}
th{background:var(--accent-bg)}
code{background:var(--code);padding:1px 5px;border-radius:4px;font-size:13px}
pre{background:var(--code);padding:12px;border-radius:8px;overflow-x:auto;font-size:13px;line-height:1.45}
pre code{background:none;padding:0}
pre.mermaid{background:var(--card);border:1px solid var(--line);text-align:center}
hr{border:0;border-top:1px solid var(--line);margin:24px 0}
.cards{display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:12px;margin:12px 0}
.card{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:14px}
.card h3{margin:0 0 6px;font-size:16px}.card p{margin:0;color:var(--muted);font-size:13px}
.card .meta{margin-top:8px;font-size:12px;color:var(--muted)}
@media (max-width:900px){.wrap{display:block}nav.toc{position:static;max-height:none;margin-top:16px}}
"""


def to_html(md_text, title, subtitle, out_path, extra_top=''):
    md = markdown.Markdown(extensions=['tables', 'fenced_code', 'toc', 'sane_lists'],
                           extension_configs={'toc': {'toc_depth': '2-2'}})
    body = md.convert(md_text)
    body = re.sub(r'<pre><code class="language-mermaid">(.*?)</code></pre>',
                  lambda m: '<pre class="mermaid">' + m.group(1) + '</pre>', body, flags=re.S)
    # local links: .md -> .html
    body = re.sub(r'href="([^"#:]+)\.md(#[^"]*)?"', lambda m: f'href="{m.group(1)}.html{m.group(2) or ""}"', body)
    body = body.replace('00-interview-guide.html', 'index.html').replace('README.html', 'index.html')
    toc = md.toc
    page = f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{html.escape(title)}</title>
<style>{CSS}</style></head><body>
<header><div class="in"><h1>{html.escape(title)}</h1><p>{subtitle}</p></div></header>
<div class="wrap"><nav class="toc"><b>Contents</b>{toc}</nav><main>{extra_top}{body}</main></div>
<script type="module">
import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.esm.min.mjs';
const dark = window.matchMedia('(prefers-color-scheme: dark)').matches;
mermaid.initialize({{ startOnLoad: false, theme: dark ? 'dark' : 'default', securityLevel: 'loose', flowchart: {{ htmlLabels: true }} }});
const blocks = [...document.querySelectorAll('pre.mermaid')];
for (let i = 0; i < blocks.length; i++) {{
  const el = blocks[i];
  const src = el.textContent;
  try {{
    const {{ svg }} = await mermaid.render('mmd' + i, src);
    el.innerHTML = svg;
    el.setAttribute('data-processed', 'true');
  }} catch (e) {{
    el.setAttribute('data-error', 'true');
    el.textContent = 'Diagram error: ' + e.message + ' --- ' + src;
  }}
}}
</script>
</body></html>
"""
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, 'w') as fh:
        fh.write(page)


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as fh:
        fh.write(text)


def main():
    builds, serves = angular_configs()
    order = list(PRODUCTS)
    facts = [product_facts(s, builds, serves) for s in order]

    # Product pages
    for i, f in enumerate(facts, start=1):
        md = product_md(f, i)
        write(f'{OUT}/projects/products/{f["slug"]}.md', md)
        to_html(md, f'{i}. {PRODUCTS[f["slug"]][0]}',
                f'AryaDash product <code>{f["slug"]}</code> &middot; <a href="../index.html">All projects</a> &middot; <a href="../../index.html">Interview guide</a>',
                f'{OUT}/projects/products/{f["slug"]}.html')

    # App pages (hand-written markdown lives next to this script's output)
    for app_md in ['01-aryadash-dashboard.md', '02-aryadash-designer.md']:
        p = f'{OUT}/projects/{app_md}'
        text = open(p).read()
        title = text.splitlines()[0].lstrip('# ').strip()
        to_html(text, title, '<a href="index.html">All projects</a> &middot; <a href="../index.html">Interview guide</a>',
                p.replace('.md', '.html'))

    # Catalog
    L = ['# Projects in VIPLine-billing', '',
         '> [Interview guide](../00-interview-guide.md)', '',
         '## Summary', '',
         'The repository contains **2 Angular applications**. The first one is an engine that is built into '
         f'**{len(facts)} separately branded products**, so there are **16 things** you can describe as "projects".', '',
         '| # | Project | Kind | Angular | Details |', '|---|---|---|---|---|',
         '| A | [AryaDash Dashboard](01-aryadash-dashboard.md) | Application (engine) | 15.2, NgModule | Config-driven UI engine; source of all 14 products |',
         '| B | [AryaDash Designer](02-aryadash-designer.md) | Application (tool) | 21.1, standalone + signals | AI assistant that writes AryaDash JSON configs |',
         '', '## The 14 products built from AryaDash Dashboard', '',
         '| # | Product | Folder | Menu | Pages | Screen JSON | Auth | Serve with |', '|---|---|---|---|---|---|---|---|']
    for i, f in enumerate(facts, start=1):
        nm = PRODUCTS[f['slug']][0]
        serve = f['serve_list'][0] if f['serve_list'] else '-'
        L.append(f"| {i} | [{nm}](products/{f['slug']}.md) | `{f['slug']}` | {len(f['menu'])} | {len(f['g'].get('pages', {}))} | "
                 f"{len(f['files'])} | {f['login'].get('auth-mode') or 'session'} | `{serve}` |")
    L += ['', 'Total screen JSON files: **%d**. Note: `angular.json` also has a `validata` build that points at '
          '`src/config/validata/global.json`, which does not exist in the repo, so it is not counted.' % sum(len(f['files']) for f in facts), '',
          '## How the projects relate', '', '```mermaid', 'flowchart LR',
          '    DES["AryaDash Designer<br/>Angular 21"] -- "generates JSON configs" --> CFGS["src/config/{product}/*.json"]',
          '    ENG["AryaDash Dashboard engine<br/>Angular 15, ~80 components"] --> CFGS']
    for i, f in enumerate(facts, start=1):
        L.append(f'    CFGS --> P{i}["{mm(PRODUCTS[f["slug"]][0])}"]')
    L += ['```', '', '## One-line description of each product', '']
    for i, f in enumerate(facts, start=1):
        L.append(f"{i}. **{PRODUCTS[f['slug']][0]}** - {PRODUCTS[f['slug']][1]}")
    catalog = '\n'.join(L) + '\n'
    write(f'{OUT}/projects/README.md', catalog)
    cards = '<div class="cards">' + ''.join(
        f'<a class="card" style="text-decoration:none;color:inherit" href="products/{f["slug"]}.html"><h3>{i}. {html.escape(PRODUCTS[f["slug"]][0])}</h3>'
        f'<p>{html.escape(PRODUCTS[f["slug"]][1])}</p><div class="meta">{len(f["menu"])} menus &middot; {len(f["files"])} screen JSON &middot; '
        f'{f["login"].get("auth-mode") or "session"} auth</div></a>' for i, f in enumerate(facts, start=1)) + '</div>'
    apps = ('<div class="cards"><a class="card" style="text-decoration:none;color:inherit" href="01-aryadash-dashboard.html"><h3>A. AryaDash Dashboard</h3>'
            '<p>Angular 15 config-driven engine. Builds all 14 products.</p></a>'
            '<a class="card" style="text-decoration:none;color:inherit" href="02-aryadash-designer.html"><h3>B. AryaDash Designer</h3>'
            '<p>Angular 21 standalone + signals. AI generates the JSON configs.</p></a></div>')
    to_html(catalog, 'Projects in VIPLine-billing', '2 Angular apps &middot; 14 products &middot; <a href="../index.html">Interview guide</a>',
            f'{OUT}/projects/index.html', extra_top=apps + cards)

    # Senior playbook
    pb = open(f'{OUT}/03-senior-developer-playbook.md').read()
    to_html(pb, 'Senior Developer Playbook (5+ years)',
            'AryaDash / VIPLine-billing &middot; <a href="index.html">Interview guide</a> &middot; <a href="projects/index.html">All projects</a>',
            f'{OUT}/03-senior-developer-playbook.html')

    # Main guide
    guide = open(f'{OUT}/00-interview-guide.md').read()
    to_html(guide, 'AryaDash — Angular UI Interview Guide',
            'VIPLine-billing &middot; aryadash-dashboard + aryadash-designer &middot; <a href="projects/index.html">All projects</a>',
            f'{OUT}/index.html')

    # Top-level README as a page too
    rd = open(f'{OUT}/README.md').read()
    to_html(rd, 'Angular UI docs - VIPLine-billing', 'Index of every page in this folder',
            f'{OUT}/contents.html')
    print('done', len(facts), 'products')


if __name__ == '__main__':
    main()
