import { test, expect } from '@playwright/test';

test('admin shows sync activity and the completed result on desktop and mobile', async ({
  page,
}) => {
  const source = {
    id: 'fixture-source',
    name: 'Official source fixture',
    code: 'EU_FUNDING',
    enabled: true,
    access_status: 'VERIFIED',
    interval_minutes: 60,
    last_success_at: null as string | null,
    last_error: null,
    lease_until: null,
  };
  const run = {
    id: 'fixture-run',
    source_id: source.id,
    source_name: source.name,
    status: 'SUCCESS',
    inserted: 3,
    updated: 2,
    rejected: 0,
    error: null,
    started_at: '2026-10-24T10:00:00Z',
  };
  let completed = false;
  let finish!: () => void;
  const pending = new Promise<void>((resolve) => {
    finish = resolve;
  });
  await page.route('**/api/v1/me', (route) =>
    route.fulfill({ json: { full_name: 'Admin', role: 'ADMIN' } }),
  );
  await page.route('**/api/v1/auth/csrf', (route) =>
    route.fulfill({ json: { token: 'fixture', headerName: 'X-CSRF-TOKEN' } }),
  );
  await page.route('**/api/v1/admin/sources', (route) => route.fulfill({ json: [source] }));
  await page.route('**/api/v1/admin/review-queue', (route) => route.fulfill({ json: [] }));
  await page.route('**/api/v1/admin/ingestion-runs', (route) =>
    route.fulfill({ json: completed ? [run] : [] }),
  );
  await page.route('**/api/v1/admin/sources/fixture-source/sync', async (route) => {
    await pending;
    source.last_success_at = run.started_at;
    completed = true;
    await route.fulfill({ json: run });
  });
  await page.goto('/admin');
  await expect(page.getByText('Not synced yet', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Sync now' }).click();
  await expect(page.getByText('Sync running', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Syncing…' })).toBeDisabled();
  await page.screenshot({ path: 'test-results/admin-sync-running.png' });
  finish();
  await expect(page.getByText('Synced', { exact: true })).toBeVisible();
  await expect(page.getByText('Latest run: 3 new · 2 updated · 0 rejected')).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole('button', { name: 'Sync now' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.screenshot({ path: 'test-results/admin-sync-mobile.png' });
});
