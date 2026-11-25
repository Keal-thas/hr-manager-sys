from playwright.sync_api import expect

from support import login, open_view, show_cursor


def test_admin_walkthrough(page, app_url):
    show_cursor(page)
    login(page, app_url)

    for code in ("my-attendance", "my-messages", "profile"):
        open_view(page, code)
        expect(page.get_by_test_id(f"view-{code}")).to_be_visible()

    open_view(page, "admin-attendance")
    page.get_by_test_id("attendance-records-open").first.click()
    records = page.locator(".ant-modal").filter(has_text="考勤明细")
    expect(records).to_be_visible()
    page.keyboard.press("Escape")
    expect(records).to_be_hidden()

    open_view(page, "admin-notice")
    page.get_by_role("button", name="发布通知").click()
    notice = page.locator(".ant-modal").filter(has_text="发布通知")
    expect(notice).to_be_visible()
    page.keyboard.press("Escape")
    expect(notice).to_be_hidden()

    for code in ("admin-template", "admin-dept", "admin-role", "admin-user"):
        open_view(page, code)
        expect(page.get_by_test_id(f"view-{code}")).to_be_visible()

    page.get_by_role("button", name="退出").click()
    expect(page.get_by_test_id("login-employee-no")).to_be_visible()
