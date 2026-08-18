import { CopyOutlined } from '@ant-design/icons';
import { Alert, Button, Descriptions, Input, Modal, Space, Table, message } from 'antd';
import type { ApiResourceTestResult } from '../model/dataSource';

export const ApiResourceTestResultModal = ({ result, resourceName, onClose }: {
  result: ApiResourceTestResult;
  resourceName: string;
  onClose: () => void;
}) => {
  const [messageApi, contextHolder] = message.useMessage();
  const copy = async () => {
    const diagnostic = result.diagnostic;
    const content = [
      `API 资源: ${resourceName}`, `结果: ${result.message}`, `错误码: ${result.code}`,
      `HTTP Status: ${result.httpStatus ?? diagnostic?.httpStatus ?? '—'}`, `耗时: ${result.elapsedMs} ms`,
      `异常类型: ${diagnostic?.exceptionType ?? '—'}`, `原始错误: ${diagnostic?.rawMessage ?? '—'}`,
      diagnostic?.responsePreview ? `响应预览: ${diagnostic.responsePreview}` : undefined,
      ...(diagnostic?.causes ?? []).map((cause, index) => `Cause ${index + 1}: ${cause.exceptionType}: ${cause.message ?? '—'}`),
    ].filter(Boolean).join('\n');
    try {
      await navigator.clipboard.writeText(content);
      messageApi.success('诊断信息已复制');
    } catch {
      messageApi.error('复制失败，请手动选择');
    }
  };
  const rows = result.rows.map((row, index) => ({ key: index, values: row }));

  return <>
    {contextHolder}
    <Modal
      rootClassName="business-overlay business-modal-overlay"
      open
      width={900}
      title={`API 资源测试 · ${resourceName}`}
      destroyOnHidden
      onCancel={onClose}
      footer={<Space>{!result.success && <Button icon={<CopyOutlined />} onClick={() => void copy()}>复制诊断</Button>}<Button type="primary" onClick={onClose}>关闭</Button></Space>}
    >
      <Alert
        showIcon
        type={result.success ? 'success' : 'error'}
        title={result.message}
        description={`${result.code} · HTTP ${result.httpStatus ?? '—'} · ${result.elapsedMs} ms · ${result.recordCount} 条记录`}
      />
      <Descriptions bordered size="small" column={2} style={{ marginTop: 12 }}>
        <Descriptions.Item label="Content-Type">{result.contentType ?? '—'}</Descriptions.Item>
        <Descriptions.Item label="返回字段">{result.columns.join(', ') || '—'}</Descriptions.Item>
        {result.diagnostic && <Descriptions.Item label="异常类型" span={2}>{result.diagnostic.exceptionType}</Descriptions.Item>}
      </Descriptions>
      {result.success && result.columns.length > 0 && (
        <Table
          size="small"
          pagination={false}
          scroll={{ x: 'max-content', y: 280 }}
          dataSource={rows}
          columns={result.columns.map((column, columnIndex) => ({
            title: column,
            key: `${column}-${columnIndex}`,
            width: 160,
            ellipsis: true,
            render: (_: unknown, row: { values: unknown[] }) => {
              const value = row.values[columnIndex];
              return value === null || value === undefined
                ? '—'
                : typeof value === 'object' ? JSON.stringify(value) : String(value);
            },
          }))}
        />
      )}
      {result.diagnostic && <>
        <div className="data-source-form-section-title">原始错误</div>
        <Input.TextArea readOnly value={result.diagnostic.rawMessage ?? '—'} autoSize={{ minRows: 2, maxRows: 6 }} />
        {result.diagnostic.responsePreview && <>
          <div className="data-source-form-section-title">响应预览（已脱敏）</div>
          <Input.TextArea readOnly value={result.diagnostic.responsePreview} autoSize={{ minRows: 3, maxRows: 10 }} />
        </>}
        <div className="data-source-form-section-title">异常链</div>
        <Input.TextArea
          readOnly
          value={result.diagnostic.causes.map((cause, index) => `${index + 1}. ${cause.exceptionType}: ${cause.message ?? '—'}`).join('\n') || '无更多下层异常'}
          autoSize={{ minRows: 2, maxRows: 8 }}
        />
      </>}
    </Modal>
  </>;
};
