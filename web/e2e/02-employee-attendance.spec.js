import { expect, test } from '@playwright/test';
import { expectMessage, login, openMenu } from './support/auth';

test.describe('员工自助', () => {
  test('员工可以阅读并签字确认考勤', async ({ page }) => {
    await login(page, '10004', 'Emp123456');
    await openMenu(page, 'my-attendance');

    await expect(page.getByTestId('my-attendance-page')).toBeVisible();
    await expect(page.getByText('阅读并签字确认')).toBeVisible();

    await page.getByTestId('attendance-sign-open').click();
    const modal = page.locator('.ant-modal').filter({ hasText: '考勤签字确认' });
    await expect(modal).toBeVisible();
    await modal.click({ trial: true });

    await modal.getByTestId('attendance-agree').check();

    const canvas = modal.getByTestId('signature-canvas');
    const box = await canvas.boundingBox();
    await page.mouse.move(box.x + 40, box.y + 60);
    await page.mouse.down();
    await page.mouse.move(box.x + 140, box.y + 90, { steps: 8 });
    await page.mouse.move(box.x + 240, box.y + 55, { steps: 8 });
    await page.mouse.up();

    await modal.getByTestId('attendance-sign-submit').click();
    await expectMessage(page, '考勤确认成功');
    await expect(page.getByText('已确认，签名时间')).toBeVisible();
  });

  test('员工可以查看消息并全部标记已读', async ({ page }) => {
    await login(page, '10004', 'Emp123456');
    await openMenu(page, 'my-messages');

    await expect(page.getByTestId('my-messages-page')).toBeVisible();
    await expect(page.getByText('欢迎使用人事综合管理系统')).toBeVisible();
    await expect(page.getByText('考勤通知').first()).toBeVisible();

    await page.getByRole('button', { name: '全部已读' }).click();
    await expectMessage(page, '已全部标记为已读');
  });

  test('员工可以保存联系方式', async ({ page }) => {
    await login(page, '10004', 'Emp123456');
    await openMenu(page, 'profile');

    await expect(page.getByTestId('profile-page')).toBeVisible();
    const phone = page.getByLabel('手机号');
    await phone.fill('13900000004');
    await page.getByTestId('profile-save').click();
    await expectMessage(page, '个人资料已保存');
  });
});
