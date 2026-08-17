/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // Backend runs on 8080; keeps the browser same-origin so there is no CORS setup in dev.
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    // Run the whole suite west of UTC, deliberately.
    //
    // Every date this product shows is a *day* — `2026-08-31`, with no time of day in it,
    // because nobody claimed one — and the way that breaks is `new Date(iso)`, which reads
    // a bare date as UTC midnight and so displays the day before for every reader west of
    // the meridian. In UTC that bug is invisible and every assertion passes; here it is one
    // failing test. `formatDay` and `todayHere` exist for exactly this and are the reason
    // nothing had to change to make the suite green in New York.
    env: { TZ: 'America/New_York' },
    coverage: {
      provider: 'v8',
      // Read the branch column: components and types inflate the statement figure.
      reporter: ['text-summary', 'html'],
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/main.tsx', 'src/test/**', 'src/**/*.test.{ts,tsx}']
    }
  }
});
