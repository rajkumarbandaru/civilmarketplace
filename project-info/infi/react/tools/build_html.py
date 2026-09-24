#!/usr/bin/env python3
"""Generate HTML from the hand-written Markdown in ~/RAJKUMAR/project-info/infi/react.

Usage: python3 tools/build_html.py
Every .md becomes a .html next to it; 00-interview-guide.md -> index.html and
README.md -> index.html (root README is skipped because the guide owns index.html).
"""
import html
import os
import re

import markdown

OUT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

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


def to_html(md_text, title, subtitle, out_path, extra_top='', renames=None):
    md = markdown.Markdown(extensions=['tables', 'fenced_code', 'toc', 'sane_lists'],
                           extension_configs={'toc': {'toc_depth': '2-2'}})
    body = md.convert(md_text)
    body = re.sub(r'<pre><code class="language-mermaid">(.*?)</code></pre>',
                  lambda m: '<pre class="mermaid">' + m.group(1) + '</pre>', body, flags=re.S)
    # local links: .md -> .html
    body = re.sub(r'href="([^"#:]+)\.md(#[^"]*)?"', lambda m: f'href="{m.group(1)}.html{m.group(2) or ""}"', body)
    body = body.replace('00-interview-guide.html', 'index.html').replace('README.html', 'index.html')
    for old, new in (renames or {}).items():
        body = body.replace(old, new)
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



NAV = ('<p style="font-size:13px"><a href="{root}index.html">Interview guide</a> · '
       '<a href="{root}five-years-experience-playbook.html">5-yr playbook</a> · '
       '<a href="{root}projects/index.html">All projects</a></p>')

PAGES = [
    ('00-interview-guide.md', 'index.html', 'Arya CMS — React / Next.js Interview Guide'),
    ('01-five-years-experience-playbook.md', 'five-years-experience-playbook.html',
     'Arya CMS — 5-Years-Experience Interview Playbook'),
    ('projects/README.md', 'projects/index.html', 'Arya CMS — How many projects'),
    ('projects/01-site-app.md', None, None),
    ('projects/02-editor-app.md', None, None),
    ('projects/03-landing-app.md', None, None),
    ('projects/04-blog-site.md', None, None),
    ('projects/05-blog-admin.md', None, None),
    ('projects/06-cms-server.md', None, None),
    ('projects/07-shared-engine.md', None, None),
    ('projects/sites/infimobile-website.md', None, None),
    ('projects/sites/infimobile-reseller.md', None, None),
    ('projects/sites/xmobee-website.md', None, None),
    ('projects/sites/xmobee-reseller.md', None, None),
]


def main():
    for md_rel, html_rel, title in PAGES:
        text = open(os.path.join(OUT, md_rel)).read()
        if not title:
            m = re.search(r'^# (.+)$', text, re.M)
            title = m.group(1) if m else md_rel
        html_rel = html_rel or md_rel[:-3] + '.html'
        depth = html_rel.count('/')
        root = '../' * depth
        sub = ('Source: ~/june-24/arya-cms/jc-cms · generated from '
               f'<code style="color:#fff;background:none">{md_rel}</code>')
        out_path = os.path.join(OUT, html_rel)
        to_html(text, title, sub, out_path, extra_top=NAV.format(root=root),
                renames={'01-five-years-experience-playbook.html':
                         'five-years-experience-playbook.html'})
        # guide links point at projects/README.md -> projects/index.html (handled in to_html)
        print('wrote', html_rel)


if __name__ == '__main__':
    main()
