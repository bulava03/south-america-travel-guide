// Home, places and attraction pages with the test data from e2e/seed
const { test, expect, GREEN, SUN } = require('./fixtures');

test.describe('home', () => {
    test('shows countries of visible places', async ({ page }) => {
        await page.goto('/');

        await expect(page.getByRole('heading', { level: 1 })).toHaveText('Explore South America');
        // Colón Theater is hidden, but Argentina stays with Iguazu Falls and Llullaillaco
        await expect(page.locator('#countries .chip')).toHaveText(
            ['Argentina', 'Bolivia', 'Brazil', 'Chile', 'Peru', 'Venezuela']);
        await expect(page.getByRole('link', { name: 'Brazil' })).toHaveAttribute('href', '/places?country=Brazil');
    });

    test('"Choose a country" lights up the country chips', async ({ page }) => {
        await page.goto('/');

        await page.getByRole('link', { name: 'Choose a country' }).click();

        await expect(page.locator('#countries')).toHaveClass(/highlight/);
        await expect(page).toHaveURL('/');
    });

    test('logo turns yellow on hover over the photo', async ({ page }) => {
        await page.goto('/');

        await page.locator('.logo').hover();

        await expect(page.locator('.logo')).toHaveCSS('color', SUN);
    });

    test('"Explore places" opens all places', async ({ page }) => {
        await page.goto('/');

        await page.getByRole('link', { name: 'Explore places' }).click();

        await expect(page).toHaveURL('/places');
        await expect(page.getByRole('heading', { level: 1 })).toHaveText('Places to see');
    });
});

test.describe('places', () => {
    test('lists visible places, most popular first', async ({ page }) => {
        await page.goto('/places');

        const names = page.locator('.card-name');
        await expect(names).toHaveCount(8);
        await expect(names.first()).toHaveText('Machu Picchu');
        await expect(page.getByText('Colón Theater')).toHaveCount(0);
    });

    test('filters by country', async ({ page }) => {
        await page.goto('/places');

        await page.locator('.chips').getByRole('link', { name: 'Brazil' }).click();

        await expect(page).toHaveURL('/places?country=Brazil');
        await expect(page.getByRole('heading', { level: 1 })).toHaveText('Brazil');
        await expect(page.locator('.card-name')).toHaveText(['Iguazu Falls', 'Christ the Redeemer', 'Sugarloaf Mountain']);
        await expect(page.locator('.chip.active')).toHaveText('Brazil');
    });

    test('searches by name', async ({ page }) => {
        await page.goto('/places');

        await page.getByRole('searchbox', { name: 'Search places' }).fill('laguna');
        await page.getByRole('button', { name: 'Search' }).click();

        await expect(page.getByText('Results for “laguna”')).toBeVisible();
        await expect(page.locator('.card-name')).toHaveText(['Laguna Colorada']);
    });

    test('shows a message when nothing is found', async ({ page }) => {
        await page.goto('/places?search=nothing-like-this');

        await expect(page.getByRole('heading', { name: 'Nothing found' })).toBeVisible();
        await page.getByRole('link', { name: 'All places' }).click();
        await expect(page.locator('.card')).toHaveCount(8);
    });

    test('fills every card according to its photo', async ({ page }) => {
        await page.goto('/places');
        const card = name => page.locator('.card', { hasText: name });

        // Landscape: cropped thumbnail instead of the original file
        await expect(card('Machu Picchu').locator('img.plate-cover')).toHaveAttribute('src',
            'https://commons.wikimedia.org/wiki/Special:FilePath/E2E%20Machu%20Picchu.jpg?width=800');
        // Vertical and panorama: whole photo over its blurred copy
        await expect(card('Christ the Redeemer').locator('img.plate-fit')).toBeVisible();
        await expect(card('Laguna Colorada').locator('img.plate-blur')).toHaveCount(1);
        // Small photo: a print
        await expect(card('Llullaillaco').locator('img.plate-print')).toBeVisible();
        // No photo: the name on green
        await expect(card('La Tortuga Island').locator('.plate-none-name')).toHaveText('La Tortuga Island');
    });

    test('chips turn green on hover', async ({ page }) => {
        await page.goto('/places');
        const chip = page.locator('.chips').getByRole('link', { name: 'Peru' });

        await chip.hover();

        await expect(chip).toHaveCSS('background-color', GREEN);
        await expect(chip).toHaveCSS('color', 'rgb(255, 255, 255)');
    });

    test('a card opens the attraction page', async ({ page }) => {
        await page.goto('/places');

        await page.locator('.card', { hasText: 'Machu Picchu' }).click();

        await expect(page).toHaveURL('/attraction/1');
    });
});

test.describe('attraction page', () => {
    test('shows details, links and more places of the country', async ({ page }) => {
        await page.goto('/attraction/1');

        await expect(page.getByRole('heading', { level: 1 })).toHaveText('Machu Picchu');
        await expect(page.locator('.badge')).toHaveText('Archaeological site');
        await expect(page.getByText('Machu Picchu is a 15th-century Inca citadel.')).toBeVisible();
        await expect(page.locator('.fact-value')).toHaveText(['13.1631° S', '72.5450° W']);
        await expect(page.getByRole('link', { name: 'Show on map' }))
            .toHaveAttribute('href', /openstreetmap\.org\/\?mlat=-13\.16310&mlon=-72\.54500/);
        await expect(page.getByRole('link', { name: 'Wikidata' }))
            .toHaveAttribute('href', 'https://www.wikidata.org/wiki/Q676203');
        await expect(page.getByRole('link', { name: 'Photo: Wikimedia Commons' }))
            .toHaveAttribute('href', 'https://commons.wikimedia.org/wiki/File:E2E%20Machu%20Picchu.jpg');

        await expect(page.getByRole('heading', { name: 'More in Peru' })).toBeVisible();
        await expect(page.locator('.mini-name')).toHaveText(['Titicaca']);
    });

    test('a place in two countries links to both', async ({ page }) => {
        await page.goto('/attraction/2');

        await expect(page.locator('.detail-info .chip')).toHaveText(['Argentina', 'Brazil']);
        await page.locator('.detail-info .chip', { hasText: 'Brazil' }).click();
        await expect(page).toHaveURL('/places?country=Brazil');
    });

    test('the whole photo is shown, not cropped', async ({ page }) => {
        await page.goto('/attraction/3');

        await expect(page.locator('.detail-photo img.plate-fit')).toHaveAttribute('src', /\?width=1600$/);
    });

    test('"All places" goes back to the list', async ({ page }) => {
        await page.goto('/attraction/1');

        await page.getByRole('link', { name: 'All places' }).click();

        await expect(page).toHaveURL('/places');
    });
});

test.describe('phones', () => {
    test.use({ viewport: { width: 390, height: 844 } });

    for (const path of ['/', '/places', '/attraction/1', '/login', '/no-such-page']) {
        test(`${path} fits the screen without horizontal scroll`, async ({ page }) => {
            await page.goto(path);

            const overflow = await page.evaluate(() =>
                document.documentElement.scrollWidth - document.documentElement.clientWidth);
            expect(overflow).toBeLessThanOrEqual(0);
        });
    }
});
