import { SafetyCertificateOutlined } from '@ant-design/icons';
import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import { OverlayTitle } from '../../../shared/components/OverlayTitle';
import { Button, Empty, Modal, Space, Table, Typography } from 'antd';
import { useState } from 'react';
import {
  modelQualityRuleSeverityLabels,
  modelQualityRuleTypeLabels,
  type ModelQualityRuleSuggestion,
} from '../model/modelQualityRule';

interface ModelQualityRuleSuggestionsModalProps {
  open: boolean;
  suggestions: ModelQualityRuleSuggestion[];
  loading: boolean;
  submitting: boolean;
  onClose: () => void;
  onAccept: (suggestions: ModelQualityRuleSuggestion[]) => void;
}

export const ModelQualityRuleSuggestionsModal = ({
  open,
  suggestions,
  loading,
  submitting,
  onClose,
  onAccept,
}: ModelQualityRuleSuggestionsModalProps) => {
  const [selectedKeys, setSelectedKeys] = useState<string[] | null>(null);

  const effectiveSelectedKeys = selectedKeys === null
    ? suggestions.map((item) => item.key)
    : selectedKeys;
  const selected = suggestions.filter((item) => effectiveSelectedKeys.includes(item.key));
  return (
    <Modal
      rootClassName="business-overlay business-modal-overlay workspace-resource-overlay model-detail-overlay model-quality-overlay"
      title={<OverlayTitle title="模型质量规则建议" icon={<SafetyCertificateOutlined />} />}
      width={820}
      open={open}
      afterClose={() => setSelectedKeys(null)}
      onCancel={() => { setSelectedKeys(null); onClose(); }}
      footer={(
        <div className="quality-suggestion-footer"><Typography.Text type="secondary">已选 {selected.length} / {suggestions.length} 条</Typography.Text><Space>
          <Button onClick={() => { setSelectedKeys(null); onClose(); }}>取消</Button>
          <Button type="primary" disabled={!selected.length} loading={submitting} onClick={() => onAccept(selected)}>
            采纳 {selected.length} 条建议
          </Button>
        </Space></div>
      )}
    >
      <Alert showIcon type="info" title="建议基于当前模型字段生成，采纳后统一保存为停用状态，请检查参数后逐条启用。" />
      {suggestions.length === 0 && !loading ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前没有新的规则建议" /> : (
        <Table<ModelQualityRuleSuggestion>
          className="management-table quality-suggestion-table"
          size="small"
          loading={loading}
          rowKey="key"
          pagination={false}
          scroll={{ x: 720, y: 380 }}
          tableLayout="fixed"
          rowSelection={{ selectedRowKeys: effectiveSelectedKeys, onChange: (keys) => setSelectedKeys(keys.map(String)) }}
          columns={[
            {
              title: '建议规则', dataIndex: 'name', width: 210,
              render: (value, item) => <Space orientation="vertical" size={1}><Typography.Text strong>{value}</Typography.Text><Typography.Text type="secondary">{modelQualityRuleTypeLabels[item.ruleType]}</Typography.Text></Space>,
            },
            { title: '检查对象', width: 190, render: (_, item) => item.fields.map((field) => field.code).join(' + ') || '模型行数' },
            { title: '严重程度', width: 90, render: (_, item) => modelQualityRuleSeverityLabels[item.severity] },
            { title: '建议原因', dataIndex: 'reason' },
          ]}
        />
      )}
    </Modal>
  );
};
