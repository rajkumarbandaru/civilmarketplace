#!/usr/bin/env python3
"""Build HTML from the Markdown in ~/RAJKUMAR/project-info/civil-market.

Usage: python3 tools/build_html.py

Every .md becomes a .html next to it. README.md -> index.html, and
00-interview-guide.md -> interview-guide.html. Mermaid fences render in the browser.
overview/action-flows.html is NOT built here - it has its own generator
(tools/gen_action_flows.py).
"""
import html
import os
import re

import markdown

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

CSS = """
:root{--bg:#f6f7f9;--fg:#1f2933;--muted:#5b6770;--card:#fff;--line:#e3e6ea;--accent:#3730a3;--accent-bg:#eef2ff;--head:#0f172a;--code:#f1f3f6}
@media (prefers-color-scheme:dark){:root{--bg:#0f1115;--fg:#e6e8eb;--muted:#9aa4ad;--card:#171a20;--line:#2a2f37;--accent:#a5b4fc;--accent-bg:#1e2240;--head:#05070b;--code:#1f232b}}
*{box-sizing:border-box}
body{margin:0;font:15px/1.6 system-ui,-apple-system,Segoe UI,sans-serif;background:var(--bg);color:var(--fg)}
header{background:var(--head);color:#fff;padding:18px 16px}
header .in{max-width:1200px;margin:0 auto}
header h1{margin:0;font-size:22px;line-height:1.3} header p{margin:4px 0 0;opacity:.75;font-size:13px}
header a{color:#c7d2fe}
.nav{max-width:1200px;margin:0 auto;padding:10px 16px 0;font-size:13px}
.nav a{color:var(--accent);text-decoration:none;margin-right:6px;padding:3px 9px;border-radius:999px;background:var(--accent-bg);display:inline-block;margin-bottom:6px}
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
@media (max-width:900px){.wrap{display:block}nav.toc{position:static;max-height:none;margin-top:16px}}
"""

NAV = [
    ('index.html', 'Home'),
    ('interview-guide.html', 'Interview guide'),
    ('overview/architecture.html', 'Architecture'),
    ('backend/backend-guide.html', 'Backend'),
    ('ui/ui-guide.html', 'UI'),
    ('docker/docker-guide.html', 'Docker'),
    ('overview/action-flows.html', 'Action flows'),
    ('interview/rapid-fire.html', 'Rapid fire'),
]

# md path (relative to ROOT) -> html path; None means "same name, .html"
RENAMES = {'README.md': 'index.html', '00-interview-guide.md': 'interview-guide.html'}


def out_path_for(md_rel):
    base = os.path.basename(md_rel)
    if base in RENAMES:
        return os.path.join(os.path.dirname(md_rel), RENAMES[base]).lstrip('/')
    return md_rel[:-3] + '.html'


def to_html(md_text, title, subtitle, out_rel):
    md = markdown.Markdown(extensions=['tables', 'fenced_code', 'toc', 'sane_lists'],
                           extension_configs={'toc': {'toc_depth': '2-3'}})
    body = md.convert(md_text)
    body = re.sub(r'<pre><code class="language-mermaid">(.*?)</code></pre>',
                  lambda m: '<pre class="mermaid">' + m.group(1) + '</pre>', body, flags=re.S)
    # local .md links -> .html, honouring the two renames
    body = re.sub(r'href="([^"#:]+)\.md(#[^"]*)?"',
                  lambda m: f'href="{m.group(1)}.html{m.group(2) or ""}"', body)
    body = body.replace('00-interview-guide.html', 'interview-guide.html')
    body = re.sub(r'href="((?:\.\./)*)README\.html"', r'href="\1index.html"', body)

    depth = out_rel.count('/')
    root = '../' * depth
    nav = ''.join(f'<a href="{root}{href}">{html.escape(label)}</a>' for href, label in NAV)
    page = f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{html.escape(title)}</title>
<style>{CSS}</style></head><body>
<header><div class="in"><h1>{html.escape(title)}</h1><p>{subtitle}</p></div></header>
<div class="nav">{nav}</div>
<div class="wrap"><nav class="toc"><b>Contents</b>{md.toc}</nav><main>{body}</main></div>
<script type="module">
import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.esm.min.mjs';
const dark = window.matchMedia('(prefers-color-scheme: dark)').matches;
mermaid.initialize({{ startOnLoad: false, theme: dark ? 'dark' : 'default', securityLevel: 'loose',
                      flowchart: {{ htmlLabels: true }}, sequence: {{ useMaxWidth: false }} }});
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
    abs_out = os.path.join(ROOT, out_rel)
    os.makedirs(os.path.dirname(abs_out), exist_ok=True)
    with open(abs_out, 'w') as fh:
        fh.write(page)


def main():
    md_files = []
    for dirpath, dirnames, filenames in os.walk(ROOT):
        dirnames[:] = [d for d in dirnames if d != 'tools']
        for fn in sorted(filenames):
            if fn.endswith('.md'):
                md_files.append(os.path.relpath(os.path.join(dirpath, fn), ROOT))

    for md_rel in sorted(md_files):
        text = open(os.path.join(ROOT, md_rel)).read()
        m = re.search(r'^# (.+)$', text, re.M)
        title = m.group(1) if m else md_rel
        out_rel = out_path_for(md_rel)
        sub = ('Civil Engineering Marketplace · source ~/RAJKUMAR · generated from '
               f'<code style="color:#fff;background:none">{html.escape(md_rel)}</code>')
        to_html(text, title, sub, out_rel)
        print('wrote', out_rel)


if __name__ == '__main__':
    main()
