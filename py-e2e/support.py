from playwright.sync_api import Page, expect

ADMIN = ("10001", "Admin1234")
EMPLOYEE = ("10004", "Emp123456")


def login(page: Page, app_url: str, credentials=ADMIN) -> None:
    page.goto(app_url)
    page.get_by_test_id("login-employee-no").fill(credentials[0])
    page.get_by_test_id("login-password").fill(credentials[1])
    page.get_by_test_id("login-submit").click()
    expect(page.get_by_test_id("dashboard-page")).to_be_visible()


def open_view(page: Page, code: str) -> None:
    page.get_by_test_id(f"menu-{code}").click()
    page.get_by_test_id(f"view-{code}").wait_for(state="visible")


def show_cursor(page: Page) -> None:
    page.add_init_script(
        """
        (() => {
          const dot = document.createElement('div');
          dot.id = 'py-cursor-dot';
          dot.style.cssText = [
            'position:fixed', 'left:0', 'top:0', 'width:16px', 'height:16px',
            'border-radius:50%', 'background:#ff4d4f', 'border:2px solid #fff',
            'box-shadow:0 0 6px rgba(0,0,0,.5)', 'pointer-events:none',
            'z-index:2147483647', 'transform:translate(-50%,-50%)',
            'transition:width .08s,height .08s,background .08s'
          ].join(';');
          const attach = () => {
            if (!document.documentElement) return;
            if (document.getElementById('py-cursor-dot')) return;
            document.documentElement.appendChild(dot);
          };
          attach();
          document.addEventListener('DOMContentLoaded', attach, { once: true });
          addEventListener('mousemove', (e) => {
            dot.style.left = e.clientX + 'px';
            dot.style.top = e.clientY + 'px';
          }, true);
          addEventListener('mousedown', (e) => {
            dot.style.left = e.clientX + 'px';
            dot.style.top = e.clientY + 'px';
            dot.style.width = '26px';
            dot.style.height = '26px';
            dot.style.background = '#fa8c16';
          }, true);
          addEventListener('mouseup', (e) => {
            dot.style.left = e.clientX + 'px';
            dot.style.top = e.clientY + 'px';
            dot.style.width = '16px';
            dot.style.height = '16px';
            dot.style.background = '#ff4d4f';
          }, true);
        })();
        """
    )
