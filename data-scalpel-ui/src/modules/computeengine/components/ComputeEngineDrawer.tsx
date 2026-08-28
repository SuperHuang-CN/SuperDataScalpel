import { InfoCircleOutlined } from '@ant-design/icons';
import { Alert, Button, Col, Drawer, Form, Input, InputNumber, Modal, Row, Segmented, Select, Space, Tooltip, Typography, message } from 'antd';
import { useEffect, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { useCreateComputeEngine, useReconfigureComputeEngine, useUpdateComputeEngine } from '../hooks/useComputeEngines';
import {
  computeBackendTypeLabels,
  defaultSparkExecutionResourcePolicy,
  type ComputeBackendType,
  type ComputeEngine,
  type CreateComputeEngineRequest,
  type UpdateComputeEngineRequest,
  type SparkExecutionResourcePolicy,
} from '../model/computeEngine';

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

  return <>
    {messageContext}{modalContext}
    <Drawer
      rootClassName="business-overlay business-drawer-overlay"
      title={engine ? (editingAllowed ? '修改计算引擎' : '查看计算引擎') : '新建计算引擎'}
      open={open}
      size={720}
      onClose={close}
      destroyOnHidden
      footer={<Space>
        <Button onClick={close}>{editingAllowed ? '取消' : '关闭'}</Button>
        {editingAllowed && <Button type="primary" loading={pending} onClick={() => form.submit()}>
          {engine ? (reconfiguring ? (waitingForDrain ? '再次应用并重新注册' : '应用并重新注册') : '保存') : '创建'}
        </Button>}
      </Space>}
    >
      <Form<ComputeEngineFormValues> autoComplete="off" form={form} layout="vertical" onFinish={submit} disabled={!editingAllowed}>
        {engine?.registrationState === 'DETACHED' && <Alert
          type="warning"
          showIcon
          message="该计算引擎已离线解除绑定"
          description={`此状态只代表 Admin 已解除注册关系。重新注册前请确认原 Dispatcher 已永久停止。${engine.detachReason ? ` 解除原因：${engine.detachReason}` : ''}`}
          style={{ marginBottom: 12 }}
        />}
        {waitingForDrain && <Alert
          type="warning"
          showIcon
          message="计算引擎正在排空"
          description="仍有排队或活动任务。任务结束后，请保留当前配置并再次应用。"
          style={{ marginBottom: 12 }}
        />}
        <Row gutter={12}>
          <Col span={12}><Form.Item label="名称" name="name" rules={[{ required: true, whitespace: true }, { max: 100 }]}><Input autoFocus /></Form.Item></Col>
          <Col span={12}><Form.Item label="计算后端" name="expectedBackendType" rules={[{ required: true }]}><Select options={Object.entries(computeBackendTypeLabels).map(([value, label]) => ({ value, label }))} onChange={(backend: ComputeBackendType) => {
            if (!engine) form.setFieldValue('resourcePolicy', defaultSparkExecutionResourcePolicy(backend));
          }} /></Form.Item></Col>
          <Col span={24}><Form.Item label="Dispatcher 地址" name="dispatcherBaseUrl" extra="Admin 仅通过该地址管理 Dispatcher 注册状态。" rules={[{ required: true, type: 'url', message: '请输入有效的 HTTP(S) 地址' }, { max: 500 }]}><Input placeholder="http://127.0.0.1:18092" /></Form.Item></Col>
          <Col span={24}><Form.Item label={engine ? '访问 Token（留空保持不变）' : '访问 Token'} name="accessToken" rules={engine ? [{ max: 1000 }] : [{ required: true, whitespace: true }, { max: 1000 }]}><Input.Password name="compute-engine-access-token" autoComplete="off" /></Form.Item></Col>
          <Col span={24}><Form.Item label="命令 Topic" name="commandTopic" rules={[{ required: true, whitespace: true }, { max: 249 }]}><Input /></Form.Item></Col>
          <Col span={12}><Form.Item label="Runner 事件 Topic" name="runnerEventTopic" rules={[{ required: true, whitespace: true }, { max: 249 }]}><Input /></Form.Item></Col>
          <Col span={12}><Form.Item label="Admin 事件 Topic" name="adminEventTopic" rules={[{ required: true, whitespace: true }, { max: 249 }]}><Input /></Form.Item></Col>
          <Col span={8}><Form.Item label="最大排队数" name="maxQueuedExecutions" rules={[{ required: true }]}><InputNumber min={0} precision={0} style={{ width: '100%' }} /></Form.Item></Col>
          <Col span={8}><Form.Item label="并发提交数" name="maxConcurrentSubmissions" rules={[{ required: true }]}><InputNumber min={1} precision={0} style={{ width: '100%' }} /></Form.Item></Col>
          <Col span={8}><Form.Item label="最大运行数" name="maxInFlightApplications" extra="0 表示不额外限制"><InputNumber min={0} precision={0} style={{ width: '100%' }} /></Form.Item></Col>
          <Col span={24}>
            <div className="compute-engine-resource-policy-header">
              <Typography.Text strong>运行资源策略</Typography.Text>
              <Space size={8}><Typography.Text type="secondary">内存单位</Typography.Text><Segmented size="small" value={memoryUnit} onChange={(value) => setMemoryUnit(value as 'GiB' | 'MiB')} options={['GiB', 'MiB']} /></Space>
            </div>
          </Col>
          <Col span={24}><Typography.Text type="secondary">默认值会用于未单独设置资源的任务；单次任务申请不能超过最大值。</Typography.Text></Col>
          <Col span={12}><Form.Item label={<span>默认驱动 CPU <Tooltip title="Spark Driver 可使用的 CPU 核数；Local Docker 映射为容器 --cpus。"><InfoCircleOutlined /></Tooltip></span>} name={['resourcePolicy', 'defaults', 'driverCores']} rules={[{ required: true }]}><InputNumber min={1} max={256} precision={0} style={{ width: '100%' }} addonAfter="Core" /></Form.Item></Col>
          <Col span={12}><Form.Item label={<span>单次最大驱动 CPU <Tooltip title="任务申请的驱动 CPU 不能超过这个值。"><InfoCircleOutlined /></Tooltip></span>} name={['resourcePolicy', 'maximums', 'driverCores']} rules={[{ required: true }]}><InputNumber min={1} max={256} precision={0} style={{ width: '100%' }} addonAfter="Core" /></Form.Item></Col>
          <Col span={12}><Form.Item label={<span>默认驱动内存 <Tooltip title="Spark Driver 内存；Local Docker 映射为容器 --memory。"><InfoCircleOutlined /></Tooltip></span>} name={['resourcePolicy', 'defaults', 'driverMemoryMiB']} getValueProps={memoryProps} normalize={toMiB} rules={[{ required: true }]}><InputNumber min={memoryUnit === 'MiB' ? 1024 : 1} max={memoryUnit === 'MiB' ? 1048576 : 1024} precision={0} style={{ width: '100%' }} addonAfter={memoryUnit} /></Form.Item></Col>
          <Col span={12}><Form.Item label="单次最大驱动内存" name={['resourcePolicy', 'maximums', 'driverMemoryMiB']} getValueProps={memoryProps} normalize={toMiB} rules={[{ required: true }]}><InputNumber min={memoryUnit === 'MiB' ? 1024 : 1} max={memoryUnit === 'MiB' ? 1048576 : 1024} precision={0} style={{ width: '100%' }} addonAfter={memoryUnit} /></Form.Item></Col>
          {!localDocker && <>
            <Col span={8}><Form.Item label="默认执行器数量" name={['resourcePolicy', 'defaults', 'executorInstances']} rules={[{ required: true }]}><InputNumber min={1} max={10000} precision={0} style={{ width: '100%' }} /></Form.Item></Col>
            <Col span={8}><Form.Item label="单次最大执行器数量" name={['resourcePolicy', 'maximums', 'executorInstances']} rules={[{ required: true }]}><InputNumber min={1} max={10000} precision={0} style={{ width: '100%' }} /></Form.Item></Col>
            <Col span={8}><Form.Item label="默认单执行器 CPU" name={['resourcePolicy', 'defaults', 'executorCores']} rules={[{ required: true }]}><InputNumber min={1} max={256} precision={0} style={{ width: '100%' }} addonAfter="Core" /></Form.Item></Col>
            <Col span={12}><Form.Item label="单次最大单执行器 CPU" name={['resourcePolicy', 'maximums', 'executorCores']} rules={[{ required: true }]}><InputNumber min={1} max={256} precision={0} style={{ width: '100%' }} addonAfter="Core" /></Form.Item></Col>
            <Col span={12}><Form.Item label="默认单执行器内存" name={['resourcePolicy', 'defaults', 'executorMemoryMiB']} getValueProps={memoryProps} normalize={toMiB} rules={[{ required: true }]}><InputNumber min={memoryUnit === 'MiB' ? 1024 : 1} max={memoryUnit === 'MiB' ? 1048576 : 1024} precision={0} style={{ width: '100%' }} addonAfter={memoryUnit} /></Form.Item></Col>
            <Col span={12}><Form.Item label="单次最大单执行器内存" name={['resourcePolicy', 'maximums', 'executorMemoryMiB']} getValueProps={memoryProps} normalize={toMiB} rules={[{ required: true }]}><InputNumber min={memoryUnit === 'MiB' ? 1024 : 1} max={memoryUnit === 'MiB' ? 1048576 : 1024} precision={0} style={{ width: '100%' }} addonAfter={memoryUnit} /></Form.Item></Col>
          </>}
          <Col span={24}><Form.Item label="说明" name="description" rules={[{ max: 1000 }]}><Input.TextArea rows={3} maxLength={1000} showCount /></Form.Item></Col>
        </Row>
        {!editingAllowed && <div className="form-readonly-hint">{readonlyHint}</div>}
      </Form>
    </Drawer>
  </>;
};
