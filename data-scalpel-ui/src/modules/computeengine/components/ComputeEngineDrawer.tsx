import {
  CloudServerOutlined,
  ControlOutlined,
  DashboardOutlined,
  IdcardOutlined,
  MessageOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { Badge, Button, Col, Drawer, Form, Input, InputNumber, Modal, Row, Segmented, Select, Space, Tag, Typography, message } from 'antd';
import { type ReactNode, useEffect, useRef, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { BusinessSecretInput } from '../../../shared/components/BusinessSecretInput';
import { ContextHelp, InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { useCreateComputeEngine, useReconfigureComputeEngine, useUpdateComputeEngine } from '../hooks/useComputeEngines';
import {
  computeBackendTypeLabels,
  computeEngineRegistrationStateColors,
  computeEngineRegistrationStateLabels,
  defaultSparkExecutionResourcePolicy,
  type ComputeBackendType,
  type ComputeEngine,
  type CreateComputeEngineRequest,
  type UpdateComputeEngineRequest,
  type SparkExecutionResourcePolicy,
} from '../model/computeEngine';

type ComputeEngineFormSectionKey = 'basic' | 'dispatcher' | 'messaging' | 'admission' | 'resources';

const computeEngineSections: { key: ComputeEngineFormSectionKey; label: string }[] = [
  { key: 'basic', label: '基本信息' },
  { key: 'dispatcher', label: 'Dispatcher' },
  { key: 'messaging', label: '消息通道' },
  { key: 'admission', label: '任务准入' },
  { key: 'resources', label: '资源策略' },
];

const ComputeEngineFormSection = ({
  id,
  title,
  description,
  icon,
  help,
  extra,
  children,
}: {
  id: string;
  title: string;
  description: string;
  icon: ReactNode;
  help?: ReactNode;
  extra?: ReactNode;
  children: ReactNode;
}) => (
  <section id={id} className="compute-engine-form-section">
    <header className="compute-engine-form-section-header">
      <span className="compute-engine-form-section-icon" aria-hidden="true">{icon}</span>
      <span className="compute-engine-form-section-copy">
        <span className="compute-engine-form-section-title-row">
          <span className="compute-engine-form-section-title">{title}</span>
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
      {extra && <span className="compute-engine-form-section-extra">{extra}</span>}
    </header>
    <div className="compute-engine-form-section-body">{children}</div>
  </section>
);

interface ComputeEngineDrawerProps {
  open: boolean;
  engine: ComputeEngine | null;
  canUpdate: boolean;
  canManage: boolean;
  onClose: () => void;
}

interface ComputeEngineFormValues {
  name: string;
  description?: string;
  dispatcherBaseUrl: string;
  accessToken?: string;
  expectedBackendType: ComputeBackendType;
  commandTopic: string;
  runnerEventTopic: string;
  adminEventTopic: string;
  maxQueuedExecutions: number;
  maxConcurrentSubmissions: number;
  maxInFlightApplications: number;
  resourcePolicy: SparkExecutionResourcePolicy;
}

const defaults: Pick<ComputeEngineFormValues,
'expectedBackendType' | 'commandTopic' | 'runnerEventTopic' | 'adminEventTopic' |
'maxQueuedExecutions' | 'maxConcurrentSubmissions' | 'maxInFlightApplications'> = {
  expectedBackendType: 'LOCAL_DOCKER',
  commandTopic: 'datascalpel.execution.command.local',
  runnerEventTopic: 'datascalpel.runner.event.local',
  adminEventTopic: 'datascalpel.execution.event',
  maxQueuedExecutions: 20,
  maxConcurrentSubmissions: 2,
  maxInFlightApplications: 2,
};

const optionalText = (value: string | undefined) => value?.trim() || undefined;

export const ComputeEngineDrawer = ({ open, engine, canUpdate, canManage, onClose }: ComputeEngineDrawerProps) => {
  const [form] = Form.useForm<ComputeEngineFormValues>();
  const [messageApi, messageContext] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const [drainingEngineId, setDrainingEngineId] = useState<string | null>(null);
  const [memoryUnit, setMemoryUnit] = useState<'GiB' | 'MiB'>('GiB');
  const [activeSection, setActiveSection] = useState<ComputeEngineFormSectionKey>('basic');
  const [editingAccessToken, setEditingAccessToken] = useState(false);
  const contentRef = useRef<HTMLDivElement>(null);
  const createMutation = useCreateComputeEngine();
  const updateMutation = useUpdateComputeEngine();
  const reconfigureMutation = useReconfigureComputeEngine();

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    form.setFieldsValue(engine ? {
      name: engine.name,
      description: engine.description ?? undefined,
      dispatcherBaseUrl: engine.dispatcherBaseUrl,
      accessToken: undefined,
      expectedBackendType: engine.expectedBackendType,
      commandTopic: engine.commandTopic,
      runnerEventTopic: engine.runnerEventTopic,
      adminEventTopic: engine.adminEventTopic,
      maxQueuedExecutions: engine.maxQueuedExecutions,
      maxConcurrentSubmissions: engine.maxConcurrentSubmissions,
      maxInFlightApplications: engine.maxInFlightApplications,
      resourcePolicy: engine.resourcePolicy,
    } : { ...defaults, resourcePolicy: defaultSparkExecutionResourcePolicy('LOCAL_DOCKER') });
  }, [engine, form, open]);

  const close = () => {
    setDrainingEngineId(null);
    setActiveSection('basic');
    setEditingAccessToken(false);
    form.resetFields();
    onClose();
  };

  const save = async (values: ComputeEngineFormValues) => {
    const request: UpdateComputeEngineRequest = {
      name: values.name.trim(),
      description: optionalText(values.description),
      dispatcherBaseUrl: values.dispatcherBaseUrl.trim().replace(/\/$/, ''),
      accessToken: optionalText(values.accessToken),
      expectedBackendType: values.expectedBackendType,
      commandTopic: values.commandTopic.trim(),
      runnerEventTopic: values.runnerEventTopic.trim(),
      adminEventTopic: values.adminEventTopic.trim(),
      maxQueuedExecutions: values.maxQueuedExecutions,
      maxConcurrentSubmissions: values.maxConcurrentSubmissions,
      maxInFlightApplications: values.maxInFlightApplications,
      resourcePolicy: values.resourcePolicy,
    };
    try {
      if (engine) {
        if (engine.registrationState === 'ACTIVE' || engine.registrationState === 'DRAINING') {
          await reconfigureMutation.mutateAsync({ id: engine.id, request });
          messageApi.success('配置已应用，计算引擎已重新激活');
        } else {
          await updateMutation.mutateAsync({ id: engine.id, request });
          messageApi.success('计算引擎已保存');
        }
      } else {
        await createMutation.mutateAsync({ ...request, accessToken: values.accessToken?.trim() ?? '' } satisfies CreateComputeEngineRequest);
        messageApi.success('计算引擎已创建');
      }
      close();
    } catch (error) {
      if (error instanceof ApiError && error.status === 409 && error.message.includes('已进入排空状态')
        && engine && ['ACTIVE', 'DRAINING'].includes(engine.registrationState)) {
        setDrainingEngineId(engine.id);
        messageApi.warning(error.message);
        return;
      }
      messageApi.error(error instanceof ApiError ? error.message : '保存计算引擎失败');
    }
  };

  const reconfiguring = engine?.registrationState === 'ACTIVE' || engine?.registrationState === 'DRAINING';
  const waitingForDrain = engine !== null && drainingEngineId === engine.id;
  const editingAllowed = engine === null
    || (engine.registrationState !== 'REGISTERING' && canUpdate && (!reconfiguring || canManage));
  const pending = createMutation.isPending || updateMutation.isPending || reconfigureMutation.isPending;
  const selectedBackend = Form.useWatch('expectedBackendType', form);
  const localDocker = selectedBackend === 'LOCAL_DOCKER';

  const memoryProps = (value: number | undefined) => ({
    value: value === undefined || memoryUnit === 'MiB' ? value : value / 1024,
  });
  const toMiB = (value: number | null) => {
    if (value === null || value === undefined) return value;
    return memoryUnit === 'MiB' ? Math.round(value) : Math.round(value * 1024);
  };

  const submit = (values: ComputeEngineFormValues) => {
    if (!engine && !values.accessToken?.trim()) {
      setEditingAccessToken(true);
      form.setFields([{ name: 'accessToken', errors: ['请输入访问 Token'] }]);
      contentRef.current?.querySelector<HTMLElement>('#compute-engine-dispatcher')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      setActiveSection('dispatcher');
      return;
    }
    const { defaults: resourceDefaults, maximums: resourceMaximums } = values.resourcePolicy;
    const invalidResourceLimit = (
      resourceDefaults.driverCores > resourceMaximums.driverCores
      || resourceDefaults.driverMemoryMiB > resourceMaximums.driverMemoryMiB
      || resourceDefaults.executorInstances > resourceMaximums.executorInstances
      || resourceDefaults.executorCores > resourceMaximums.executorCores
      || resourceDefaults.executorMemoryMiB > resourceMaximums.executorMemoryMiB
    );
    if (invalidResourceLimit) {
      messageApi.error('运行资源默认值不能超过单次最大值');
      return;
    }
    if (engine?.registrationState !== 'ACTIVE' || waitingForDrain) {
      void save(values);
      return;
    }
    modalApi.confirm({
      rootClassName: 'business-overlay business-modal-overlay',
      title: '应用计算引擎配置',
      content: `将暂停“${engine.name}”的新任务准入，安全排空后自动反注册并重新注册。`,
      okText: '应用并重新注册',
      cancelText: '取消',
      onOk: () => save(values),
    });
  };

  const readonlyHint = engine?.registrationState === 'REGISTERING'
    ? '计算引擎正在注册，当前只能查看配置。'
    : '当前账号缺少修改或管理权限，只能查看配置。';

  const scrollToSection = (section: ComputeEngineFormSectionKey) => {
    const target = contentRef.current?.querySelector<HTMLElement>(`#compute-engine-${section}`);
    target?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    setActiveSection(section);
  };

  const updateActiveSection = () => {
    const container = contentRef.current;
    if (!container) return;
    if (container.scrollTop + container.clientHeight >= container.scrollHeight - 8) {
      setActiveSection('resources');
      return;
    }
    const containerTop = container.getBoundingClientRect().top;
    const visible = computeEngineSections.filter(({ key }) => {
      const target = container.querySelector<HTMLElement>(`#compute-engine-${key}`);
      return target && target.getBoundingClientRect().top - containerTop <= 40;
    });
    setActiveSection(visible.at(-1)?.key ?? 'basic');
  };

  const headerStatus = engine ? (
    <Tag color={computeEngineRegistrationStateColors[engine.registrationState]}>
      {computeEngineRegistrationStateLabels[engine.registrationState]}
    </Tag>
  ) : <Tag>待创建</Tag>;

  const footerStatus = waitingForDrain ? (
    <InlineFeedback
      tone="warning"
      label="计算引擎正在排空"
      detail="仍有排队或活动任务。任务结束后，请保留当前配置并再次应用。"
      ariaLabel="查看计算引擎排空说明"
    />
  ) : !editingAllowed ? (
    <InlineFeedback
      tone="warning"
      label={readonlyHint}
      detail="当前配置可以查看，但不能提交修改。"
      ariaLabel="查看计算引擎只读原因"
    />
  ) : reconfiguring ? (
    <Badge status="processing" text="保存将触发安全排空与重新注册" />
  ) : (
    <Badge status="default" text={engine ? '修改后保存配置' : '创建后等待 Dispatcher 注册'} />
  );

  return <>
    {messageContext}{modalContext}
    <Drawer
      rootClassName="business-overlay business-drawer-overlay"
      className="compute-engine-drawer"
      title={(
        <div className="compute-engine-drawer-title">
          <span className="compute-engine-drawer-title-icon" aria-hidden="true"><ThunderboltOutlined /></span>
          <span className="compute-engine-drawer-title-copy">
            <span>{engine ? (editingAllowed ? '修改计算引擎' : '查看计算引擎') : '新建计算引擎'}</span>
            <Typography.Text type="secondary">配置 Dispatcher、消息通道、准入容量与 Spark 运行资源</Typography.Text>
          </span>
        </div>
      )}
      extra={<span className="compute-engine-drawer-header-status">{headerStatus}</span>}
      open={open}
      size="min(1080px, 100vw)"
      closable={pending ? false : { placement: 'end' }}
      maskClosable={!pending}
      onClose={close}
      destroyOnHidden
      footer={(
        <div className="compute-engine-drawer-footer">
          {footerStatus}
          <Space>
            <Button disabled={pending} onClick={close}>{editingAllowed ? '取消' : '关闭'}</Button>
            {editingAllowed && (
              <Button type="primary" loading={pending} onClick={() => form.submit()}>
                {engine ? (reconfiguring ? (waitingForDrain ? '再次应用并重新注册' : '应用并重新注册') : '保存修改') : '创建引擎'}
              </Button>
            )}
          </Space>
        </div>
      )}
    >
      <div className="compute-engine-drawer-layout">
        <nav className="compute-engine-section-nav" aria-label="计算引擎配置分区">
          {computeEngineSections.map((section, index) => (
            <Button
              key={section.key}
              type="text"
              className={activeSection === section.key ? 'is-active' : undefined}
              onClick={() => scrollToSection(section.key)}
            >
              <span className="compute-engine-section-step" aria-hidden="true">{index + 1}</span>
              <span className="compute-engine-section-step-label">{section.label}</span>
            </Button>
          ))}
        </nav>

        <div className="compute-engine-form-scroll" ref={contentRef} onScroll={updateActiveSection}>
          <Form<ComputeEngineFormValues>
            autoComplete="off"
            form={form}
            layout="vertical"
            className="compute-engine-form"
            onFinish={submit}
            disabled={!editingAllowed}
          >
            <ComputeEngineFormSection
              id="compute-engine-basic"
              title="基本信息"
              description="标识计算引擎并声明期望的 Spark 运行后端"
              icon={<IdcardOutlined />}
            >
              {engine?.registrationState === 'DETACHED' && (
                <InlineFeedback
                  tone="warning"
                  label="该计算引擎已离线解除绑定"
                  detail={`此状态只代表 Admin 已解除注册关系。重新注册前请确认原 Dispatcher 已永久停止。${engine.detachReason ? ` 解除原因：${engine.detachReason}` : ''}`}
                  ariaLabel="查看离线解绑说明"
                  className="compute-engine-section-feedback"
                />
              )}
              <Row gutter={14}>
                <Col xs={24} md={12}>
                  <Form.Item label="名称" name="name" rules={[{ required: true, whitespace: true, message: '请输入名称' }, { max: 100 }]}>
                    <Input name="compute-engine-display-name" autoComplete="off" autoFocus />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item label="计算后端" name="expectedBackendType" rules={[{ required: true, message: '请选择计算后端' }]}>
                    <Select
                      options={Object.entries(computeBackendTypeLabels).map(([value, label]) => ({ value, label }))}
                      onChange={(backend: ComputeBackendType) => {
                        if (!engine) form.setFieldValue('resourcePolicy', defaultSparkExecutionResourcePolicy(backend));
                      }}
                    />
                  </Form.Item>
                </Col>
                <Col span={24}>
                  <Form.Item label="说明" name="description" rules={[{ max: 1000 }]}>
                    <Input.TextArea name="compute-engine-description" autoComplete="off" rows={3} maxLength={1000} showCount />
                  </Form.Item>
                </Col>
              </Row>
            </ComputeEngineFormSection>

            <ComputeEngineFormSection
              id="compute-engine-dispatcher"
              title="Dispatcher 访问"
              description="配置 Admin 管理 Dispatcher 的内部地址与认证凭据"
              icon={<CloudServerOutlined />}
              help="Admin 只通过该地址管理 Dispatcher 注册状态；访问 Token 将作为内部认证凭据保存。"
            >
              <Row gutter={14}>
                <Col span={24}>
                  <Form.Item label="Dispatcher 地址" name="dispatcherBaseUrl" rules={[{ required: true, type: 'url', message: '请输入有效的 HTTP(S) 地址' }, { max: 500 }]}>
                    <Input type="url" name="compute-engine-dispatcher-url" autoComplete="off" placeholder="如：http://127.0.0.1:18092" />
                  </Form.Item>
                </Col>
                <Col span={24}>
                  {editingAccessToken ? (
                    <Form.Item label={engine ? '新的访问 Token' : '访问 Token'} name="accessToken" rules={engine ? [{ max: 1000 }] : [{ required: true, whitespace: true, message: '请输入访问 Token' }, { max: 1000 }]}>
                      <BusinessSecretInput
                        name="compute-engine-access-token"
                        autoComplete="off"
                        placeholder={engine ? '输入新 Token；不修改可返回已保存状态' : '输入 Dispatcher 访问 Token'}
                        addonAfter={engine ? (
                          <Button
                            type="text"
                            size="small"
                            onClick={() => {
                              form.setFieldValue('accessToken', undefined);
                              form.setFields([{ name: 'accessToken', errors: [] }]);
                              setEditingAccessToken(false);
                            }}
                          >
                            保持原值
                          </Button>
                        ) : undefined}
                      />
                    </Form.Item>
                  ) : (
                    <div className="compute-engine-credential-control">
                      <InlineFeedback
                        tone={engine ? 'success' : 'info'}
                        label={engine ? '已安全保存访问 Token' : '尚未配置访问 Token'}
                      />
                      <Button
                        onClick={() => {
                          form.setFieldValue('accessToken', undefined);
                          form.setFields([{ name: 'accessToken', errors: [] }]);
                          setEditingAccessToken(true);
                        }}
                      >
                        {engine ? '替换 Token' : '配置 Token'}
                      </Button>
                    </div>
                  )}
                </Col>
              </Row>
            </ComputeEngineFormSection>

            <ComputeEngineFormSection
              id="compute-engine-messaging"
              title="消息通道"
              description="配置任务命令、Runner 事件和 Admin 事件的 Kafka Topic"
              icon={<MessageOutlined />}
              help="命令 Topic 用于下发执行命令；Runner 与 Admin 事件 Topic 分别承载运行进度和控制面状态。"
            >
              <Row gutter={14}>
                <Col span={24}>
                  <Form.Item label="命令 Topic" name="commandTopic" rules={[{ required: true, whitespace: true }, { max: 249 }]}>
                    <Input className="compute-engine-topic-input" name="compute-engine-command-topic" autoComplete="off" />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item label="Runner 事件 Topic" name="runnerEventTopic" rules={[{ required: true, whitespace: true }, { max: 249 }]}>
                    <Input className="compute-engine-topic-input" name="compute-engine-runner-event-topic" autoComplete="off" />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item label="Admin 事件 Topic" name="adminEventTopic" rules={[{ required: true, whitespace: true }, { max: 249 }]}>
                    <Input className="compute-engine-topic-input" name="compute-engine-admin-event-topic" autoComplete="off" />
                  </Form.Item>
                </Col>
              </Row>
            </ComputeEngineFormSection>

            <ComputeEngineFormSection
              id="compute-engine-admission"
              title="任务准入"
              description="限制排队、提交和运行阶段的并发容量"
              icon={<ControlOutlined />}
              help="最大运行数设为 0 时不额外限制正在运行的应用数量；Dispatcher 仍会执行其他安全约束。"
            >
              <Row gutter={14}>
                <Col xs={24} md={8}>
                  <Form.Item label="最大排队数" name="maxQueuedExecutions" rules={[{ required: true }]}>
                    <InputNumber min={0} precision={0} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={8}>
                  <Form.Item label="并发提交数" name="maxConcurrentSubmissions" rules={[{ required: true }]}>
                    <InputNumber min={1} precision={0} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={8}>
                  <Form.Item label="最大运行数" name="maxInFlightApplications" rules={[{ required: true }]}>
                    <InputNumber min={0} precision={0} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
              </Row>
            </ComputeEngineFormSection>

            <ComputeEngineFormSection
              id="compute-engine-resources"
              title="运行资源策略"
              description="默认值用于未单独配置的任务，单次申请不得超过最大值"
              icon={<DashboardOutlined />}
              extra={(
                <span className="compute-engine-memory-unit-control">
                  <span>内存单位</span>
                  <Segmented
                    size="small"
                    value={memoryUnit}
                    disabled={!editingAllowed}
                    onChange={(value) => setMemoryUnit(value as 'GiB' | 'MiB')}
                    options={['GiB', 'MiB']}
                  />
                </span>
              )}
            >
              <div className="compute-engine-resource-column-labels" aria-hidden="true">
                <span>任务默认值</span>
                <span>单次最大值</span>
              </div>
              <div className="compute-engine-resource-group-title">Driver 资源</div>
              <Row gutter={14}>
                <Col xs={24} md={12}>
                  <Form.Item label={(
                    <span className="compute-engine-field-label">驱动 CPU<ContextHelp ariaLabel="驱动 CPU 说明" content="Spark Driver 可使用的 CPU 核数；Local Docker 映射为容器 --cpus。" /></span>
                  )} name={['resourcePolicy', 'defaults', 'driverCores']} rules={[{ required: true }]}>
                    <InputNumber min={1} max={256} precision={0} style={{ width: '100%' }} addonAfter="Core" />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item label="驱动 CPU" name={['resourcePolicy', 'maximums', 'driverCores']} rules={[{ required: true }]}>
                    <InputNumber min={1} max={256} precision={0} style={{ width: '100%' }} addonAfter="Core" />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item label={(
                    <span className="compute-engine-field-label">驱动内存<ContextHelp ariaLabel="驱动内存说明" content="Spark Driver 内存；Local Docker 映射为容器 --memory。" /></span>
                  )} name={['resourcePolicy', 'defaults', 'driverMemoryMiB']} getValueProps={memoryProps} normalize={toMiB} rules={[{ required: true }]}>
                    <InputNumber min={memoryUnit === 'MiB' ? 1024 : 1} max={memoryUnit === 'MiB' ? 1048576 : 1024} precision={0} style={{ width: '100%' }} addonAfter={memoryUnit} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item label="驱动内存" name={['resourcePolicy', 'maximums', 'driverMemoryMiB']} getValueProps={memoryProps} normalize={toMiB} rules={[{ required: true }]}>
                    <InputNumber min={memoryUnit === 'MiB' ? 1024 : 1} max={memoryUnit === 'MiB' ? 1048576 : 1024} precision={0} style={{ width: '100%' }} addonAfter={memoryUnit} />
                  </Form.Item>
                </Col>
              </Row>

              {!localDocker && <>
                <div className="compute-engine-resource-group-title">Executor 资源</div>
                <Row gutter={14}>
                  <Col xs={24} md={12}>
                    <Form.Item label="执行器数量" name={['resourcePolicy', 'defaults', 'executorInstances']} rules={[{ required: true }]}>
                      <InputNumber min={1} max={10000} precision={0} style={{ width: '100%' }} />
                    </Form.Item>
                  </Col>
                  <Col xs={24} md={12}>
                    <Form.Item label="执行器数量" name={['resourcePolicy', 'maximums', 'executorInstances']} rules={[{ required: true }]}>
                      <InputNumber min={1} max={10000} precision={0} style={{ width: '100%' }} />
                    </Form.Item>
                  </Col>
                  <Col xs={24} md={12}>
                    <Form.Item label="单执行器 CPU" name={['resourcePolicy', 'defaults', 'executorCores']} rules={[{ required: true }]}>
                      <InputNumber min={1} max={256} precision={0} style={{ width: '100%' }} addonAfter="Core" />
                    </Form.Item>
                  </Col>
                  <Col xs={24} md={12}>
                    <Form.Item label="单执行器 CPU" name={['resourcePolicy', 'maximums', 'executorCores']} rules={[{ required: true }]}>
                      <InputNumber min={1} max={256} precision={0} style={{ width: '100%' }} addonAfter="Core" />
                    </Form.Item>
                  </Col>
                  <Col xs={24} md={12}>
                    <Form.Item label="单执行器内存" name={['resourcePolicy', 'defaults', 'executorMemoryMiB']} getValueProps={memoryProps} normalize={toMiB} rules={[{ required: true }]}>
                      <InputNumber min={memoryUnit === 'MiB' ? 1024 : 1} max={memoryUnit === 'MiB' ? 1048576 : 1024} precision={0} style={{ width: '100%' }} addonAfter={memoryUnit} />
                    </Form.Item>
                  </Col>
                  <Col xs={24} md={12}>
                    <Form.Item label="单执行器内存" name={['resourcePolicy', 'maximums', 'executorMemoryMiB']} getValueProps={memoryProps} normalize={toMiB} rules={[{ required: true }]}>
                      <InputNumber min={memoryUnit === 'MiB' ? 1024 : 1} max={memoryUnit === 'MiB' ? 1048576 : 1024} precision={0} style={{ width: '100%' }} addonAfter={memoryUnit} />
                    </Form.Item>
                  </Col>
                </Row>
              </>}
            </ComputeEngineFormSection>
          </Form>
        </div>
      </div>
    </Drawer>
  </>;
};
