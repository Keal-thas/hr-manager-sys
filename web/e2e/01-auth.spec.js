import { expect, test } from '@playwright/test';
import { login } from './support/auth';

test.describe('登录与会话', () => {
  test('未登录时只能看到登录页', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByTestId('login-employee-no')).toBeVisible();
    await expect(page.getByTestId('login-submit')).toBeVisible();
    await expect(page.getByTestId('dashboard-page')).toHaveCount(0);
  });

  test('密码错误不会进入系统', async ({ page }) => {
    await page.goto('/');
    await page.getByTestId('login-employee-no').fill('10001');
    await page.getByTestId('login-password').fill('WrongPass1');
    await page.getByTestId('login-submit').click();

    await expect(page.locator('.ant-message')).toContainText('密码错误');
    await expect(page.getByTestId('login-employee-no')).toBeVisible();
  });

  test('管理员登录后看到全部菜单和首页统计', async ({ page }) => {
    await login(page, '10001', 'Admin1234');

    await expect(page.getByTestId('dashboard-page')).toBeVisible();
    await expect(page.getByText('有效用户')).toBeVisible();
    await expect(page.getByText('最近一期考勤')).toBeVisible();
    for (const code of [
      'dashboard', 'my-attendance', 'my-messages', 'profile',
      'admin-attendance', 'admin-notice', 'admin-template',
      'admin-dept', 'admin-role', 'admin-user'
    ]) {
      await expect(page.getByTestId(`menu-${code}`)).toBeVisible();
    }
  });

  test('退出登录后会话失效并回到登录页', async ({ page }) => {
    await login(page, '10001', 'Admin1234');
    await page.getByRole('button', { name: '退出' }).click();

    await expect(page.getByTestId('login-employee-no')).toBeVisible();
    const me = await page.request.get('/api/auth/me');
    expect(me.status()).toBe(401);
  });
});
