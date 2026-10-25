// Sign-in page and admin panel. The sync API is mocked: a real sync would call Wikidata.
const { test, expect, signIn, ADMIN, GREEN } = require('./fixtures');

const COUNTRIES = ['Argentina', 'Bolivia', 'Brazil', 'Chile', 'Colombia', 'Ecuador',
    'Guyana', 'Paraguay', 'Peru', 'Suriname', 'Uruguay', 'Venezuela'];
const STARTED = '2026-10-25T10:00:00Z';
const FINISHED = '2026-10-25T10:01:38Z';
const TWO_DAYS_AGO = () => new Date(Date.now() - 2 * 24 * 3600 * 1000).toISOString();

function country(name, step = null, extra = {}) {
    return { name, step, placeCount: 0, message: null, failedStep: null, lastSync: null, ...extra };
}

function status(fields) {
    return {
        running: false, stage: null, startedAt: null, finishedAt: null, result: null, error: null,
        countries: COUNTRIES.map(name => country(name)),
        ...fields,
    };
}

// Brazil is being fetched, Argentina is done, Venezuela failed, the others wait
const RUNNING = status({
    running: true,
    stage: 'WIKIDATA',
    startedAt: new Date(Date.now() - 42000).toISOString(),
    countries: COUNTRIES.map(name => {
        if (name === 'Argentina') return country(name, 'DONE', { placeCount: 25 });
        if (name === 'Brazil') return country(name, 'DETAILS');
        if (name === 'Venezuela') {
            return country(name, 'FAILED', { message: 'timed out', failedStep: 'CANDIDATES' });
        }
        return country(name, 'WAITING');
    }),
});

const FINISHED_STATUS = status({
    stage: 'SAVING',
    startedAt: STARTED,
    finishedAt: FINISHED,
    result: { places: 296, created: 4, removed: 2 },
    countries: COUNTRIES.map(name => name === 'Venezuela'
        ? country(name, 'FAILED', {
            message: 'timed out', failedStep: 'CANDIDATES',
            lastSync: { syncedAt: FINISHED, succeeded: false, placeCount: 0, message: 'timed out' },
        })
        : country(name, 'DONE', {
            placeCount: 25,
            lastSync: { syncedAt: TWO_DAYS_AGO(), succeeded: true, placeCount: 25, message: null },
        })),
});

/**
 * Replaces GET/POST /api/admin/sync with the states returned by {@code next()}.
 * @returns posted URLs
 */
async function mockSync(page, next) {
    const posted = [];
    await page.route(/\/api\/admin\/sync(\/[^/?]+)?$/, route => {
        if (route.request().method() === 'POST') {
            posted.push(new URL(route.request().url()).pathname);
        }
        const answer = next(route.request());
        return route.fulfill({ status: answer.status || 200, json: answer.body || answer });
    });
    return posted;
}

test.describe('sign in', () => {
    test('admin panel asks to sign in first', async ({ page }) => {
        await page.goto('/admin');

        await expect(page).toHaveURL('/login');
        await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
    });

    test('wrong password shows an error', async ({ page }) => {
        await page.goto('/login');
        await page.getByLabel('Username').fill(ADMIN.username);
        await page.getByLabel('Password').fill('wrong');
        await page.getByRole('button', { name: 'Sign in' }).click();

        await expect(page).toHaveURL('/login?error');
        await expect(page.getByText('Wrong username or password.')).toBeVisible();
    });

    test('signing in opens the admin panel, logging out returns home', async ({ page }) => {
        await page.goto('/admin');
        await page.getByLabel('Username').fill(ADMIN.username);
        await page.getByLabel('Password').fill(ADMIN.password);
        await page.getByRole('button', { name: 'Sign in' }).click();

        await expect(page).toHaveURL(/\/admin(\?continue)?$/);
        await expect(page.locator('.admin-user')).toHaveText('admin');

        await page.getByRole('button', { name: 'Log out' }).click();
        await expect(page).toHaveURL('/');
    });

    test('the label is inside the field box', async ({ page }) => {
        await page.goto('/login');
        const field = page.locator('.field', { hasText: 'Username' });

        await field.locator('span').click();

        await expect(page.getByLabel('Username')).toBeFocused();
    });
});

test.describe('admin panel', () => {
    test.beforeEach(async ({ page }) => {
        await signIn(page);
    });

    test('shows statistics', async ({ page }) => {
        const stat = label => page.locator('.stat', { hasText: label });

        await expect(stat('Places').locator('.stat-value')).toHaveText('9');
        await expect(stat('Places').locator('.stat-note')).toHaveText('on the site: 8');
        await expect(stat('Hidden').locator('.stat-value')).toHaveText('1');
        await expect(stat('Countries').locator('.stat-note')).toHaveText('1 failed last time');
    });

    test('shows the saved result of every country', async ({ page }) => {
        const card = name => page.locator('.sync-country', { hasText: name });

        await expect(page.locator('.sync-country')).toHaveCount(12);
        await expect(card('Brazil').locator('.sync-country-status')).toHaveText('25 places');
        await expect(card('Brazil').locator('.sync-country-note')).toHaveText('Last: 2 days ago');
        await expect(card('Brazil').getByRole('button', { name: 'Sync Brazil' })).toBeVisible();
        await expect(card('Venezuela').locator('.sync-country-status')).toHaveText('Failed last time');
        await expect(card('Venezuela').locator('.sync-country-note')).toHaveText('HttpTimeoutException: request timed out');
        await expect(card('Venezuela').getByRole('button', { name: 'Retry Venezuela' })).toHaveClass(/btn-sun/);
        await expect(card('Argentina').locator('.sync-country-status')).toHaveText('No sync record yet');
        await expect(page.locator('#sync-all')).toBeEnabled();
    });

    test('shows the progress of every country, then the result', async ({ page }) => {
        let state = 'idle';
        const posted = await mockSync(page, request => {
            if (request.method() === 'POST') {
                state = 'running';
                return { status: 202, body: RUNNING };
            }
            return state === 'running' ? RUNNING : state === 'finished' ? FINISHED_STATUS : status({});
        });
        await page.reload();

        await page.getByRole('button', { name: 'Sync now' }).click();

        await expect.poll(() => posted).toEqual(['/api/admin/sync']);
        await expect(page.locator('.sync-running-title')).toHaveText('Syncing: 2 of 12 countries');
        await expect(page.locator('.sync-stage-run')).toContainText('Wikidata');
        await expect(page.locator('#sync-all')).toBeDisabled();

        const card = name => page.locator('.sync-country', { hasText: name });
        await expect(card('Argentina').locator('.sync-country-status')).toHaveText('25 places');
        await expect(card('Brazil').locator('.sync-country-status')).toHaveText('Details…');
        await expect(card('Brazil').locator('.sync-bar')).toHaveClass([/sync-bar-done/, /sync-bar-done/, /sync-bar-run/]);
        await expect(card('Venezuela').locator('.sync-country-status')).toHaveText('Failed, kept as it was');
        await expect(card('Venezuela').locator('.sync-bar')).toHaveClass([/sync-bar-done/, /sync-bar-fail/, /sync-bar-wait/]);
        await expect(card('Chile').locator('.sync-country-status')).toHaveText('Waiting');
        // No buttons on the cards while a sync is running
        await expect(page.locator('#sync-countries button')).toHaveCount(0);

        // The page reloads when the sync finishes
        state = 'finished';
        await expect(page.locator('#sync-state .notice')).toHaveText(
            'Done in 1:38: 296 places, 4 new, 2 removed. Venezuela failed and was left as it was.');
        await expect(card('Venezuela').getByRole('button', { name: 'Retry Venezuela' })).toBeVisible();
        await expect(card('Brazil').getByRole('button', { name: 'Sync Brazil' })).toBeVisible();
        await expect(page.locator('#sync-all')).toBeEnabled();
    });

    test('a failed country can be retried on its own', async ({ page }) => {
        const posted = await mockSync(page, request => request.method() === 'POST'
            ? { status: 202, body: RUNNING } : FINISHED_STATUS);
        await page.reload();

        await page.getByRole('button', { name: 'Retry Venezuela' }).click();

        await expect.poll(() => posted).toEqual(['/api/admin/sync/Venezuela']);
    });

    test('a second sync is refused while one is running', async ({ page }) => {
        await mockSync(page, request => request.method() === 'POST'
            ? { status: 409, body: { status: 'error', message: 'A sync is already running' } }
            : status({}));
        await page.reload();

        await page.getByRole('button', { name: 'Sync now' }).click();

        await expect(page.locator('#sync-state .notice-error')).toHaveText('A sync is already running');
    });

    test('Wikidata failure is shown in red', async ({ page }) => {
        await mockSync(page, () => status({
            startedAt: STARTED, finishedAt: FINISHED,
            error: 'Wikidata did not answer for any country. Nothing was changed, try again later.',
        }));
        await page.reload();

        await expect(page.locator('#sync-state .notice-error')).toHaveText(/^Wikidata did not answer/);
    });

    test('filters places', async ({ page }) => {
        const rows = page.locator('.places-table tbody tr');
        await expect(rows).toHaveCount(9);

        await page.getByRole('link', { name: 'Hidden · 1' }).click();
        await expect(page).toHaveURL(/show=HIDDEN/);
        await expect(rows).toHaveCount(1);
        await expect(rows.first()).toContainText('Colón Theater');
        // A hidden place has no page on the site
        await expect(rows.first().getByRole('link', { name: 'Colón Theater' })).toHaveCount(0);

        await page.getByRole('link', { name: 'No photo · 1' }).click();
        await expect(rows).toHaveCount(1);
        await expect(rows.first()).toContainText('La Tortuga Island');

        await page.getByRole('link', { name: 'All · 9' }).click();
        await page.getByLabel('Country').selectOption('Bolivia');
        await expect(page).toHaveURL(/country=Bolivia/);
        await expect(rows).toHaveCount(2);

        await page.getByRole('searchbox', { name: 'Search places' }).fill('titi');
        await page.getByRole('searchbox', { name: 'Search places' }).press('Enter');
        await expect(rows).toHaveCount(1);
        await expect(rows.first()).toContainText('Titicaca');
    });

    test('photo preview shows the card as on the site and the whole photo', async ({ page }) => {
        const row = page.locator('.places-table tbody tr', { hasText: 'Christ the Redeemer' });
        const preview = row.locator('.preview');
        await expect(row.locator('.thumb .plate-fit')).toHaveCount(1);
        await expect(preview).toBeHidden();

        await row.locator('.thumb').hover();

        await expect(preview).toBeVisible();
        await expect(preview.locator('.preview-card .card-name')).toHaveText('Christ the Redeemer');
        await expect(preview.locator('.preview-full img')).toHaveAttribute('src', /E2E%20Christ\.jpg\?width=1600$/);
        await expect(preview.locator('.preview-note')).toContainText('2667 × 4000 px');
        await expect(preview.getByRole('link', { name: 'Open on Commons' }))
            .toHaveAttribute('href', 'https://commons.wikimedia.org/wiki/File:E2E%20Christ.jpg');
        await expect(row.locator('td').nth(3)).toHaveText('Whole photo');
    });

    test('buttons turn green on hover', async ({ page }) => {
        const button = page.getByRole('button', { name: 'Log out' });

        await button.hover();

        await expect(button).toHaveCSS('background-color', GREEN);
    });

    test('hiding a place removes it from the site, showing brings it back', async ({ page }) => {
        const row = () => page.locator('.places-table tbody tr', { hasText: 'Sugarloaf Mountain' });

        await row().getByRole('button', { name: 'Hide' }).click();
        await expect(row().locator('.status')).toHaveText('Hidden');
        await expect(row()).toHaveClass(/row-hidden/);

        await page.goto('/places');
        await expect(page.locator('.card', { hasText: 'Sugarloaf Mountain' })).toHaveCount(0);
        expect((await page.goto('/attraction/9')).status()).toBe(404);

        await page.goto('/admin?show=HIDDEN');
        await row().getByRole('button', { name: 'Show' }).click();
        // After the reload the place is no longer in the hidden list
        await expect(row()).toHaveCount(0);
        await page.goto('/admin');
        await expect(row().locator('.status')).toHaveText('Visible');

        await page.goto('/places');
        await expect(page.locator('.card', { hasText: 'Sugarloaf Mountain' })).toHaveCount(1);
    });
});
