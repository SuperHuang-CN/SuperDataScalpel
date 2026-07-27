import { Alert, Form, Select, Space, Tag, Typography } from 'antd';
import { dataModelStatusLabels, type DataModel, type DataModelStatus } from '../../../model';
import type { DataServiceFormValues } from '../../model/dataServiceEditor';

const modelStatusColors: Record<DataModelStatus, string> = {
  DRAFT: 'default', PUBLISHED: 'success', DISABLED: 'warning',
};

interface SqlModelSelectionProps {
  models: DataModel[];
  selectedModelIds: string[];
  dataSourceSelected: boolean;
  loading: boolean;
  readOnly: boolean;
  onChange: () => void;
}

export const SqlModelSelection = ({
  models,
  selectedModelIds,
  dataSourceSelected,
  loading,
  readOnly,
  onChange,
}: SqlModelSelectionProps) => {
  const selectedModels = selectedModelIds
    .map((modelId) => models.find((model) => model.id === modelId))
    .filter((model): model is DataModel => model !== undefined);

  return (
    <>
      <Form.Item<DataServiceFormValues>
        label="关联模型"
        name="modelIds"
        rules={[{ required: true, type: 'array', min: 1, message: '请至少选择一个模型' }]}
      >
        <Select
          mode="multiple"
          showSearch
          optionFilterProp="label"
          maxTagCount="responsive"
          disabled={readOnly || !dataSourceSelected}
          loading={loading}
          placeholder={dataSourceSelected ? '可选择任意状态的模型' : '请先选择数据源'}
          options={models.map((model) => ({
            value: model.id,
            label: `${model.name}（${model.code}） · ${dataModelStatusLabels[model.status]}`,
          }))}
          onChange={onChange}
        />
      </Form.Item>
      <Alert
        type="info"
        showIcon
        className="data-service-model-notice"
        message="关联模型用于来源说明和血缘记录，不是 SQL 访问白名单。"
      />
      {selectedModels.length > 0 && (
        <Space orientation="vertical" size={5} className="data-service-selected-models">
          <Typography.Text strong>已选模型物理位置</Typography.Text>
          {selectedModels.map((model) => (
            <div className="data-service-selected-model" key={model.id}>
              <Space size={5} wrap>
                <Tag color={modelStatusColors[model.status]}>{dataModelStatusLabels[model.status]}</Tag>
                <Typography.Text>{model.name}（{model.code}）</Typography.Text>
              </Space>
              <Typography.Text copyable code>
                {[model.catalogName, model.schemaName, model.physicalTableName].filter(Boolean).join('.')}
              </Typography.Text>
            </div>
          ))}
        </Space>
      )}
    </>
  );
};
