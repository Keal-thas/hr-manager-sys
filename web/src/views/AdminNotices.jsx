import React, { useEffect, useState } from 'react';
import {
  Button, Card, Checkbox, Form, Input, Modal, Select, Space, Table, Tabs, Tag, message
} from 'antd';
import api from '../api';

export default function AdminNotices({ tab }) {
  const [activeTab, setActiveTab] = useState(tab || 'notices');
  const [notices, setNotices] = useState([]);
  const [noticePage, setNoticePage] = useState(1);
  const [noticeSize, setNoticeSize] = useState(10);
  const [noticeTotal, setNoticeTotal] = useState(0);
  const [templates, setTemplates] = useState([]);
  const [departments, setDepartments] = useState([]);
  const [users, setUsers] = useState([]);
  const [publishOpen, setPublishOpen] = useState(false);
  const [templateOpen, setTemplateOpen] = useState(false);
  const [editingTemplate, setEditingTemplate] = useState(null);
  const [publishForm] = Form.useForm();
  const [templateForm] = Form.useForm();
  const targetType = Form.useWatch('targetType', publishForm);

  const loadNotices = async (page = noticePage, size = noticeSize) => {
    const data = await api.get('/admin/notices', { params: { page, size } });
    setNotices(data.records || []);
    setNoticeTotal(data.total || 0);
  };

  const loadTemplates = async () => {
    setTemplates(await api.get('/admin/templates'));
  };

  const loadOptions = async () => {
    const [deptList, userData] = await Promise.all([
      api.get('/admin/departments'),
      api.get('/admin/users?page=1&size=1000')
    ]);
    setDepartments(deptList || []);
    setUsers(userData.records || []);
  };

  useEffect(() => {
    setActiveTab(tab || 'notices');
  }, [tab]);

  useEffect(() => {
    loadNotices(1, noticeSize);
    loadTemplates();
    loadOptions();
  }, []);

  const publish = async (values) => {
    const payload = {
      ...values,
      targetIds: Array.isArray(values.targetIds) ? values.targetIds.join(',') : values.targetIds
    };
    const result = await api.post('/admin/notices', payload);
    message.success(`通知已发布，目标 ${result.targetCount} 人`);
    setPublishOpen(false);
    publishForm.resetFields();
    loadNotices(1, noticeSize);
  };

  const openTemplate = (record) => {
    setEditingTemplate(record || null);
    setTemplateOpen(true);
    if (record) {
      templateForm.setFieldsValue({ title: record.title, content: record.content });
    } else {
      templateForm.resetFields();
    }
  };

  const saveTemplate = async (values) => {
    if (editingTemplate) {
      await api.put(`/admin/templates/${editingTemplate.id}`, values);
    } else {
      await api.post('/admin/templates', values);
    }
    message.success('模板已保存');
    setTemplateOpen(false);
    loadTemplates();
  };

  const disableTemplate = async (id) => {
    await api.delete(`/admin/templates/${id}`);
    message.success('模板已停用');
    loadTemplates();
  };

  const targetOptions = () => {
    if (targetType === 'DEPT') {
      return departments.map((item) => ({ value: item.id, label: `${item.code} ${item.name}` }));
    }
    if (targetType === 'USER') {
      return users.map((item) => ({ value: item.id, label: `${item.employeeNo} ${item.name}` }));
    }
    return [];
  };

  return (
    <Card>
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'notices',
            label: '通知列表',
            children: (
              <>
                <Space style={{ marginBottom: 12 }}>
                  <Button type="primary" onClick={() => { publishForm.resetFields(); setPublishOpen(true); }}>发布通知</Button>
                </Space>
                <Table
                  rowKey="id"
                  dataSource={notices}
                  pagination={{
                    current: noticePage,
                    pageSize: noticeSize,
                    total: noticeTotal,
                    onChange: (p, s) => {
                      setNoticePage(p);
                      setNoticeSize(s);
                      loadNotices(p, s);
                    }
                  }}
                  columns={[
                    { title: '标题', dataIndex: 'title' },
                    { title: '目标', dataIndex: 'targetType', width: 100, render: (v) => <Tag color="blue">{v}</Tag> },
                    { title: '状态', dataIndex: 'status', width: 100, render: (v) => <Tag color="green">{v}</Tag> },
                    { title: '发布时间', dataIndex: 'createdAt', width: 180 }
                  ]}
                />
              </>
            )
          },
          {
            key: 'templates',
            label: '通知模板',
            children: (
              <>
                <Space style={{ marginBottom: 12 }}>
                  <Button type="primary" onClick={() => openTemplate(null)}>新建模板</Button>
                </Space>
                <Table
                  rowKey="id"
                  dataSource={templates}
                  pagination={false}
                  columns={[
                    { title: '标题', dataIndex: 'title' },
                    { title: '内容', dataIndex: 'content' },
                    { title: '状态', dataIndex: 'status', width: 100, render: (v) => <Tag>{v}</Tag> },
                    {
                      title: '操作',
                      width: 160,
                      render: (_, row) => (
                        <Space>
                          <Button size="small" onClick={() => openTemplate(row)}>编辑</Button>
                          <Button size="small" danger onClick={() => disableTemplate(row.id)}>停用</Button>
                        </Space>
                      )
                    }
                  ]}
                />
              </>
            )
          }
        ]}
      />

      <Modal
        title="发布通知"
        open={publishOpen}
        onCancel={() => setPublishOpen(false)}
        footer={null}
        width={680}
      >
        <Form
          form={publishForm}
          layout="vertical"
          initialValues={{ targetType: 'ALL', saveAsTemplate: false }}
          onFinish={publish}
        >
          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="content" label="内容" rules={[{ required: true, message: '请输入内容' }]}>
            <Input.TextArea rows={6} />
          </Form.Item>
          <Form.Item name="targetType" label="发送范围" rules={[{ required: true }]}>
            <Select options={[
              { value: 'ALL', label: '全员发送' },
              { value: 'DEPT', label: '按部门发送' },
              { value: 'USER', label: '指定员工发送' }
            ]} />
          </Form.Item>
          {targetType && targetType !== 'ALL' ? (
            <Form.Item name="targetIds" label={targetType === 'DEPT' ? '选择部门' : '选择员工'} rules={[{ required: true }]}>
              <Select mode="multiple" showSearch optionFilterProp="label" options={targetOptions()} />
            </Form.Item>
          ) : null}
          <Form.Item name="saveAsTemplate" label="同时存为模板" valuePropName="checked">
            <Checkbox>保存为模板</Checkbox>
          </Form.Item>
          <Button type="primary" htmlType="submit" block>发布</Button>
        </Form>
      </Modal>

      <Modal
        title={editingTemplate ? '编辑模板' : '新建模板'}
        open={templateOpen}
        onCancel={() => setTemplateOpen(false)}
        footer={null}
      >
        <Form form={templateForm} layout="vertical" onFinish={saveTemplate}>
          <Form.Item name="title" label="标题" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="content" label="内容" rules={[{ required: true }]}>
            <Input.TextArea rows={6} />
          </Form.Item>
          <Button type="primary" htmlType="submit" block>保存</Button>
        </Form>
      </Modal>
    </Card>
  );
}
