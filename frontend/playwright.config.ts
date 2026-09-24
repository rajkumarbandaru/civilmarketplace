import { defineConfig, devices } from '@playwright/test'

const PORT = Number(process.env.E2E_PORT ?? 4173)

/**
 * End-to-end tests run against the Vite dev server with every `/api` call mocked in the spec
 * (see e2e/fixtures.ts), so they need no backend. Point E2E_BASE_URL at a running stack to
 * skip the local server.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? `http://localhost:${PORT}`,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : {
        // same-origin makes the app call /api on the page's host, where the specs intercept it.
        command: `npx vite --port ${PORT} --strictPort`,
        env: { VITE_API_BASE_URL: 'same-origin' },
        url: `http://localhost:${PORT}`,
        reuseExistingServer: !process.env.CI,
        timeout: 120_000,
      },
})
