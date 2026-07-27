import { Alert, Button, Col, Drawer, Form, Input, InputNumber, Modal, Row, Select, Space, message } from 'antd';
import { useEffect, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { useCreateComputeEngine, useReconfigureComputeEngine, useUpdateComputeEngine } from '../hooks/useComputeEngines';
import {
  computeBackendTypeLabels,
  type ComputeBackendType,
  type ComputeEngine,
  type CreateComputeEngineRequest,
  type UpdateComputeEngineRequest,
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
    } : defaults);
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

  const submit = (values: ComputeEngineFormValues) => {
    if (engine?.registrationState !== 'ACTIVE' || waitingForDrain) {
      void save(values);
      return;
    }
    modalApi.confirm({
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
      <Form<ComputeEngineFormValues> form={form} layout="vertical" onFinish={submit} disabled={!editingAllowed}>
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
          <Col span={12}><Form.Item label="计算后端" name="expectedBackendType" rules={[{ required: true }]}><Select options={Object.entries(computeBackendTypeLabels).map(([value, label]) => ({ value, label }))} /></Form.Item></Col>
          <Col span={24}><Form.Item label="Dispatcher 地址" name="dispatcherBaseUrl" extra="Admin 仅通过该地址管理 Dispatcher 注册状态。" rules={[{ required: true, type: 'url', message: '请输入有效的 HTTP(S) 地址' }, { max: 500 }]}><Input placeholder="http://127.0.0.1:18092" /></Form.Item></Col>
          <Col span={24}><Form.Item label={engine ? '访问 Token（留空保持不变）' : '访问 Token'} name="accessToken" rules={engine ? [{ max: 1000 }] : [{ required: true, whitespace: true }, { max: 1000 }]}><Input.Password autoComplete="new-password" /></Form.Item></Col>
          <Col span={24}><Form.Item label="命令 Topic" name="commandTopic" rules={[{ required: true, whitespace: true }, { max: 249 }]}><Input /></Form.Item></Col>
          <Col span={12}><Form.Item label="Runner 事件 Topic" name="runnerEventTopic" rules={[{ required: true, whitespace: true }, { max: 249 }]}><Input /></Form.Item></Col>
          <Col span={12}><Form.Item label="Admin 事件 Topic" name="adminEventTopic" rules={[{ required: true, whitespace: true }, { max: 249 }]}><Input /></Form.Item></Col>
          <Col span={8}><Form.Item label="最大排队数" name="maxQueuedExecutions" rules={[{ required: true }]}><InputNumber min={0} precision={0} style={{ width: '100%' }} /></Form.Item></Col>
          <Col span={8}><Form.Item label="并发提交数" name="maxConcurrentSubmissions" rules={[{ required: true }]}><InputNumber min={1} precision={0} style={{ width: '100%' }} /></Form.Item></Col>
          <Col span={8}><Form.Item label="最大运行数" name="maxInFlightApplications" extra="0 表示不额外限制"><InputNumber min={0} precision={0} style={{ width: '100%' }} /></Form.Item></Col>
          <Col span={24}><Form.Item label="说明" name="description" rules={[{ max: 1000 }]}><Input.TextArea rows={3} maxLength={1000} showCount /></Form.Item></Col>
        </Row>
        {!editingAllowed && <div className="form-readonly-hint">{readonlyHint}</div>}
      </Form>
    </Drawer>
  </>;
};
