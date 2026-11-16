import React, { useEffect, useState } from 'react';
import {
  Button, Card, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tabs, Tag, message
} from 'antd';
import api from '../api';

export default function AdminSystem({ tab }) {
  const [activeTab, setActiveTab] = useState(tab || 'departments');
  const [depts, setDepts] = useState([]);
  const [deptFlat, setDeptFlat] = useState([]);
  const [deptOpen, setDeptOpen] = useState(false);
  const [editingDept, setEditingDept] = useState(null);
  const [deptForm] = Form.useForm();

  const [roles, setRoles] = useState([]);
  const [menuOptions, setMenuOptions] = useState([]);
  const [roleOpen, setRoleOpen] = useState(false);
  const [editingRole, setEditingRole] = useState(null);
  const [roleForm] = Form.useForm();

  const [users, setUsers] = useState([]);
  const [userPage, setUserPage] = useState(1);
  const [userSize, setUserSize] = useState(10);
  const [userTotal, setUserTotal] = useState(0);
  const [userKeyword, setUserKeyword] = useState('');
  const [userOpen, setUserOpen] = useState(false);
  const [editingUser, setEditingUser] = useState(null);
  const [userForm] = Form.useForm();

  const loadDepts = async () => {
    const [tree, flat] = await Promise.all([
      api.get('/admin/departments/tree'),
      api.get('/admin/departments')
    ]);
    setDepts(tree || []);
    setDeptFlat(flat || []);
  };

  const loadRoles = async () => {
    setRoles(await api.get('/admin/roles'));
  };

  const loadMenus = async () => {
    setMenuOptions(await api.get('/admin/menus'));
  };

  const loadUsers = async (page = userPage, size = userSize, keyword = userKeyword) => {
    const data = await api.get('/admin/users', { params: { page, size, keyword } });
    setUsers(data.records || []);
    setUserTotal(data.total || 0);
  };

  useEffect(() => {
    setActiveTab(tab || 'departments');
  }, [tab]);

  useEffect(() => {
    loadDepts();
    loadRoles();
    loadMenus();
    loadUsers(1, userSize, '');
  }, []);

  const openDept = (record) => {
    setEditingDept(record || null);
    setDeptOpen(true);
    if (record) {
      deptForm.setFieldsValue({ name: record.name, sort: record.sort });
    } else {
      deptForm.resetFields();
    }
  };

  const saveDept = async (values) => {
    if (editingDept) {
      await api.put(`/admin/departments/${editingDept.id}`, values);
    } else {
      await api.post('/admin/departments', values);
    }
    message.success('部门已保存');
    setDeptOpen(false);
    loadDepts();
  };

  const removeDept = async (id) => {
    await api.delete(`/admin/departments/${id}`);
    message.success('部门已删除');
    loadDepts();
  };

  const openRole = (record) => {
    setEditingRole(record || null);
    setRoleOpen(true);
    if (record) {
      roleForm.setFieldsValue({
        code: record.code,
        name: record.name,
        permissions: record.permissions ? record.permissions.split(',') : []
      });
    } else {
      roleForm.resetFields();
    }
  };

  const saveRole = async (values) => {
    const payload = { ...values, permissions: (values.permissions || []).join(',') };
    if (editingRole) {
      await api.put(`/admin/roles/${editingRole.id}`, payload);
    } else {
      await api.post('/admin/roles', payload);
    }
    message.success('角色已保存');
    setRoleOpen(false);
    loadRoles();
  };

  const disableRole = async (id) => {
    await api.delete(`/admin/roles/${id}`);
    message.success('角色已停用');
    loadRoles();
  };

  const openUser = (record) => {
    setEditingUser(record || null);
    setUserOpen(true);
    if (record) {
      userForm.setFieldsValue({
        employeeNo: record.employeeNo,
        name: record.name,
        departmentId: record.departmentId,
        roleId: record.roleId,
        phone: record.phone,
        email: record.email,
        status: record.status
      });
    } else {
      userForm.resetFields();
      userForm.setFieldsValue({ status: 'ACTIVE' });
    }
  };

  const saveUser = async (values) => {
    if (editingUser) {
      await api.put(`/admin/users/${editingUser.id}`, values);
    } else {
      await api.post('/admin/users', values);
    }
    message.success('用户已保存');
    setUserOpen(false);
    loadUsers(userPage, userSize, userKeyword);
  };

  const resetPassword = async (id) => {
    await api.post(`/admin/users/${id}/reset-password`, {});
    message.success('密码已重置为 Init1234');
  };

  const toggleUser = async (record) => {
    await api.post(`/admin/users/${record.id}/status`, {
      status: record.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
    });
    message.success('用户状态已更新');
    loadUsers(userPage, userSize, userKeyword);
  };

  const deptOptions = deptFlat.map((item) => ({
    value: item.id,
    label: `${item.code} ${item.name}`
  }));

  const roleOptions = roles.filter((item) => item.status === 'ACTIVE').map((item) => ({
    value: item.id,
    label: item.name
  }));

  return (
    <Card>
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'departments',
            label: '部门管理',
            children: (
              <>
                <Space style={{ marginBottom: 12 }}>
                  <Button type="primary" onClick={() => openDept(null)}>新增部门</Button>
                </Space>
                <Table
                  rowKey="id"
                  dataSource={depts}
                  pagination={false}
                  defaultExpandAllRows
                  columns={[
                    { title: '部门编码', dataIndex: 'code', width: 150 },
                    { title: '部门名称', dataIndex: 'name' },
                    { title: '层级', dataIndex: 'level', width: 90 },
                    { title: '排序', dataIndex: 'sort', width: 90 },
                    {
                      title: '操作',
                      width: 180,
                      render: (_, row) => (
                        <Space>
                          <Button size="small" onClick={() => openDept(row)}>编辑</Button>
                          <Popconfirm title="确认删除该部门？" onConfirm={() => removeDept(row.id)}>
                            <Button size="small" danger>删除</Button>
                          </Popconfirm>
                        </Space>
                      )
                    }
                  ]}
                />
              </>
            )
          },
          {
            key: 'roles',
            label: '角色管理',
            children: (
              <>
                <Space style={{ marginBottom: 12 }}>
                  <Button type="primary" onClick={() => openRole(null)}>新增角色</Button>
                </Space>
                <Table
                  rowKey="id"
                  dataSource={roles}
                  pagination={false}
                  columns={[
                    { title: '编码', dataIndex: 'code', width: 140 },
                    { title: '名称', dataIndex: 'name', width: 160 },
                    { title: '权限', dataIndex: 'permissions' },
                    { title: '状态', dataIndex: 'status', width: 100, render: (v) => <Tag color={v === 'ACTIVE' ? 'green' : 'default'}>{v}</Tag> },
                    {
                      title: '操作',
                      width: 160,
                      render: (_, row) => (
                        <Space>
                          <Button size="small" onClick={() => openRole(row)}>编辑</Button>
                          <Popconfirm title="确认停用该角色？" onConfirm={() => disableRole(row.id)}>
                            <Button size="small" danger>停用</Button>
                          </Popconfirm>
                        </Space>
                      )
                    }
                  ]}
                />
              </>
            )
          },
          {
            key: 'users',
            label: '用户管理',
            children: (
              <>
                <Space style={{ marginBottom: 12 }}>
                  <Button type="primary" onClick={() => openUser(null)}>新增用户</Button>
                  <Input.Search
                    placeholder="工号 / 姓名 / 手机号"
                    allowClear
                    onSearch={(value) => {
                      setUserKeyword(value);
                      setUserPage(1);
                      loadUsers(1, userSize, value);
                    }}
                  />
                </Space>
                <Table
                  rowKey="id"
                  dataSource={users}
                  pagination={{
                    current: userPage,
                    pageSize: userSize,
                    total: userTotal,
                    showSizeChanger: true,
                    onChange: (p, s) => {
                      setUserPage(p);
                      setUserSize(s);
                      loadUsers(p, s, userKeyword);
                    }
                  }}
                  columns={[
                    { title: '工号', dataIndex: 'employeeNo', width: 110 },
                    { title: '姓名', dataIndex: 'name', width: 120 },
                    { title: '部门', dataIndex: 'departmentName', width: 160 },
                    { title: '角色', dataIndex: 'roleName', width: 130 },
                    { title: '手机号', dataIndex: 'phone', width: 140 },
                    { title: '状态', dataIndex: 'status', width: 100, render: (v) => <Tag color={v === 'ACTIVE' ? 'green' : 'red'}>{v}</Tag> },
                    {
                      title: '操作',
                      width: 280,
                      render: (_, row) => (
                        <Space wrap>
                          <Button size="small" onClick={() => openUser(row)}>编辑</Button>
                          <Popconfirm title="确认重置密码为 Init1234？" onConfirm={() => resetPassword(row.id)}>
                            <Button size="small">重置密码</Button>
                          </Popconfirm>
                          <Popconfirm title="确认切换用户状态？" onConfirm={() => toggleUser(row)}>
                            <Button size="small" danger={row.status === 'ACTIVE'}>{row.status === 'ACTIVE' ? '停用' : '启用'}</Button>
                          </Popconfirm>
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

      <Modal title={editingDept ? '编辑部门' : '新增部门'} open={deptOpen} onCancel={() => setDeptOpen(false)} footer={null}>
        <Form form={deptForm} layout="vertical" onFinish={saveDept}>
          <Form.Item name="name" label="部门名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          {!editingDept ? (
            <Form.Item name="parentId" label="上级部门">
              <Select allowClear placeholder="不选表示一级部门" options={deptOptions} />
            </Form.Item>
          ) : null}
          <Form.Item name="sort" label="排序">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Button type="primary" htmlType="submit" block>保存</Button>
        </Form>
      </Modal>

      <Modal title={editingRole ? '编辑角色' : '新增角色'} open={roleOpen} onCancel={() => setRoleOpen(false)} footer={null}>
        <Form form={roleForm} layout="vertical" onFinish={saveRole}>
          <Form.Item name="code" label="角色编码" rules={[{ required: true }]}>
            <Input disabled={!!editingRole} />
          </Form.Item>
          <Form.Item name="name" label="角色名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="permissions" label="权限菜单">
            <Select mode="multiple" options={menuOptions.map((item) => ({ value: item.code, label: item.name }))} />
          </Form.Item>
          <Button type="primary" htmlType="submit" block>保存</Button>
        </Form>
      </Modal>

      <Modal title={editingUser ? '编辑用户' : '新增用户'} open={userOpen} onCancel={() => setUserOpen(false)} footer={null} width={620}>
        <Form form={userForm} layout="vertical" onFinish={saveUser}>
          <Form.Item name="employeeNo" label="工号" rules={[{ required: true, pattern: /^\d+$/, message: '工号必须为数字' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="name" label="姓名" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          {!editingUser ? (
            <Form.Item name="password" label="初始密码" extra="不填则使用 Init1234">
              <Input.Password />
            </Form.Item>
          ) : null}
          <Form.Item name="departmentId" label="部门" rules={[{ required: true }]}>
            <Select options={deptOptions} showSearch optionFilterProp="label" />
          </Form.Item>
          <Form.Item name="roleId" label="角色" rules={[{ required: true }]}>
            <Select options={roleOptions} />
          </Form.Item>
          <Form.Item name="phone" label="手机号">
            <Input />
          </Form.Item>
          <Form.Item name="email" label="邮箱">
            <Input />
          </Form.Item>
          {editingUser ? (
            <Form.Item name="status" label="状态">
              <Select options={[{ value: 'ACTIVE', label: '启用' }, { value: 'DISABLED', label: '停用' }]} />
            </Form.Item>
          ) : null}
          <Button type="primary" htmlType="submit" block>保存</Button>
        </Form>
      </Modal>
    </Card>
  );
}
