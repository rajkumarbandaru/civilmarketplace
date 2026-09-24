# React / Next.js UI — Arya CMS (jc-cms)

Interview-prep notes for the React side of `~/june-24/arya-cms` (app folder `jc-cms/`).
Open the HTML files in a browser (flowcharts need internet once to load Mermaid):

```bash
xdg-open ~/RAJKUMAR/project-info/infi/react/index.html
```

## Read in this order

| # | File | What it covers |
|---|---|---|
| 1 | [index.html](index.html) / [00-interview-guide.md](00-interview-guide.md) | **Start here.** 60-second pitch, what it is, tech stack, folder structure, architecture, 13 flowcharts (dev start-up, SSR request, provider tree, JSON→React render, event→action chain, API executor, login, auth gate, global data, modals, theme, editor publish, deploy), React/Next concepts mapped to files, 7 STAR bug stories, 25 Q&As, how to run, glossary |
| 2 | [five-years-experience-playbook.html](five-years-experience-playbook.html) / [01-five-years-experience-playbook.md](01-five-years-experience-playbook.md) | **For a 5-years-experience candidate.** How to position the project at senior level, resume bullets, the 3-minute walkthrough, a depth ladder showing how far each answer must go, 4 senior STAR stories, architecture/system-design questions, behavioural answers, rapid-fire checks, questions to ask them, red flags, a 7-day plan and live-coding tactics |
| 3 | [projects/index.html](projects/index.html) / [projects/README.md](projects/README.md) | **How many projects**: 7 code projects + 4 site projects, tables and relationship charts |
| 4 | `projects/0N-*.html` / `.md` | One page per code project (site app, editor, landing, blog site, blog admin, Express server, shared engine) |
| 5 | `projects/sites/*.html` / `.md` | One page per site (InfiMobile website, InfiMobile reseller, Xmobee website, Xmobee reseller) |

## Projects (one page each)

| # | Project | HTML | Markdown |
|---|---|---|---|
| 1 | Site app (`app/site`) | [html](projects/01-site-app.html) | [md](projects/01-site-app.md) |
| 2 | Editor app (`app/editor`) | [html](projects/02-editor-app.html) | [md](projects/02-editor-app.md) |
| 3 | Landing app (`app/landing`) | [html](projects/03-landing-app.html) | [md](projects/03-landing-app.md) |
| 4 | Blog site (`app/blog-site`) | [html](projects/04-blog-site.html) | [md](projects/04-blog-site.md) |
| 5 | Blog admin (`app/blog-admin`) | [html](projects/05-blog-admin.html) | [md](projects/05-blog-admin.md) |
| 6 | CMS server (`server/`) | [html](projects/06-cms-server.html) | [md](projects/06-cms-server.md) |
| 7 | Shared engine (`packages/`) | [html](projects/07-shared-engine.html) | [md](projects/07-shared-engine.md) |
| S1 | InfiMobile website | [html](projects/sites/infimobile-website.html) | [md](projects/sites/infimobile-website.md) |
| S2 | InfiMobile reseller portal | [html](projects/sites/infimobile-reseller.html) | [md](projects/sites/infimobile-reseller.md) |
| S3 | Xmobee website | [html](projects/sites/xmobee-website.html) | [md](projects/sites/xmobee-website.md) |
| S4 | Xmobee reseller portal | [html](projects/sites/xmobee-reseller.html) | [md](projects/sites/xmobee-reseller.md) |

## One-screen summary

```
 Editor app (:3010) ──publish──▶ Express /cmsapi (:5010) ──writes──▶ config/<site>/*.json
                                                                        │
                                   ACTIVE_SITE picks the folder ────────┘
                                                                        ▼
 Browser ─▶ Site app (:4010, Next.js SSR [...slug].js) ─▶ packages/ engine
              PageRenderer → SectionRenderer → ColumnRenderer → ComponentRegistry (≈35 types)
              events: click → interceptor → eventRouter → apiCall / scriptUrl / openModal / login
              executeApi(api.json) ─▶ REST microservices (wallet, inventory, user, KYC, MNO, payments)

 Sites: InfiMobile website · InfiMobile reseller · Xmobee website · Xmobee reseller
 Also:  Landing app (next export → S3) · Blog site + Blog admin (static export)
```

The Markdown files are hand-written from the real repo; every `.html` is generated from them with
`python3 tools/build_html.py`.
