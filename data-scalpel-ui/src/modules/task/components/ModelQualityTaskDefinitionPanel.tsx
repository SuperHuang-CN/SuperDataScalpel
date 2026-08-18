import { SaveOutlined } from '@ant-design/icons';
import { Alert, Button, Descriptions, Empty, InputNumber, Select, Space, Spin, Table, Tag, Typography, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useDataModels } from '../../model';
import { ApiError } from '../../../shared/api/http';
import { useModelQualityTaskDefinition, useUpdateModelQualityTaskDefinition } from '../hooks/useTasks';
import type { DataTask } from '../model/task';

interface ModelQualityTaskDefinitionPanelProps {
  task: DataTask;
  canUpdate: boolean;
}

export const ModelQualityTaskDefinitionPanel = ({
  task,
  canUpdate,
}: ModelQualityTaskDefinitionPanelProps) => {
  const [messageApi, messageContext] = message.useMessage();
  const [modelId, setModelId] = useState<string>();
  const [failureSampleLimit, setFailureSampleLimit] = useState(100);
  const definitionQuery = useModelQualityTaskDefinition(task.id);
  const modelsQuery = useDataModels({ page: 0, size: 500, sort: 'code' });
  const updateMutation = useUpdateModelQualityTaskDefinition();
  const definition = definitionQuery.data;
  const editable = canUpdate && task.status !== 'PUBLISHED';

  useEffect(() => {
    setModelId(definition?.targetModel?.modelId);
    setFailureSampleLimit(definition?.failureSampleLimit ?? 100);
  }, [definition?.targetModel?.modelId, definition?.failureSampleLimit]);

  const options = useMemo(() => {
    const items = (modelsQuery.data?.content ?? []).map((model) => ({
      value: model.id,
      label: `${model.name} · ${model.code} · ${model.status}`,
    }));
    if (definition?.targetModel && !items.some((item) => item.value === definition.targetModel?.modelId)) {
      items.push({
        value: definition.targetModel.modelId,
        label: `${definition.targetModel.modelName} · ${definition.targetModel.modelCode}`,
      });
    }
    return items;
  }, [definition?.targetModel, modelsQuery.data?.content]);

  const save = async () => {
    if (!modelId) return;
    try {
      await updateMutation.mutateAsync({ id: task.id, modelId, failureSampleLimit });
      messageApi.success('质检目标模型已保存');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '保存质检定义失败');
    }
  };

  if (definitionQuery.isPending) return <Spin tip="正在加载质检定义…" />;
  if (definitionQuery.isError) return (
    <Alert type="error" showIcon message="质检定义加载失败"
      action={<Button size="small" onClick={() => void definitionQuery.refetch()}>重试</Button>} />
  );

  return (
    <div className="task-detail-tab-panel">
      {messageContext}
      <div className="task-detail-tab-toolbar">
        <div>
          <Typography.Text strong>质检定义</Typography.Text>
          <Typography.Paragraph type="secondary" className="task-definition-help">
            每次运行都会检查该模型当时全部启用且有效的质量规则；规则修改不会影响已经排队或运行的实例。
          </Typography.Paragraph>
        </div>
        <div>
          <Typography.Text strong>每条失败规则样本数</Typography.Text>
          <div>
            <InputNumber
              min={0}
              max={1000}
              precision={0}
              value={failureSampleLimit}
              disabled={!editable}
              onChange={(value) => setFailureSampleLimit(value ?? 0)}
            />
          </div>
          <Typography.Text type="secondary">
            0表示关闭；样本可能包含业务数据，仅保存主键和规则相关字段，最多1000条。
          </Typography.Text>
        </div>
        {editable && (
          <Button type="primary" icon={<SaveOutlined />} disabled={!modelId}
            loading={updateMutation.isPending} onClick={() => void save()}>
            保存
          </Button>
        )}
      </div>
      <Space orientation="vertical" size={16} className="task-definition-form">
        <div>
          <Typography.Text strong>目标模型</Typography.Text>
          <Select
            showSearch
            optionFilterProp="label"
            value={modelId}
            disabled={!editable}
            loading={modelsQuery.isFetching}
            placeholder="请选择要完整质检的模型"
            options={options}
            onChange={setModelId}
            className="task-quality-model-select"
          />
        </div>
        {!definition?.configured ? <Empty description="尚未配置质检目标模型" /> : (
          <>
            <Descriptions bordered size="small" column={3}>
              <Descriptions.Item label="模型">{definition.targetModel?.modelName}</Descriptions.Item>
              <Descriptions.Item label="模型编码"><Typography.Text code>{definition.targetModel?.modelCode}</Typography.Text></Descriptions.Item>
              <Descriptions.Item label="模型结构版本">v{definition.targetModel?.schemaVersion}</Descriptions.Item>
              <Descriptions.Item label="可执行规则"><Tag color="success">{definition.executableRuleCount}</Tag></Descriptions.Item>
              <Descriptions.Item label="跳过规则"><Tag color={definition.skippedRuleCount ? 'warning' : 'default'}>{definition.skippedRuleCount}</Tag></Descriptions.Item>
              <Descriptions.Item label="定义序号">v{definition.version}</Descriptions.Item>
              <Descriptions.Item label="失败样本">{definition.failureSampleLimit === 0 ? '关闭' : `每条最多 ${definition.failureSampleLimit} 条`}</Descriptions.Item>
            </Descriptions>
            {definition.skippedRuleCount > 0 && (
              <Table
                size="small"
                rowKey="ruleId"
                pagination={false}
                dataSource={definition.skippedRules}
                columns={[
                  { title: '跳过规则', dataIndex: 'ruleName' },
                  { title: '原因', dataIndex: 'reason' },
                ]}
              />
            )}
          </>
        )}
        {task.status === 'PUBLISHED' && (
          <Alert type="info" showIcon message="已发布任务需先停用，才能更换目标模型。" />
        )}
      </Space>
    </div>
  );
};
