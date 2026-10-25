// @ts-check
const { defineConfig, devices } = require('@playwright/test');

/**
 * The app must be running with test data: docker compose -f docker-compose.e2e.yml up -d --build --wait
 * Tests change data (hide a place and show it again), so they run one by one.
 */
module.exports = defineConfig({
    testDir: './tests',
    fullyParallel: false,
    workers: 1,
    forbidOnly: !!process.env.CI,
    retries: process.env.CI ? 1 : 0,
    reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
    use: {
        baseURL: process.env.BASE_URL || 'http://localhost:8081',
        trace: 'retain-on-failure',
        screenshot: 'only-on-failure',
    },
    projects: [
        { name: 'chromium', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } } },
    ],
});
