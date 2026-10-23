import { test, expect } from '@playwright/test';
test('landing leads to explorer', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: /Your next opportunity/ })).toBeVisible();
  await page.getByRole('link', { name: 'Explore opportunities', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Opportunity explorer' })).toBeVisible();
});
test('registration rejects short passwords', async ({ page }) => {
  await page.goto('/register');
  await page.getByLabel('Full name').fill('Fixture User');
  await page.getByLabel('Email address').fill('fixture@example.test');
  await page.getByLabel('Password').fill('short');
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByText('Use at least 12 characters')).toBeVisible();
});
test('protected workspace prompts sign-in', async ({ page }) => {
  await page.goto('/dashboard');
  await expect(page.getByRole('link', { name: 'Sign in →' })).toBeVisible();
});
test('registration creates an authenticated company workspace', async ({ page }) => {
  const email = `e2e-${Date.now()}@example.test`;
  await page.goto('/register');
  await page.getByLabel('Full name').fill('Acceptance Test');
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password').fill('Disposable-Test-Password-42');
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page).toHaveURL(/onboarding/);
  await page.getByLabel('Company name').fill('Acceptance test company');
  await page.getByLabel('Industry', { exact: true }).fill('Software');
  await page.getByLabel('Service keywords').fill('Java, API integration');
  await page.getByRole('button', { name: 'Save company profile' }).click();
  await expect(page.getByText('Profile saved. Matches recalculated.')).toBeVisible();
  await page.getByRole('link', { name: 'Overview', exact: true }).click();
  await expect(page.getByText('Relevant company matches')).toBeVisible();
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page.getByRole('link', { name: 'Sign in', exact: true })).toBeVisible();
});
