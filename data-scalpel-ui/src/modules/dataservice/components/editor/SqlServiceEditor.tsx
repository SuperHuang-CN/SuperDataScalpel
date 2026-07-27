import { Form, Select, Typography } from 'antd';
import { useRef } from 'react';
import { MonacoSqlEditor } from '../../../../shared/components/MonacoSqlEditor';
import { useDataSources } from '../../../datasource';
import { useDataModels } from '../../../model';
import { useServiceEngineDataSourceRegistrations } from '../../../serviceengine';
import type { SqlServiceTestResponse } from '../../model/dataService';
import type { DataServiceFormValues } from '../../model/dataServiceEditor';
import { CommonServiceFields } from './CommonServiceFields';
import { SqlModelSelection } from './SqlModelSelection';
import { SqlParameterEditor } from './SqlParameterEditor';
import { SqlTestPanel } from './SqlTestPanel';

interface SqlServiceEditorProps {
  form: ReturnType<typeof Form.useForm<DataServiceFormValues>>[0];
  creating: boolean;
  readOnly: boolean;
  canTest: boolean;
  canViewDirectories: boolean;
  canViewModels: boolean;
  canViewDataSources: boolean;
  canViewEngines: boolean;
  testValues: Record<string, string>;
  testResult: SqlServiceTestResponse | null;
  onTestValuesChange: (values: Record<string, string>) => void;
  onResetTest: () => void;
}

export const SqlServiceEditor = ({
  form,
  creating,
  readOnly,
  canTest,
  canViewDirectories,
  canViewModels,
  canViewDataSources,
  canViewEngines,
  testValues,
  testResult,
  onTestValuesChange,
  onResetTest,
}: SqlServiceEditorProps) => {
  const selectedDataSourceId = Form.useWatch('dataSourceId', form);
  const selectedModelIds = Form.useWatch('modelIds', form) ?? [];
  const querySectionRef = useRef<HTMLDivElement>(null);
  const parameterSectionRef = useRef<HTMLDivElement>(null);
  const resultSectionRef = useRef<HTMLDivElement>(null);
  const dataSourcesQuery = useDataSources(
    { search: 'type:"POSTGRESQL" AND enabled:"true"', page: 0, size: 500, sort: 'code' },
    canViewDataSources,
  );
  const modelsQuery = useDataModels(
    {
      search: selectedDataSourceId ? `storageDataSourceId:"${selectedDataSourceId}"` : undefined,
      page: 0,
      size: 500,
      sort: 'code',
    },
    canViewModels && Boolean(selectedDataSourceId),
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
  const scrollTo = (element: HTMLDivElement | null) => element?.scrollIntoView({ behavior: 'smooth', block: 'start' });

  return (
    <div className="data-service-sql-workbench">
      <aside className="data-service-sql-sidebar">
        <Typography.Title level={5}>服务配置</Typography.Title>
        <CommonServiceFields creating={creating} readOnly={readOnly} canViewDirectories={canViewDirectories} section="identity" />
        <Form.Item<DataServiceFormValues> label="PostgreSQL 数据源" name="dataSourceId" rules={[{ required: true, message: '请选择数据源' }]}>
          <Select
            showSearch
            optionFilterProp="label"
            disabled={readOnly || !canViewDataSources}
            loading={dataSourcesQuery.isFetching}
            options={(dataSourcesQuery.data?.content ?? []).map((dataSource) => ({
              value: dataSource.id, label: `${dataSource.name}（${dataSource.code}）`,
            }))}
            onChange={() => {
              form.setFieldsValue({ modelIds: [], engineId: undefined });
              onTestValuesChange({});
              onResetTest();
            }}
          />
        </Form.Item>
        <SqlModelSelection
          models={modelsQuery.data?.content ?? []}
          selectedModelIds={selectedModelIds}
          dataSourceSelected={Boolean(selectedDataSourceId)}
          loading={modelsQuery.isFetching}
          readOnly={readOnly || !canViewModels}
          onChange={onResetTest}
        />
        <Form.Item<DataServiceFormValues>
          label="Service Engine"
          name="engineId"
          extra="仅显示已注册所选数据源的 Engine。"
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
        <CommonServiceFields creating={creating} readOnly={readOnly} canViewDirectories={false} section="routing" />
      </aside>

      <main className="data-service-sql-main">
        <nav className="data-service-section-nav" aria-label="SQL 服务分区导航">
          <ButtonLink label="查询定义" onClick={() => scrollTo(querySectionRef.current)} />
          <ButtonLink label="参数定义" onClick={() => scrollTo(parameterSectionRef.current)} />
          <ButtonLink label="测试结果" onClick={() => scrollTo(resultSectionRef.current)} />
        </nav>
        <section ref={querySectionRef} className="data-service-editor-section">
          <Typography.Title level={5}>查询定义</Typography.Title>
          <Typography.Paragraph type="secondary">仅支持一条只读 SELECT 或 WITH ... SELECT；参数使用 <code>:name</code>。</Typography.Paragraph>
          <Form.Item<DataServiceFormValues> name="sqlText" rules={[{ required: true, whitespace: true, message: '请输入只读 SQL' }, { max: 100000 }]}>
            <MonacoSqlEditor height={360} readOnly={readOnly} onChange={onResetTest} />
          </Form.Item>
        </section>
        <section ref={parameterSectionRef} className="data-service-editor-section">
          <Typography.Title level={5}>参数定义</Typography.Title>
          <SqlParameterEditor
            form={form}
            readOnly={readOnly}
            canTest={canTest}
            testValues={testValues}
            onTestValuesChange={onTestValuesChange}
          />
        </section>
        <section ref={resultSectionRef} className="data-service-editor-section">
          <Typography.Title level={5}>测试结果</Typography.Title>
          <SqlTestPanel result={testResult} />
        </section>
      </main>
    </div>
  );
};

const ButtonLink = ({ label, onClick }: { label: string; onClick: () => void }) => (
  <Typography.Link onClick={onClick}>{label}</Typography.Link>
);
