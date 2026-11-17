import { existsSync } from 'node:fs';
import { defineConfig, devices } from '@playwright/test';

const baseURL = process.env.E2E_BASE_URL || 'http://127.0.0.1:18080';
const chromePaths = [
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe'
];
const hasLocalChrome = chromePaths.some((item) => existsSync(item));
const useSystemChrome = process.env.E2E_CHANNEL !== 'bundled'
  && (process.env.E2E_CHANNEL === 'chrome' || (!process.env.CI && hasLocalChrome));

export default defineConfig({
  testDir: './e2e',
  timeout: 45000,
  expect: { timeout: 10000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 2 : 0,
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }]
  ],
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure'
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        ...(useSystemChrome ? { channel: 'chrome' } : {})
      }
    }
  ],
  webServer: process.env.E2E_BASE_URL ? undefined : {
    command: 'bash ../scripts/e2e-server.sh',
    url: `${baseURL}/api/health`,
    reuseExistingServer: !process.env.CI,
    timeout: 240000,
    stdout: 'pipe',
    stderr: 'pipe'
  }
});
