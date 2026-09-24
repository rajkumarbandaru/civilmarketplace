import { test, expect } from './fixtures';

test.describe('Global search', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  const search = (page: import('@playwright/test').Page) =>
    page.getByRole('textbox', { name: /search services, materials and equipment/i });

  test('suggests catalogue entries as you type', async ({ page }) => {
    await search(page).fill('plumbing');
    await expect(page.getByRole('button', { name: /Plumbing Services ₹/ })).toBeVisible();
    await expect(page.getByText(/See all results for/)).toBeVisible();
  });

  test('reports when nothing matches', async ({ page }) => {
    await search(page).fill('zzqqxx');
    await expect(page.getByText(/Nothing matches/)).toBeVisible();
  });

  test('Enter opens the results page with the query', async ({ page }) => {
    await search(page).fill('land survey');
    await search(page).press('Enter');
    await expect(page).toHaveURL(/\/services\?q=land%20survey$/);
  });

  test('choosing a suggestion goes to its booking page (sign-in required)', async ({ page }) => {
    await search(page).fill('plumbing');
    await page.getByRole('button', { name: /Plumbing Services ₹/ }).click();
    // Booking is protected, so a visitor is sent to sign in first.
    await expect(page).toHaveURL(/\/login$/);
  });
});
