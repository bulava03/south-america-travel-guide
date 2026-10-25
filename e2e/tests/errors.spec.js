// Error pages: the same full-screen photo as home
const { test, expect, SUN } = require('./fixtures');

test('missing attraction shows 404 with the most visited places', async ({ page }) => {
    const response = await page.goto('/attraction/12345');

    expect(response.status()).toBe(404);
    await expect(page.locator('.error-code')).toHaveText('Error 404');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('This place is not on our map');
    await expect(page.locator('.hero-error .chip')).toHaveText(
        ['Machu Picchu', 'Iguazu Falls', 'Christ the Redeemer', 'Titicaca']);
    await expect(page.getByText('Salar de Uyuni · Bolivia')).toBeVisible();
});

test('a most visited place opens its page', async ({ page }) => {
    await page.goto('/attraction/12345');

    await page.locator('.hero-error .chip', { hasText: 'Titicaca' }).click();

    await expect(page).toHaveURL('/attraction/4');
});

test('unknown address shows 404', async ({ page }) => {
    const response = await page.goto('/no-such-page');

    expect(response.status()).toBe(404);
    await expect(page.getByRole('link', { name: 'Explore places' })).toHaveAttribute('href', '/places');
    await expect(page.getByRole('link', { name: 'Home page' })).toHaveAttribute('href', '/');
});

test('hidden place is not found', async ({ page }) => {
    const response = await page.goto('/attraction/6');

    expect(response.status()).toBe(404);
});

test('navigation turns yellow on hover over the photo', async ({ page }) => {
    await page.goto('/no-such-page');
    const link = page.locator('.navlink', { hasText: 'Places' });

    await link.hover();

    await expect(link).toHaveCSS('color', SUN);
});
