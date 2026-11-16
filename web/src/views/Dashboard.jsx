import React, { useEffect, useState } from 'react';
import { Card, Col, Descriptions, Row, Spin, Statistic, Tag } from 'antd';
import api from '../api';

const statusColor = {
  UNREAD: 'default',
  READ: 'blue',
  CONFIRMED: 'green'
};

export default function Dashboard({ session }) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.get('/dashboard').then(setData).finally(() => setLoading(false));
  }, []);

  if (loading) {
    return <Spin />;
  }
  if (!data) {
    return null;
  }

  return (
    <div>
      <Row gutter={[16, 16]}>
        <Col xs={24} md={6}>
          <Card><Statistic title="有效用户" value={data.userCount} /></Card>
        </Col>
        <Col xs={24} md={6}>
          <Card><Statistic title="部门" value={data.departmentCount} /></Card>
        </Col>
        <Col xs={24} md={6}>
          <Card><Statistic title="角色" value={data.roleCount} /></Card>
        </Col>
        <Col xs={24} md={6}>
          <Card><Statistic title="我的未读消息" value={data.unreadCount} /></Card>
        </Col>
      </Row>

      <Card title="最近一期考勤" style={{ marginTop: 16 }}>
        {data.latestBatch ? (
          <Descriptions column={2} size="small">
            <Descriptions.Item label="月份">
              {data.latestBatch.batchYear} 年 {data.latestBatch.batchMonth} 月
            </Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color="green">{data.latestBatch.status}</Tag>
              {data.latestBatch.latest ? <Tag color="blue">最新</Tag> : null}
            </Descriptions.Item>
            <Descriptions.Item label="总人数">{data.latestBatch.recordCount}</Descriptions.Item>
            <Descriptions.Item label="已确认">
              {data.latestBatch.confirmedCount} / 未读 {data.latestBatch.unreadCount}
            </Descriptions.Item>
          </Descriptions>
        ) : (
          <span className="muted">暂无考勤数据</span>
        )}
      </Card>

      <Card title="我的考勤" style={{ marginTop: 16 }}>
        {data.myAttendance ? (
          <Descriptions column={2} size="small">
            <Descriptions.Item label="月份">
              {data.myAttendance.batchYear} 年 {data.myAttendance.batchMonth} 月
            </Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color={statusColor[data.myAttendance.status] || 'default'}>{data.myAttendance.status}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="姓名">{data.myAttendance.name}</Descriptions.Item>
            <Descriptions.Item label="部门">{data.myAttendance.departmentName}</Descriptions.Item>
          </Descriptions>
        ) : (
          <span className="muted">暂无我的考勤</span>
        )}
      </Card>

      <div className="muted" style={{ marginTop: 16 }}>
        当前登录: {session.user.name}（{session.user.employeeNo}）
      </div>
    </div>
  );
}
