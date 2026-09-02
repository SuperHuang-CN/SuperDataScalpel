import { CompactAlert as Alert } from '../../../../shared/components/ContextualFeedback';
import {
  ScriptWorkbench,
  type ScriptRequestExample,
  type ScriptRunRequest,
} from '@superhuang/super-api-studio-script-workbench';
import '@superhuang/super-api-studio-script-workbench/style.css';
import { Form, Input, Select, Typography } from 'antd';
import { useState } from 'react';
import { useDataSources } from '../../../datasource';
import { useServiceEngineDataSourceRegistrations } from '../../../serviceengine';
import { useExecuteScriptDraft, useScriptCompletion } from '../../hooks/useDataServices';
import {
  defaultScriptRequestExamples,
  scriptRequestExamplesValidationMessage,
  type DataServiceFormValues,
} from '../../model/dataServiceEditor';
import { ResizableScriptWorkbench } from './ResizableScriptWorkbench';
import { ScriptRequestExamplesPanel } from './ScriptRequestExamplesPanel';

interface ScriptServiceEditorProps {
  form: ReturnType<typeof Form.useForm<DataServiceFormValues>>[0];
  engineId: string;
  readOnly: boolean;
  canRun: boolean;
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

const HiddenExamplesField = () => null;

export const ScriptServiceEditor = ({
  form,
  engineId,
  readOnly,
  canRun,
  canViewDataSources,
  canViewEngines,
}: ScriptServiceEditorProps) => {
  const selectedDataSourceId = Form.useWatch('dataSourceId', form);
  const contextPath = Form.useWatch('contextPath', form);
  const script = Form.useWatch('script', form) ?? '';
  const examples = Form.useWatch('examples', form) ?? defaultScriptRequestExamples();
  const [activeExampleId, setActiveExampleId] = useState<string>();
  const dataSourcesQuery = useDataSources(
    { search: 'enabled:"true"', page: 0, size: 500, sort: 'code' },
    canViewDataSources,
  );
  const registrationsQuery = useServiceEngineDataSourceRegistrations(
    {
      search: `engineId:"${engineId}" AND status:"READY"`,
      page: 0,
      size: 500,
      sort: 'dataSourceId',
    },
    canViewEngines && Boolean(engineId),
  );
  const completionQuery = useScriptCompletion(
    engineId,
    selectedDataSourceId,
    canRun && canViewDataSources && canViewEngines,
  );
  const executeMutation = useExecuteScriptDraft();
  const activeExample = examples.find((example) => example.id === activeExampleId) ?? examples[0];

  const run = async (request: ScriptRunRequest) => {
    if (!canRun) {
      throw new Error('当前账号没有脚本调试权限');
    }
    await form.validateFields(['dataSourceId', 'engineId', 'contextPath', 'script']);
    return executeMutation.mutateAsync({
      engineId,
      dataSourceId: selectedDataSourceId ?? '',
      routePath: contextPath ?? '',
      script,
      ...request,
    });
  };

  const readyDataSourceIds = new Set((registrationsQuery.data?.content ?? []).map((item) => item.dataSourceId));
  const dataSources = (dataSourcesQuery.data?.content ?? []).filter((dataSource) => (
    JDBC_TYPES.has(dataSource.type)
    && (readyDataSourceIds.has(dataSource.id) || dataSource.id === selectedDataSourceId)
  ));

  return (
    <div className="data-service-script-workbench">
      <aside className="data-service-script-sidebar">
        <div className="data-service-script-config">
          <Typography.Title level={5}>定义配置</Typography.Title>
          <Typography.Paragraph type="secondary">仅显示已在当前 Service Engine 中就绪的 JDBC 数据源。</Typography.Paragraph>
          {selectedDataSourceId && !registrationsQuery.isFetching && !readyDataSourceIds.has(selectedDataSourceId) && (
            <Alert className="data-service-model-notice" type="warning" showIcon message="当前数据源与所属 Engine 不兼容，请重新选择" />
          )}
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
                label: `${dataSource.name}（${dataSource.code}）${readyDataSourceIds.has(dataSource.id) ? '' : ' · 当前 Engine 不可用'}`,
                disabled: !readyDataSourceIds.has(dataSource.id),
              }))}
            />
          </Form.Item>
          <Typography.Paragraph type="secondary">
            第一版固定使用 Groovy、POST 和单默认数据源。脚本可以使用 API Studio 提供的
            <code> db </code>、<code>log</code>、<code>Assert</code>、<code>Utils</code> 和
            <code>Pager</code>。
          </Typography.Paragraph>
        </div>
        <ScriptRequestExamplesPanel
          examples={examples}
          activeExampleId={activeExample?.id}
          readOnly={readOnly}
          onActiveExampleChange={setActiveExampleId}
          onChange={(value) => form.setFieldValue('examples', value)}
        />
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
        <ResizableScriptWorkbench>
          <ScriptWorkbench
            value={script}
            onChange={readOnly ? () => undefined : (value) => form.setFieldValue('script', value)}
            onRun={run}
            completionData={completionQuery.data}
            examples={activeExample ? [activeExample] : []}
            onExamplesChange={() => undefined}
            readOnly={readOnly}
          />
        </ResizableScriptWorkbench>
      </main>
    </div>
  );
};
