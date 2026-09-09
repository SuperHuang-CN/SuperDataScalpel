import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import { ArrowLeftOutlined, ExperimentOutlined, SaveOutlined } from '@ant-design/icons';
import type { FormProps } from 'antd';
import { Button, Form, Modal, Result, Skeleton, Space, Tag, Typography, message } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useBlocker, useLocation, useNavigate, useParams, type BlockerFunction } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { useCurrentUser } from '../../system';
import { ScriptServiceEditor } from '../components/editor/ScriptServiceEditor';
import { SqlServiceEditor } from '../components/editor/SqlServiceEditor';
import { StandardServiceEditor } from '../components/editor/StandardServiceEditor';
import { SpatialServiceEditor } from '../components/editor/SpatialServiceEditor';
import {
  useDataService,
  useTestSqlDataService,
  useUpdateDataServiceDefinition,
} from '../hooks/useDataServices';
import { useDataServiceRelatedModels } from '../hooks/useDataServiceRelatedModels';
import { dataServiceStatusLabels, dataServiceTypeLabels, type SqlServiceTestResponse } from '../model/dataService';
import {
  buildDataServiceDefinitionRequest,
  buildSqlServiceTestRequest,
  dataServiceDefinitionFingerprint,
  dataServiceEditorMode,
  detailToDataServiceFormValues,
  type DataServiceFormValues,
} from '../model/dataServiceEditor';
import './dataServiceEditor.css';

const problemMessage = (error: unknown, fallback: string) => (
  error instanceof ApiError ? error.problem?.detail ?? error.message : fallback
);

interface DefinitionEditorLocationState {
  fromDataServiceList?: boolean;
}

const errorSection = (field: string | number | undefined) => {
  if (field === 'modelId' || field === 'dataSourceId' || field === 'modelIds') return '来源配置';
  if (field === 'sqlText') return 'SQL 模板';
  if (field === 'script') return 'Groovy 脚本';
  if (field === 'examples') return '请求 Example';
  if (field === 'parameters') return '参数定义';
  return '服务定义';
};

export const DataServiceDefinitionEditorPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const locationState = location.state as DefinitionEditorLocationState | null;
  const [form] = Form.useForm<DataServiceFormValues>();
  const watchedValues = Form.useWatch([], form) as DataServiceFormValues | undefined;
  const [baselineFingerprint, setBaselineFingerprint] = useState<string | null>(null);
  const [initializedId, setInitializedId] = useState<string | null>(null);
  const [testValues, setTestValues] = useState<Record<string, string>>({});
  const [testResult, setTestResult] = useState<SqlServiceTestResponse | null>(null);
  const [sqlResultCollapsed, setSqlResultCollapsed] = useState(true);
  const [errorSummary, setErrorSummary] = useState<string[]>([]);
  const [operationError, setOperationError] = useState<string | null>(null);
  const allowNavigationRef = useRef(false);
  const [messageApi, messageContext] = message.useMessage();
  const detailQuery = useDataService(id, Boolean(id));
  const currentUserQuery = useCurrentUser();
  const permissions = new Set(currentUserQuery.data?.permissions ?? []);
  const canUpdate = permissions.has('service.update');
  const canViewModels = permissions.has('model.view');
  const canViewDataSources = permissions.has('datasource.view');
  const canViewEngines = permissions.has('service.engine.view');
  const detail = detailQuery.data;
  const relatedModels = useDataServiceRelatedModels(detail, canViewModels);
  const mode = dataServiceEditorMode(detail);
  const editable = mode === 'EDITABLE' && canUpdate;
  const dependencyUnavailable = !canViewEngines
    || detail?.type === 'STANDARD_TABLE' && !canViewModels
    || detail?.type === 'SQL_QUERY' && (!canViewModels || !canViewDataSources)
    || detail?.type === 'SCRIPT_API' && !canViewDataSources
    || detail?.type === 'SPATIAL_SERVICE' && !canViewModels;
  const canSave = editable && !dependencyUnavailable;
  const canTest = detail?.type === 'SQL_QUERY' && editable && !dependencyUnavailable;
  const canRunScript = detail?.type === 'SCRIPT_API' && editable && !dependencyUnavailable;
  const updateMutation = useUpdateDataServiceDefinition();
  const testMutation = useTestSqlDataService();
  const currentFingerprint = useMemo(
    () => watchedValues ? dataServiceDefinitionFingerprint(watchedValues) : null,
    [watchedValues],
  );
  const dirty = initializedId === id
    && editable
    && baselineFingerprint !== null
    && currentFingerprint !== null
    && currentFingerprint !== baselineFingerprint;
  const blocker = useBlocker(useCallback<BlockerFunction>(
    ({ currentLocation, nextLocation }) => !allowNavigationRef.current && dirty && (
      currentLocation.pathname !== nextLocation.pathname || currentLocation.search !== nextLocation.search
    ),
    [dirty],
  ));

  useEffect(() => {
    if (!id || !detail || initializedId === id) return;
    const values = detailToDataServiceFormValues(detail);
    const initialize = window.setTimeout(() => {
      allowNavigationRef.current = false;
      form.resetFields();
      form.setFieldsValue(values);
      setBaselineFingerprint(dataServiceDefinitionFingerprint(values));
      setInitializedId(id);
      setTestValues({});
      setTestResult(null);
      setSqlResultCollapsed(true);
      setErrorSummary([]);
      setOperationError(null);
    }, 0);
    return () => window.clearTimeout(initialize);
  }, [detail, form, id, initializedId]);

  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => {
      if (!dirty) return;
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);

  useEffect(() => {
    if (!dirty && blocker.state === 'blocked') blocker.reset();
  }, [blocker, dirty]);

  const backToDefinition = () => navigate(id ? `/dataservice/${id}?tab=definition` : '/dataservice', {
    replace: true,
    state: { fromDataServiceList: locationState?.fromDataServiceList },
  });

  const applyValidationErrors = (errorFields: { name: (string | number)[] }[]) => {
    setErrorSummary([...new Set(errorFields.map((item) => errorSection(item.name[0])))]
      .map((section) => `${section}存在未完成或不合法的字段`));
    const firstField = errorFields[0]?.name;
    if (firstField) form.scrollToField(firstField, { focus: true, block: 'center' });
  };

  const save = async (values: DataServiceFormValues) => {
    if (!id) return;
    if (detail?.type === 'SPATIAL_SERVICE'
        && detail.spatialDefinition?.modelId
        && values.modelId !== detail.spatialDefinition.modelId) {
      const confirmed = await new Promise<boolean>((resolve) => Modal.confirm({
        title: '更换空间模型并重置样式？',
        content: '空间模型发生变化后，当前样式草稿会重置为新 Geometry 类型的默认简单样式。',
        okText: '继续保存',
        cancelText: '取消',
        onOk: () => resolve(true),
        onCancel: () => resolve(false),
      }));
      if (!confirmed) return;
    }
    setOperationError(null);
    try {
      const updated = await updateMutation.mutateAsync({ id, request: buildDataServiceDefinitionRequest(values) });
      const savedValues = detailToDataServiceFormValues(updated);
      form.resetFields();
      form.setFieldsValue(savedValues);
      setBaselineFingerprint(dataServiceDefinitionFingerprint(savedValues));
      setErrorSummary([]);
      allowNavigationRef.current = true;
      messageApi.success(`服务定义 v${updated.definitionVersion ?? 1} 已保存`);
      navigate(`/dataservice/${id}?tab=definition`, {
        replace: true,
        state: { fromDataServiceList: locationState?.fromDataServiceList },
      });
    } catch (error) {
      const text = problemMessage(error, '保存服务定义失败');
      setOperationError(text);
      messageApi.error(text);
    }
  };

  const testSql = async () => {
    setOperationError(null);
    try {
      await form.validateFields(['dataSourceId', 'modelIds', 'sqlText', 'parameters']);
      const response = await testMutation.mutateAsync(buildSqlServiceTestRequest(form.getFieldsValue(true), testValues));
      setTestResult(response);
      setSqlResultCollapsed(false);
      if (response.valid) messageApi.success(`SQL 测试通过，耗时 ${response.elapsedMs} ms`);
      else messageApi.warning('SQL 测试未通过，请查看测试结果');
    } catch (error) {
      if (error instanceof ApiError) {
        const text = error.problem?.detail ?? error.message;
        setOperationError(text);
        messageApi.error(text);
      } else if (typeof error === 'object' && error !== null && 'errorFields' in error) {
        applyValidationErrors((error as { errorFields: { name: (string | number)[] }[] }).errorFields);
      }
    }
  };

  if (!id) return <Result status="404" title="数据服务定义地址无效" extra={<Button onClick={() => navigate('/dataservice')}>返回列表</Button>} />;
  if (detailQuery.isLoading || currentUserQuery.isLoading || !detail || initializedId !== id) {
    if (detailQuery.isError) return <Result status="error" title="服务定义加载失败" subTitle={problemMessage(detailQuery.error, '请确认数据服务是否存在')} extra={<Button onClick={backToDefinition}>返回详情</Button>} />;
    return <div className="data-service-editor-loading"><Skeleton active paragraph={{ rows: 10 }} /></div>;
  }

  const onFinishFailed: FormProps<DataServiceFormValues>['onFinishFailed'] = ({ errorFields }) => applyValidationErrors(errorFields);

  return (
    <div className={`data-service-editor-page data-service-definition-editor-page data-service-editor-${detail.type === 'STANDARD_TABLE' ? 'standard' : detail.type === 'SQL_QUERY' ? 'sql' : detail.type === 'SPATIAL_SERVICE' ? 'spatial' : 'script'}`}>
      {messageContext}
      <header className="data-service-editor-header">
        <div className="data-service-editor-heading">
          <Button type="text" icon={<ArrowLeftOutlined />} onClick={backToDefinition}>返回详情</Button>
          <div>
            <Space size={7} wrap>
              <Typography.Title level={4}>{detail.name} · 服务定义</Typography.Title>
              <Tag>{dataServiceTypeLabels[detail.type]}</Tag>
              <Tag>{dataServiceStatusLabels[detail.status]}</Tag>
              <Tag color={detail.definitionConfigured ? 'success' : 'default'}>
                {detail.definitionConfigured ? `定义 v${detail.definitionVersion}` : '定义未配置'}
              </Tag>
              {dirty && <Tag color="processing">有未保存修改</Tag>}
            </Space>
            <Typography.Text type="secondary"><code>{detail.code}</code> · {detail.type === 'SPATIAL_SERVICE'
              ? 'GeoServer Engine 由基础信息维护，图层名由服务编码确定'
              : 'Engine 和服务 Context Path 由基础信息维护'}</Typography.Text>
          </div>
        </div>
        <Space>
          {detail.type === 'SQL_QUERY' && canTest && <Button icon={<ExperimentOutlined />} loading={testMutation.isPending} onClick={() => void testSql()}>测试 SQL</Button>}
          {canSave && <Button type="primary" icon={<SaveOutlined />} loading={updateMutation.isPending} onClick={() => form.submit()}>保存定义</Button>}
        </Space>
      </header>

      <div className="data-service-editor-notices">
        {dependencyUnavailable && <Alert type="warning" showIcon message="缺少维护当前服务定义所需的资源查看权限" />}
        {mode === 'READ_ONLY' && <Alert type="info" showIcon message="服务已启用，请先停用后再修改定义" />}
        {mode === 'DEPLOYMENT_LOCKED' && <Alert type="warning" showIcon message="当前部署状态尚未确认移除，请先清理部署" />}
        {operationError && <Alert type="error" showIcon message="服务定义操作失败" description={operationError} />}
        {errorSummary.length > 0 && <Alert type="error" showIcon message="请修正表单错误" description={<ul>{errorSummary.map((item) => <li key={item}>{item}</li>)}</ul>} />}
      </div>

      <Form<DataServiceFormValues>
        autoComplete="off"
        form={form}
        layout="vertical"
        className={`data-service-editor-form${detail.type === 'STANDARD_TABLE' || detail.type === 'SPATIAL_SERVICE' ? ' data-service-standard-definition-form' : detail.type === 'SQL_QUERY' ? ' data-service-sql-definition-form' : ''}`}
        onFinish={(values) => void save(values)}
        onFinishFailed={onFinishFailed}
        onValuesChange={() => setTestResult(null)}
      >
        <Form.Item name="type" hidden><input /></Form.Item>
        <Form.Item name="engineId" hidden><input /></Form.Item>
        <Form.Item name="contextPath" hidden><input /></Form.Item>
        {detail.type === 'STANDARD_TABLE' ? (
          <StandardServiceEditor
            form={form}
            serviceId={detail.id}
            engineId={detail.engineId}
            readOnly={!editable}
            canViewModels={canViewModels}
            canViewEngines={canViewEngines}
          />
        ) : detail.type === 'SQL_QUERY' ? (
          <SqlServiceEditor
            form={form}
            engineId={detail.engineId}
            readOnly={!editable}
            canTest={canTest}
            canViewModels={canViewModels}
            canViewDataSources={canViewDataSources}
            canViewEngines={canViewEngines}
            testValues={testValues}
            testResult={testResult}
            relatedModels={relatedModels}
            resultCollapsed={sqlResultCollapsed}
            onTestValuesChange={setTestValues}
            onResetTest={() => setTestResult(null)}
            onResultCollapsedChange={setSqlResultCollapsed}
          />
        ) : detail.type === 'SPATIAL_SERVICE' ? (
          <SpatialServiceEditor
            form={form}
            serviceId={detail.id}
            readOnly={!editable}
            canViewModels={canViewModels}
          />
        ) : (
          <ScriptServiceEditor
            form={form}
            engineId={detail.engineId}
            readOnly={!editable}
            canRun={canRunScript}
            canViewDataSources={canViewDataSources}
            canViewEngines={canViewEngines}
          />
        )}
      </Form>

      <Modal
        rootClassName="business-overlay business-modal-overlay"
        open={blocker.state === 'blocked'}
        title="离开未保存的服务定义？"
        okText="离开"
        cancelText="继续编辑"
        onOk={() => { allowNavigationRef.current = true; blocker.proceed?.(); }}
        onCancel={() => blocker.reset?.()}
      >
        当前服务定义尚未保存，离开后这些修改会丢失。
      </Modal>
    </div>
  );
};
