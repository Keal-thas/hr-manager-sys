import React, { useEffect, useRef, useState } from 'react';
import {
  Alert, Button, Card, Checkbox, Descriptions, Empty, InputNumber, Modal, Select, Space, Table, Tag, message
} from 'antd';
import api from '../api';

const statusColor = {
  UNREAD: 'default',
  READ: 'blue',
  CONFIRMED: 'green'
};

const statusText = {
  UNREAD: '未阅读',
  READ: '已阅读',
  CONFIRMED: '已确认'
};

const now = new Date();

export default function MyAttendance() {
  const year = now.getFullYear();
  const month = now.getMonth() + 1;
  const [current, setCurrent] = useState(null);
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [open, setOpen] = useState(false);
  const [agreed, setAgreed] = useState(false);
  const [saving, setSaving] = useState(false);
  const [hasInk, setHasInk] = useState(false);
  const canvasRef = useRef(null);
  const drawing = useRef(false);

  const load = async (y = year, m = month) => {
    setLoading(true);
    try {
      const [cur, hist] = await Promise.all([
        api.get('/my/attendance/current', { params: { year: y, month: m } }),
        api.get('/my/attendance/history?page=1&size=10')
      ]);
      setCurrent(cur);
      setHistory(hist.records || []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load(year, month);
  }, []);

  const dataRows = current?.data
    ? Object.entries(current.data).map(([key, value]) => ({ key, field: key, value }))
    : [];

  const openSign = async () => {
    if (current && current.status !== 'CONFIRMED') {
      const detail = await api.get(`/my/attendance/${current.batchId}`);
      setCurrent(detail);
    }
    setAgreed(false);
    setHasInk(false);
    setOpen(true);
    setTimeout(() => clearCanvas(), 50);
  };

  const clearCanvas = () => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    setHasInk(false);
  };

  const point = (event) => {
    const canvas = canvasRef.current;
    const rect = canvas.getBoundingClientRect();
    const source = event.touches ? event.touches[0] : event;
    return {
      x: (source.clientX - rect.left) * (canvas.width / rect.width),
      y: (source.clientY - rect.top) * (canvas.height / rect.height)
    };
  };

  const start = (event) => {
    event.preventDefault();
    const canvas = canvasRef.current;
    const ctx = canvas.getContext('2d');
    const { x, y } = point(event);
    drawing.current = true;
    setHasInk(true);
    ctx.strokeStyle = '#111827';
    ctx.lineWidth = 2.5;
    ctx.lineCap = 'round';
    ctx.beginPath();
    ctx.moveTo(x, y);
  };

  const move = (event) => {
    if (!drawing.current) return;
    event.preventDefault();
    const canvas = canvasRef.current;
    const ctx = canvas.getContext('2d');
    const { x, y } = point(event);
    ctx.lineTo(x, y);
    ctx.stroke();
  };

  const end = () => {
    drawing.current = false;
  };

  const submitSign = async () => {
    if (!agreed) {
      message.warning('请先勾选同意考勤内容');
      return;
    }
    if (!hasInk) {
      message.warning('请先签名');
      return;
    }
    const signatureImage = canvasRef.current.toDataURL('image/png');
    setSaving(true);
    try {
      await api.post(`/my/attendance/${current.batchId}/confirm`, { signatureImage, agree: true });
      message.success('考勤确认成功');
      setOpen(false);
      await load();
    } finally {
      setSaving(false);
    }
  };

  const penaltyRows = (current?.penalties || []).map((item) => ({
    ...item,
    ...(item.data || {}),
    key: item.id
  }));

  return (
    <div data-testid="my-attendance-page">
      <Card size="small" style={{ marginBottom: 16 }}>
        <Space wrap>
          <span>考勤年份</span>
          <InputNumber value={year} disabled style={{ width: 100 }} />
          <span>考勤月份</span>
          <Select
            value={month}
            disabled
            style={{ width: 100 }}
            options={Array.from({ length: 12 }, (_, i) => ({ value: i + 1, label: `${i + 1} 月` }))}
          />
          <Button type="primary" onClick={() => load(year, month)} data-testid="attendance-query">
            查询
          </Button>
        </Space>
      </Card>
      {current ? (
        <Card
          title={`${current.batchYear} 年 ${current.batchMonth} 月考勤`}
          extra={<Tag color={statusColor[current.status] || 'default'}>{statusText[current.status] || current.status}</Tag>}
        >
          <Descriptions column={3} size="small" style={{ marginBottom: 16 }}>
            <Descriptions.Item label="姓名">{current.name}</Descriptions.Item>
            <Descriptions.Item label="工号">{current.employeeNo}</Descriptions.Item>
            <Descriptions.Item label="部门">{current.departmentName}</Descriptions.Item>
          </Descriptions>
          {current.status === 'CONFIRMED' ? (
            <Alert type="success" showIcon message={`已确认，签名时间 ${current.signatureTime || ''}`} />
          ) : (
            <Space>
              <Button type="primary" onClick={openSign} data-testid="attendance-sign-open">阅读并签字确认</Button>
              <span className="muted">确认前请核对考勤明细</span>
            </Space>
          )}
          <Table
            style={{ marginTop: 16 }}
            size="small"
            pagination={false}
            dataSource={dataRows}
            columns={[
              { title: '字段', dataIndex: 'field', width: 260 },
              { title: '值', dataIndex: 'value' }
            ]}
          />
          {penaltyRows.length ? (
            <>
              <div className="muted" style={{ marginTop: 16, marginBottom: 8 }}>处罚明细</div>
              <Table
                size="small"
                pagination={false}
                dataSource={penaltyRows}
                scroll={{ x: 1200 }}
                columns={[
                  { title: '编号', dataIndex: 'code', width: 150 },
                  { title: '开单日期', dataIndex: '开单日期', width: 120 },
                  { title: '执行月份', dataIndex: '执行月份', width: 110 },
                  { title: '惩罚种类', dataIndex: '惩罚种类' },
                  { title: '处罚等级', dataIndex: '处罚等级' },
                  { title: '处罚层级', dataIndex: '处罚层级' },
                  { title: '原因', dataIndex: '原因' },
                  { title: '绩效奖点数', dataIndex: '绩效奖点数', width: 120 }
                ]}
              />
            </>
          ) : null}
        </Card>
      ) : (
        <Empty description="暂无可查询考勤" />
      )}

      <Card title="历史考勤" style={{ marginTop: 16 }}>
        <Table
          rowKey="id"
          size="small"
          dataSource={history}
          pagination={false}
          columns={[
            { title: '年份', dataIndex: 'batchYear', width: 90 },
            { title: '月份', dataIndex: 'batchMonth', width: 90 },
            { title: '状态', dataIndex: 'status', width: 120, render: (v) => <Tag color={statusColor[v] || 'default'}>{statusText[v] || v}</Tag> },
            { title: '确认时间', dataIndex: 'signatureTime' }
          ]}
        />
      </Card>

      <Modal
        title="考勤签字确认"
        open={open}
        onCancel={() => setOpen(false)}
        onOk={submitSign}
        confirmLoading={saving}
        okText="提交确认"
        width={680}
        okButtonProps={{ 'data-testid': 'attendance-sign-submit' }}
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 12 }}
          message="本人承诺已认真阅读本表中的内容，明白并认可其中的法律含义"
        />
        <Checkbox checked={agreed} onChange={(e) => setAgreed(e.target.checked)} data-testid="attendance-agree">
          我已阅读并同意考勤内容
        </Checkbox>
        <div style={{ marginTop: 12, marginBottom: 8 }}>请在下方签名：</div>
        <canvas
          ref={canvasRef}
          width={620}
          height={180}
          className="signature-canvas"
          data-testid="signature-canvas"
          onMouseDown={start}
          onMouseMove={move}
          onMouseUp={end}
          onMouseLeave={end}
          onTouchStart={start}
          onTouchMove={move}
          onTouchEnd={end}
        />
        <Button size="small" style={{ marginTop: 8 }} onClick={clearCanvas}>清除签名</Button>
      </Modal>
    </div>
  );
}
