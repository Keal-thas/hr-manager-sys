import { expect, test } from '@playwright/test';
import { expectMessage, login, openMenu } from './support/auth';

test.describe('管理员流程', () => {
  test('管理员可以浏览用户和部门数据', async ({ page }) => {
    await login(page, '10001', 'Admin1234');

    await openMenu(page, 'admin-user');
    await expect(page.getByTestId('admin-system-page')).toBeVisible();
    await expect(page.getByText('10001')).toBeVisible();
    await expect(page.getByText('10004')).toBeVisible();

    await openMenu(page, 'admin-dept');
    await expect(page.getByRole('cell', { name: '信息科技部' })).toBeVisible();
    await expect(page.getByRole('cell', { name: '人力资源部' })).toBeVisible();
  });

  test('管理员可以发布全员通知', async ({ page }) => {
    await login(page, '10001', 'Admin1234');
    await openMenu(page, 'admin-notice');

    await page.getByRole('button', { name: '发布通知' }).click();
    const modal = page.locator('.ant-modal').filter({ hasText: '发布通知' });
    await expect(modal).toBeVisible();

    const title = `E2E 通知 ${Date.now()}`;
    await modal.getByLabel('标题').fill(title);
    await modal.getByLabel('内容').fill('这是一条由 Playwright 发布的端到端测试通知。');
    await modal.getByTestId('notice-publish-submit').click();

    await expectMessage(page, '通知已发布');
    await expect(page.getByText(title)).toBeVisible();
  });

  test('管理员可以下载考勤模板并查看批次明细', async ({ page }) => {
    await login(page, '10001', 'Admin1234');
    await openMenu(page, 'admin-attendance');

    const template = await page.request.get('/api/admin/attendance/template');
    expect(template.status()).toBe(200);
    expect(template.headers()['content-type']).toContain('spreadsheet');
    expect((await template.body()).length).toBeGreaterThan(1000);

    await expect(page.getByText('考勤批次')).toBeVisible();
    await page.getByTestId('attendance-records-open').first().click();
    await expect(page.locator('.ant-modal').filter({ hasText: '考勤明细' })).toBeVisible();
    await expect(page.getByText('张三').first()).toBeVisible();
  });

  test('监控端点可用', async ({ page }) => {
    const health = await page.request.get('/actuator/health');
    expect(health.status()).toBe(200);
    expect((await health.json()).status).toBe('UP');

    await login(page, '10001', 'Admin1234');
    const metrics = await page.request.get('/actuator/prometheus');
    expect(metrics.status()).toBe(200);
    const text = await metrics.text();
    expect(text).toContain('hr_auth_login_total');
    expect(text).toContain('http_server_requests_seconds');
  });
});
