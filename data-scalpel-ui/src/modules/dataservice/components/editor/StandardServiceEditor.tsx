import { Card, Form, Select } from 'antd';
import { useDataModels } from '../../../model';
import { useServiceEngineDataSourceRegistrations } from '../../../serviceengine';
import type { DataServiceFormValues } from '../../model/dataServiceEditor';
import { CommonServiceFields } from './CommonServiceFields';

interface StandardServiceEditorProps {
  form: ReturnType<typeof Form.useForm<DataServiceFormValues>>[0];
  creating: boolean;
  readOnly: boolean;
  canViewDirectories: boolean;
  canViewModels: boolean;
  canViewEngines: boolean;
}

export const StandardServiceEditor = ({
  form,
  creating,
  readOnly,
  canViewDirectories,
  canViewModels,
  canViewEngines,
}: StandardServiceEditorProps) => {
  const selectedModelId = Form.useWatch('modelId', form);
  const modelsQuery = useDataModels(
    { search: 'status:"PUBLISHED"', page: 0, size: 500, sort: 'code' },
    canViewModels,
  );
  const selectedModel = (modelsQuery.data?.content ?? []).find((model) => model.id === selectedModelId);
  const registrationsQuery = useServiceEngineDataSourceRegistrations(
    {
      search: selectedModel?.storageDataSourceId ? `dataSourceId:"${selectedModel.storageDataSourceId}"` : undefined,
      page: 0,
      size: 500,
      sort: 'engineId',
    },
    canViewEngines && Boolean(selectedModel?.storageDataSourceId),
  );

  return (
    <div className="data-service-standard-editor">
      <Card size="small" title="基本信息">
        <CommonServiceFields creating={creating} readOnly={readOnly} canViewDirectories={canViewDirectories} section="identity" includeDescription />
      </Card>
      <Card size="small" title="服务来源">
        <Form.Item<DataServiceFormValues> label="发布模型" name="modelId" rules={[{ required: true, message: '请选择已发布模型' }]}>
          <Select
            showSearch
            optionFilterProp="label"
            disabled={readOnly || !canViewModels}
            loading={modelsQuery.isFetching}
            options={(modelsQuery.data?.content ?? []).map((model) => ({ value: model.id, label: `${model.name}（${model.code}）` }))}
            onChange={() => form.setFieldValue('engineId', undefined)}
          />
        </Form.Item>
      </Card>
      <Card size="small" title="部署配置">
        <Form.Item<DataServiceFormValues>
          label="Service Engine"
          name="engineId"
          extra="仅显示已注册所选模型存储数据源的 Engine。"
          rules={[{ required: true, message: '请选择 Service Engine' }]}
        >
          <Select
            showSearch
            optionFilterProp="label"
            disabled={readOnly || !canViewEngines || !selectedModel?.storageDataSourceId}
            loading={registrationsQuery.isFetching}
            options={(registrationsQuery.data?.content ?? []).map((registration) => ({
              value: registration.engineId,
              label: `${registration.engineName}（${registration.engineCode}） · ${registration.status === 'READY' ? '已就绪' : '待同步'}`,
            }))}
          />
        </Form.Item>
        <CommonServiceFields creating={creating} readOnly={readOnly} canViewDirectories={false} section="routing" includeDescription={false} />
      </Card>
    </div>
  );
};
