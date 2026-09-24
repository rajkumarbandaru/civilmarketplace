# UI Interview Questions — React, TypeScript, Vite

Answers taken from this codebase. Reference: [ui-guide.md](ui-guide.md).

---

## A. State and data

**1. Redux Toolkit *and* React Query — isn't that duplicated state?**
No, they hold different things. React Query owns **server state**: bookings, catalogue, tickets —
data that lives on a server, needs caching, refetching and invalidation. Redux owns **client
state**: the session (tokens, current user) and UI state that several branches read. The rule I
apply: if the server is the source of truth, it does not belong in Redux.

**2. Why is the session in `sessionStorage` and not `localStorage`?**
This was a deliberate change. `localStorage` is per origin, so every tab shares one set of keys and
signing in as an admin in one tab silently replaced the customer session in another.
`sessionStorage` is per tab, so two accounts can be open side by side — which matters constantly
when testing an admin console against a member view. There is a one-time migration that moves a
pre-existing `localStorage` session into the tab.
→ `src/services/authStorage.ts`.

**3. What about XSS — is storing a token in web storage safe?**
It is the weak point, and I would say so. Any script on the page can read it. The stronger design is
an httpOnly, SameSite cookie so JavaScript never touches the token, with CSRF protection on top.
Web storage was chosen for a token used across origins in dev; I would move to cookies for
production, which the same-origin nginx proxy already makes practical.

**4. How does token refresh work?**
An Axios response interceptor: on a 401 that is not from an auth endpoint and has not already been
retried, it posts the refresh token to `/auth/refresh`, stores the new pair, sets the header on the
original request and replays it; if the refresh fails, it logs out. Two details matter — the
`_retry` flag prevents an infinite loop, and auth endpoints are excluded because a 401 from *login*
means "wrong password", not "session expired". Refreshing there would turn a clear error into a
logout.
→ `src/services/api.ts`.

---

## B. Build, config and deployment

**5. Why is `VITE_API_BASE_URL` set to `same-origin` in Docker?**
Because the gateway resolves the tenant from the `Host` header. If the bundle called
`http://localhost:8080` directly, every tenant's traffic would arrive as host `localhost` and all of
them would fall back to one tenant. Calling the page's own origin means `acme.localhost:3000`
reaches the gateway as `acme` — nginx proxies with `proxy_set_header Host $http_host`.

**6. `VITE_*` variables — build time or runtime?**
Build time. Vite inlines them into the bundle, so changing a container's environment does nothing to
an already-built `dist`. They are passed as Docker **build args**. This was a real bug: they were
listed under `environment:` in Compose, where nginx never reads them, so they silently did nothing.

**7. Can you put a secret in a `VITE_` variable?**
Never — it ships to every browser. Only the Razorpay **key id** is there (public by design); the key
secret stays in the container environment for payment-service, and the Gemini key stays server-side
in support-service.

**8. How is the frontend image built?**
Multi-stage: `node:20-alpine` runs `npm ci` then `npm run build`, and `nginx:alpine` serves the
`dist`. Dependencies are copied before source so the install layer caches, and files are copied
individually so the host's `node_modules` (built for a different platform) and stale `dist` never
land in the image. An earlier version copied a prebuilt `dist`, which shipped whatever happened to
be lying on someone's laptop — or nothing at all on a clean clone.

**9. How do you cache static assets correctly?**
Vite emits content-hashed files under `/assets`, cached one year `immutable`. `index.html` is
`no-cache` because it is the only stable URL and it names the hashed bundles — a browser holding
yesterday's copy keeps requesting yesterday's chunks, which still exist, so nothing errors and the
app just stays on the old version invisibly.

**10. SPA routing on nginx?**
`try_files $uri $uri/ /index.html`, so a deep link like `/admin/users` returns the app instead of a
404 and React Router takes over.

---

## C. Performance

**11. What did you do about bundle size?**
Manual chunks in Rollup: `vendor` (react, react-dom, router), `mui`, `state` (Redux), `query`.
Splitting means a router change does not invalidate the MUI chunk in everyone's cache. Route-level
code splitting is the next step for the admin console, which most users never open.

**12. A PWA problem you hit?**
The MUI chunk with icons is around 4.2 MB, over Workbox's 2 MiB default for precaching, which failed
the build at the service-worker step even though the bundle itself was fine. Raised
`maximumFileSizeToCacheInBytes`. The better fix is to stop importing icons in bulk.

**13. How do you keep the UI responsive while data loads?**
React Query gives loading and error states per query, with cached data shown immediately on
revisit; forms use React Hook Form so typing does not re-render the whole form; heavy pages get
skeletons rather than spinners.

---

## D. React and TypeScript specifics

**14. Where do you use `useEffect`, and where do you avoid it?**
Avoid it for data fetching — that is React Query's job, which also handles caching, retries and
race conditions. Use it for real side effects: subscribing to a tracking poll, syncing the MUI theme
when the UI-config arrives, cleaning up timers on unmount.

**15. How does runtime theming work?**
After login the app calls `GET /api/v1/ui-config/me`, which returns the effective theme — colours,
radius, density, button style, layout, brand — merged from platform, workspace and personal
settings. That builds the MUI theme, so a Super Admin's change re-colours everyone's UI without a
deploy. The discipline it requires: components must read `theme.palette.primary.main`, never a
hard-coded hex. I found several pages with the shipped violet written in literally, so re-theming
left them unchanged — fixed by reading from the theme.

**16. How is TypeScript actually helping here?**
API response types are declared per service module (`AdminUser`, `SupportTicket`, `ResolvedTheme`…),
so a renamed backend field breaks the build instead of rendering `undefined`. The build runs `tsc`
before Vite, so type errors fail CI rather than production.

**17. Forms and validation?**
React Hook Form with Yup resolvers — uncontrolled inputs keep re-renders local, and the schema
mirrors the backend rules. Server-side validation still exists; the client version is for speed of
feedback, not for trust.

**18. How do you handle API errors in the UI?**
A shared `apiError` helper turns the backend's standard error body into a message, with the
interceptor handling 401 separately. 403 means the role is not allowed — the UI hides those actions,
but the backend still enforces it, because hiding a button is not authorisation.

---

## E. Cross-cutting

**19. How does the UI know which menu and pages a role gets?**
`/ui-config/me` returns the workspace menu for that role, so admins configure navigation per
workspace without a frontend release. Routes are still guarded client-side, and every endpoint is
guarded server-side.

**20. How do you develop against the backend?**
`npm run dev` on port 5173 with a Vite proxy forwarding `/api` and `/ws` to the gateway, which the
backend runs in Docker. The proxy port follows `HOST_PORT_GATEWAY`, so it still works when the
gateway is remapped to avoid a port clash.

**21. What would you improve in the frontend?**
Three things: move the token to httpOnly cookies; add tests — there is lint and `tsc`, but no React
Testing Library suite, and the theming and interceptor logic deserve it; and route-level code
splitting so a customer never downloads the admin console.
