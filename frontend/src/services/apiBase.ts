/**
 * Where the API lives, as one decision shared by every client in the app.
 *
 * `same-origin` means the page calls `/api/v1/...` on whatever host it was loaded from, and the
 * frontend's own nginx proxies that to the gateway with the `Host` header intact. That is what
 * makes multi-tenancy work in a browser at all: the gateway resolves the tenant from `Host`, so a
 * bundle hard-coded to `localhost:8080` sends every tenant's traffic under one hostname and they
 * all resolve to `platform.tenant.fallback` — whichever subdomain was actually opened.
 *
 * A sentinel rather than an empty string because the Dockerfile deliberately ignores empty build
 * args, so that a `docker compose build` without the variable set falls through to `frontend/.env`
 * rather than blanking it. An empty value here would be indistinguishable from "not supplied".
 *
 * Anything else is used as-is, which keeps `npm run dev` pointing straight at the gateway.
 */
const SAME_ORIGIN = 'same-origin';

const configured = import.meta.env.VITE_API_BASE_URL;

export const API_ORIGIN: string =
  configured === SAME_ORIGIN ? '' : (configured || 'http://localhost:8080');

/** True when requests go to the page's own host, so relative URLs are the correct ones to build. */
export const isSameOrigin = API_ORIGIN === '';
