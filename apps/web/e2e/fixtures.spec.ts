import { test, expect } from '@playwright/test';

// Explicit browser-test fixtures only. These responses never enter the application database.
const opportunity = {
  id: 'fixture-id',
  title: 'Browser test fixture — software services',
  type: 'TENDER',
  issuer_name: 'Fixture issuer',
  country_code: 'MK',
  deadline_at: null,
  published_at: null,
  estimated_value: null,
  currency: null,
  status: 'UNKNOWN',
  effective_status: 'UNKNOWN',
  source_name: 'Fixture source',
  source_code: 'TED',
  source_url: 'https://ted.europa.eu',
  description: 'Test fixture description',
  last_verified_at: null,
  details: {},
  documents: [],
  provenance: [],
};
test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/me', (route) => route.fulfill({ status: 401, json: {} }));
  await page.route('**/api/v1/auth/csrf', (route) =>
    route.fulfill({ json: { token: 'test-token', headerName: 'X-CSRF-TOKEN' } }),
  );
});
test('explorer sends search and type filters to the API', async ({ page }) => {
  await page.route('**/api/v1/opportunities?*', (route) =>
    route.fulfill({ json: { items: [opportunity], total: 1, page: 0, size: 20 } }),
  );
  await page.goto('/opportunities');
  await expect(page.getByRole('link', { name: opportunity.title })).toBeVisible();
  await page.getByRole('textbox', { name: 'Search opportunities' }).fill('software');
  const searched = page.waitForRequest(
    (r) =>
      r.url().includes('/api/v1/opportunities?') &&
      new URL(r.url()).searchParams.get('q') === 'software',
  );
  await page.getByRole('button', { name: 'Search', exact: true }).click();
  await searched;
  const filtered = page.waitForRequest(
    (r) =>
      r.url().includes('/api/v1/opportunities?') &&
      new URL(r.url()).searchParams.get('type') === 'GRANT',
  );
  await page.getByRole('tab', { name: 'Grants', exact: true }).click();
  await filtered;
});
test('details preserve unknown values and save the correct opportunity', async ({ page }) => {
  await page.route('**/api/v1/opportunities/fixture-id', (route) =>
    route.fulfill({ json: opportunity }),
  );
  await page.route('**/api/v1/saved-opportunities', (route) => route.fulfill({ status: 204 }));
  await page.goto('/opportunities/fixture-id');
  await expect(page.getByRole('heading', { name: opportunity.title })).toBeVisible();
  await expect(
    page.getByText('Applicant conditions have not been confirmed.', { exact: false }),
  ).toBeVisible();
  const save = page.waitForRequest(
    (r) => r.url().endsWith('/saved-opportunities') && r.method() === 'POST',
  );
  await page.getByRole('button', { name: 'Save opportunity' }).click();
  expect((await save).postDataJSON()).toEqual({ opportunityId: 'fixture-id' });
  await expect(page.getByRole('button', { name: 'Saved to your workspace' })).toBeVisible();
});
test('saved workflow can be updated and removed', async ({ page }) => {
  let state = 'SAVED';
  let removed = false;
  await page.route('**/api/v1/saved-opportunities?*', (route) =>
    route.fulfill({
      json: removed
        ? []
        : [{ ...opportunity, id: 'saved-id', opportunity_id: opportunity.id, status: state }],
    }),
  );
  await page.route('**/api/v1/saved-opportunities/saved-id/status', (route) => {
    state = route.request().postDataJSON().status;
    return route.fulfill({ status: 204 });
  });
  await page.route('**/api/v1/saved-opportunities/saved-id', (route) => {
    removed = true;
    return route.fulfill({ status: 204 });
  });
  await page.goto('/dashboard/saved');
  const status = page.getByRole('combobox', {
    name: `Application status for ${opportunity.title}`,
  });
  await status.selectOption('APPLIED');
  await expect(status).toHaveValue('APPLIED');
  await page.getByRole('button', { name: 'Remove', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Your shortlist starts here' })).toBeVisible();
});
