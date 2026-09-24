import { test, expect, mockApi, json, signInAs, customer } from './fixtures';

const notifications = [
  { id: 1, userId: 7, type: 'BOOKING', title: 'Booking confirmed', message: 'Your plumber is booked',
    referenceType: 'BOOKING', referenceId: 42, isRead: false, createdAt: new Date().toISOString() },
  { id: 2, userId: 7, type: 'PAYMENT', title: 'Payment received', message: '₹1,734.60 paid',
    referenceType: 'PAYMENT', referenceId: 9, isRead: true, createdAt: new Date().toISOString() },
];

test.describe('Notification bell', () => {
  test.beforeEach(async ({ page }) => {
    await signInAs(page, customer);
    await mockApi(page, '/notifications/unread-count', { unreadCount: 1 });
    await mockApi(page, /\/api\/v1\/notifications\?/, { content: notifications, totalElements: 2, number: 0, totalPages: 1 });
  });

  test('shows the unread count and lists notifications', async ({ page }) => {
    await page.goto('/dashboard');
    const bell = page.getByRole('button', { name: 'Notifications (1 unread)' });
    await expect(bell).toBeVisible();
    await bell.click();
    await expect(page.getByText('Booking confirmed')).toBeVisible();
    await expect(page.getByText('Payment received')).toBeVisible();
  });

  test('mark all read clears the badge', async ({ page }) => {
    let markedAll = false;
    // Stateful, like the server: once everything is read, a later badge poll must also say 0.
    await mockApi(page, '/notifications/unread-count', (route) => json(route, { unreadCount: markedAll ? 0 : 1 }));
    await mockApi(page, '/notifications/read-all', (route) => { markedAll = true; return json(route, {}); });
    await page.goto('/dashboard');
    await page.getByRole('button', { name: 'Notifications (1 unread)' }).click();
    await page.getByRole('button', { name: 'Mark all read' }).click();
    await expect.poll(() => markedAll).toBe(true);
    // Aimed at the menu: the clicked button unmounts, so a page-level keypress can land on <body>.
    await page.getByRole('menu').press('Escape');
    await expect(page.getByRole('button', { name: 'Notifications (0 unread)' })).toBeVisible();
  });

  test('opening an unread notification marks it read', async ({ page }) => {
    const marked: string[] = [];
    await mockApi(page, '/notifications/*/read', (route) => { marked.push(route.request().url()); return json(route, {}); });
    await page.goto('/dashboard');
    await page.getByRole('button', { name: 'Notifications (1 unread)' }).click();
    await page.getByText('Booking confirmed').click();
    await expect.poll(() => marked.length).toBe(1);
    expect(marked[0]).toContain('/notifications/1/read');
  });

  test('is hidden for visitors', async ({ browser }) => {
    const page = await browser.newPage();
    await page.route('**/api/**', (route) => json(route, {}, 404));
    await page.goto('/');
    await expect(page.getByRole('textbox', { name: /search services/i })).toBeVisible();
    await expect(page.getByRole('button', { name: /^Notifications/ })).toHaveCount(0);
    await page.close();
  });
});
