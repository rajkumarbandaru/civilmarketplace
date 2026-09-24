import { test as base, expect, Page, Route } from '@playwright/test';

/**
 * Every spec runs against the dev server with the API mocked here, so no backend is needed.
 *
 * Unhandled `/api` calls get a 404, which the app already treats as "nothing to show" (the
 * catalogue, theme and site content all fall back to their shipped defaults). A spec adds the
 * endpoints it cares about with `mockApi`, which takes precedence over the catch-all.
 */

export const customer = { id: 7, name: 'Asha Rao', email: 'asha@example.com', role: 'CUSTOMER' };
export const admin = { id: 1, name: 'Admin', email: 'admin@example.com', role: 'ADMIN' };

type Handler = (route: Route) => unknown;

export const json = (route: Route, body: unknown, status = 200) =>
  route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });

/**
 * Mock one API path for a page. A string is a glob relative to /api/v1 — note `?` in a glob is a
 * one-character wildcard, so use a RegExp when the query string matters.
 */
export const mockApi = (page: Page, path: string | RegExp, handler: Handler | object) =>
  page.route(typeof path === 'string' ? `**/api/v1${path}` : path, (route) =>
    typeof handler === 'function' ? (handler as Handler)(route) : json(route, handler)
  );

/** Starts the tab already signed in, the way authStorage rehydrates it on load. */
export const signInAs = async (page: Page, user: typeof customer) => {
  await page.addInitScript((u) => {
    sessionStorage.setItem('accessToken', 'e2e-access');
    sessionStorage.setItem('refreshToken', 'e2e-refresh');
    sessionStorage.setItem('user', JSON.stringify(u));
  }, user);
};

export const test = base.extend<{ page: Page }>({
  page: async ({ page }, use) => {
    await page.route('**/api/**', (route) => json(route, { message: 'not mocked' }, 404));
    await page.route('**/ws/**', (route) => route.abort());
    await use(page);
  },
});

export { expect };
