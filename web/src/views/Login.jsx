import React, { useState } from 'react';
import { Alert, Button, Card, Form, Input, Tag, Typography } from 'antd';
import { LockOutlined, SafetyCertificateOutlined, ScheduleOutlined, TeamOutlined, UserOutlined } from '@ant-design/icons';
import api from '../api';

const highlights = [
  { icon: <ScheduleOutlined />, title: '考勤确认', text: '月度考勤签收, 手写签名, 异常提醒' },
  { icon: <TeamOutlined />, title: '组织权限', text: '部门树, 角色菜单, 用户生命周期' },
  { icon: <SafetyCertificateOutlined />, title: '安全登录', text: 'BCrypt 密码, 失败锁定, 初始密码强制修改' }
];

export default function Login({ onLogin }) {
  const [loading, setLoading] = useState(false);

  const submit = async (values) => {
    setLoading(true);
    try {
      const data = await api.post('/auth/login', values);
      onLogin(data);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-page">
      <div className="login-shell">
        <section className="login-hero">
          <Tag color="blue">HR MANAGER</Tag>
          <Typography.Title level={1} className="login-hero-title">
            让考勤与组织管理更简单
          </Typography.Title>
          <Typography.Paragraph className="login-hero-text">
            一个保持简单的 HR 管理系统: 月度考勤签收, 站内消息通知, 部门与角色权限.
          </Typography.Paragraph>
          <div className="login-highlights">
            {highlights.map((item) => (
              <div className="login-highlight" key={item.title}>
                <span className="login-highlight-icon">{item.icon}</span>
                <div>
                  <div className="login-highlight-title">{item.title}</div>
                  <div className="login-highlight-text">{item.text}</div>
                </div>
              </div>
            ))}
          </div>
        </section>

        <Card className="login-card" variant="borderless">
          <Typography.Title level={3} style={{ textAlign: 'center', marginBottom: 4 }}>
            人事综合管理系统
          </Typography.Title>
          <div className="muted" style={{ textAlign: 'center', marginBottom: 20 }}>
            考勤确认 · 消息通知 · 组织权限
          </div>
          <Form layout="vertical" onFinish={submit}>
            <Form.Item name="employeeNo" label="工号" rules={[{ required: true, message: '请输入工号' }]}>
              <Input
                prefix={<UserOutlined />}
                placeholder="例如 10001"
                size="large"
                data-testid="login-employee-no"
              />
            </Form.Item>
            <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
              <Input.Password
                prefix={<LockOutlined />}
                placeholder="请输入密码"
                size="large"
                data-testid="login-password"
              />
            </Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              size="large"
              block
              loading={loading}
              data-testid="login-submit"
            >
              登录
            </Button>
          </Form>
          <Alert
            style={{ marginTop: 18 }}
            type="info"
            showIcon
            message="演示账号"
            description={
              <div>
                管理员: 10001 / Admin1234<br />
                人事: 10002 / Hr123456<br />
                员工: 10003 / Emp123456
              </div>
            }
          />
        </Card>
      </div>
    </div>
  );
}
