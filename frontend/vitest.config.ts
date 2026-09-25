import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// Kept apart from vite.config.ts so the PWA plugin and dev proxy stay out of the test run.
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    css: false,
    // Pin the zone so date assertions read the same on every machine and in CI.
    env: { TZ: 'UTC' },
    // Form tests type character by character; on a loaded machine running every file in parallel
    // the 5s default is hit by a test that takes 1s alone.
    testTimeout: 15000,
  },
})
