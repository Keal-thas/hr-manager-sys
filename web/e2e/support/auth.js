import { expect } from '@playwright/test';

export async function login(page, employeeNo, password) {
  await page.goto('/');
  await page.getByTestId('login-employee-no').fill(employeeNo);
  await page.getByTestId('login-password').fill(password);
  await page.getByTestId('login-submit').click();
  await expect(page.getByTestId('dashboard-page')).toBeVisible();
}

export async function openMenu(page, code) {
  await page.getByTestId(`menu-${code}`).click();
  await expect(page.getByTestId(`view-${code}`)).toBeVisible();
}

export async function expectMessage(page, text) {
  await expect(page.locator('.ant-message')).toContainText(text);
}
