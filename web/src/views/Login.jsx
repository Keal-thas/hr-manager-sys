import React, { useState } from 'react';
import { Alert, Button, Card, Form, Input, Typography } from 'antd';
import { LockOutlined, UserOutlined } from '@ant-design/icons';
import api from '../api';

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
