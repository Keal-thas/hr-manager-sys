import React, { useEffect, useState } from 'react';
import {
  Button, Card, Col, Descriptions, Form, Input, InputNumber, Modal, Row, Select, Space,
  Table, Tag, Upload, message
} from 'antd';
import { DownloadOutlined, UploadOutlined } from '@ant-design/icons';
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

const today = new Date();
const defaultYear = today.getMonth() === 0 ? today.getFullYear() - 1 : today.getFullYear();
const defaultMonth = today.getMonth() === 0 ? 12 : today.getMonth();

export default function AdminAttendance() {
  const [batches, setBatches] = useState([]);
  const [batchPage, setBatchPage] = useState(1);
  const [batchSize, setBatchSize] = useState(10);
  const [batchTotal, setBatchTotal] = useState(0);
  const [batchYear, setBatchYear] = useState();
  const [batchMonth, setBatchMonth] = useState();
  const [loading, setLoading] = useState(false);
  const [uploadForm] = Form.useForm();
  const [fileList, setFileList] = useState([]);
  const [uploading, setUploading] = useState(false);

  const [recordsOpen, setRecordsOpen] = useState(false);
  const [recordBatch, setRecordBatch] = useState(null);
  const [records, setRecords] = useState([]);
  const [recordPage, setRecordPage] = useState(1);
  const [recordSize, setRecordSize] = useState(10);
  const [recordTotal, setRecordTotal] = useState(0);
  const [recordStatus, setRecordStatus] = useState();
  const [recordKeyword, setRecordKeyword] = useState('');
  const [recordsLoading, setRecordsLoading] = useState(false);

  const [detail, setDetail] = useState(null);
  const [penaltiesOpen, setPenaltiesOpen] = useState(false);
  const [penaltyBatch, setPenaltyBatch] = useState(null);
  const [penalties, setPenalties] = useState([]);
  const [penaltyPage, setPenaltyPage] = useState(1);
  const [penaltySize, setPenaltySize] = useState(10);
  const [penaltyTotal, setPenaltyTotal] = useState(0);
  const [penaltyFileList, setPenaltyFileList] = useState([]);
  const [penaltyKeyword, setPenaltyKeyword] = useState('');
  const [signRecord, setSignRecord] = useState(null);
  const [signFileList, setSignFileList] = useState([]);

  const loadBatches = async (page = batchPage, size = batchSize, year = batchYear, month = batchMonth) => {
    setLoading(true);
    try {
      const data = await api.get('/admin/attendance/batches', { params: { page, size, year, month } });
      setBatches(data.records || []);
      setBatchTotal(data.total || 0);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadBatches(1, batchSize);
  }, []);

  const submitUpload = async (values) => {
    if (!fileList.length) {
      message.warning('请选择考勤 Excel 文件');
      return;
    }
    const formData = new FormData();
    formData.append('year', values.year);
    formData.append('month', values.month);
    formData.append('mode', values.mode);
    formData.append('file', fileList[0].originFileObj);
    setUploading(true);
    try {
      await api.post('/admin/attendance/upload', formData);
      message.success('上传成功');
      setFileList([]);
      loadBatches(1, batchSize);
    } finally {
      setUploading(false);
    }
  };

  const loadRecords = async (page = recordPage, size = recordSize, status = recordStatus, keyword = recordKeyword, batch = recordBatch) => {
    if (!batch) return;
    setRecordsLoading(true);
    try {
      const data = await api.get(`/admin/attendance/batches/${batch.id}/records`, {
        params: { page, size, status, keyword }
      });
      setRecords(data.records || []);
      setRecordTotal(data.total || 0);
    } finally {
      setRecordsLoading(false);
    }
  };

  const openRecords = (batch) => {
    setRecordBatch(batch);
    setRecordsOpen(true);
    setRecordPage(1);
    setRecordStatus(undefined);
    setRecordKeyword('');
    loadRecords(1, recordSize, undefined, '', batch);
  };

  const openDetail = async (id) => {
    setDetail(await api.get(`/admin/attendance/records/${id}`));
  };

  const remind = async (batch, type) => {
    const result = await api.post(`/admin/attendance/batches/${batch.id}/remind?type=${type}`);
    message.success(`已发送 ${result.count} 条提醒`);
  };

  const loadPenalties = async (batch, page = 1, size = penaltySize, keyword = penaltyKeyword) => {
    const data = await api.get(`/admin/attendance/batches/${batch.id}/penalties`, { params: { page, size, keyword } });
    setPenalties(data.records || []);
    setPenaltyTotal(data.total || 0);
  };

  const openPenalties = (batch) => {
    setPenaltyBatch(batch);
    setPenaltiesOpen(true);
    setPenaltyPage(1);
    setPenaltyKeyword('');
    loadPenalties(batch, 1, penaltySize, '');
  };

  const uploadPenalty = async () => {
    if (!penaltyFileList.length) {
      message.warning('请选择考勤异常 Excel 文件');
      return;
    }
    const formData = new FormData();
    formData.append('file', penaltyFileList[0].originFileObj);
    await api.post(`/admin/attendance/batches/${penaltyBatch.id}/penalties/upload`, formData);
    message.success('异常清单上传成功');
    setPenaltyFileList([]);
    loadPenalties(penaltyBatch, penaltyPage, penaltySize, penaltyKeyword);
  };

  const uploadOfflineSignature = async () => {
    if (!signFileList.length) {
      message.warning('请选择签字文件');
      return;
    }
    const formData = new FormData();
    formData.append('file', signFileList[0].originFileObj);
    await api.post(`/admin/attendance/records/${signRecord.id}/offline-signature`, formData);
    message.success('线下签字已上传');
    setSignRecord(null);
    setSignFileList([]);
    loadRecords(recordPage, recordSize, recordStatus, recordKeyword, recordBatch);
  };

  const download = (path) => window.open(path, '_blank');

  return (
    <div data-testid="admin-attendance-page">
      <Card title="上传考勤">
        <Form
          form={uploadForm}
          layout="inline"
          initialValues={{ year: defaultYear, month: defaultMonth, mode: 'NEW' }}
          onFinish={submitUpload}
        >
          <Form.Item name="year" label="年份" rules={[{ required: true }]}>
            <InputNumber min={2000} max={2100} style={{ width: 110 }} />
          </Form.Item>
          <Form.Item name="month" label="月份" rules={[{ required: true }]}>
            <Select style={{ width: 100 }} options={Array.from({ length: 12 }, (_, i) => ({ value: i + 1, label: `${i + 1} 月` }))} />
          </Form.Item>
          <Form.Item name="mode" label="方式" rules={[{ required: true }]}>
            <Select style={{ width: 130 }} options={[
              { value: 'NEW', label: '新增考勤' },
              { value: 'OVERWRITE', label: '覆盖上传' },
              { value: 'INCREMENTAL', label: '增量/修改' }
            ]} />
          </Form.Item>
          <Form.Item label="文件">
            <Upload
              accept=".xlsx,.xls"
              beforeUpload={() => false}
              fileList={fileList}
              maxCount={1}
              onChange={({ fileList: list }) => setFileList(list)}
            >
              <Button icon={<UploadOutlined />}>选择 Excel</Button>
            </Upload>
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={uploading} data-testid="attendance-upload-submit">上传</Button>
          </Form.Item>
          <Form.Item>
            <Space>
              <Button icon={<DownloadOutlined />} onClick={() => download('/api/admin/attendance/template')}>考勤模板</Button>
              <Button icon={<DownloadOutlined />} onClick={() => download('/api/admin/attendance/penalty-template')}>异常模板</Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      <Card title="考勤批次" style={{ marginTop: 16 }}>
        <Space wrap style={{ marginBottom: 12 }}>
          <InputNumber
            placeholder="年份"
            value={batchYear}
            onChange={setBatchYear}
            style={{ width: 110 }}
          />
          <Select
            placeholder="月份"
            allowClear
            value={batchMonth}
            onChange={setBatchMonth}
            style={{ width: 110 }}
            options={Array.from({ length: 12 }, (_, i) => ({ value: i + 1, label: `${i + 1} 月` }))}
          />
          <Button
            type="primary"
            onClick={() => {
              setBatchPage(1);
              loadBatches(1, batchSize, batchYear, batchMonth);
            }}
          >
            查询
          </Button>
          <Button
            onClick={() => {
              setBatchYear(undefined);
              setBatchMonth(undefined);
              setBatchPage(1);
              loadBatches(1, batchSize, undefined, undefined);
            }}
          >
            重置
          </Button>
        </Space>
        <Table
          rowKey="id"
          loading={loading}
          dataSource={batches}
          pagination={{
            current: batchPage,
            pageSize: batchSize,
            total: batchTotal,
            showSizeChanger: true,
            onChange: (p, s) => {
              setBatchPage(p);
              setBatchSize(s);
              loadBatches(p, s);
            }
          }}
          columns={[
            { title: '年份', dataIndex: 'batchYear', width: 90 },
            { title: '月份', dataIndex: 'batchMonth', width: 90 },
            {
              title: '状态',
              dataIndex: 'status',
              width: 120,
              render: (v, row) => <Space><Tag color="green">{v}</Tag>{row.latest ? <Tag color="blue">最新</Tag> : null}</Space>
            },
            { title: '总人数', dataIndex: 'recordCount', width: 90 },
            { title: '已确认', dataIndex: 'confirmedCount', width: 90 },
            { title: '已阅读', dataIndex: 'readCount', width: 90 },
            { title: '未读', dataIndex: 'unreadCount', width: 80 },
            { title: '上传方式', dataIndex: 'uploadMode', width: 110 },
            { title: '创建时间', dataIndex: 'createdAt', width: 180 },
            {
              title: '操作',
              render: (_, row) => (
                <Space wrap>
                  <Button size="small" onClick={() => openRecords(row)} data-testid="attendance-records-open">明细</Button>
                  <Button size="small" onClick={() => openPenalties(row)}>异常</Button>
                  {row.latest ? (
                    <>
                      <Button size="small" onClick={() => remind(row, 'ATTENDANCE')}>提醒</Button>
                      <Button size="small" danger onClick={() => remind(row, 'ABNORMAL')}>异常提醒</Button>
                    </>
                  ) : null}
                  <Button size="small" onClick={() => download(`/api/admin/attendance/batches/${row.id}/export`)}>导出</Button>
                </Space>
              )
            }
          ]}
        />
      </Card>

      <Modal
        title={recordBatch ? `${recordBatch.batchYear} 年 ${recordBatch.batchMonth} 月考勤明细` : '考勤明细'}
        open={recordsOpen}
        onCancel={() => setRecordsOpen(false)}
        footer={null}
        width={1100}
      >
        <Space style={{ marginBottom: 12 }}>
          <Select
            placeholder="状态"
            allowClear
            style={{ width: 130 }}
            value={recordStatus}
            onChange={(value) => {
              setRecordStatus(value);
              setRecordPage(1);
              loadRecords(1, recordSize, value, recordKeyword);
            }}
            options={[
              { value: 'UNREAD', label: '未阅读' },
              { value: 'READ', label: '已阅读' },
              { value: 'CONFIRMED', label: '已确认' }
            ]}
          />
          <Input.Search
            placeholder="工号 / 姓名"
            allowClear
            onSearch={(value) => {
              setRecordKeyword(value);
              setRecordPage(1);
              loadRecords(1, recordSize, recordStatus, value);
            }}
          />
        </Space>
        <Table
          rowKey="id"
          size="small"
          loading={recordsLoading}
          dataSource={records}
          pagination={{
            current: recordPage,
            pageSize: recordSize,
            total: recordTotal,
            onChange: (p, s) => {
              setRecordPage(p);
              setRecordSize(s);
              loadRecords(p, s, recordStatus, recordKeyword);
            }
          }}
          columns={[
            { title: '工号', dataIndex: 'employeeNo', width: 110 },
            { title: '姓名', dataIndex: 'name', width: 120 },
            { title: '部门', dataIndex: 'departmentName', width: 160 },
            {
              title: '状态',
              dataIndex: 'status',
              width: 110,
              render: (v) => <Tag color={statusColor[v] || 'default'}>{statusText[v] || v}</Tag>
            },
            { title: '确认时间', dataIndex: 'signatureTime', width: 180 },
            {
              title: '操作',
              render: (_, row) => (
                <Space>
                  <Button size="small" onClick={() => openDetail(row.id)}>详情</Button>
                  {recordBatch && !recordBatch.latest && row.status !== 'CONFIRMED' ? (
                    <Button size="small" onClick={() => { setSignRecord(row); setSignFileList([]); }}>
                      上传签字
                    </Button>
                  ) : null}
                </Space>
              )
            }
          ]}
        />
      </Modal>

      <Modal
        title="考勤明细详情"
        open={!!detail}
        onCancel={() => setDetail(null)}
        footer={null}
        width={760}
      >
        {detail ? (
          <>
            <Descriptions column={2} size="small">
              <Descriptions.Item label="工号">{detail.employeeNo}</Descriptions.Item>
              <Descriptions.Item label="姓名">{detail.name}</Descriptions.Item>
              <Descriptions.Item label="部门">{detail.departmentName}</Descriptions.Item>
              <Descriptions.Item label="状态">{statusText[detail.status] || detail.status}</Descriptions.Item>
              <Descriptions.Item label="确认时间">{detail.signatureTime || '-'}</Descriptions.Item>
            </Descriptions>
            <Table
              style={{ marginTop: 12 }}
              size="small"
              pagination={false}
              dataSource={Object.entries(detail.data || {}).map(([key, value]) => ({ key, field: key, value }))}
              columns={[
                { title: '字段', dataIndex: 'field', width: 260 },
                { title: '值', dataIndex: 'value' }
              ]}
            />
            {detail.signatureImage ? (
              <div style={{ marginTop: 12 }}>
                <div className="muted">电子签名</div>
                {detail.signatureImage.startsWith('data:application/pdf') ? (
                  <a href={detail.signatureImage} target="_blank" rel="noreferrer">查看签字PDF</a>
                ) : (
                  <img src={detail.signatureImage} alt="signature" style={{ maxWidth: 420, border: '1px solid #eee' }} />
                )}
              </div>
            ) : null}
          </>
        ) : null}
      </Modal>

      <Modal
        title={penaltyBatch ? `${penaltyBatch.batchYear} 年 ${penaltyBatch.batchMonth} 月异常明细` : '异常明细'}
        open={penaltiesOpen}
        onCancel={() => setPenaltiesOpen(false)}
        footer={null}
        width={900}
      >
        <Space style={{ marginBottom: 12 }}>
          <Upload
            accept=".xlsx,.xls"
            beforeUpload={() => false}
            fileList={penaltyFileList}
            maxCount={1}
            onChange={({ fileList: list }) => setPenaltyFileList(list)}
          >
            <Button icon={<UploadOutlined />}>选择异常清单</Button>
          </Upload>
          <Button type="primary" onClick={uploadPenalty}>上传异常清单</Button>
          <Input.Search
            placeholder="编号 / 工号 / 姓名"
            allowClear
            onSearch={(value) => {
              setPenaltyKeyword(value);
              setPenaltyPage(1);
              loadPenalties(penaltyBatch, 1, penaltySize, value);
            }}
          />
        </Space>
        <Table
          rowKey="id"
          size="small"
          dataSource={penalties}
          pagination={{
            current: penaltyPage,
            pageSize: penaltySize,
            total: penaltyTotal,
            onChange: (p, s) => {
              setPenaltyPage(p);
              setPenaltySize(s);
              loadPenalties(penaltyBatch, p, s);
            }
          }}
          scroll={{ x: 1400 }}
          columns={[
            { title: '编号', dataIndex: 'code', width: 150 },
            { title: '工号', dataIndex: 'employeeNo', width: 110 },
            { title: '姓名', dataIndex: 'name', width: 120 },
            { title: '部门', dataIndex: 'departmentName', width: 160 },
            { title: '导入方式', dataIndex: 'importType', width: 100 },
            { title: '开单日期', width: 120, render: (_, row) => row.data?.['开单日期'] },
            { title: '执行月份', width: 110, render: (_, row) => row.data?.['执行月份'] },
            { title: '惩罚种类', render: (_, row) => row.data?.['惩罚种类'] },
            { title: '处罚等级', render: (_, row) => row.data?.['处罚等级'] },
            { title: '处罚层级', render: (_, row) => row.data?.['处罚层级'] },
            { title: '原因', render: (_, row) => row.data?.['原因'] },
            { title: '绩效奖点数', width: 120, render: (_, row) => row.data?.['绩效奖点数'] }
          ]}
        />
      </Modal>

      <Modal
        title="线下签字上传"
        open={!!signRecord}
        onCancel={() => { setSignRecord(null); setSignFileList([]); }}
        onOk={uploadOfflineSignature}
        okText="上传并确认"
        confirmLoading={false}
      >
        <p className="muted">
          {signRecord ? `${signRecord.employeeNo} ${signRecord.name}` : ''}
        </p>
        <Upload
          accept="image/*,.pdf"
          beforeUpload={() => false}
          fileList={signFileList}
          maxCount={1}
          onChange={({ fileList: list }) => setSignFileList(list)}
        >
          <Button icon={<UploadOutlined />}>选择图片或PDF</Button>
        </Upload>
      </Modal>
    </div>
  );
}
