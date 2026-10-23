import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './e2e',
  use: { baseURL: process.env.E2E_URL || 'http://localhost:3000' },
  webServer: process.env.E2E_URL
    ? undefined
    : { command: 'npm run dev', url: 'http://localhost:3000', reuseExistingServer: true },
  reporter: 'list',
});
