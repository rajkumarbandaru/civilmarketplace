import { Page } from '@playwright/test';
import { test, expect, mockApi, json } from './fixtures';

/** Two-step sign-in (authenticator app), required for Super Admins. */

const superAdmin = { id: 1, name: 'Operator', email: 'ops@example.com', role: 'SUPER_ADMIN' };
const session = { success: true, user: superAdmin, accessToken: 'a-final', refreshToken: 'r-final' };

const signInWithPassword = async (page: Page) => {
  await page.goto('/login');
  await page.getByLabel('Email').fill(superAdmin.email);
  await page.getByLabel('Password', { exact: true }).fill('Secret123!');
  await page.getByRole('button', { name: 'Sign In' }).click();
};

const storedAccessToken = (page: Page) => page.evaluate(() => sessionStorage.getItem('accessToken'));

test.describe('Two-step sign-in', () => {
  test('first sign-in of a Super Admin enrols an authenticator, shows recovery codes once, then signs in', async ({ page }) => {
    let enabledWith: Record<string, unknown> | null = null;
    await mockApi(page, '/auth/login', { success: true, mfaRequired: true, mfaSetupRequired: true, mfaToken: 'setup-ticket' });
    await mockApi(page, '/auth/mfa/setup', (route) => {
      expect(route.request().postDataJSON()).toEqual({ mfaToken: 'setup-ticket' });
      return json(route, { secret: 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP',
        otpauthUri: 'otpauth://totp/Civil%20Marketplace:ops%40example.com?secret=JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP' });
    });
    await mockApi(page, '/auth/mfa/enable', (route) => {
      enabledWith = route.request().postDataJSON();
      return json(route, { ...session, recoveryCodes: ['abcde-fghjk', 'mnpqr-stuvw'] });
    });

    await signInWithPassword(page);
    const step = page.getByTestId('mfa-step');
    await expect(step.getByText('Set up two-step sign-in')).toBeVisible();
    await expect(step.getByAltText('QR code for your authenticator app')).toBeVisible();
    await expect(page.getByTestId('mfa-secret')).toHaveText('JBSW Y3DP EHPK 3PXP JBSW Y3DP EHPK 3PXP');
    expect(await storedAccessToken(page)).toBeNull();

    await step.getByLabel('Authentication code').fill('12ab3456');
    await expect(step.getByLabel('Authentication code')).toHaveValue('123456');
    await step.getByRole('button', { name: 'Turn on and sign in' }).click();
    expect(enabledWith).toEqual({ mfaToken: 'setup-ticket', code: '123456' });

    const codes = page.getByTestId('mfa-recovery-codes');
    await expect(codes.getByText('abcde-fghjk')).toBeVisible();
    await expect(page).toHaveURL(/\/login$/);
    await codes.getByRole('button', { name: 'I have saved them' }).click();
    await expect(page).toHaveURL(/\/admin$/);
    expect(await storedAccessToken(page)).toBe('a-final');
  });

  test('an enrolled Super Admin enters a code; a wrong one is reported and can be retried', async ({ page }) => {
    let attempts = 0;
    await mockApi(page, '/auth/login', { success: true, mfaRequired: true, mfaSetupRequired: false, mfaToken: 'verify-ticket' });
    await mockApi(page, '/auth/mfa/verify', (route) => {
      attempts += 1;
      const { code } = route.request().postDataJSON();
      return code === '246810'
        ? json(route, session)
        : json(route, { message: 'That code is not right. Check your authenticator app and try again.' }, 400);
    });

    await signInWithPassword(page);
    const step = page.getByTestId('mfa-step');
    await expect(step.getByText('Enter the 6-digit code from your authenticator app.')).toBeVisible();
    await expect(step.getByRole('button', { name: 'Verify' })).toBeDisabled();

    await step.getByLabel('Authentication code').fill('111111');
    await step.getByRole('button', { name: 'Verify' }).click();
    await expect(step.getByRole('alert')).toContainText('That code is not right');
    await expect(step.getByLabel('Authentication code')).toHaveValue('');

    await step.getByLabel('Authentication code').fill('246810');
    await step.getByRole('button', { name: 'Verify' }).click();
    await expect(page).toHaveURL(/\/admin$/);
    expect(attempts).toBe(2);
  });

  test('a recovery code can be used instead, and "Back to sign in" abandons the step', async ({ page }) => {
    let sent = '';
    await mockApi(page, '/auth/login', { success: true, mfaRequired: true, mfaSetupRequired: false, mfaToken: 'verify-ticket' });
    await mockApi(page, '/auth/mfa/verify', (route) => {
      sent = route.request().postDataJSON().code;
      return json(route, session);
    });

    await signInWithPassword(page);
    await page.getByRole('button', { name: 'Back to sign in' }).click();
    await expect(page.getByRole('button', { name: 'Sign In' })).toBeVisible();

    await page.getByRole('button', { name: 'Sign In' }).click();
    const step = page.getByTestId('mfa-step');
    await step.getByRole('button', { name: 'Use a recovery code' }).click();
    await step.getByLabel('Recovery code').fill('abcde-fghjk');
    await step.getByRole('button', { name: 'Verify' }).click();
    await expect(page).toHaveURL(/\/admin$/);
    expect(sent).toBe('abcde-fghjk');
  });

  test('social sign-in of a Super Admin continues on the two-step screen without receiving tokens', async ({ page }) => {
    await mockApi(page, '/auth/mfa/verify', (route) => json(route, session));
    await page.goto('/oauth2/redirect?mfaToken=social-ticket&mfaSetup=false');
    await expect(page).toHaveURL(/\/login$/);
    const step = page.getByTestId('mfa-step');
    await expect(step).toBeVisible();
    expect(await storedAccessToken(page)).toBeNull();
    await step.getByLabel('Authentication code').fill('246810');
    await step.getByRole('button', { name: 'Verify' }).click();
    await expect(page).toHaveURL(/\/admin$/);
  });

  test('accounts without MFA sign in in one step as before', async ({ page }) => {
    await mockApi(page, '/auth/login', { ...session, user: { ...superAdmin, role: 'CUSTOMER' } });
    await signInWithPassword(page);
    await expect(page).toHaveURL(/\/dashboard$/);
  });
});
