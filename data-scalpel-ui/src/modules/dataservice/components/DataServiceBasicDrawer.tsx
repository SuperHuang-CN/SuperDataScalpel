import { ApiOutlined, CloudServerOutlined, IdcardOutlined } from '@ant-design/icons';
import { Badge, Button, Drawer, Form, Input, Modal, Select, Space, Tag, Typography, message } from 'antd';
import { type ReactNode, useEffect, useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { ContextHelp, InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { useServiceEngines } from '../../serviceengine';
import { useCreateDataService, useUpdateDataService } from '../hooks/useDataServices';
import {
  dataServiceTypeLabels,
  type DataServiceDetail,
  type DataServiceSummary,
  type DataServiceType,
} from '../model/dataService';
import {
  buildDataServiceCreateRequest,
  buildDataServiceUpdateRequest,
  dataServiceBasicFingerprint,
  initialDataServiceFormValues,
  type DataServiceFormValues,
} from '../model/dataServiceEditor';
import { DataServiceTypeIcon } from './DataServiceTypeIcon';
import { CommonServiceFields } from './editor/CommonServiceFields';

type DataServiceBasicTarget = DataServiceSummary | DataServiceDetail;

interface DataServiceBasicDrawerProps {
  open: boolean;
  type: DataServiceType | null;
  dataService?: DataServiceBasicTarget | null;
  initialDirectoryId?: string;
  canViewDirectories: boolean;
  canViewEngines: boolean;
  onClose: () => void;
  onCreated?: (dataService: DataServiceDetail) => void;
  onUpdated?: (dataService: DataServiceDetail) => void;
}

const problemMessage = (error: unknown, fallback: string) => (
  error instanceof ApiError ? error.problem?.detail ?? error.message : fallback
);

const basicFormValues = (dataService: DataServiceBasicTarget): DataServiceFormValues => ({
  code: dataService.code,
  name: dataService.name,
  directoryId: dataService.directoryId ?? undefined,
  type: dataService.type,
  engineId: dataService.engineId,
  contextPath: dataService.contextPath ?? undefined,
  description: dataService.description ?? undefined,
});

const isEditable = (dataService: DataServiceBasicTarget | null | undefined) => (
  !dataService
  || (
    dataService.status !== 'ENABLED'
    && (!dataService.deploymentStatus || dataService.deploymentStatus === 'REMOVED')
  )
);

const DataServiceFormSection = ({
  title,
  description,
  icon,
  help,
  children,
}: {
  title: string;
  description: string;
  icon: ReactNode;
  help?: ReactNode;
  children: ReactNode;
}) => (
  <section className="data-service-basic-form-section">
    <header className="data-service-basic-form-section-header">
      <span className="data-service-basic-form-section-icon" aria-hidden="true">{icon}</span>
      <span className="data-service-basic-form-section-copy">
        <span className="data-service-basic-form-section-title-row">
          <span className="data-service-basic-form-section-title">{title}</span>
          {help && (
            <ContextHelp
              ariaLabel={`${title}说明`}
              content={help}
              presentation="popover"
              placement="bottomLeft"
            />
          )}
        </span>
        <Typography.Text type="secondary">{description}</Typography.Text>
      </span>
    </header>
    <div className="data-service-basic-form-section-body">{children}</div>
  </section>
);

export const DataServiceBasicDrawer = ({
  open,
  type,
  dataService,
  initialDirectoryId,
  canViewDirectories,
  canViewEngines,
  onClose,
  onCreated,
  onUpdated,
}: DataServiceBasicDrawerProps) => {
  const [form] = Form.useForm<DataServiceFormValues>();
  const watchedValues = Form.useWatch([], form) as DataServiceFormValues | undefined;
  const watchedEngineId = Form.useWatch('engineId', form);
  const [baselineFingerprint, setBaselineFingerprint] = useState<string | null>(null);
  const [operationError, setOperationError] = useState<string | null>(null);
  const [messageApi, messageContext] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const createMutation = useCreateDataService();
  const updateMutation = useUpdateDataService();
  const editing = Boolean(dataService);
  const effectiveType = dataService?.type ?? type;
  const editable = isEditable(dataService);
  const pending = createMutation.isPending || updateMutation.isPending;
  const enginesQuery = useServiceEngines(
    { page: 0, size: 500, sort: 'code' },
    open && canViewEngines,
  );
  const currentFingerprint = useMemo(
    () => watchedValues ? dataServiceBasicFingerprint(watchedValues) : null,
    [watchedValues],
  );
  const dirty = open
    && baselineFingerprint !== null
    && currentFingerprint !== null
    && currentFingerprint !== baselineFingerprint;
  const engineChanged = Boolean(
    dataService?.definitionConfigured
    && watchedEngineId
    && watchedEngineId !== dataService.engineId,
  );
  const engineOptions = useMemo(() => {
    const requiredEngineType = effectiveType === 'SPATIAL_SERVICE' ? 'GEOSERVER' : 'DATASCALPEL';
    const options = (enginesQuery.data?.content ?? [])
      .filter((engine) => (engine.type ?? 'DATASCALPEL') === requiredEngineType)
      .map((engine) => ({
      value: engine.id,
      label: `${engine.name}（${engine.code}）${engine.enabled ? '' : ' · 已停用'}`,
      disabled: !engine.enabled && engine.id !== dataService?.engineId,
    }));
    if (dataService && !options.some((option) => option.value === dataService.engineId)) {
      options.unshift({
        value: dataService.engineId,
        label: `${dataService.engineId} · Engine 已移除或不可见`,
        disabled: true,
      });
    }
    return options;
  }, [dataService, effectiveType, enginesQuery.data?.content]);

  useEffect(() => {
    if (!open) return;
    const values = dataService
      ? basicFormValues(dataService)
      : type ? initialDataServiceFormValues(type, initialDirectoryId) : undefined;
    if (!values) return;
    const initialize = window.setTimeout(() => {
      form.resetFields();
      form.setFieldsValue(values);
      setBaselineFingerprint(dataServiceBasicFingerprint(values));
      setOperationError(null);
    }, 0);
    return () => window.clearTimeout(initialize);
  }, [dataService, form, initialDirectoryId, open, type]);

  const closeImmediately = () => {
    setBaselineFingerprint(null);
    setOperationError(null);
    onClose();
  };

  const requestClose = () => {
    if (pending) return;
    if (!dirty) {
      closeImmediately();
      return;
    }
    modalApi.confirm({
      rootClassName: 'business-overlay business-modal-overlay',
      title: editing ? '放弃未保存的基础信息？' : '放弃创建数据服务？',
      content: editing
        ? '关闭后，本次对服务基础信息的修改将丢失。'
        : '关闭后，当前填写的服务基础信息将丢失。',
      okText: '放弃',
      cancelText: '继续编辑',
      okButtonProps: { danger: true },
      onOk: closeImmediately,
    });
  };

  const submit = async (values: DataServiceFormValues) => {
    setOperationError(null);
    try {
      if (dataService) {
        const updated = await updateMutation.mutateAsync({
          id: dataService.id,
          request: buildDataServiceUpdateRequest(values),
        });
        setBaselineFingerprint(dataServiceBasicFingerprint(basicFormValues(updated)));
        messageApi.success('数据服务基础信息已保存');
        onUpdated?.(updated);
        return;
      }
      const created = await createMutation.mutateAsync(buildDataServiceCreateRequest(values));
      setBaselineFingerprint(dataServiceBasicFingerprint(basicFormValues(created)));
      onCreated?.(created);
    } catch (error) {
      const text = problemMessage(error, editing ? '保存数据服务失败' : '创建数据服务失败');
      setOperationError(text);
      messageApi.error(text);
    }
  };

  const title = dataService
    ? `编辑${dataServiceTypeLabels[dataService.type]}服务`
    : effectiveType ? `新建${dataServiceTypeLabels[effectiveType]}服务` : '新建数据服务';
  const canSubmit = editable && canViewEngines;
  const footerStatus = operationError ? (
    <InlineFeedback
      tone="error"
      label={editing ? '保存失败' : '创建失败'}
      detail={operationError}
      ariaLabel={editing ? '查看保存失败详情' : '查看创建失败详情'}
    />
  ) : !editable ? (
    <InlineFeedback
      tone="warning"
      label="当前基础信息只读"
      detail={dataService?.status === 'ENABLED'
        ? '服务已启用，请先停用后再修改基础信息。'
        : '当前部署状态尚未确认移除，请先清理部署。'}
      ariaLabel="查看基础信息只读原因"
    />
  ) : !canViewEngines ? (
    <InlineFeedback
      tone="warning"
      label="缺少 Engine 查看权限"
      detail="无法选择 Service Engine，因此当前不能保存数据服务基础信息。"
      ariaLabel="查看权限限制详情"
    />
  ) : dirty ? (
    <Badge status="processing" text="有未保存的修改" />
  ) : (
    <Badge status="default" text={editing ? '基础信息已加载' : '创建后保存为草稿'} />
  );

  return (
    <>
      {messageContext}
      {modalContext}
      <Drawer
        rootClassName="business-overlay business-drawer-overlay"
        className="data-service-basic-drawer"
        title={(
          <div className="data-service-basic-drawer-title">
            <span className="data-service-basic-drawer-title-icon" aria-hidden="true">
              {effectiveType && <DataServiceTypeIcon type={effectiveType} />}
            </span>
            <span className="data-service-basic-drawer-title-copy">
              <span>{title}</span>
              <Typography.Text type="secondary">
                {editing
                  ? '维护服务标识、目录归属与运行引擎'
                  : '先建立服务草稿，服务定义可在创建后继续配置'}
              </Typography.Text>
            </span>
          </div>
        )}
        extra={effectiveType ? <Tag className="data-service-basic-drawer-header-tag">{dataServiceTypeLabels[effectiveType]}</Tag> : undefined}
        open={open}
        size="min(820px, 100vw)"
        onClose={requestClose}
        closable={pending ? false : { placement: 'end' }}
        maskClosable={!pending}
        destroyOnHidden
        footer={(
          <div className="data-service-basic-drawer-footer">
            {footerStatus}
            <Space>
              <Button disabled={pending} onClick={requestClose}>取消</Button>
              <Button
                type="primary"
                loading={pending}
                disabled={!canSubmit}
                onClick={() => form.submit()}
              >
                {editing ? '保存修改' : '创建服务'}
              </Button>
            </Space>
          </div>
        )}
      >
        <Form<DataServiceFormValues>
          autoComplete="off"
          form={form}
          layout="vertical"
          className="data-service-basic-form"
          onFinish={(values) => void submit(values)}
        >
          <Form.Item name="type" hidden><Input /></Form.Item>
          {!canViewDirectories && <Form.Item name="directoryId" hidden><Input /></Form.Item>}
          <DataServiceFormSection
            title="基本信息"
            description="定义服务在目录与管理界面中的识别信息"
            icon={<IdcardOutlined />}
          >
            <CommonServiceFields
              creating={!editing}
              readOnly={!editable}
              canViewDirectories={canViewDirectories}
              section="identity"
              includeDescription
            />
          </DataServiceFormSection>

          <DataServiceFormSection
            title="运行配置"
            description={effectiveType === 'SPATIAL_SERVICE' ? '选择承载图层发布的 GeoServer 空间引擎' : '选择承载部署的 Service Engine，并配置服务自身的 Context Path'}
            icon={<CloudServerOutlined />}
            help={(
              <div className="data-service-basic-section-help">
                {effectiveType === 'SPATIAL_SERVICE' ? <>
                  <span>GeoServer Engine 承载 PostGIS 图层的 WMS/WFS 发布。</span>
                  <span>图层名由服务编码确定，空间服务不配置 Context Path，也不进入 API Gateway。</span>
                  <span>更换 GeoServer 不会清空已有定义，但保存后需重新检查数据源注册与模型兼容性。</span>
                </> : <>
                  <span>Service Engine 承载服务定义的部署与实际请求处理。</span>
                  <span>Context Path 是服务在 Engine 上的实际访问路径；发布网关时可再配置不同的公开路径。</span>
                  <span>更换 Engine 不会清空已有定义，但保存后需要重新检查定义兼容性。</span>
                </>}
              </div>
            )}
          >
            {engineChanged && (
              <InlineFeedback
                tone="warning"
                label="Service Engine 已变更"
                detail="已有服务定义会继续保留；保存后请在定义页重新检查兼容性。"
                ariaLabel="查看更换 Service Engine 的影响"
                className="data-service-basic-section-feedback"
              />
            )}
            <Form.Item<DataServiceFormValues>
              label={effectiveType === 'SPATIAL_SERVICE' ? '所属 GeoServer Engine' : '所属 Service Engine'}
              name="engineId"
              rules={[{ required: true, message: effectiveType === 'SPATIAL_SERVICE' ? '请选择 GeoServer Engine' : '请选择 Service Engine' }]}
            >
              <Select
                showSearch
                optionFilterProp="label"
                disabled={!editable || !canViewEngines}
                loading={enginesQuery.isFetching}
                options={engineOptions}
                placeholder={effectiveType === 'SPATIAL_SERVICE' ? '选择启用的 GeoServer Engine' : '选择启用的 Service Engine'}
              />
            </Form.Item>
            {effectiveType !== 'SPATIAL_SERVICE' && <Form.Item<DataServiceFormValues>
              label="服务 Context Path"
              name="contextPath"
              rules={[
                { required: true, whitespace: true, message: '请输入服务 Context Path' },
                {
                  validator: async (_, value: string | undefined) => {
                    const path = value?.trim().toLowerCase() ?? '';
                    if (!/^\/open-api\/v1\/[a-z0-9][a-z0-9/_-]*$/.test(path) || path.endsWith('/') || path.includes('//')) {
                      throw new Error('路径必须位于 /open-api/v1/，使用小写静态路径且不能以 / 结尾');
                    }
                  },
                },
              ]}
            >
              <Input
                name="data-service-context-path"
                autoComplete="off"
                prefix={<ApiOutlined />}
                placeholder="如：/open-api/v1/customers"
                disabled={!editable}
              />
            </Form.Item>}
          </DataServiceFormSection>
        </Form>
      </Drawer>
    </>
  );
};
