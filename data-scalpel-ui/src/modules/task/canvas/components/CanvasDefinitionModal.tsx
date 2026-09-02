import { CompactAlert as Alert } from '../../../../shared/components/ContextualFeedback';
import { CopyOutlined, DownloadOutlined } from '@ant-design/icons';
import { Button, Input, List, Modal, Space, Statistic, Tag, message } from 'antd';
import { downloadCanvasDefinition, formatCanvasDefinition } from '../canvasDefinitionIO';
import { canvasCompilationIssues } from '../canvasCompilationPresentation';
import type { CanvasDefinition, CanvasValidationResult } from '../canvasTypes';

interface CanvasDefinitionModalProps {
  open: boolean;
  definition: CanvasDefinition;
  validation: CanvasValidationResult | null;
  validationStatus: string;
  onClose: () => void;
}

export const CanvasDefinitionModal = ({
  open,
  definition,
  validation,
  validationStatus,
  onClose,
}: CanvasDefinitionModalProps) => {
  const content = formatCanvasDefinition(definition);
  const issues = canvasCompilationIssues(validation);
  const errors = issues.filter((item) => item.severity === 'ERROR');
  const warnings = issues.filter((item) => item.severity === 'WARNING');

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(content);
      message.success('Canvas 定义已复制');
    } catch {
      message.error('复制失败，请手动选择 JSON 内容');
    }
  };

  return (
    <Modal
      open={open}
      title="Canvas 任务配置定义"
      width={920}
      onCancel={onClose}
      footer={(
        <Space>
          <Button icon={<CopyOutlined />} onClick={() => void copy()}>复制 JSON</Button>
          <Button icon={<DownloadOutlined />} onClick={() => downloadCanvasDefinition(definition)}>下载 JSON</Button>
          <Button type="primary" onClick={onClose}>关闭</Button>
        </Space>
      )}
    >
      <div className="canvas-definition-summary">
        <Statistic title="节点" value={definition.nodes.length} />
        <Statistic title="连线" value={definition.edges.length} />
        <Statistic title="错误" value={validation ? errors.length : '—'} styles={{ content: { color: errors.length > 0 ? '#cf1322' : undefined } }} />
        <Statistic title="警告" value={validation ? warnings.length : '—'} styles={{ content: { color: warnings.length > 0 ? '#d48806' : undefined } }} />
        {validation ? (
          <Tag color={validation.valid ? 'success' : 'error'}>
            {validation.valid ? 'Engine 校验有效' : 'Engine 校验无效，可继续导出'}
          </Tag>
        ) : <Tag>尚未完成 Engine 校验</Tag>}
      </div>
      {!validation && (
        <Alert
          showIcon
          type="info"
          title="当前定义尚无 Task Engine 校验结果"
          description={validationStatus}
          className="canvas-definition-validation-pending"
        />
      )}
      {issues.length > 0 && (
        <List
          size="small"
          className="canvas-definition-issues"
          dataSource={issues}
          renderItem={(item) => (
            <List.Item>
              <Tag color={item.severity === 'ERROR' ? 'error' : 'warning'}>{item.code}</Tag>
              {item.nodeId ? `[${definition.nodes.find((node) => node.id === item.nodeId)?.name ?? item.nodeId}] ` : ''}
              {item.message}
            </List.Item>
          )}
        />
      )}
      <Input.TextArea
        readOnly
        value={content}
        aria-label="Canvas 定义 JSON"
        className="canvas-definition-json"
        autoSize={{ minRows: 18, maxRows: 28 }}
      />
    </Modal>
  );
};
