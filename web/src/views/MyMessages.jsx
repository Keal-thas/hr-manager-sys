import React, { useEffect, useState } from 'react';
import { Button, Card, Input, Modal, Select, Space, Table, Tag, message } from 'antd';
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
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState('');
  const [query, setQuery] = useState({ keyword: '', status: '' });

  const load = async () => {
    setLoading(true);
    try {
      const result = await api.get('/my/messages', {
        params: { page, size, keyword: query.keyword, status: query.status }
      });
      setData(result);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, [page, size, query]);

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
      data-testid="my-messages-page"
      title="消息通知"
      extra={<Button onClick={readAll}>全部已读</Button>}
    >
      <Space wrap style={{ marginBottom: 12 }}>
        <Input.Search
          placeholder="标题检索"
          allowClear
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onSearch={(value) => {
            setPage(1);
            setQuery({ keyword: value, status });
          }}
          style={{ width: 260 }}
          data-testid="message-keyword"
        />
        <Select
          value={status}
          style={{ width: 130 }}
          onChange={(value) => setStatus(value)}
          options={[
            { value: '', label: '全部' },
            { value: 'UNREAD', label: '未阅读' },
            { value: 'READ', label: '已阅读' }
          ]}
          data-testid="message-status"
        />
        <Button
          type="primary"
          onClick={() => {
            setPage(1);
            setQuery({ keyword, status });
          }}
        >
          查询
        </Button>
        <Button
          onClick={() => {
            setKeyword('');
            setStatus('');
            setPage(1);
            setQuery({ keyword: '', status: '' });
          }}
        >
          重置
        </Button>
      </Space>
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
          { title: '序号', width: 70, render: (_, __, index) => (page - 1) * size + index + 1 },
          {
            title: '标题',
            dataIndex: 'title',
            render: (v, row) => <span style={{ fontWeight: row.readFlag ? 400 : 600 }}>{v}</span>
          },
          {
            title: '类型',
            dataIndex: 'type',
            width: 120,
            render: (v) => <Tag color={typeColor[v] || 'default'}>{typeText[v] || v}</Tag>
          },
          {
            title: '发布日期',
            dataIndex: 'createdAt',
            width: 130,
            render: (v) => (v ? String(v).slice(0, 10) : '-')
          },
          {
            title: '状态',
            dataIndex: 'readFlag',
            width: 100,
            render: (v) => (v ? <Tag>已读</Tag> : <Tag color="red">未读</Tag>)
          },
          {
            title: '操作',
            width: 120,
            render: (_, row) => (
              <Button size="small" type="link" onClick={(e) => { e.stopPropagation(); open(row); }}>
                详情/阅读
              </Button>
            )
          }
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
