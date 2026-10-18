import { defineConfig, devices } from "@playwright/test"

// The end-to-end tests run against the seeded demo stack (compose.prod.yaml + compose.demo.yaml
// + scripts/seed-demo.mjs). See docs/demo.md.
export default defineConfig({
  testDir: "./tests",
  globalSetup: "./support/global-setup.ts",
  timeout: 45_000,
  expect: { timeout: 8_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["list"], ["html", { open: "never" }]] : [["list"]],
  use: {
    baseURL: process.env.BASE_URL ?? "http://localhost:8088",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    locale: "en-US",
    timezoneId: "Africa/Casablanca",
    actionTimeout: 10_000,
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
})
