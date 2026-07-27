import { SaveOutlined } from '@ant-design/icons';
import { Alert, Button, InputNumber, Modal, Space, Spin, Tag, Typography, message } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useBlocker, type BlockerFunction } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { CanvasDesigner } from '../canvas/CanvasDesigner';
import type { CanvasDefinition } from '../canvas/canvasTypes';
import {
  useCanvasTaskDefinition,
  useTaskStreamingConfiguration,
  useUpdateCanvasTaskDefinition,
  useUpdateTaskStreamingConfiguration,
} from '../hooks/useTasks';
import type { DataTask } from '../model/task';

interface CanvasTaskDefinitionPanelProps {
  task: DataTask;
  canUpdate: boolean;
  onDirtyChange: (dirty: boolean) => void;
  protectNavigation?: boolean;
}

const definitionFingerprint = (definition: CanvasDefinition | null | undefined) => (
  definition ? JSON.stringify(definition) : null
);

export const CanvasTaskDefinitionPanel = ({
  task,
  canUpdate,
  onDirtyChange,
  protectNavigation = true,
}: CanvasTaskDefinitionPanelProps) => {
  const [messageApi, messageContext] = message.useMessage();
  const [currentDefinition, setCurrentDefinition] = useState<CanvasDefinition | null>(null);
  const [inspectorDirty, setInspectorDirty] = useState(false);
  const [triggerIntervalOverride, setTriggerIntervalOverride] = useState<number | null>(null);
  const definitionQuery = useCanvasTaskDefinition(task.id);
  const streaming = task.type === 'SPARK_STREAMING_CANVAS';
  const streamingConfigurationQuery = useTaskStreamingConfiguration(task.id, streaming);
  const saveMutation = useUpdateCanvasTaskDefinition();
  const saveStreamingConfigurationMutation = useUpdateTaskStreamingConfiguration();
  const editable = task.status === 'DRAFT' || task.status === 'DISABLED';
  const savedDefinition = definitionQuery.data?.definition;
  const savedFingerprint = useMemo(() => definitionFingerprint(savedDefinition), [savedDefinition]);
  const currentFingerprint = useMemo(() => definitionFingerprint(currentDefinition), [currentDefinition]);
  const dirty = currentFingerprint !== null && currentFingerprint !== savedFingerprint;
  const triggerIntervalSeconds = triggerIntervalOverride
    ?? streamingConfigurationQuery.data?.triggerIntervalSeconds
    ?? null;
  const streamingConfigurationDirty = streaming
    && triggerIntervalOverride !== null
    && streamingConfigurationQuery.data !== undefined
    && triggerIntervalOverride !== streamingConfigurationQuery.data.triggerIntervalSeconds;
  const hasPendingChanges = dirty || inspectorDirty || streamingConfigurationDirty;
  const blocker = useBlocker(useCallback<BlockerFunction>(
    ({ currentLocation, nextLocation }) => protectNavigation && hasPendingChanges && (
      currentLocation.pathname !== nextLocation.pathname || currentLocation.search !== nextLocation.search
    ),
    [hasPendingChanges, protectNavigation],
  ));

  useEffect(() => {
    onDirtyChange(hasPendingChanges);
    return () => onDirtyChange(false);
  }, [hasPendingChanges, onDirtyChange]);

  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => {
      if (!hasPendingChanges) return;
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [hasPendingChanges]);

  useEffect(() => {
    if ((!protectNavigation || !hasPendingChanges) && blocker.state === 'blocked') blocker.reset();
  }, [blocker, hasPendingChanges, protectNavigation]);

  const save = async () => {
    if (!currentDefinition) return;
    if (inspectorDirty) {
      messageApi.warning('请先应用或放弃当前节点配置');
      return;
    }
    try {
      const saved = await saveMutation.mutateAsync({ id: task.id, definition: currentDefinition });
      messageApi.success(`Canvas 定义已保存为 v${saved.version}`);
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '保存 Canvas 定义失败');
    }
  };

  const saveStreamingConfiguration = async () => {
    if (triggerIntervalSeconds === null) return;
    try {
      await saveStreamingConfigurationMutation.mutateAsync({
        id: task.id,
        request: { triggerIntervalSeconds },
      });
      setTriggerIntervalOverride(null);
      messageApi.success('微批间隔已保存');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '保存微批间隔失败');
    }
  };

  if (definitionQuery.isLoading) return <Spin tip="正在加载 Canvas 定义…" />;
  if (!definitionQuery.data) {
    return <Alert type="error" showIcon message="Canvas 定义加载失败" description="请稍后重试。" />;
  }

  return (
    <div className="task-detail-tab-panel task-canvas-definition-panel">
      {messageContext}
      {!editable && (
        <Alert
          type={definitionQuery.data.configured ? 'info' : 'error'}
          showIcon
          message={definitionQuery.data.configured
            ? '任务已发布，当前定义不能保存。停用任务后才能修改定义。'
            : '任务已发布，但 Canvas 定义缺失'}
          description={definitionQuery.data.configured
            ? undefined
            : '当前任务无法运行。请先停用任务，再重新配置 Canvas 定义或删除任务。'}
          className="task-canvas-readonly-alert"
        />
      )}
      <CanvasDesigner
        initialDefinition={definitionQuery.data.definition}
        onDefinitionChange={setCurrentDefinition}
        onInspectorDirtyChange={setInspectorDirty}
        executionMode={streaming ? 'STREAMING' : 'BATCH'}
        toolbarLeading={(
          <Space wrap size={8}>
            <Typography.Text strong>Canvas 定义</Typography.Text>
            <Tag color={definitionQuery.data.configured ? 'success' : 'default'}>
              {definitionQuery.data.configured ? `v${definitionQuery.data.version}` : '未配置'}
            </Tag>
            {dirty && <Tag color="processing">有未保存修改</Tag>}
            {inspectorDirty && <Tag color="warning">节点配置尚未应用</Tag>}
            {streaming && (
              <Space size={4}>
                <Typography.Text type="secondary">微批间隔</Typography.Text>
                <InputNumber
                  min={1}
                  max={300}
                  value={triggerIntervalSeconds}
                  disabled={!editable || streamingConfigurationQuery.isLoading}
                  addonAfter="秒"
                  onChange={setTriggerIntervalOverride}
                />
                {canUpdate && editable && (
                  <Button
                    loading={saveStreamingConfigurationMutation.isPending}
                    disabled={!streamingConfigurationDirty}
                    onClick={() => void saveStreamingConfiguration()}
                  >
                    保存间隔
                  </Button>
                )}
              </Space>
            )}
          </Space>
        )}
        toolbarTrailing={canUpdate && editable ? (
          <Button
            type="primary"
            icon={<SaveOutlined />}
            loading={saveMutation.isPending}
            disabled={!dirty || inspectorDirty}
            onClick={() => void save()}
          >
            保存定义
          </Button>
        ) : null}
      />
      <Modal
        open={blocker.state === 'blocked'}
        title="离开未保存的 Canvas 定义？"
        okText="放弃并离开"
        okButtonProps={{ danger: true }}
        cancelText="继续编辑"
        closable={false}
        mask={{ closable: false }}
        onOk={() => blocker.proceed?.()}
        onCancel={() => blocker.reset?.()}
      >
        {inspectorDirty
          ? '当前节点配置尚未应用，离开后节点配置和其他画布修改都会丢失。'
          : '当前 Canvas 定义尚未保存，离开后这些修改会丢失。'}
      </Modal>
    </div>
  );
};
