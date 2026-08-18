import { ArrowLeftOutlined, SaveOutlined } from '@ant-design/icons';
import type { FormProps } from 'antd';
import {
  Alert,
  Button,
  Card,
  Form,
  Modal,
  Result,
  Select,
  Skeleton,
  Space,
  Tag,
  Typography,
  message,
} from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useBlocker, useLocation, useNavigate, useParams, type BlockerFunction } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { useServiceEngines } from '../../serviceengine';
import { useCurrentUser } from '../../system';
import { CommonServiceFields } from '../components/editor/CommonServiceFields';
import { useDataService, useUpdateDataService } from '../hooks/useDataServices';
import {
  dataServiceDeploymentStatusLabels,
  dataServiceStatusLabels,
  dataServiceTypeLabels,
  type DataServiceDetail,
} from '../model/dataService';
import {
  buildDataServiceUpdateRequest,
  dataServiceBasicFingerprint,
  dataServiceEditorMode,
  detailToDataServiceFormValues,
  type DataServiceFormValues,
} from '../model/dataServiceEditor';
import './dataServiceEditor.css';

const problemMessage = (error: unknown, fallback: string) => (
  error instanceof ApiError ? error.problem?.detail ?? error.message : fallback
);

export const DataServiceEditorPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { id } = useParams<{ id: string }>();
  const initializationKey = `basic:${id ?? ''}`;
  const [form] = Form.useForm<DataServiceFormValues>();
  const watchedValues = Form.useWatch([], form) as DataServiceFormValues | undefined;
  const [baselineFingerprint, setBaselineFingerprint] = useState<string | null>(null);
  const [initializedKey, setInitializedKey] = useState<string | null>(null);
  const [operationError, setOperationError] = useState<string | null>(null);
  const allowNavigationRef = useRef(false);
  const [messageApi, messageContext] = message.useMessage();
  const currentUserQuery = useCurrentUser();
  const permissions = new Set(currentUserQuery.data?.permissions ?? []);
  const canUpdate = permissions.has('service.update');
  const canViewDirectories = permissions.has('directory.view');
  const canViewEngines = permissions.has('service.engine.view');
  const detailQuery = useDataService(id, Boolean(id));
  const detail = detailQuery.data;
  const serviceType = detail?.type;
  const mode = dataServiceEditorMode(detail);
  const editable = mode === 'EDITABLE';
  const readOnly = !editable || !canUpdate;
  const canSave = editable && canUpdate && canViewEngines;
  const enginesQuery = useServiceEngines({ page: 0, size: 500, sort: 'code' }, canViewEngines);
  const updateMutation = useUpdateDataService();
  const currentFingerprint = useMemo(
    () => watchedValues ? dataServiceBasicFingerprint(watchedValues) : null,
    [watchedValues],
  );
  const dirty = initializedKey === initializationKey
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
    if (initializedKey === initializationKey) return;
    const initialValues = detail ? detailToDataServiceFormValues(detail) : undefined;
    if (!initialValues) return;
    const initialize = window.setTimeout(() => {
      allowNavigationRef.current = false;
      form.resetFields();
      form.setFieldsValue(initialValues);
      setBaselineFingerprint(dataServiceBasicFingerprint(initialValues));
      setInitializedKey(initializationKey);
      setOperationError(null);
    }, 0);
    return () => window.clearTimeout(initialize);
  }, [detail, form, initializationKey, initializedKey]);

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
    setBaselineFingerprint(dataServiceBasicFingerprint(savedValues));
    setOperationError(null);
  };

  const save = async (values: DataServiceFormValues) => {
    setOperationError(null);
    try {
      if (!id) return;
      const updated = await updateMutation.mutateAsync({ id, request: buildDataServiceUpdateRequest(values) });
      resetBaseline(updated);
      allowNavigationRef.current = true;
      messageApi.success('数据服务基础信息已保存');
      navigate(`/dataservice/${updated.id}?tab=basic`, { replace: true, state: location.state });
    } catch (error) {
      const text = problemMessage(error, '保存数据服务失败');
      setOperationError(text);
      messageApi.error(text);
    }
  };

  const goBack = () => {
    if (id) navigate(`/dataservice/${id}?tab=basic`, { replace: true, state: location.state });
    else navigate('/dataservice');
  };

  if (detailQuery.isLoading) {
    return <div className="data-service-editor-loading"><Skeleton active paragraph={{ rows: 8 }} /></div>;
  }
  if (detailQuery.isError || !detail) {
    return <Result status="error" title="数据服务详情加载失败" subTitle={problemMessage(detailQuery.error, '请确认数据服务是否存在')} extra={<Button onClick={goBack}>返回数据服务</Button>} />;
  }
  if (!serviceType || initializedKey !== initializationKey) {
    return <div className="data-service-editor-loading"><Skeleton active paragraph={{ rows: 8 }} /></div>;
  }

  const onFinishFailed: FormProps<DataServiceFormValues>['onFinishFailed'] = ({ errorFields }) => {
    const firstField = errorFields[0]?.name;
    if (firstField) form.scrollToField(firstField, { focus: true, block: 'center' });
  };

  return (
    <div className="data-service-editor-page data-service-editor-standard data-service-basic-editor-page">
      {messageContext}
      <header className="data-service-editor-header">
        <div className="data-service-editor-heading">
          <Button type="text" icon={<ArrowLeftOutlined />} onClick={goBack}>返回详情</Button>
          <div>
            <Space size={7} wrap>
              <Typography.Title level={4}>修改{detail.name}</Typography.Title>
              <Tag>{dataServiceTypeLabels[serviceType]}</Tag>
              {detail && <Tag>{dataServiceStatusLabels[detail.status]}</Tag>}
              {detail?.deploymentStatus && <Tag>{dataServiceDeploymentStatusLabels[detail.deploymentStatus]}</Tag>}
              {dirty && <Tag color="processing">有未保存修改</Tag>}
            </Space>
            <Typography.Text type="secondary">维护服务身份、Engine 与公开访问配置，服务定义不会被修改。</Typography.Text>
          </div>
        </div>
        <Space>
          {canSave && (
            <Button type="primary" icon={<SaveOutlined />} loading={updateMutation.isPending} onClick={() => form.submit()}>保存</Button>
          )}
        </Space>
      </header>

      <div className="data-service-editor-notices">
        {!canViewEngines && <Alert type="warning" showIcon message="缺少 Service Engine 查看权限，无法保存基础信息" />}
        {mode === 'READ_ONLY' && <Alert type="info" showIcon message="服务已启用，请先停用后再修改基础信息" />}
        {mode === 'DEPLOYMENT_LOCKED' && <Alert type="warning" showIcon message="当前部署状态尚未确认移除，请先清理部署" />}
        {operationError && <Alert type="error" showIcon message="保存失败" description={operationError} />}
      </div>

      <Form<DataServiceFormValues>
        autoComplete="off"
        form={form}
        layout="vertical"
        className="data-service-editor-form data-service-basic-form"
        onFinish={(values) => void save(values)}
        onFinishFailed={onFinishFailed}
      >
        <Form.Item name="type" hidden><input /></Form.Item>
        <Card size="small" title="基本信息">
          <CommonServiceFields creating={false} readOnly={readOnly} canViewDirectories={canViewDirectories} section="identity" includeDescription />
        </Card>
        <Card size="small" title="运行与访问">
          <Form.Item<DataServiceFormValues> label="所属 Service Engine" name="engineId" rules={[{ required: true, message: '请选择 Service Engine' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              disabled={readOnly || !canViewEngines}
              loading={enginesQuery.isFetching}
              options={(enginesQuery.data?.content ?? []).map((engine) => ({
                value: engine.id,
                label: `${engine.name}（${engine.code}）${engine.enabled ? '' : ' · 已停用'}`,
                disabled: !engine.enabled && engine.id !== detail?.engineId,
              }))}
            />
          </Form.Item>
          <CommonServiceFields creating={false} readOnly={readOnly} canViewDirectories={false} section="routing" includeDescription={false} />
        </Card>
      </Form>

      <Modal
        rootClassName="business-overlay business-modal-overlay"
        open={blocker.state === 'blocked'}
        title="离开未保存的基础信息？"
        okText="离开"
        cancelText="继续编辑"
        onOk={() => { allowNavigationRef.current = true; blocker.proceed?.(); }}
        onCancel={() => blocker.reset?.()}
      >
        当前基础信息尚未保存，离开后这些修改会丢失。
      </Modal>
    </div>
  );
};
