// Shared test setup: no requests leave the machine, photos from Commons are replaced by a placeholder
const base = require('@playwright/test');

const PHOTO = '<svg xmlns="http://www.w3.org/2000/svg" width="1600" height="1067">' +
    '<rect width="100%" height="100%" fill="#4FA3E0"/></svg>';

const ADMIN = { username: 'admin', password: process.env.E2E_ADMIN_PASSWORD || 'e2e-admin' };

const test = base.test.extend({
    page: async ({ page }, use) => {
        await page.route(/^https:\/\/(commons|upload)\.wikimedia\.org\//, route =>
            route.fulfill({ contentType: 'image/svg+xml', body: PHOTO }));
        await page.route(/^https:\/\/fonts\.(googleapis|gstatic)\.com\//, route => route.abort());
        await use(page);
    },
});

// Opens the admin panel through the sign-in page, as a browser does
async function signIn(page) {
    await page.goto('/admin');
    await page.getByLabel('Username').fill(ADMIN.username);
    await page.getByLabel('Password').fill(ADMIN.password);
    await page.getByRole('button', { name: 'Sign in' }).click();
    // Spring Security returns to the saved request as /admin?continue
    await page.waitForURL(/\/admin(\?continue)?$/);
}

// Colors from site.css
const GREEN = 'rgb(0, 166, 81)';
const SUN = 'rgb(255, 198, 41)';

module.exports = { test, expect: base.expect, signIn, ADMIN, GREEN, SUN };
