from playwright.sync_api import expect

from support import ADMIN, EMPLOYEE, login, open_view


def test_open_login_page(page, app_url):
    # 跳转, 然后用页面级断言等待标题和元素就绪
    page.goto(f"{app_url}/")
    expect(page).to_have_title("人事综合管理系统")
    expect(page.get_by_test_id("login-submit")).to_be_visible()


def test_login_waits_for_dashboard(page, app_url):
    # 表单输入 + 点击提交, 等待登录后的首页出现
    page.goto(app_url)
    page.get_by_test_id("login-employee-no").fill(ADMIN[0])
    page.get_by_test_id("login-password").fill(ADMIN[1])
    page.get_by_test_id("login-submit").click()
    expect(page.get_by_test_id("dashboard-page")).to_be_visible()
    expect(page.get_by_text("有效用户")).to_be_visible()


def test_switch_views_by_menu(page, app_url):
    # SPA 内跳转: 点菜单, 等 view 容器可见再继续
    login(page, app_url)
    for code in ("my-attendance", "my-messages", "profile", "admin-user"):
        open_view(page, code)
        expect(page.get_by_test_id(f"view-{code}")).to_be_visible()


def test_employee_profile_and_message(page, app_url):
    # 语义定位 + 表单提交 + 等提示消息
    login(page, app_url, EMPLOYEE)
    open_view(page, "profile")
    page.get_by_label("手机号").fill("13900000004")
    page.get_by_test_id("profile-save").click()
    expect(page.locator(".ant-message")).to_contain_text("个人资料已保存")

    open_view(page, "my-messages")
    page.get_by_role("button", name="全部已读").click()
    expect(page.locator(".ant-message")).to_contain_text("已全部标记为已读")
