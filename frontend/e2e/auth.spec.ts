import { test, expect, mockApi, json, customer, admin } from './fixtures';

test.describe('Authentication', () => {
  test('protected pages redirect a visitor to sign in', async ({ page }) => {
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByText('Welcome Back')).toBeVisible();
  });

  test('validates the form before calling the API', async ({ page }) => {
    let called = false;
    await mockApi(page, '/auth/login', (route) => { called = true; return json(route, {}); });
    await page.goto('/login');
    await page.getByRole('button', { name: 'Sign In' }).click();
    await expect(page.getByText('Email is required')).toBeVisible();
    expect(called).toBe(false);
  });

  test('shows the server message on bad credentials', async ({ page }) => {
    await mockApi(page, '/auth/login', (route) => json(route, { message: 'Invalid email or password' }, 401));
    await page.goto('/login');
    await page.getByLabel('Email').fill(customer.email);
    await page.getByLabel('Password', { exact: true }).fill('wrong-password');
    await page.getByRole('button', { name: 'Sign In' }).click();
    await expect(page.getByRole('alert')).toContainText('Invalid email or password');
    await expect(page).toHaveURL(/\/login$/);
  });

  for (const [user, landing] of [[customer, '/dashboard'], [admin, '/admin']] as const) {
    test(`${user.role} lands on ${landing} after signing in`, async ({ page }) => {
      await mockApi(page, '/auth/login', async (route) => {
        expect(route.request().postDataJSON()).toEqual({ email: user.email, password: 'Secret123!' });
        return json(route, { user, accessToken: 'a', refreshToken: 'r' });
      });
      await page.goto('/login');
      await page.getByLabel('Email').fill(user.email);
      await page.getByLabel('Password', { exact: true }).fill('Secret123!');
      await page.getByRole('button', { name: 'Sign In' }).click();
      await expect(page).toHaveURL(new RegExp(`${landing}$`));
      expect(await page.evaluate(() => sessionStorage.getItem('accessToken'))).toBe('a');
    });
  }
});
