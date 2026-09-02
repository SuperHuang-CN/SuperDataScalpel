import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import { CloseOutlined, RobotOutlined, SaveOutlined } from '@ant-design/icons';
import { Button, Modal, Space, Spin, Tag, Typography, message } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useBlocker, type BlockerFunction } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { CanvasDesigner, type CanvasDesignerHandle } from '../canvas/CanvasDesigner';
import { canvasDefinitionFingerprint } from '../canvas/canvasSerialization';
import { emptyCanvasDefinition } from '../canvas/defaultCanvas';
import {
  CANVAS_SCHEMA_MINOR_VERSION,
  CANVAS_SCHEMA_VERSION,
  type CanvasDefinition,
} from '../canvas/canvasTypes';
import {
  useCanvasTaskDefinition,
  useAcceptTaskCanvasProposal,
  useTaskCanvasProposal,
  useUpdateCanvasTaskDefinition,
} from '../hooks/useTasks';
import type { DataTask } from '../model/task';

interface CanvasTaskDefinitionPanelProps {
  task: DataTask;
  canUpdate: boolean;
  toolbarContext?: ReactNode;
  onDirtyChange?: (dirty: boolean) => void;
  protectNavigation?: boolean;
  onCancelEdit?: () => void;
  assistantChangeSetId?: string;
}

export const CanvasTaskDefinitionPanel = ({
  task,
  canUpdate,
  toolbarContext,
  onDirtyChange,
  protectNavigation = true,
  onCancelEdit,
  assistantChangeSetId,
}: CanvasTaskDefinitionPanelProps) => {
  const [messageApi, messageContext] = message.useMessage();
  const [currentDefinition, setCurrentDefinition] = useState<CanvasDefinition | null>(null);
  const [replacementDefinition, setReplacementDefinition] = useState<CanvasDefinition | null>(null);
  const [reconfigureConfirmOpen, setReconfigureConfirmOpen] = useState(false);
  const [cancelEditConfirmOpen, setCancelEditConfirmOpen] = useState(false);
  const [inspectorDirty, setInspectorDirty] = useState(false);
  const [savedFingerprintOverride, setSavedFingerprintOverride] = useState<{
    taskId: string;
    fingerprint: string | null;
  } | null>(null);
  const [savingAndLeaving, setSavingAndLeaving] = useState(false);
  const designerRef = useRef<CanvasDesignerHandle>(null);
  const definitionQuery = useCanvasTaskDefinition(task.id);
  const streaming = task.type === 'SPARK_STREAMING_CANVAS';
  const saveMutation = useUpdateCanvasTaskDefinition();
  const proposalQuery = useTaskCanvasProposal(assistantChangeSetId);
  const acceptProposalMutation = useAcceptTaskCanvasProposal();
  const editable = task.status === 'DRAFT' || task.status === 'DISABLED';
  const querySavedFingerprint = useMemo(
    () => canvasDefinitionFingerprint(definitionQuery.data?.definition),
    [definitionQuery.data?.definition],
  );
  const savedFingerprint = savedFingerprintOverride?.taskId === task.id
    ? savedFingerprintOverride.fingerprint
    : querySavedFingerprint;
  const currentFingerprint = useMemo(
    () => canvasDefinitionFingerprint(currentDefinition),
    [currentDefinition],
  );
  const dirty = currentFingerprint !== null && currentFingerprint !== savedFingerprint;
  const hasPendingChanges = dirty || inspectorDirty;
  const blocker = useBlocker(useCallback<BlockerFunction>(
    ({ currentLocation, nextLocation }) => protectNavigation && hasPendingChanges && (
      currentLocation.pathname !== nextLocation.pathname || currentLocation.search !== nextLocation.search
    ),
    [hasPendingChanges, protectNavigation],
  ));

  useEffect(() => {
    onDirtyChange?.(hasPendingChanges);
    return () => onDirtyChange?.(false);
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
    if (!savingAndLeaving
      && (!protectNavigation || !hasPendingChanges)
      && blocker.state === 'blocked') blocker.reset();
  }, [blocker, hasPendingChanges, protectNavigation, savingAndLeaving]);

  const persistCanvasDefinition = async (
    definition: CanvasDefinition,
    announceSuccess: boolean,
  ): Promise<boolean> => {
    try {
      const saved = await saveMutation.mutateAsync({ id: task.id, definition });
      setReplacementDefinition(null);
      setCurrentDefinition(saved.definition);
      setSavedFingerprintOverride({
        taskId: task.id,
        fingerprint: canvasDefinitionFingerprint(saved.definition),
      });
      if (announceSuccess) messageApi.success(`Canvas 定义已保存为 v${saved.version}`);
      return true;
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '保存 Canvas 定义失败');
      return false;
    }
  };

  const save = async () => {
    if (!currentDefinition) return;
    if (inspectorDirty) {
      messageApi.warning('请先应用或放弃当前节点配置');
      return;
    }
    await persistCanvasDefinition(currentDefinition, true);
  };

  const saveAllPendingChanges = async (): Promise<boolean> => {
    let definitionToSave = currentDefinition;
    if (inspectorDirty) {
      definitionToSave = await designerRef.current?.applyPendingInspector() ?? null;
      if (!definitionToSave) {
        messageApi.warning('当前节点配置校验未通过，请修正后再离开');
        return false;
      }
      setCurrentDefinition(definitionToSave);
      setInspectorDirty(false);
    }
    const definitionNeedsSave = definitionToSave !== null
      && canvasDefinitionFingerprint(definitionToSave) !== savedFingerprint;
    if (definitionNeedsSave && definitionToSave) {
      const canvasSaved = await persistCanvasDefinition(definitionToSave, false);
      if (!canvasSaved) return false;
    }
    messageApi.success('修改已保存');
    return true;
  };

  const saveAndLeave = async (leave: () => void) => {
    setSavingAndLeaving(true);
    try {
      if (await saveAllPendingChanges()) leave();
    } finally {
      setSavingAndLeaving(false);
    }
  };

  const requestCancelEdit = () => {
    if (!onCancelEdit) return;
    if (hasPendingChanges) {
      setCancelEditConfirmOpen(true);
      return;
    }
    onCancelEdit();
  };

  if (definitionQuery.isLoading) return <Spin tip="正在加载 Canvas 定义…" />;
  if (!definitionQuery.data) {
    return <Alert type="error" showIcon message="Canvas 定义加载失败" description="请稍后重试。" />;
  }
  const incompatible = definitionQuery.data.loadStatus === 'INCOMPATIBLE';
  if (incompatible && !replacementDefinition) {
    return (
      <div className="task-detail-tab-panel task-canvas-definition-panel task-definition-incompatible">
        {messageContext}
        <Alert
          type="warning"
          showIcon
          message="旧 Canvas 定义无法在当前编辑器中打开"
          description={definitionQuery.data.message
            ?? `当前定义使用 Canvas ${definitionQuery.data.schemaVersion}.${definitionQuery.data.schemaMinorVersion}，请重新配置。`}
          action={(
            <Space>
              {onCancelEdit && (
                <Button icon={<CloseOutlined />} onClick={requestCancelEdit}>
                  取消编辑
                </Button>
              )}
              <Button
                type="primary"
                disabled={!canUpdate || !editable}
                onClick={() => setReconfigureConfirmOpen(true)}
              >
                重新配置
              </Button>
            </Space>
          )}
        />
        <Typography.Paragraph type="secondary" style={{ marginTop: 12 }}>
          重新配置会从空白 Canvas {CANVAS_SCHEMA_VERSION}.{CANVAS_SCHEMA_MINOR_VERSION} 开始。
          旧定义会保留到你主动保存新定义时才被覆盖。
        </Typography.Paragraph>
        <Modal
          open={reconfigureConfirmOpen}
          title="重新配置 Canvas 定义？"
          okText="从空白画布开始"
          cancelText="取消"
          onOk={() => {
            const emptyDefinition = emptyCanvasDefinition();
            setReplacementDefinition(emptyDefinition);
            setCurrentDefinition(emptyDefinition);
            setInspectorDirty(false);
            setReconfigureConfirmOpen(false);
          }}
          onCancel={() => setReconfigureConfirmOpen(false)}
        >
          当前 Canvas {definitionQuery.data.schemaVersion}.{definitionQuery.data.schemaMinorVersion} 定义无法转换为
          Canvas {CANVAS_SCHEMA_VERSION}.{CANVAS_SCHEMA_MINOR_VERSION}。确认后只会在内存中创建空白定义，
          保存前不会修改后台数据。
        </Modal>
      </div>
    );
  }

  const initialDefinition = replacementDefinition ?? definitionQuery.data.definition;
  if (!initialDefinition) {
    return <Alert type="error" showIcon message="Canvas 定义内容缺失" />;
  }

  const applyAssistantProposal = () => {
    if (!assistantChangeSetId || !proposalQuery.data) return;
    if (hasPendingChanges) {
      messageApi.warning('当前 Canvas 或节点配置存在未保存修改，请先保存或放弃修改后再应用 AI 提案');
      return;
    }
    const execute = async () => {
      try {
        const changeSet = await acceptProposalMutation.mutateAsync({
          id: assistantChangeSetId,
          taskId: task.id,
        });
        const definition = changeSet.taskCanvasResult?.definition;
        if (!definition) throw new Error('提案未返回 Canvas 定义');
        designerRef.current?.replaceDefinition(definition);
        messageApi.success('AI 提案已应用为未保存的 Canvas 草稿，请检查编译结果后手动保存');
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.message : '应用 AI Canvas 提案失败');
        throw error;
      }
    };
    if ((currentDefinition ?? initialDefinition).nodes.length > 0) {
      Modal.confirm({
        rootClassName: 'business-overlay business-modal-overlay',
        title: '完整替换当前 Canvas？',
        content: 'AI V1 只支持完整 Canvas 提案。确认后仅替换当前内存画布，后台已保存定义不会改变，直到你点击“保存定义”。',
        okText: '替换为 AI 提案',
        cancelText: '取消',
        onOk: execute,
      });
      return;
    }
    void execute().catch(() => undefined);
  };

  const proposal = proposalQuery.data?.taskCanvasProposal;

  return (
    <div className="task-detail-tab-panel task-canvas-definition-panel">
      {messageContext}
      {assistantChangeSetId && proposalQuery.isLoading && (
        <Alert showIcon type="info" message="正在加载 AI Canvas 提案…" className="task-canvas-proposal-alert" />
      )}
      {assistantChangeSetId && proposalQuery.error && (
        <Alert
          showIcon
          type="warning"
          message="AI Canvas 提案加载失败"
          description={proposalQuery.error instanceof ApiError ? proposalQuery.error.message : '请返回 AI 助手后重试。'}
          className="task-canvas-proposal-alert"
        />
      )}
      {proposalQuery.data && proposal && (
        <Alert
          showIcon
          icon={<RobotOutlined />}
          type={proposalQuery.data.status === 'PENDING' ? 'info'
            : proposalQuery.data.status === 'APPLIED' ? 'success' : 'warning'}
          message={<Space wrap>
            <Typography.Text strong>AI Canvas 完整替换提案</Typography.Text>
            <Tag>{proposal.nodeCount} 个节点</Tag>
            <Tag>{proposal.plan.inputs.length} 个输入</Tag>
            <Tag>{proposal.plan.steps.length} 个处理步骤</Tag>
            <Tag>{proposal.plan.output.type}</Tag>
          </Space>}
          description={<Space direction="vertical" size={4}>
            <Typography.Text>{proposal.summary}</Typography.Text>
            <Typography.Text type="secondary">
              {proposal.plan.inputs.map((input) => `${input.name}（${input.type}）`).join('、')}
              {' → '}{proposal.plan.steps.map((step) => step.name).join(' → ') || '直接输出'}
              {' → '}{proposal.plan.output.name}
            </Typography.Text>
            {proposal.assumptions.length > 0 && (
              <Typography.Text type="secondary">假设：{proposal.assumptions.join('；')}</Typography.Text>
            )}
            {proposal.needsUserInput.length > 0 && (
              <Typography.Text type="warning">待补配置：{proposal.needsUserInput.join('；')}</Typography.Text>
            )}
            <Typography.Text type="secondary">
              应用只会生成当前页面的未保存草稿，仍需检查 Task Engine 编译结果并手动保存。
            </Typography.Text>
          </Space>}
          action={(proposalQuery.data.status === 'PENDING' || proposalQuery.data.status === 'APPLIED') ? (
            <Button
              type="primary"
              icon={<RobotOutlined />}
              disabled={hasPendingChanges}
              loading={acceptProposalMutation.isPending}
              onClick={applyAssistantProposal}
            >
              {proposalQuery.data.status === 'APPLIED' ? '重新应用提案' : '应用提案'}
            </Button>
          ) : undefined}
          className="task-canvas-proposal-alert"
        />
      )}
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
        ref={designerRef}
        initialDefinition={initialDefinition}
        onDefinitionChange={setCurrentDefinition}
        onInspectorDirtyChange={setInspectorDirty}
        executionMode={streaming ? 'STREAMING' : 'BATCH'}
        trialContext={{
          taskId: task.id,
          baseDefinitionVersion: definitionQuery.data.version,
          taskStatus: task.status,
        }}
        toolbarLeading={(
          <Space wrap size={8}>
            {toolbarContext ?? <Typography.Text strong>Canvas 定义</Typography.Text>}
            <Tag color={incompatible ? 'warning' : definitionQuery.data.configured ? 'success' : 'default'}>
              {incompatible
                ? `正在替换旧定义 v${definitionQuery.data.version}`
                : definitionQuery.data.configured ? `v${definitionQuery.data.version}` : '未配置'}
            </Tag>
            {dirty && <Tag color="processing">有未保存修改</Tag>}
            {inspectorDirty && <Tag color="warning">节点配置尚未应用</Tag>}
            {streaming && <Tag color="processing">触发间隔由无界输入节点配置</Tag>}
          </Space>
        )}
        toolbarTrailing={(onCancelEdit || (canUpdate && editable)) ? (
          <Space>
            {onCancelEdit && (
              <Button
                icon={<CloseOutlined />}
                disabled={saveMutation.isPending}
                onClick={requestCancelEdit}
              >
                取消编辑
              </Button>
            )}
            {canUpdate && editable && (
              <Button
                type="primary"
                icon={<SaveOutlined />}
                loading={saveMutation.isPending}
                disabled={!dirty || inspectorDirty}
                onClick={() => void save()}
              >
                保存定义
              </Button>
            )}
          </Space>
        ) : null}
      />
      <Modal
        open={cancelEditConfirmOpen}
        title="取消编辑 Canvas 定义？"
        closable={false}
        mask={{ closable: false }}
        footer={(
          <Space>
            <Button disabled={savingAndLeaving} onClick={() => setCancelEditConfirmOpen(false)}>
              继续编辑
            </Button>
            <Button
              danger
              disabled={savingAndLeaving}
              onClick={() => {
                setCancelEditConfirmOpen(false);
                onCancelEdit?.();
              }}
            >
              放弃并离开
            </Button>
            <Button
              type="primary"
              icon={<SaveOutlined />}
              loading={savingAndLeaving}
              onClick={() => void saveAndLeave(() => {
                setCancelEditConfirmOpen(false);
                onCancelEdit?.();
              })}
            >
              保存并离开
            </Button>
          </Space>
        )}
        onCancel={() => setCancelEditConfirmOpen(false)}
      >
        当前 Canvas 定义或节点配置存在未保存修改。你可以保存后离开、放弃修改或继续编辑。
      </Modal>
      <Modal
        open={blocker.state === 'blocked'}
        title="离开未保存的 Canvas 定义？"
        closable={false}
        mask={{ closable: false }}
        onCancel={() => blocker.reset?.()}
        footer={(
          <Space>
            <Button disabled={savingAndLeaving} onClick={() => blocker.reset?.()}>
              继续编辑
            </Button>
            <Button danger disabled={savingAndLeaving} onClick={() => blocker.proceed?.()}>
              放弃并离开
            </Button>
            <Button
              type="primary"
              icon={<SaveOutlined />}
              loading={savingAndLeaving}
              onClick={() => void saveAndLeave(() => blocker.proceed?.())}
            >
              保存并离开
            </Button>
          </Space>
        )}
      >
        {inspectorDirty
          ? '当前节点配置尚未应用。“保存并离开”会先校验并应用节点配置，再保存整个 Canvas。'
          : '当前 Canvas 定义尚未保存。你可以保存后离开、放弃修改或继续编辑。'}
      </Modal>
    </div>
  );
};
