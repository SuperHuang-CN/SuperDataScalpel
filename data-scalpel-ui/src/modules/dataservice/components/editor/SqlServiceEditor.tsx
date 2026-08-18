import { DownOutlined, UpOutlined } from '@ant-design/icons';
import { Alert, Button, Form, Modal, Select, Splitter, Tag, Typography } from 'antd';
import { useRef, useState } from 'react';
import { MonacoSqlEditor } from '../../../../shared/components/MonacoSqlEditor';
import { useDataSources } from '../../../datasource';
import { useServiceEngineDataSourceRegistrations } from '../../../serviceengine';
import type { DataServiceRelatedModelView } from '../../hooks/useDataServiceRelatedModels';
import type { SqlServiceTestResponse } from '../../model/dataService';
import type { DataServiceFormValues } from '../../model/dataServiceEditor';
import { SqlModelSelection } from './SqlModelSelection';
import { SqlParameterEditor } from './SqlParameterEditor';
import { SqlTestPanel } from './SqlTestPanel';

interface SqlServiceEditorProps {
  form: ReturnType<typeof Form.useForm<DataServiceFormValues>>[0];
  engineId: string;
  readOnly: boolean;
  canTest: boolean;
  canViewModels: boolean;
  canViewDataSources: boolean;
  canViewEngines: boolean;
  testValues: Record<string, string>;
  testResult: SqlServiceTestResponse | null;
  relatedModels: DataServiceRelatedModelView[];
  resultCollapsed: boolean;
  onTestValuesChange: (values: Record<string, string>) => void;
  onResetTest: () => void;
  onResultCollapsedChange: (collapsed: boolean) => void;
}

const SQL_RESULT_PANE_HEIGHT_STORAGE_KEY = 'data-scalpel.sql-result-pane-height';
const SQL_RESULT_PANE_DEFAULT_HEIGHT = 300;
const SQL_RESULT_PANE_MIN_HEIGHT = 180;
const SQL_RESULT_PANE_MAX_SAVED_HEIGHT = 720;
const SQL_RESULT_PANE_COLLAPSED_HEIGHT = 44;

const readResultPaneHeight = (): number => {
  try {
    const preference = window.localStorage.getItem(SQL_RESULT_PANE_HEIGHT_STORAGE_KEY);
    if (preference === null) return SQL_RESULT_PANE_DEFAULT_HEIGHT;
    const stored = Number(preference);
    if (Number.isFinite(stored)) {
      return Math.min(SQL_RESULT_PANE_MAX_SAVED_HEIGHT, Math.max(SQL_RESULT_PANE_MIN_HEIGHT, stored));
    }
  } catch {
    // Local storage may be unavailable in hardened browser environments.
  }
  return SQL_RESULT_PANE_DEFAULT_HEIGHT;
};

const writeResultPaneHeight = (height: number) => {
  try {
    window.localStorage.setItem(SQL_RESULT_PANE_HEIGHT_STORAGE_KEY, String(Math.round(height)));
  } catch {
    // Keep resizing functional even when the preference cannot be persisted.
  }
};

export const SqlServiceEditor = ({
  form,
  engineId,
  readOnly,
  canTest,
  canViewModels,
  canViewDataSources,
  canViewEngines,
  testValues,
  testResult,
  relatedModels,
  resultCollapsed,
  onTestValuesChange,
  onResetTest,
  onResultCollapsedChange,
}: SqlServiceEditorProps) => {
  const selectedDataSourceId = Form.useWatch('dataSourceId', form);
  const selectedModelIds = Form.useWatch('modelIds', form) ?? [];
  const [resultPaneHeight, setResultPaneHeight] = useState(readResultPaneHeight);
  const [modal, modalContext] = Modal.useModal();
  const definitionScrollRef = useRef<HTMLDivElement>(null);
  const querySectionRef = useRef<HTMLDivElement>(null);
  const parameterSectionRef = useRef<HTMLDivElement>(null);
  const dataSourcesQuery = useDataSources(
    { search: 'type:"POSTGRESQL" AND enabled:"true"', page: 0, size: 500, sort: 'code' },
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
  const readyDataSourceIds = new Set((registrationsQuery.data?.content ?? []).map((item) => item.dataSourceId));
  const selectableDataSources = (dataSourcesQuery.data?.content ?? []).filter(
    (dataSource) => readyDataSourceIds.has(dataSource.id) || dataSource.id === selectedDataSourceId,
  );
  const selectedDataSource = selectableDataSources.find((dataSource) => dataSource.id === selectedDataSourceId);
  const applyDataSourceChange = (dataSourceId: string) => {
    form.setFieldsValue({ dataSourceId, modelIds: [] });
    onTestValuesChange({});
    onResetTest();
  };
  const changeDataSource = (dataSourceId: string) => {
    if (dataSourceId === selectedDataSourceId) return;
    if (selectedModelIds.length === 0) {
      applyDataSourceChange(dataSourceId);
      return;
    }
    modal.confirm({
      title: '切换 PostgreSQL 数据源？',
      content: `切换数据源将清空当前关联的 ${selectedModelIds.length} 个模型和 SQL 测试结果。`,
      okText: '切换并清空',
      cancelText: '取消',
      onOk: () => applyDataSourceChange(dataSourceId),
    });
  };
  const scrollTo = (element: HTMLDivElement | null) => {
    if (!element || !definitionScrollRef.current) return;
    definitionScrollRef.current.scrollTo({ top: element.offsetTop, behavior: 'smooth' });
  };
  const resizeResultPane = (sizes: number[]) => {
    const nextHeight = sizes[1];
    if (!resultCollapsed && nextHeight >= SQL_RESULT_PANE_MIN_HEIGHT) {
      setResultPaneHeight(Math.round(nextHeight));
    }
  };
  const finishResultPaneResize = (sizes: number[]) => {
    const nextHeight = sizes[1];
    if (!resultCollapsed && nextHeight >= SQL_RESULT_PANE_MIN_HEIGHT) {
      const normalized = Math.round(nextHeight);
      setResultPaneHeight(normalized);
      writeResultPaneHeight(normalized);
    }
  };
  const resetResultPaneHeight = () => {
    setResultPaneHeight(SQL_RESULT_PANE_DEFAULT_HEIGHT);
    writeResultPaneHeight(SQL_RESULT_PANE_DEFAULT_HEIGHT);
    onResultCollapsedChange(false);
  };
  const resultSummary = !testResult
    ? <Tag>尚未执行 SQL 测试</Tag>
    : testResult.valid
      ? (
        <Tag color="success">
          <span>SQL 测试通过，耗时 {testResult.elapsedMs} ms</span>
          <span> · {testResult.resultFields.length} 列 · {testResult.preview?.resultList.length ?? 0} 行</span>
        </Tag>
      )
      : <Tag color="error">SQL 测试未通过 · {testResult.problems.length} 个问题</Tag>;

  return (
    <div className="data-service-sql-workbench">
      {modalContext}
      <aside className="data-service-sql-sidebar">
        <Typography.Title level={5}>定义配置</Typography.Title>
        {selectedDataSourceId && !registrationsQuery.isFetching && !readyDataSourceIds.has(selectedDataSourceId) && (
          <Alert className="data-service-model-notice" type="warning" showIcon message="当前数据源与所属 Engine 不兼容，请重新选择" />
        )}
        <Form.Item<DataServiceFormValues> name="dataSourceId" hidden rules={[{ required: true, message: '请选择数据源' }]}>
          <Select />
        </Form.Item>
        <Form.Item label="PostgreSQL 数据源" required>
          <Select
            aria-label="PostgreSQL 数据源"
            value={selectedDataSourceId}
            showSearch
            optionFilterProp="label"
            disabled={readOnly || !canViewDataSources}
            loading={dataSourcesQuery.isFetching}
            options={selectableDataSources.map((dataSource) => ({
              value: dataSource.id,
              label: `${dataSource.name}（${dataSource.code}）${readyDataSourceIds.has(dataSource.id) ? '' : ' · 当前 Engine 不可用'}`,
              disabled: !readyDataSourceIds.has(dataSource.id),
            }))}
            onChange={changeDataSource}
          />
        </Form.Item>
        <Form.Item<DataServiceFormValues>
          name="modelIds"
          hidden
          rules={[{ required: true, type: 'array', min: 1, message: '请至少选择一个模型' }]}
        >
          <Select mode="multiple" />
        </Form.Item>
        <SqlModelSelection
          selectedModelIds={selectedModelIds}
          relatedModels={relatedModels}
          dataSourceId={selectedDataSourceId}
          dataSourceName={selectedDataSource?.name ?? relatedModels[0]?.storageDataSourceName ?? undefined}
          dataSourceSelected={Boolean(selectedDataSourceId)}
          readOnly={readOnly || !canViewModels}
          canViewModels={canViewModels}
          onChange={(modelIds) => {
            if (modelIds.length === selectedModelIds.length
              && modelIds.every((modelId, index) => modelId === selectedModelIds[index])) return;
            form.setFieldValue('modelIds', modelIds);
            onResetTest();
          }}
        />
      </aside>

      <main className="data-service-sql-main">
        <Splitter
          orientation="vertical"
          className="data-service-sql-splitter"
          lazy
          onResize={resizeResultPane}
          onResizeEnd={finishResultPaneResize}
          onDraggerDoubleClick={resetResultPaneHeight}
        >
          <Splitter.Panel min={260}>
            <div className="data-service-sql-definition-pane">
              <nav className="data-service-section-nav" aria-label="SQL 服务分区导航">
                <ButtonLink label="查询定义" onClick={() => scrollTo(querySectionRef.current)} />
                <ButtonLink label="参数定义" onClick={() => scrollTo(parameterSectionRef.current)} />
              </nav>
              <div ref={definitionScrollRef} className="data-service-sql-definition-scroll">
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
              </div>
            </div>
          </Splitter.Panel>
          <Splitter.Panel
            size={resultCollapsed ? SQL_RESULT_PANE_COLLAPSED_HEIGHT : resultPaneHeight}
            min={resultCollapsed ? SQL_RESULT_PANE_COLLAPSED_HEIGHT : SQL_RESULT_PANE_MIN_HEIGHT}
            max={resultCollapsed ? SQL_RESULT_PANE_COLLAPSED_HEIGHT : '55%'}
            resizable={!resultCollapsed}
          >
            <section className={`data-service-sql-result-pane${resultCollapsed ? ' data-service-sql-result-pane-collapsed' : ''}`}>
              <div className="data-service-sql-result-header">
                <div className="data-service-sql-result-title">
                  <Typography.Text strong>测试结果</Typography.Text>
                  {resultSummary}
                </div>
                <Button
                  type="text"
                  size="small"
                  icon={resultCollapsed ? <UpOutlined /> : <DownOutlined />}
                  aria-label={resultCollapsed ? '展开测试结果' : '收起测试结果'}
                  aria-expanded={!resultCollapsed}
                  onClick={() => onResultCollapsedChange(!resultCollapsed)}
                >
                  {resultCollapsed ? '展开' : '收起'}
                </Button>
              </div>
              {!resultCollapsed && (
                <div className="data-service-sql-result-body">
                  <SqlTestPanel result={testResult} />
                </div>
              )}
            </section>
          </Splitter.Panel>
        </Splitter>
      </main>
    </div>
  );
};

const ButtonLink = ({ label, onClick }: { label: string; onClick: () => void }) => (
  <Typography.Link onClick={onClick}>{label}</Typography.Link>
);
