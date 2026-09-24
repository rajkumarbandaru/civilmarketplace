# 03 — Frontend (UI)

## Stack

React 18 · TypeScript · Vite · Material UI (MUI) + Emotion · Tailwind CSS · Redux Toolkit ·
TanStack React Query · React Router 6 · React Hook Form + Yup · Axios · Framer Motion ·
`vite-plugin-pwa` (installable app, service worker).

## Folder structure (`frontend/`)

```
frontend/
├── index.html
├── vite.config.ts        dev server (5173), /api + /ws proxy to the gateway, PWA, chunking
├── tailwind.config.js, postcss.config.js
├── .env                  VITE_* build-time settings
├── Dockerfile            multi-stage: node:20 build → nginx:alpine serve
├── nginx.conf            used inside the container (port 3000, /api proxy)
├── public/               icons, favicon, PWA images
└── src/
    ├── main.tsx          entry point: mounts <App/> with providers
    ├── App.tsx           routes
    ├── theme.ts          MUI theme (overridden at runtime by the admin UI-config)
    ├── pages/
    │   ├── HomePage.tsx
    │   ├── auth/         Login, Register, OTP, OAuth2 redirect
    │   ├── dashboard/    user dashboard
    │   ├── booking/      create/list/detail bookings
    │   ├── services/     service catalogue
    │   ├── tracking/     booking/order tracking
    │   ├── profile/      profile, KYC
    │   ├── settings/     appearance and account settings
    │   ├── support/      tickets, Ask AI
    │   └── admin/        admin console (dashboard, analytics, revenue, workspaces,
    │                     theme, content, announcements, audit, KYC, ...)
    ├── components/       shared UI parts (Navbar, cards, dialogs, ...)
    ├── layouts/          page shells (public, member, admin)
    ├── providers/        context providers (theme/UI-config, query client, ...)
    ├── store/            Redux Toolkit store + slices (auth state, etc.)
    ├── services/         apiBase.ts, api.ts (Axios instance) + per-feature API modules
    ├── hooks/            custom hooks
    ├── constants/, types/, utils/, styles/
```

## How the UI talks to the backend

1. `src/services/apiBase.ts` reads `VITE_API_BASE_URL`.
   - A URL such as `http://localhost:8080` means: call that host directly.
   - `same-origin` (what Docker builds use) means: call `/api/v1/...` on the page's own host.
2. `src/services/api.ts` creates the Axios instance with `baseURL: ${API_BASE_URL}/api/v1`.
   Interceptors attach `Authorization: Bearer <accessToken>` and handle 401 / token refresh.
3. The request reaches the **API gateway**, which routes it to the right microservice (see the route
   table in [backend-guide.md](../backend/backend-guide.md)).
4. React Query caches server data. Redux holds client state such as the logged-in user and token.

### Runtime theming

After login, the UI calls `GET /api/v1/ui-config/me` (admin-service). It returns the effective
theme: colors, radius, density, button style, layout, brand name and logo. A Super Admin edits it in the
admin console, and members see the change. Pages read colors from the MUI theme
(`theme.palette.primary.main`) rather than hard-coding them.

## Two ways to run the UI

### A) Dev mode — Vite dev server (hot reload)

```bash
cd ~/RAJKUMAR/frontend
npm install              # first time only
npm run dev              # http://localhost:5173
```

- Vite proxies `/api` → `http://localhost:${HOST_PORT_GATEWAY:-8080}` and `/ws` → websocket.
- The backend still needs to be running (usually in Docker). Only the UI runs on your machine.
- If the gateway runs on a different host port: `HOST_PORT_GATEWAY=8087 npm run dev`.
- `frontend/.env` has `VITE_API_BASE_URL=http://localhost:8080`, so in dev the bundle may call the
  gateway directly (CORS allows `localhost:5173`).

### B) Docker mode — built bundle served by nginx

```bash
cd ~/RAJKUMAR/docker
docker compose up -d --build frontend     # http://localhost:3000
```

- Stage 1 (`node:20-alpine`) runs `npm ci` then `npm run build` (`tsc && vite build`), producing `dist/`.
- Stage 2 (`nginx:alpine`) copies `dist/` into `/usr/share/nginx/html` with `frontend/nginx.conf`.
- nginx on port 3000:
  - `/api/` → `proxy_pass http://api-gateway:8080`, keeping `Host: $http_host` (needed for tenancy).
  - `/` → `try_files ... /index.html` (SPA routing).
  - `/assets/` cached for 1 year (hashed filenames); `index.html` never cached.
- **`VITE_*` values are baked in at build time.** Changing them means rebuilding the image
  (`docker compose build frontend`). Setting them at container runtime does nothing.

## Other commands

```bash
npm run build     # type-check + production build into dist/
npm run preview   # serve dist/ locally
npm run lint      # ESLint
npx tsc --noEmit  # type-check only
```

## Frontend environment variables (`frontend/.env`)

| Variable | Meaning |
|---|---|
| `VITE_API_BASE_URL` | Gateway URL, or `same-origin` |
| `VITE_SOCIAL_PROVIDERS` | Which social login buttons to show (`google,facebook`) |
| `VITE_RAZORPAY_KEY_ID` | Razorpay **public** key id (never put a secret in a `VITE_` variable) |
| `VITE_GOOGLE_MAPS_API_KEY` | Maps (passed as a Docker build arg) |

## Multi-tenant URLs

Every tenant has its own subdomain, e.g. `http://acme.localhost:3000`. Browsers resolve
`*.localhost` to 127.0.0.1 automatically. The login page calls `/api/v1/tenant-resolution/**`
to show that tenant's name and branding before anyone logs in.
