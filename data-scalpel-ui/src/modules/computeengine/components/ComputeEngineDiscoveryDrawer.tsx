import { CloudServerOutlined, ReloadOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Col, Drawer, Empty, Form, Input, InputNumber, Row, Space, Table, Tag, Typography } from 'antd';
import { useEffect, useId, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { ComputeEngineTopics } from './ComputeEngineTopics';
import { BusinessSecretInput } from '../../../shared/components/BusinessSecretInput';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { discoverComputeTargets, registerComputeTargets } from '../api/computeEngineApi';
import { computeEnginesKey } from '../hooks/useComputeEngines';
import { computeBackendTypeLabels, computeEngineRegistrationStateLabels,
  type ComputeTargetDiscovery, type DiscoverComputeTargetsRequest, type RegisterComputeTarget } from '../model/computeEngine';

type TargetRow = ComputeTargetDiscovery['targets'][number];
interface Values extends DiscoverComputeTargetsRequest { targets: Record<string, RegisterComputeTarget> }
const isChecking = (target: TargetRow['target']) => !target.ready && (target.checking === true
  // Older Admin/Dispatcher versions do not pass the explicit flag yet.
  || (target.issues.length === 1 && target.issues[0] === '目标就绪状态检查中，请稍后刷新'));
const blocked = ({ target, engine }: TargetRow) => !target.ready || !target.resourcePolicy
  || (target.registeredEngineId !== null && target.registeredEngineId !== engine?.id)
  || Boolean(engine && ['ACTIVE', 'DRAINING', 'REGISTERING', 'DETACHED'].includes(engine.registrationState));

export const ComputeEngineDiscoveryDrawer = ({ initialUrl, onClose }: { initialUrl?: string; onClose: () => void }) => {
  const [form] = Form.useForm<Values>();
  const [selected, setSelected] = useState<string[]>([]);
  const [error, setError] = useState<string>();
  const queryClient = useQueryClient();
  const registration = useMutation({ mutationFn: registerComputeTargets });
  const scope = useId();
  const sequence = useRef(0);
  const previousInstance = useRef<string | undefined>(undefined);
  const [connection, setConnection] = useState<{ request: DiscoverComputeTargetsRequest; sequence: number; deadline: number }>();
  const [timedOut, setTimedOut] = useState(false);
  const discovery = useQuery({
    queryKey: ['compute-target-discovery', scope, connection?.sequence],
    queryFn: ({ signal }) => discoverComputeTargets(connection!.request, signal),
    enabled: Boolean(connection) && !timedOut && !registration.isPending,
    gcTime: 0, retry: false, refetchOnWindowFocus: false, refetchOnReconnect: false,
    refetchInterval: (query) => !timedOut && query.state.status !== 'error'
      && Date.now() < (connection?.deadline ?? 0) && query.state.data?.targets.some(({ target }) => isChecking(target)) ? 2000 : false,
  });
  const busy = (discovery.isFetching && !discovery.data) || registration.isPending;
  const rows = discovery.data?.targets ?? [];
  const pending = rows.some(({ target }) => isChecking(target));

  useEffect(() => {
    if (!connection || !pending || discovery.isError || timedOut) return;
    const timer = window.setTimeout(() => {
      setTimedOut(true);
      void queryClient.cancelQueries({ queryKey: ['compute-target-discovery', scope, connection.sequence] });
    }, Math.max(0, connection.deadline - Date.now()));
    return () => window.clearTimeout(timer);
  }, [connection, pending, discovery.isError, timedOut, queryClient, scope]);

  useEffect(() => {
    const result = discovery.data;
    if (!result) return;
    const drafts = form.getFieldsValue(true).targets ?? {};
    const sameInstance = previousInstance.current === result.dispatcherInstanceId;
    form.setFieldValue('targets', Object.fromEntries(result.targets.map(({ target, engine }) => {
      const draft = drafts[target.targetKey];
      if (!engine && sameInstance && draft?.targetFingerprint === target.targetFingerprint) {
        return [target.targetKey, { ...draft, resourcePolicy: target.resourcePolicy }];
      }
      return [target.targetKey, {
        targetKey: target.targetKey, targetFingerprint: target.targetFingerprint, name: engine?.name ?? target.name,
        maxQueuedExecutions: engine?.maxQueuedExecutions ?? 20,
        maxConcurrentSubmissions: engine?.maxConcurrentSubmissions ?? 2,
        maxInFlightApplications: engine?.maxInFlightApplications ?? 2,
        resourcePolicy: target.resourcePolicy,
      }];
    })));
    previousInstance.current = result.dispatcherInstanceId;
    setSelected((previous) => previous.filter((key) => result.targets.some((row) => row.target.targetKey === key && !blocked(row))));
  }, [discovery.data, form]);

  const connect = async () => {
    if (busy) return;
    setError(undefined);
    try {
      const values = await form.validateFields(['dispatcherBaseUrl', 'accessToken']);
      setTimedOut(false);
      setConnection({ request: { dispatcherBaseUrl: values.dispatcherBaseUrl.trim(), accessToken: values.accessToken },
        sequence: ++sequence.current, deadline: Date.now() + 60_000 });
    } catch (failure) {
      if (failure instanceof ApiError) setError(failure.message);
    }
  };

  const register = async () => {
    if (busy || !discovery.data || selected.length === 0) return;
    setError(undefined);
    try {
      await queryClient.cancelQueries({ queryKey: ['compute-target-discovery', scope] });
      await form.validateFields(selected.map((key) => ['targets', key]), { recursive: true });
      const values = form.getFieldsValue(true);
      const result = await registration.mutateAsync({
        dispatcherBaseUrl: values.dispatcherBaseUrl.trim(), accessToken: values.accessToken,
        dispatcherInstanceId: discovery.data.dispatcherInstanceId,
        targets: selected.map((key) => values.targets[key]),
      });
      setSelected(result.items.filter((item) => !item.success).map((item) => item.targetKey));
      await queryClient.invalidateQueries({ queryKey: [computeEnginesKey] });
      await connect();
    } catch (failure) {
      if (failure instanceof ApiError) setError(failure.message);
    }
  };

  const capacity = (row: TargetRow) => {
    const key = row.target.targetKey;
    return <div>
      <Typography.Paragraph type="secondary">{row.engine ? '已有引擎沿用保存的配置；修改请进入引擎详情。' : '消息通道和后端连接由 Dispatcher 部署配置管理，Admin 读取后登记所选目标。'}</Typography.Paragraph>
      {discovery.data?.messaging ? <ComputeEngineTopics engine={discovery.data.messaging} /> : row.engine ? <ComputeEngineTopics engine={row.engine} /> : <Typography.Paragraph type="secondary">此 Dispatcher 使用旧版引擎独占通道。升级后可从部署配置发现实例共享通道。</Typography.Paragraph>}
      <Row gutter={16}>
        {([['maxQueuedExecutions', '最大排队数', 0, 100000], ['maxConcurrentSubmissions', '并发提交数', 1, 64], ['maxInFlightApplications', '最大运行数（0 不限制）', 0, 100000]] as const).map(([field, label, min, max]) => <Col span={8} key={field}>
          <Form.Item label={label} name={['targets', key, field]} rules={[{ required: true }]}><InputNumber min={min} max={max} precision={0} disabled={busy || Boolean(row.engine)} style={{ width: '100%' }} /></Form.Item>
        </Col>)}
      </Row>
      <Row gutter={16}>
        {(['defaults', 'maximums'] as const).map((policy) => <Col span={12} key={policy}>
          <Typography.Text strong>{policy === 'defaults' ? '任务默认资源（Dispatcher 只读）' : '单次任务上限（Dispatcher 只读）'}</Typography.Text>
          {([['driverCores', 'Driver CPU（核）'], ['driverMemoryMiB', 'Driver 内存（MiB）'], ['executorInstances', 'Executor 数量'], ['executorCores', '单 Executor CPU（核）'], ['executorMemoryMiB', '单 Executor 内存（MiB）']] as const)
            .filter(([field]) => row.target.backendType !== 'LOCAL_DOCKER' || field.startsWith('driver'))
            .map(([field, label]) => <Form.Item key={field} label={label} tooltip={field.endsWith('MiB') ? (row.target.backendType === 'LOCAL_DOCKER' ? '容器总内存，JVM 堆使用其中的 75%。' : 'JVM 堆内存；集群容器还需额外非堆内存，不是容器总额度。') : undefined}>
              <Typography.Text>{row.target.resourcePolicy?.[policy][field] ?? '未提供，请升级 Dispatcher'}</Typography.Text>
            </Form.Item>)}
        </Col>)}
      </Row>
    </div>;
  };

  return <Drawer open width={1000} onClose={onClose} closable={!busy} maskClosable={!busy} keyboard={!busy}
    rootClassName="business-overlay business-drawer-overlay resource-workspace-overlay"
    title={<Space><CloudServerOutlined /><span>连接 Dispatcher<Typography.Text type="secondary" style={{ display: 'block', fontSize: 12 }}>发现执行目标，勾选后逐个注册为计算引擎</Typography.Text></span></Space>}
    footer={<div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
      <Typography.Text type="secondary">已选 {selected.length} 个目标 · 成功项保留，失败项可重试</Typography.Text>
      <Space><Button onClick={onClose} disabled={busy}>关闭</Button><Button type="primary" loading={registration.isPending} disabled={busy || selected.length === 0} onClick={() => void register()}>注册所选目标</Button></Space>
    </div>}>
    <Form form={form} layout="vertical" autoComplete="off" initialValues={{ dispatcherBaseUrl: initialUrl }} disabled={busy}
      onValuesChange={(changed: Partial<Values>) => {
        if ('dispatcherBaseUrl' in changed || 'accessToken' in changed) {
          setConnection(undefined); setTimedOut(false); previousInstance.current = undefined;
          registration.reset(); setSelected([]); setError(undefined);
        }
      }}>
      <Row gutter={16} align="bottom">
        <Col span={12}><Form.Item name="dispatcherBaseUrl" label="Dispatcher 地址" rules={[{ required: true, whitespace: true, message: '请输入 Dispatcher 地址' }]}>
          <Input placeholder="http://192.168.5.102:18092" />
        </Form.Item></Col>
        <Col span={8}><Form.Item name="accessToken" label="访问 Token" rules={[{ required: true, whitespace: true, message: '请输入访问 Token' }]}>
          <BusinessSecretInput name="dispatcherDiscoveryToken" />
        </Form.Item></Col>
        <Col span={4}><Form.Item><Button block icon={<ReloadOutlined />} loading={busy && !registration.isPending} onClick={() => void connect()}>连接并发现</Button></Form.Item></Col>
      </Row>
      {error && <InlineFeedback tone="error" label={error} />}
      {discovery.isError && <InlineFeedback tone="error" label={discovery.error instanceof ApiError ? discovery.error.message : '获取检查结果失败，请重试'} />}
      {timedOut && pending && <InlineFeedback tone="warning" label="就绪检查等待超过 60 秒，已停止自动刷新；请点击连接并发现重试。" />}
      {pending && !timedOut && !discovery.isError && <Typography.Paragraph type="secondary" role="status">正在检查执行目标，每 2 秒自动刷新，最多等待 60 秒。</Typography.Paragraph>}
      {discovery.data && <Typography.Paragraph type="secondary">实例 {discovery.data.dispatcherInstanceId} · {rows.length} 个已启用目标</Typography.Paragraph>}
      {registration.data && <Typography.Paragraph role="status">本次成功 {registration.data.items.filter((item) => item.success).length} 个，失败 {registration.data.items.filter((item) => !item.success).length} 个</Typography.Paragraph>}
      <Table<TargetRow> size="small" className="management-table" rowKey={(row) => row.target.targetKey} dataSource={rows} pagination={false}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={discovery.data ? '此 Dispatcher 没有启用的执行目标' : '填写地址和 Token 后连接'} /> }}
        rowSelection={{ selectedRowKeys: selected, onChange: (keys) => setSelected(keys.map(String)), getCheckboxProps: (row) => ({ disabled: busy || blocked(row) }) }}
        expandable={{ expandedRowRender: capacity, expandRowByClick: false, columnTitle: '策略' }}
        columns={[
          { title: '执行目标', width: 180, render: (_, { target }) => <><Typography.Text strong>{target.name}</Typography.Text><div><Typography.Text type="secondary" copyable={{ text: target.targetKey }}>目标 Key：{target.targetKey}</Typography.Text></div></> },
          { title: '计算后端', width: 160, render: (_, { target }) => computeBackendTypeLabels[target.backendType] },
          { title: '引擎名称', width: 220, render: (_, { target, engine }) => engine ? <Link to={`/compute-engine/${engine.id}`} onClick={onClose}>{engine.name}</Link> : <Form.Item name={['targets', target.targetKey, 'name']} style={{ margin: 0 }} rules={[{ required: true, whitespace: true, message: '请输入引擎名称' }, { max: 100 }]}><Input autoComplete="off" aria-label={`${target.name}的引擎名称`} /></Form.Item> },
          { title: '状态', render: (_, row) => {
            const result = registration.data?.items.find((item) => item.targetKey === row.target.targetKey);
            return <Space orientation="vertical" size={2}>
              <Tag color={row.target.ready ? 'success' : isChecking(row.target) && !timedOut ? 'processing' : 'warning'}>{row.target.ready ? '目标就绪' : isChecking(row.target) ? (timedOut ? '检查超时' : discovery.isError ? '检查结果获取失败' : '检查中') : '目标未就绪'}</Tag>
              <Typography.Text type="secondary">{row.engine ? computeEngineRegistrationStateLabels[row.engine.registrationState] : row.target.registeredEngineId ? '其他引擎已占用' : '未注册'}</Typography.Text>
              {!row.target.ready && !isChecking(row.target) && <Typography.Text type="danger">{row.target.issues.join('；')}</Typography.Text>}
              {result && !result.success && <Typography.Text type="danger">{result.error}</Typography.Text>}
            </Space>;
          } },
        ]} />
    </Form>
  </Drawer>;
};
