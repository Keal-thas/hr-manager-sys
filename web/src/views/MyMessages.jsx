import React, { useEffect, useState } from 'react';
import { Button, Card, Modal, Space, Table, Tag, message } from 'antd';
import api from '../api';

const typeText = {
  ATTENDANCE: '考勤通知',
  ATTENDANCE_REMIND: '考勤提醒',
  ATTENDANCE_ABNORMAL: '考勤异常',
  NOTICE: '系统通知'
};

const typeColor = {
  ATTENDANCE: 'blue',
  ATTENDANCE_REMIND: 'orange',
  ATTENDANCE_ABNORMAL: 'red',
  NOTICE: 'green'
};

export default function MyMessages() {
  const [data, setData] = useState({ records: [], total: 0 });
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState(null);

  const load = async () => {
    setLoading(true);
    try {
      const result = await api.get('/my/messages', { params: { page, size } });
      setData(result);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, [page, size]);

  const open = async (record) => {
    setSelected(record);
    if (!record.readFlag) {
      await api.post(`/my/messages/${record.id}/read`);
      load();
    }
  };

  const readAll = async () => {
    await api.post('/my/messages/read-all');
    message.success('已全部标记为已读');
    load();
  };

  return (
    <Card
      title="消息通知"
      extra={<Button onClick={readAll}>全部已读</Button>}
    >
      <Table
        rowKey="id"
        loading={loading}
        dataSource={data.records}
        onRow={(record) => ({ onClick: () => open(record), style: { cursor: 'pointer' } })}
        pagination={{
          current: page,
          pageSize: size,
          total: data.total,
          showSizeChanger: true,
          onChange: (p, s) => {
            setPage(p);
            setSize(s);
          }
        }}
        columns={[
          {
            title: '类型',
            dataIndex: 'type',
            width: 130,
            render: (v) => <Tag color={typeColor[v] || 'default'}>{typeText[v] || v}</Tag>
          },
          { title: '标题', dataIndex: 'title' },
          {
            title: '状态',
            dataIndex: 'readFlag',
            width: 100,
            render: (v) => (v ? <Tag>已读</Tag> : <Tag color="red">未读</Tag>)
          },
          { title: '时间', dataIndex: 'createdAt', width: 180 }
        ]}
      />
      <Modal
        title={selected?.title}
        open={!!selected}
        onCancel={() => setSelected(null)}
        footer={<Button onClick={() => setSelected(null)}>关闭</Button>}
        width={640}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Tag color={typeColor[selected?.type] || 'default'}>{typeText[selected?.type] || selected?.type}</Tag>
          <div style={{ whiteSpace: 'pre-wrap', lineHeight: 1.8 }}>{selected?.content}</div>
          <div className="muted">{selected?.createdAt}</div>
        </Space>
      </Modal>
    </Card>
  );
}
