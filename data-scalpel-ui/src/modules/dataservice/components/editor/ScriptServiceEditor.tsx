import {
  ScriptWorkbench,
  type ScriptRequestExample,
  type ScriptRunRequest,
} from '@superhuang/super-api-studio-script-workbench';
import '@superhuang/super-api-studio-script-workbench/style.css';
import { Alert, Form, Input, Select, Typography } from 'antd';
import { useDataSources } from '../../../datasource';
import { useServiceEngineDataSourceRegistrations } from '../../../serviceengine';
import { useExecuteScriptDraft, useScriptCompletion } from '../../hooks/useDataServices';
import {
  defaultScriptRequestExamples,
  scriptRequestExamplesValidationMessage,
  type DataServiceFormValues,
} from '../../model/dataServiceEditor';
import { CommonServiceFields } from './CommonServiceFields';

interface ScriptServiceEditorProps {
  form: ReturnType<typeof Form.useForm<DataServiceFormValues>>[0];
  creating: boolean;
  readOnly: boolean;
  canRun: boolean;
  canViewDirectories: boolean;
  canViewDataSources: boolean;
  canViewEngines: boolean;
}

const JDBC_TYPES = new Set([
  'MYSQL',
  'POSTGRESQL',
  'ORACLE',
  'SQL_SERVER',
  'CLICKHOUSE',
  'DAMENG',
  'KINGBASE',
  'OPENGAUSS',
]);

const HiddenExamplesField = (_props: {
  value?: ScriptRequestExample[];
  onChange?: (value: ScriptRequestExample[]) => void;
}) => null;

export const ScriptServiceEditor = ({
  form,
  creating,
  readOnly,
  canRun,
  canViewDirectories,
  canViewDataSources,
  canViewEngines,
}: ScriptServiceEditorProps) => {
  const selectedDataSourceId = Form.useWatch('dataSourceId', form);
  const selectedEngineId = Form.useWatch('engineId', form);
  const routePath = Form.useWatch('routePath', form);
  const script = Form.useWatch('script', form) ?? '';
  const examples = Form.useWatch('examples', form) ?? defaultScriptRequestExamples();
  const dataSourcesQuery = useDataSources(
    { search: 'enabled:"true"', page: 0, size: 500, sort: 'code' },
    canViewDataSources,
  );
  const registrationsQuery = useServiceEngineDataSourceRegistrations(
    {
      search: selectedDataSourceId ? `dataSourceId:"${selectedDataSourceId}"` : undefined,
      page: 0,
      size: 500,
      sort: 'engineId',
    },
    canViewEngines && Boolean(selectedDataSourceId),
  );
  const completionQuery = useScriptCompletion(
    selectedEngineId,
    selectedDataSourceId,
    canRun && canViewDataSources && canViewEngines,
  );
  const executeMutation = useExecuteScriptDraft();

  const run = async (request: ScriptRunRequest) => {
    if (!canRun) {
      throw new Error('当前账号没有脚本调试权限');
    }
    await form.validateFields(['dataSourceId', 'engineId', 'routePath', 'script']);
    return executeMutation.mutateAsync({
      engineId: selectedEngineId ?? '',
      dataSourceId: selectedDataSourceId ?? '',
      routePath: routePath ?? '',
      script,
      ...request,
    });
  };

  const dataSources = (dataSourcesQuery.data?.content ?? [])
    .filter((dataSource) => JDBC_TYPES.has(dataSource.type));

  return (
    <div className="data-service-script-workbench">
      <aside className="data-service-script-sidebar">
        <Typography.Title level={5}>服务配置</Typography.Title>
        <CommonServiceFields
          creating={creating}
          readOnly={readOnly}
          canViewDirectories={canViewDirectories}
          section="identity"
        />
        <Form.Item<DataServiceFormValues>
          label="默认 JDBC 数据源"
          name="dataSourceId"
          rules={[{ required: true, message: '请选择默认数据源' }]}
        >
          <Select
            showSearch
            optionFilterProp="label"
            disabled={readOnly || !canViewDataSources}
            loading={dataSourcesQuery.isFetching}
            options={dataSources.map((dataSource) => ({
              value: dataSource.id,
              label: `${dataSource.name}（${dataSource.code}）`,
            }))}
            onChange={() => form.setFieldValue('engineId', undefined)}
          />
        </Form.Item>
        <Form.Item<DataServiceFormValues>
          label="Service Engine"
          name="engineId"
          extra="仅显示已注册默认数据源的 Engine。"
          rules={[{ required: true, message: '请选择 Service Engine' }]}
        >
          <Select
            showSearch
            optionFilterProp="label"
            disabled={readOnly || !canViewEngines || !selectedDataSourceId}
            loading={registrationsQuery.isFetching}
            options={(registrationsQuery.data?.content ?? []).map((registration) => ({
              value: registration.engineId,
              label: `${registration.engineName}（${registration.engineCode}） · ${registration.status === 'READY' ? '已就绪' : '待同步'}`,
            }))}
          />
        </Form.Item>
        <CommonServiceFields
          creating={creating}
          readOnly={readOnly}
          canViewDirectories={false}
          section="routing"
        />
        <Typography.Paragraph type="secondary">
          第一版固定使用 Groovy、POST 和单默认数据源。脚本可以使用 API Studio 提供的
          <code> db </code>、<code>log</code>、<code>Assert</code>、<code>Utils</code> 和
          <code>Pager</code>。
        </Typography.Paragraph>
      </aside>

      <main className="data-service-script-main">
        {completionQuery.isError ? (
          <Alert
            type="warning"
            showIcon
            message="脚本补全数据加载失败"
            description="仍可继续编辑和调试脚本。"
          />
        ) : null}
        <Form.Item<DataServiceFormValues>
          name="script"
          hidden
          rules={[
            { required: true, whitespace: true, message: '请输入 Groovy 脚本' },
            { max: 500000, message: '脚本长度不能超过 500000 字符' },
          ]}
        >
          <Input.TextArea />
        </Form.Item>
        <Form.Item<DataServiceFormValues>
          name="examples"
          hidden
          rules={[{
            validator: (_, value: ScriptRequestExample[] | undefined) => {
              const message = scriptRequestExamplesValidationMessage(value);
              return message ? Promise.reject(new Error(message)) : Promise.resolve();
            },
          }]}
        >
          <HiddenExamplesField />
        </Form.Item>
        <ScriptWorkbench
          value={script}
          onChange={readOnly ? () => undefined : (value) => form.setFieldValue('script', value)}
          onRun={run}
          completionData={completionQuery.data}
          examples={examples}
          onExamplesChange={readOnly ? () => undefined : (value) => form.setFieldValue('examples', value)}
          readOnly={readOnly}
        />
      </main>
    </div>
  );
};
