import React, { useEffect, useState } from 'react';
import { Button, Card, Col, Descriptions, Form, Input, Row, message } from 'antd';
import api from '../api';

export default function Profile() {
  const [profile, setProfile] = useState(null);
  const [profileForm] = Form.useForm();
  const [passwordForm] = Form.useForm();

  const load = async () => {
    const data = await api.get('/my/profile');
    setProfile(data);
    profileForm.setFieldsValue({
      phone: data.user?.phone,
      email: data.user?.email
    });
  };

  useEffect(() => {
    load();
  }, []);

  const saveProfile = async (values) => {
    const data = await api.put('/my/profile', values);
    setProfile(data);
    message.success('个人资料已保存');
  };

  const changePassword = async (values) => {
    if (values.newPassword !== values.confirmPassword) {
      message.warning('两次输入的新密码不一致');
      return;
    }
    await api.post('/auth/change-password', {
      oldPassword: values.oldPassword,
      newPassword: values.newPassword
    });
    message.success('密码修改成功');
    passwordForm.resetFields();
  };

  return (
    <Row gutter={[16, 16]}>
      <Col xs={24} lg={12}>
        <Card title="个人信息">
          <Descriptions column={1} size="small">
            <Descriptions.Item label="工号">{profile?.user?.employeeNo}</Descriptions.Item>
            <Descriptions.Item label="姓名">{profile?.user?.name}</Descriptions.Item>
            <Descriptions.Item label="部门">{profile?.user?.departmentName}</Descriptions.Item>
            <Descriptions.Item label="角色">{profile?.user?.roleName}</Descriptions.Item>
          </Descriptions>
        </Card>
      </Col>
      <Col xs={24} lg={12}>
        <Card title="联系方式">
          <Form form={profileForm} layout="vertical" onFinish={saveProfile}>
            <Form.Item name="phone" label="手机号">
              <Input placeholder="请输入手机号" />
            </Form.Item>
            <Form.Item name="email" label="邮箱">
              <Input placeholder="请输入邮箱" />
            </Form.Item>
            <Button type="primary" htmlType="submit">保存</Button>
          </Form>
        </Card>
      </Col>
      <Col xs={24} lg={12}>
        <Card title="修改密码">
          <Form form={passwordForm} layout="vertical" onFinish={changePassword}>
            <Form.Item name="oldPassword" label="原密码" rules={[{ required: true, message: '请输入原密码' }]}>
              <Input.Password />
            </Form.Item>
            <Form.Item
              name="newPassword"
              label="新密码"
              extra="8-50 位，必须包含大写字母、小写字母和数字"
              rules={[{ required: true, message: '请输入新密码' }]}
            >
              <Input.Password />
            </Form.Item>
            <Form.Item name="confirmPassword" label="确认新密码" rules={[{ required: true, message: '请再次输入新密码' }]}>
              <Input.Password />
            </Form.Item>
            <Button type="primary" htmlType="submit">修改密码</Button>
          </Form>
        </Card>
      </Col>
    </Row>
  );
}
