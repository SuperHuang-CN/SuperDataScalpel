import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import { CopyOutlined } from '@ant-design/icons';
import { Button, Descriptions, Input, Modal, Space, message } from 'antd';
import type { ConnectionTestResult } from '../model/dataSource';

interface ConnectionTestResultModalProps {
  open: boolean;
  result: ConnectionTestResult;
  targetLabel?: string;
  onClose: () => void;
}

const formatConnectionTestDiagnostic = (
  result: ConnectionTestResult,
  targetLabel?: string,
): string => {
  const diagnostic = result.diagnostic;
  return [
    '连接测试失败',
    targetLabel ? `目标: ${targetLabel}` : undefined,
    `结论: ${result.message}`,
    `错误码: ${result.code}`,
    `耗时: ${result.elapsedMs} ms`,
    `异常类型: ${diagnostic?.exceptionType ?? '—'}`,
    `SQLState: ${diagnostic?.sqlState ?? '—'}`,
    `Vendor Code: ${diagnostic?.vendorCode ?? '—'}`,
    `HTTP Status: ${diagnostic?.httpStatus ?? '—'}`,
    `原始错误: ${diagnostic?.rawMessage ?? '—'}`,
    diagnostic?.responsePreview ? `响应预览: ${diagnostic.responsePreview}` : undefined,
    ...(diagnostic?.causes ?? []).map((cause, index) => (
      `Cause ${index + 1}: ${cause.exceptionType}: ${cause.message ?? '—'}`
    )),
  ].filter((line): line is string => Boolean(line)).join('\n');
};

export const ConnectionTestResultModal = ({
  open,
  result,
  targetLabel,
  onClose,
}: ConnectionTestResultModalProps) => {
  const [messageApi, messageContext] = message.useMessage();
  const diagnostic = result.diagnostic;
  const causeText = (diagnostic?.causes ?? []).map((cause, index) => (
    `${index + 1}. ${cause.exceptionType}: ${cause.message ?? '—'}`
  )).join('\n');

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(formatConnectionTestDiagnostic(result, targetLabel));
      messageApi.success('连接诊断信息已复制');
    } catch {
      messageApi.error('复制失败，请手动选择诊断信息');
    }
  };

  return (
    <>
      {messageContext}
      <Modal
        rootClassName="business-overlay business-modal-overlay"
        open={open}
        title="连接测试失败"
        width={760}
        onCancel={onClose}
        destroyOnHidden
        footer={(
          <Space>
            <Button icon={<CopyOutlined />} onClick={() => void copy()}>复制诊断信息</Button>
            <Button type="primary" onClick={onClose}>关闭</Button>
          </Space>
        )}
      >
        <Alert
          showIcon
          type="error"
          title={result.message}
          description={`错误码 ${result.code} · 耗时 ${result.elapsedMs} ms`}
        />
        <Descriptions size="small" bordered column={1} style={{ marginTop: 12 }}>
          {targetLabel && <Descriptions.Item label="连接目标">{targetLabel}</Descriptions.Item>}
          <Descriptions.Item label="异常类型">{diagnostic?.exceptionType ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="SQLState">{diagnostic?.sqlState ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Vendor Code">{diagnostic?.vendorCode ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="HTTP Status">{diagnostic?.httpStatus ?? '—'}</Descriptions.Item>
        </Descriptions>
        {diagnostic ? (
          <>
            <div className="data-source-form-section-title">原始错误</div>
            <Input.TextArea
              readOnly
              aria-label="原始错误"
              value={diagnostic.rawMessage ?? '—'}
              autoSize={{ minRows: 3, maxRows: 8 }}
            />
            {diagnostic.responsePreview && (
              <>
                <div className="data-source-form-section-title">响应预览（已脱敏）</div>
                <Input.TextArea
                  readOnly
                  aria-label="响应预览"
                  value={diagnostic.responsePreview}
                  autoSize={{ minRows: 3, maxRows: 10 }}
                />
              </>
            )}
            <div className="data-source-form-section-title">异常链</div>
            <Input.TextArea
              readOnly
              aria-label="异常链"
              value={causeText || '无更多下层异常'}
              autoSize={{ minRows: 3, maxRows: 10 }}
            />
          </>
        ) : (
          <Alert
            showIcon
            type="warning"
            title="服务端未返回技术诊断信息"
            style={{ marginTop: 12 }}
          />
        )}
      </Modal>
    </>
  );
};
