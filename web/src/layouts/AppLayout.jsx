import React, { useState } from 'react';
import { Button, Layout, Menu, Space, Tag, Typography } from 'antd';
import { LogoutOutlined, MenuFoldOutlined, MenuUnfoldOutlined, UserOutlined } from '@ant-design/icons';

const { Header, Sider, Content, Footer } = Layout;

export default function AppLayout({ session, menus, titles, view, onNavigate, onLogout, children }) {
  const [collapsed, setCollapsed] = useState(false);

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        width={224}
        theme="dark"
        collapsible
        collapsed={collapsed}
        trigger={null}
        breakpoint="lg"
        collapsedWidth={0}
        onBreakpoint={(broken) => setCollapsed(broken)}
        data-testid="app-sider"
      >
        <div className="app-logo">
          <span className="app-logo-mark">HR</span>
          {!collapsed ? <span>HR MANAGER</span> : null}
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[view]}
          onClick={(event) => onNavigate(event.key)}
          items={menus.map((item) => ({
            key: item.code,
            label: <span data-testid={`menu-${item.code}`}>{item.name}</span>
          }))}
        />
      </Sider>
      <Layout>
        <Header className="app-header">
          <Space size={12}>
            <Button
              type="text"
              aria-label={collapsed ? '展开菜单' : '收起菜单'}
              icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={() => setCollapsed(!collapsed)}
            />
            <div>
              <Typography.Text type="secondary" className="app-brand">
                人事综合管理系统
              </Typography.Text>
              <div className="page-title" data-testid="page-title">{titles[view] || '首页'}</div>
            </div>
          </Space>
          <Space size={10} wrap>
            <Tag icon={<UserOutlined />} color="blue">{session.user.name}</Tag>
            <span className="muted">{session.user.roleName}</span>
            <Button icon={<LogoutOutlined />} onClick={onLogout}>退出</Button>
          </Space>
        </Header>
        <Content className="app-content">
          <div className="app-content-inner" data-testid={`view-${view}`}>{children}</div>
        </Content>
        <Footer className="app-footer">HR Manager · 考勤确认 · 消息通知 · 组织权限</Footer>
      </Layout>
    </Layout>
  );
}
