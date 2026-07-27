import {
  ArrowLeftOutlined,
  ClearOutlined,
  CopyOutlined,
  ExperimentOutlined,
  PlayCircleOutlined,
  SaveOutlined,
  StopOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import type { FormProps } from 'antd';
import { Alert, Button, Form, Modal, Result, Skeleton, Space, Tag, Typography, message } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  useBlocker,
  useLocation,
  useNavigate,
  useParams,
  type BlockerFunction,
} from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { useCurrentUser } from '../../system';
import { gatewayProviderLabels } from '../model/apiConsumer';
import { StandardServiceEditor } from '../components/editor/StandardServiceEditor';
import { SqlServiceEditor } from '../components/editor/SqlServiceEditor';
import {
  useCleanupDataServiceDeployment,
  useCreateDataService,
  useDataService,
  useDisableDataService,
  useEnableDataService,
  usePublishDataService,
  useTestSqlDataService,
  useUpdateDataService,
} from '../hooks/useDataServices';
import {
  dataServiceDeploymentStatusLabels,
  dataServiceStatusLabels,
  dataServiceTypeLabels,
  gatewayServicePublicationStatusLabels,
  type DataServiceDeploymentStatus,
  type DataServiceDetail,
  type DataServiceStatus,
  type DataServiceType,
  type GatewayServicePublicationStatus,
  type SqlServiceTestResponse,
} from '../model/dataService';
import { buildDataServiceCurlCommand } from '../model/dataServiceCurl';
import { gatewayOperationError, publishedGatewayBinding } from '../model/dataServiceGateway';
import {
  buildDataServiceCreateRequest,
  buildDataServiceUpdateRequest,
  buildSqlServiceTestRequest,
  dataServiceEditorMode,
  dataServiceFormFingerprint,
  detailToDataServiceFormValues,
  initialDataServiceFormValues,
  type DataServiceFormValues,
} from '../model/dataServiceEditor';
import './dataServiceEditor.css';

const serviceStatusColors: Record<DataServiceStatus, string> = {
  DRAFT: 'default', ENABLED: 'success', DISABLED: 'warning',
};
const deploymentStatusColors: Record<DataServiceDeploymentStatus, string> = {
  PENDING: 'processing', DEPLOYED: 'success', FAILED: 'error', REMOVING: 'processing', REMOVED: 'default',
};
const gatewayStatusColors: Record<GatewayServicePublicationStatus, string> = {
  PUBLISHING: 'processing',
  PUBLISHED: 'success',
  PUBLISH_FAILED: 'error',
  REMOVING: 'processing',
  REMOVE_FAILED: 'error',
};

const routeServiceType = (pathname: string): DataServiceType | undefined => {
  if (pathname === '/dataservice/new/standard') return 'STANDARD_TABLE';
  if (pathname === '/dataservice/new/sql') return 'SQL_QUERY';
  return undefined;
};

const problemMessage = (error: unknown, fallback: string) => (
  error instanceof ApiError ? error.problem?.detail ?? error.message : fallback
);

const errorSection = (field: string | number | undefined) => {
  if (field === 'modelId' || field === 'dataSourceId' || field === 'modelIds' || field === 'engineId') return '来源与 Engine';
  if (field === 'sqlText') return 'SQL 模板';
  if (field === 'parameters') return '参数定义';
  return '基本信息';
};

export const DataServiceEditorPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { id } = useParams();
  const creatingType = routeServiceType(location.pathname);
  const creating = Boolean(creatingType);
  const initializationKey = creatingType ? `new:${creatingType}` : `detail:${id ?? ''}`;
  const fromList = Boolean((location.state as { fromDataServiceList?: boolean } | null)?.fromDataServiceList);
  const [form] = Form.useForm<DataServiceFormValues>();
  const watchedValues = Form.useWatch([], form) as DataServiceFormValues | undefined;
  const [baselineFingerprint, setBaselineFingerprint] = useState<string | null>(null);
  const [initializedKey, setInitializedKey] = useState<string | null>(null);
  const [testValues, setTestValues] = useState<Record<string, string>>({});
  const [testResult, setTestResult] = useState<SqlServiceTestResponse | null>(null);
  const [errorSummary, setErrorSummary] = useState<string[]>([]);
  const [operationError, setOperationError] = useState<string | null>(null);
  const allowNavigationRef = useRef(false);
  const [messageApi, messageContext] = message.useMessage();
  const currentUserQuery = useCurrentUser();
  const permissions = new Set(currentUserQuery.data?.permissions ?? []);
  const canCreate = permissions.has('service.create');
  const canUpdate = permissions.has('service.update');
  const canPublish = permissions.has('service.publish');
  const canViewDirectories = permissions.has('directory.view');
  const canViewModels = permissions.has('model.view');
  const canViewDataSources = permissions.has('datasource.view');
  const canViewEngines = permissions.has('service.engine.view');
  const detailQuery = useDataService(id, !creating && Boolean(id));
  const detail = detailQuery.data;
  const serviceType = creatingType ?? detail?.type;
  const mode = creating ? 'CREATE' : dataServiceEditorMode(detail);
  const definitionEditable = mode === 'CREATE' || mode === 'EDITABLE';
  const definitionReadOnly = !definitionEditable || (!creating && !canUpdate);
  const dependencyUnavailable = !canViewEngines
    || serviceType === 'STANDARD_TABLE' && !canViewModels
    || serviceType === 'SQL_QUERY' && (!canViewDataSources || !canViewModels);
  const canSave = definitionEditable && (creating ? canCreate : canUpdate) && !dependencyUnavailable;
  const canTest = serviceType === 'SQL_QUERY'
    && (creating ? canCreate : canUpdate)
    && !dependencyUnavailable;
  const createMutation = useCreateDataService();
  const updateMutation = useUpdateDataService();
  const testMutation = useTestSqlDataService();
  const enableMutation = useEnableDataService();
  const publishMutation = usePublishDataService();
  const disableMutation = useDisableDataService();
  const cleanupMutation = useCleanupDataServiceDeployment();
  const currentFingerprint = useMemo(
    () => watchedValues ? dataServiceFormFingerprint(watchedValues) : null,
    [watchedValues],
  );
  const dirty = initializedKey === initializationKey
    && definitionEditable
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
    if (initializedKey === initializationKey) return;
    const initialValues = creatingType
      ? initialDataServiceFormValues(creatingType)
      : detail ? detailToDataServiceFormValues(detail) : undefined;
    if (!initialValues) return;
    const initialize = window.setTimeout(() => {
      allowNavigationRef.current = false;
      form.resetFields();
      form.setFieldsValue(initialValues);
      setBaselineFingerprint(dataServiceFormFingerprint(initialValues));
      setInitializedKey(initializationKey);
      setTestValues({});
      setTestResult(null);
      setErrorSummary([]);
    }, 0);
    return () => window.clearTimeout(initialize);
  }, [creatingType, detail, form, initializationKey, initializedKey]);

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

  const resetBaseline = (savedDetail: DataServiceDetail) => {
    const savedValues = detailToDataServiceFormValues(savedDetail);
    form.resetFields();
    form.setFieldsValue(savedValues);
    setBaselineFingerprint(dataServiceFormFingerprint(savedValues));
    setErrorSummary([]);
    setOperationError(null);
  };

  const save = async (values: DataServiceFormValues) => {
    setOperationError(null);
    try {
      if (creating) {
        const created = await createMutation.mutateAsync(buildDataServiceCreateRequest(values));
        resetBaseline(created);
        allowNavigationRef.current = true;
        messageApi.success('数据服务草稿已创建');
        navigate(`/dataservice/${created.id}`, {
          replace: true,
          state: { fromDataServiceList: fromList },
        });
        return;
      }
      if (!id) return;
      const updated = await updateMutation.mutateAsync({ id, request: buildDataServiceUpdateRequest(values) });
      resetBaseline(updated);
      messageApi.success('数据服务已保存');
    } catch (error) {
      const errorMessage = problemMessage(error, '保存数据服务失败');
      setOperationError(errorMessage);
      messageApi.error(errorMessage);
    }
  };

  const applyValidationErrors = (errorFields: { name: (string | number)[] }[]) => {
    const sections = [...new Set(errorFields.map((error) => errorSection(error.name[0])))]
      .map((section) => `${section}存在未完成或不合法的字段`);
    setErrorSummary(sections);
    const firstField = errorFields[0]?.name;
    if (firstField) form.scrollToField(firstField, { focus: true, block: 'center' });
  };

  const onFinishFailed: FormProps<DataServiceFormValues>['onFinishFailed'] = ({ errorFields }) => {
    applyValidationErrors(errorFields);
  };

  const testSql = async () => {
    setOperationError(null);
    try {
      await form.validateFields(['dataSourceId', 'modelIds', 'sqlText', 'parameters']);
      const response = await testMutation.mutateAsync(buildSqlServiceTestRequest(form.getFieldsValue(true), testValues));
      setTestResult(response);
      if (response.valid) messageApi.success(`SQL 测试通过，耗时 ${response.elapsedMs} ms`);
      else messageApi.warning('SQL 测试未通过，请查看测试结果');
    } catch (error) {
      if (error instanceof ApiError) {
        const errorMessage = error.problem?.detail ?? error.message;
        setOperationError(errorMessage);
        messageApi.error(errorMessage);
        return;
      }
      if (typeof error === 'object' && error !== null && 'errorFields' in error) {
        applyValidationErrors((error as { errorFields: { name: (string | number)[] }[] }).errorFields);
      }
    }
  };

  const refreshDetail = async () => {
    await detailQuery.refetch();
  };

  const enable = async () => {
    if (!id || !detail) return;
    if (dirty) {
      messageApi.warning('请先保存当前修改，再启用服务');
      return;
    }
    setOperationError(null);
    try {
      const response = await enableMutation.mutateAsync(id);
      if (response.status === 'ENABLED' && response.deploymentStatus === 'DEPLOYED') {
        messageApi.success(detail.status === 'DISABLED' ? '数据服务已重新启用' : '数据服务已启用');
      } else {
        setOperationError(response.deploymentError || 'Engine 未确认启用，请查看部署状态');
        messageApi.error(response.deploymentError || 'Engine 未确认启用，请查看部署状态');
      }
      await refreshDetail();
    } catch (error) {
      const errorMessage = problemMessage(error, '启用数据服务失败');
      setOperationError(errorMessage);
      messageApi.error(errorMessage);
      await refreshDetail();
    }
  };

  const publish = async () => {
    if (!id) return;
    setOperationError(null);
    try {
      const response = await publishMutation.mutateAsync(id);
      const binding = publishedGatewayBinding(response);
      if (binding) {
        messageApi.success(`数据服务已发布到 ${gatewayProviderLabels[binding.provider]}`);
      } else {
        const errorMessage = gatewayOperationError(response) || '网关未确认发布结果';
        setOperationError(errorMessage);
        messageApi.error(errorMessage);
      }
      await refreshDetail();
    } catch (error) {
      const errorMessage = problemMessage(error, '发布到网关失败');
      setOperationError(errorMessage);
      messageApi.error(errorMessage);
      await refreshDetail();
    }
  };

  const disable = async () => {
    if (!id) return;
    setOperationError(null);
    try {
      const response = await disableMutation.mutateAsync(id);
      if (response.status === 'DISABLED' && response.deploymentStatus === 'REMOVED') {
        messageApi.success('数据服务已停用');
      } else {
        setOperationError(gatewayOperationError(response) || response.deploymentError || '服务未确认停用结果');
      }
      await refreshDetail();
    } catch (error) {
      const errorMessage = problemMessage(error, '停用数据服务失败');
      setOperationError(errorMessage);
      messageApi.error(errorMessage);
      await refreshDetail();
    }
  };

  const cleanup = async () => {
    if (!id) return;
    setOperationError(null);
    try {
      const response = await cleanupMutation.mutateAsync(id);
      if (response.deploymentStatus === 'REMOVED') messageApi.success('失败或不确定的部署已清理');
      else setOperationError(response.deploymentError || 'Engine 未确认清理结果');
      await refreshDetail();
    } catch (error) {
      const errorMessage = problemMessage(error, '清理失败部署失败');
      setOperationError(errorMessage);
      messageApi.error(errorMessage);
      await refreshDetail();
    }
  };

  const copyCurl = async () => {
    if (!detail) return;
    const binding = publishedGatewayBinding(detail);
    if (!binding?.gatewayUrl) {
      messageApi.error('当前版本尚未成功发布到网关');
      return;
    }
    if (!navigator.clipboard) {
      messageApi.error('当前浏览器不支持自动复制，请使用 HTTPS 或 localhost 访问');
      return;
    }
    try {
      await navigator.clipboard.writeText(buildDataServiceCurlCommand(binding.gatewayUrl, '', detail));
      messageApi.success('访问 cURL 已复制');
    } catch {
      messageApi.error('复制失败，请检查浏览器的剪贴板权限');
    }
  };

  const goBack = () => {
    if (fromList) navigate(-1);
    else navigate('/dataservice');
  };

  if (!creating && detailQuery.isLoading) {
    return <div className="data-service-editor-loading"><Skeleton active paragraph={{ rows: 8 }} /></div>;
  }
  if (!creating && detailQuery.isError) {
    const notFound = detailQuery.error instanceof ApiError && detailQuery.error.status === 404;
    return <Result status={notFound ? '404' : 'error'} title={notFound ? '数据服务不存在' : '数据服务详情加载失败'} subTitle={problemMessage(detailQuery.error, '请稍后重试')} extra={<Button onClick={goBack}>返回数据服务</Button>} />;
  }
  if (!creating && !detail) {
    return <Result status="404" title="数据服务不存在或无权查看" extra={<Button onClick={goBack}>返回数据服务</Button>} />;
  }
  if (!serviceType || initializedKey !== initializationKey) {
    return <div className="data-service-editor-loading"><Skeleton active paragraph={{ rows: 8 }} /></div>;
  }

  const lockedForRetry = detail?.deploymentStatus === 'PENDING' || detail?.deploymentStatus === 'FAILED';
  const currentGatewayBinding = detail ? publishedGatewayBinding(detail) : undefined;
  const gatewayError = detail ? gatewayOperationError(detail) : undefined;
  const pageTitle = creating
    ? `新建${dataServiceTypeLabels[serviceType]}服务`
    : detail?.name ?? '服务详情';

  return (
    <div className={`data-service-editor-page data-service-editor-${serviceType === 'SQL_QUERY' ? 'sql' : 'standard'}`}>
      {messageContext}
      <header className="data-service-editor-header">
        <div className="data-service-editor-heading">
          <Button type="text" icon={<ArrowLeftOutlined />} onClick={goBack}>返回列表</Button>
          <div>
            <Space size={7} wrap>
              <Typography.Title level={4}>{pageTitle}</Typography.Title>
              <Tag>{dataServiceTypeLabels[serviceType]}</Tag>
              {detail && <Tag color={serviceStatusColors[detail.status]}>{dataServiceStatusLabels[detail.status]}</Tag>}
              {detail?.deploymentStatus && <Tag color={deploymentStatusColors[detail.deploymentStatus]}>{dataServiceDeploymentStatusLabels[detail.deploymentStatus]}</Tag>}
              {detail?.gatewayBindings.map((binding) => (
                <Tag key={binding.id} color={gatewayStatusColors[binding.publicationStatus]}>
                  {gatewayProviderLabels[binding.provider]} · {gatewayServicePublicationStatusLabels[binding.publicationStatus]}
                </Tag>
              ))}
              {dirty && <Tag color="processing">有未保存修改</Tag>}
            </Space>
            {detail && <Typography.Text type="secondary"><code>{detail.code}</code> · revision {detail.revision}</Typography.Text>}
          </div>
        </div>
        <Space wrap className="data-service-editor-actions">
          {serviceType === 'SQL_QUERY' && canTest && <Button icon={<ExperimentOutlined />} loading={testMutation.isPending} onClick={() => void testSql()}>测试 SQL</Button>}
          {canSave && <Button type="primary" icon={<SaveOutlined />} loading={createMutation.isPending || updateMutation.isPending} onClick={() => form.submit()}>{creating ? '保存草稿' : '保存'}</Button>}
          {!creating && canPublish && definitionEditable && <Button type="primary" icon={<PlayCircleOutlined />} loading={enableMutation.isPending} onClick={() => void enable()}>{detail?.status === 'DISABLED' ? '重新启用' : '启用'}</Button>}
          {!creating && canPublish && mode === 'DEPLOYMENT_LOCKED' && lockedForRetry && <Button type="primary" icon={<PlayCircleOutlined />} loading={enableMutation.isPending} onClick={() => void enable()}>重试启用</Button>}
          {!creating && canPublish && mode === 'DEPLOYMENT_LOCKED' && lockedForRetry && <Button icon={<ClearOutlined />} loading={cleanupMutation.isPending} onClick={() => void cleanup()}>清理部署</Button>}
          {!creating && canPublish && mode === 'READ_ONLY' && detail?.status === 'ENABLED' && <Button type="primary" icon={<UploadOutlined />} loading={publishMutation.isPending} onClick={() => void publish()}>{currentGatewayBinding ? '重新发布到网关' : '发布到网关'}</Button>}
          {!creating && canPublish && mode === 'READ_ONLY' && detail?.status === 'ENABLED' && <Button danger icon={<StopOutlined />} loading={disableMutation.isPending} onClick={() => void disable()}>停用</Button>}
          {!creating && currentGatewayBinding && <Button icon={<CopyOutlined />} onClick={() => void copyCurl()}>复制网关 cURL</Button>}
        </Space>
      </header>

      <div className="data-service-editor-notices">
        {dependencyUnavailable && <Alert type="warning" showIcon message="缺少当前服务定义所需资源的查看权限" description="相关资源选择和维护操作已禁用。" />}
        {mode === 'READ_ONLY' && <Alert type="info" showIcon message="服务已在 Engine 中启用，定义为只读；可以发布到网关，停用后才能修改。" />}
        {mode === 'DEPLOYMENT_LOCKED' && <Alert type="warning" showIcon message="当前 Engine 操作尚未完成或状态不确定，服务定义已锁定。" description={lockedForRetry ? '可以重试启用相同版本，或先清理 Engine 上的部署状态。' : '请等待停用完成，或根据部署错误处理。'} />}
        {(detail?.deploymentError || gatewayError || operationError) && <Alert type="error" showIcon message="服务操作错误" description={operationError ?? gatewayError ?? detail?.deploymentError} />}
        {errorSummary.length > 0 && <Alert type="error" showIcon message="请修正表单错误" description={<ul>{errorSummary.map((error) => <li key={error}>{error}</li>)}</ul>} />}
      </div>

      <Form<DataServiceFormValues>
        form={form}
        layout="vertical"
        className="data-service-editor-form"
        onFinish={(values) => void save(values)}
        onFinishFailed={onFinishFailed}
        onValuesChange={() => setTestResult(null)}
      >
        <Form.Item name="type" hidden><input /></Form.Item>
        {serviceType === 'STANDARD_TABLE' ? (
          <StandardServiceEditor
            form={form}
            creating={creating}
            readOnly={definitionReadOnly}
            canViewDirectories={canViewDirectories}
            canViewModels={canViewModels}
            canViewEngines={canViewEngines}
          />
        ) : (
          <SqlServiceEditor
            form={form}
            creating={creating}
            readOnly={definitionReadOnly}
            canTest={canTest}
            canViewDirectories={canViewDirectories}
            canViewModels={canViewModels}
            canViewDataSources={canViewDataSources}
            canViewEngines={canViewEngines}
            testValues={testValues}
            testResult={testResult}
            onTestValuesChange={setTestValues}
            onResetTest={() => setTestResult(null)}
          />
        )}
      </Form>

      <Modal
        open={blocker.state === 'blocked'}
        title="离开未保存的数据服务？"
        okText="离开"
        cancelText="继续编辑"
        onOk={() => {
          allowNavigationRef.current = true;
          blocker.proceed?.();
        }}
        onCancel={() => blocker.reset?.()}
      >
        当前服务定义尚未保存，离开后这些修改会丢失。
      </Modal>
    </div>
  );
};
