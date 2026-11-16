import React, { useEffect, useState } from 'react';
import { Button, Form, Input, Layout, Menu, Modal, Space, Spin, Tag, message } from 'antd';
import { LogoutOutlined, UserOutlined } from '@ant-design/icons';
import api from './api';
import Login from './views/Login';
import Dashboard from './views/Dashboard';
import MyAttendance from './views/MyAttendance';
import MyMessages from './views/MyMessages';
import Profile from './views/Profile';
import AdminAttendance from './views/AdminAttendance';
import AdminNotices from './views/AdminNotices';
import AdminSystem from './views/AdminSystem';

const { Header, Sider, Content } = Layout;

const titles = {
  dashboard: '首页',
  'my-attendance': '我的考勤',
  'my-messages': '消息通知',
  profile: '个人中心',
  'admin-attendance': '考勤管理',
  'admin-notice': '通知发布',
  'admin-template': '通知模板',
  'admin-dept': '部门管理',
  'admin-role': '角色管理',
  'admin-user': '用户管理'
};

export default function App() {
  const [session, setSession] = useState(null);
  const [loading, setLoading] = useState(true);
  const [view, setView] = useState('dashboard');
  const [passwordForm] = Form.useForm();

  useEffect(() => {
    api.get('/auth/me')
      .then(setSession)
      .catch(() => {})
      .finally(() => setLoading(false));
    const unauthorized = () => setSession(null);
    window.addEventListener('app:unauthorized', unauthorized);
    return () => window.removeEventListener('app:unauthorized', unauthorized);
  }, []);

  useEffect(() => {
    if (!session) return;
    const menus = session.menus || [];
    if (menus.length && !menus.some((item) => item.code === view)) {
      setView(menus[0].code);
    }
  }, [session]);

  const logout = async () => {
    try {
      await api.post('/auth/logout');
    } finally {
      setSession(null);
    }
  };

  const changeInitialPassword = async (values) => {
    if (values.newPassword !== values.confirmPassword) {
      message.warning('两次输入的新密码不一致');
      return;
    }
    await api.post('/auth/change-password', {
      oldPassword: values.oldPassword,
      newPassword: values.newPassword
    });
    const data = await api.get('/auth/me');
    setSession(data);
    message.success('密码已修改');
  };

  if (loading) {
    return <div style={{ display: 'flex', height: '100vh', alignItems: 'center', justifyContent: 'center' }}><Spin size="large" /></div>;
  }

  if (!session) {
    return <Login onLogin={(data) => { setSession(data); setView('dashboard'); }} />;
  }

  const menus = session.menus || [];

  const renderView = () => {
    switch (view) {
      case 'dashboard':
        return <Dashboard session={session} />;
      case 'my-attendance':
        return <MyAttendance />;
      case 'my-messages':
        return <MyMessages />;
      case 'profile':
        return <Profile />;
      case 'admin-attendance':
        return <AdminAttendance />;
      case 'admin-notice':
        return <AdminNotices tab="notices" />;
      case 'admin-template':
        return <AdminNotices tab="templates" />;
      case 'admin-dept':
        return <AdminSystem tab="departments" />;
      case 'admin-role':
        return <AdminSystem tab="roles" />;
      case 'admin-user':
        return <AdminSystem tab="users" />;
      default:
        return <Dashboard session={session} />;
    }
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider width={220} theme="dark">
        <div className="app-logo">HR MANAGER</div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[view]}
          onClick={(event) => setView(event.key)}
          items={menus.map((item) => ({ key: item.code, label: item.name }))}
        />
      </Sider>
      <Layout>
        <Header className="app-header">
          <div className="page-title">{titles[view] || '人事综合管理系统'}</div>
          <Space>
            <Tag icon={<UserOutlined />} color="blue">{session.user.name}</Tag>
            <span className="muted">{session.user.roleName}</span>
            <Button icon={<LogoutOutlined />} onClick={logout}>退出</Button>
          </Space>
        </Header>
        <Content style={{ padding: 20 }}>{renderView()}</Content>
      </Layout>

      <Modal
        title="请修改初始密码"
        open={!!session.mustChangePassword}
        closable={false}
        maskClosable={false}
        footer={null}
      >
        <p className="muted">首次登录必须修改初始密码后才能使用系统。</p>
        <Form form={passwordForm} layout="vertical" onFinish={changeInitialPassword}>
          <Form.Item name="oldPassword" label="原密码" rules={[{ required: true, message: '请输入原密码' }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item name="newPassword" label="新密码" rules={[{ required: true, message: '请输入新密码' }]}>
            <Input.Password placeholder="8-50 位，包含大写、小写和数字" />
          </Form.Item>
          <Form.Item name="confirmPassword" label="确认新密码" rules={[{ required: true, message: '请再次输入新密码' }]}>
            <Input.Password />
          </Form.Item>
          <Button type="primary" htmlType="submit" block>确认修改</Button>
        </Form>
      </Modal>
    </Layout>
  );
}
