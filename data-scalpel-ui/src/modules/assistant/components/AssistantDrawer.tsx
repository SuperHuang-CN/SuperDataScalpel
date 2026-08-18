import {
  FolderOutlined,
  HistoryOutlined,
  PlusOutlined,
  RobotOutlined,
  SendOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import { useQueryClient } from '@tanstack/react-query';
import { Alert, Button, Collapse, Drawer, Empty, Input, Modal, Select, Space, Spin, Tag, Tooltip, Typography, message } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { downloadBlob } from '../../../shared/browser/downloadBlob';
import {
  ConnectionTestResultModal,
  useTestSavedDataSourceConnection,
  type ConnectionTestResult,
  type DataSourceAssistantLocationState,
} from '../../datasource';
import { exportDirectoryTree, invalidateDirectoryTree } from '../../directory';
import {
  useApproveAssistantChangeSet,
  useArchiveAssistantSession,
  useAssistantMessages,
  useAssistantSession,
  useAssistantSessions,
  useAvailableAssistantModels,
  useCreateAssistantSession,
  useRejectAssistantChangeSet,
  useSelectAssistantModel,
  useSendAssistantMessage,
} from '../hooks/useAssistant';
import type { AssistantClientAction } from '../model/assistant';
import { DirectoryChangeSetCard } from './DirectoryChangeSetCard';

interface AssistantDrawerProps {
  open: boolean;
  pageKey: string;
  sidebarCollapsed: boolean;
  permissions: Set<string>;
  onClose: () => void;
  onNavigatePage: (pageKey: string) => void;
  onSetSidebarCollapsed: (collapsed: boolean) => void;
  onManageModels: () => void;
}

export const AssistantDrawer = ({
  open,
  pageKey,
  sidebarCollapsed,
  permissions,
  onClose,
  onNavigatePage,
  onSetSidebarCollapsed,
  onManageModels,
}: AssistantDrawerProps) => {
  const navigate = useNavigate();
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [draft, setDraft] = useState('');
  const [testFailure, setTestFailure] = useState<{
    result: ConnectionTestResult;
    targetLabel: string;
  } | null>(null);
  const [messageApi, messageContext] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const messageListRef = useRef<HTMLDivElement | null>(null);
  const queryClient = useQueryClient();
  const modelsQuery = useAvailableAssistantModels(open);
  const sessionsQuery = useAssistantSessions(open);
  const sessions = useMemo(() => sessionsQuery.data?.content ?? [], [sessionsQuery.data?.content]);
  const models = useMemo(() => modelsQuery.data ?? [], [modelsQuery.data]);
  const selectedSessionId = activeSessionId
    ?? sessions.find((session) => session.status === 'ACTIVE')?.id
    ?? sessions[0]?.id
    ?? null;
  const sessionQuery = useAssistantSession(selectedSessionId, open);
  const messagesQuery = useAssistantMessages(selectedSessionId, open);
  const createSessionMutation = useCreateAssistantSession();
  const selectModelMutation = useSelectAssistantModel();
  const archiveMutation = useArchiveAssistantSession();
  const sendMutation = useSendAssistantMessage();
  const testDataSourceMutation = useTestSavedDataSourceConnection();
  const approveMutation = useApproveAssistantChangeSet(selectedSessionId);
  const rejectMutation = useRejectAssistantChangeSet(selectedSessionId);
  const activeSession = sessionQuery.data?.session ?? sessions.find((session) => session.id === selectedSessionId) ?? null;
  const changeSet = sessionQuery.data?.latestChangeSet ?? null;
  const latestRun = sessionQuery.data?.latestRun ?? null;
  const canManageDirectories = permissions.has('directory.manage');
  const canManageModels = permissions.has('system.configuration.view');

  useEffect(() => {
    const element = messageListRef.current;
    if (element) element.scrollTop = element.scrollHeight;
  }, [messagesQuery.data?.content.length, sendMutation.isPending]);

  const sessionOptions = useMemo(() => sessions.map((session) => ({
    value: session.id,
    label: `${session.status === 'ARCHIVED' ? '已归档 · ' : ''}${session.title}`,
  })), [sessions]);

  const showError = (error: unknown, fallback: string) => messageApi.error(error instanceof ApiError ? error.message : fallback);

  const newSession = async () => {
    const preferredModel = models.find((model) => model.defaultModel) ?? models[0];
    if (!preferredModel) return;
    try {
      const session = await createSessionMutation.mutateAsync(preferredModel.id);
      setActiveSessionId(session.id);
      setDraft('');
    } catch (error) { showError(error, '创建 AI 会话失败'); }
  };

  const selectModel = async (modelId: string) => {
    if (!selectedSessionId) return;
    try { await selectModelMutation.mutateAsync({ id: selectedSessionId, modelId }); }
    catch (error) { showError(error, '切换 AI 模型失败'); }
  };

  const archive = async () => {
    if (!selectedSessionId) return;
    try {
      await archiveMutation.mutateAsync(selectedSessionId);
      messageApi.success('会话已归档');
    } catch (error) { showError(error, '归档会话失败'); }
  };

  const executeActions = async (actions: AssistantClientAction[]) => {
    for (const action of actions) {
      if (action.type === 'NAVIGATE_PAGE') onNavigatePage(action.pageKey);
      if (action.type === 'SET_APP_SIDEBAR_COLLAPSED') onSetSidebarCollapsed(action.collapsed);
      if (action.type === 'DOWNLOAD_DIRECTORY_EXPORT') {
        if (!permissions.has('directory.view')) {
          messageApi.error('当前账号没有目录查看权限，无法导出');
          continue;
        }
        try {
          const blob = await exportDirectoryTree(action.scope);
          downloadBlob(blob, `DataScalpel-${action.scope}-目录-${new Date().toISOString().slice(0, 10)}.xlsx`);
          messageApi.success('目录导出已开始下载');
        } catch (error) { showError(error, '导出目录失败'); }
      }
      if (action.type === 'OPEN_DATA_SOURCE_CREATE') {
        if (!permissions.has('datasource.view') || !permissions.has('datasource.create')) {
          messageApi.error('当前账号没有新建数据源权限');
          continue;
        }
        const state: DataSourceAssistantLocationState = {
          assistantDataSourceAction: { kind: 'CREATE', draft: action.dataSourceDraft },
        };
        navigate('/datasource', { state });
      }
      if (action.type === 'OPEN_DATA_SOURCE_EDIT') {
        if (!permissions.has('datasource.view') || !permissions.has('datasource.update')) {
          messageApi.error('当前账号没有修改数据源权限');
          continue;
        }
        const state: DataSourceAssistantLocationState = {
          assistantDataSourceAction: {
            kind: 'EDIT',
            dataSourceId: action.dataSourceId,
            draft: action.dataSourceDraft,
          },
        };
        navigate(`/datasource/${action.dataSourceId}`, { state });
      }
      if (action.type === 'CONFIRM_DATA_SOURCE_TEST') {
        if (!permissions.has('datasource.view') || !permissions.has('datasource.test')) {
          messageApi.error('当前账号没有测试数据源连接的权限');
          continue;
        }
        modalApi.confirm({
          rootClassName: 'business-overlay business-modal-overlay',
          title: '确认测试数据源连接',
          content: `确认使用系统中已保存的配置测试“${action.dataSourceName}”吗？测试结果只在当前界面展示，不会发送给 AI 模型。`,
          okText: '确认测试',
          cancelText: '取消',
          onOk: async () => {
            try {
              setTestFailure(null);
              const result = await testDataSourceMutation.mutateAsync(action.dataSourceId);
              if (result.success) {
                messageApi.success(`${action.dataSourceName}：${result.message}（${result.elapsedMs} ms）`);
              } else {
                setTestFailure({ result, targetLabel: action.dataSourceName });
              }
            } catch (error) {
              showError(error, '测试数据源连接失败');
              throw error;
            }
          },
        });
      }
    }
  };

  const send = async () => {
    const content = draft.trim();
    if (!selectedSessionId || !content || activeSession?.status !== 'ACTIVE') return;
    try {
      setDraft('');
      const turn = await sendMutation.mutateAsync({
        id: selectedSessionId,
        request: { content, pageKey, sidebarCollapsed },
      });
      await executeActions(turn.clientActions);
    } catch (error) {
      setDraft(content);
      showError(error, 'AI 助手暂时无法回复');
    }
  };

  const approve = () => {
    if (!changeSet) return;
    const hasDeletes = changeSet.directoryPlan.deletes.length > 0;
    modalApi.confirm({
      rootClassName: 'business-overlay business-modal-overlay',
      title: hasDeletes ? '确认执行包含删除的目录计划' : '确认执行目录计划',
      content: hasDeletes
        ? `计划将删除 ${changeSet.directoryPlan.deletes.length} 个空叶子目录。执行前会再次检查目录和业务引用，确认继续吗？`
        : `确认执行“${changeSet.summary}”吗？执行前会再次检查目录是否发生变化。`,
      okText: hasDeletes ? '确认危险变更' : '确认执行',
      cancelText: '取消',
      okButtonProps: { danger: hasDeletes },
      onOk: async () => {
        try {
          const result = await approveMutation.mutateAsync(changeSet.id);
          await invalidateDirectoryTree(queryClient, result.directoryPlan.scope);
          messageApi.success('目录变更计划已执行');
        } catch (error) { showError(error, '执行目录变更计划失败'); throw error; }
      },
    });
  };

  const reject = async () => {
    if (!changeSet) return;
    try { await rejectMutation.mutateAsync(changeSet.id); messageApi.success('目录变更计划已拒绝'); }
    catch (error) { showError(error, '拒绝目录变更计划失败'); }
  };

  const emptyModels = !modelsQuery.isLoading && models.length === 0;

  return <>
    {messageContext}{modalContext}
    <Drawer
      rootClassName="assistant-drawer business-overlay business-drawer-overlay"
      title={<Space><RobotOutlined /><span>AI 助手</span><Tag color="blue">V1</Tag></Space>}
      width={460}
      open={open}
      onClose={onClose}
      destroyOnHidden={false}
    >
      {modelsQuery.isLoading ? <div className="assistant-centered"><Spin /></div> : emptyModels ? <Empty
        image={Empty.PRESENTED_IMAGE_SIMPLE}
        description="系统尚未启用通过 Tool Calling 测试的 AI 模型"
      >
        {canManageModels && <Button icon={<SettingOutlined />} onClick={onManageModels}>管理 AI 模型</Button>}
      </Empty> : <div className="assistant-drawer-layout">
        <div className="assistant-toolbar">
          <Select
            aria-label="历史会话"
            className="assistant-session-select"
            prefix={<HistoryOutlined />}
            placeholder="选择历史会话"
            value={selectedSessionId ?? undefined}
            options={sessionOptions}
            loading={sessionsQuery.isLoading}
            onChange={(id) => { setActiveSessionId(id); setDraft(''); }}
          />
          <Tooltip title="新建会话"><Button icon={<PlusOutlined />} aria-label="新建 AI 会话" loading={createSessionMutation.isPending} onClick={() => void newSession()} /></Tooltip>
          {activeSession?.status === 'ACTIVE' && <Tooltip title="归档会话"><Button icon={<FolderOutlined />} aria-label="归档当前 AI 会话" loading={archiveMutation.isPending} onClick={() => void archive()} /></Tooltip>}
        </div>
        {activeSession && <div className="assistant-model-row">
          <Typography.Text type="secondary">模型</Typography.Text>
          <Select
            size="small"
            value={activeSession.selectedModelId}
            options={models.map((model) => ({ value: model.id, label: `${model.name}${model.defaultModel ? ' · 默认' : ''}` }))}
            disabled={activeSession.status === 'ARCHIVED' || sendMutation.isPending}
            loading={selectModelMutation.isPending}
            onChange={(id) => void selectModel(id)}
          />
        </div>}
        {!activeSession ? <div className="assistant-centered"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="新建会话后即可开始使用"><Button type="primary" icon={<PlusOutlined />} onClick={() => void newSession()}>新建会话</Button></Empty></div> : <>
          {activeSession.status === 'ARCHIVED' && <Alert type="info" showIcon message="该会话已归档，只能查看历史记录" />}
          <div className="assistant-message-list" ref={messageListRef}>
            {messagesQuery.isLoading ? <div className="assistant-centered"><Spin /></div> : (messagesQuery.data?.content.length ?? 0) === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="可以询问系统用法，或让我查询目录和数据源" /> : messagesQuery.data?.content.map((item) => <div key={item.id} className={`assistant-message assistant-message-${item.role.toLowerCase()}`}>
              <div className="assistant-message-role">{item.role === 'USER' ? '你' : 'AI 助手'}</div>
              <div className="assistant-message-content">{item.content}</div>
            </div>)}
            {sendMutation.isPending && <div className="assistant-message assistant-message-assistant"><div className="assistant-message-role">AI 助手</div><Space><Spin size="small" /><Typography.Text type="secondary">正在理解并检查可用工具…</Typography.Text></Space></div>}
            {latestRun && (latestRun.toolInvocations.length > 0 || latestRun.status === 'FAILED') && <Collapse
              ghost
              size="small"
              className="assistant-audit-collapse"
              items={[{
                key: latestRun.id,
                label: <Space><span>最近运行</span><Tag color={latestRun.status === 'COMPLETED' ? 'success' : latestRun.status === 'FAILED' ? 'error' : 'processing'}>{latestRun.status}</Tag><Typography.Text type="secondary">{latestRun.modelName} · {latestRun.toolInvocations.length} 次工具调用</Typography.Text></Space>,
                children: <Space direction="vertical" size={5}>{latestRun.failureSummary && <Alert type="warning" showIcon message={latestRun.failureSummary} />}{latestRun.toolInvocations.map((invocation) => <Space key={invocation.id}><Tag color={invocation.status === 'SUCCEEDED' ? 'blue' : 'red'}>{invocation.status}</Tag><Typography.Text code>{invocation.toolName}</Typography.Text><Typography.Text type="secondary">{invocation.risk}</Typography.Text></Space>)}</Space>,
              }]}
            />}
            {changeSet && <DirectoryChangeSetCard
              changeSet={changeSet}
              canManage={canManageDirectories}
              approving={approveMutation.isPending}
              rejecting={rejectMutation.isPending}
              onApprove={approve}
              onReject={() => void reject()}
            />}
          </div>
          <div className="assistant-composer">
            <Input.TextArea
              aria-label="给 AI 助手发送消息"
              autoComplete="off"
              value={draft}
              maxLength={8000}
              autoSize={{ minRows: 2, maxRows: 5 }}
              disabled={activeSession.status === 'ARCHIVED' || sendMutation.isPending}
              placeholder="例如：查找 PostgreSQL 数据源，或帮我准备新建数据源表单"
              onChange={(event) => setDraft(event.target.value)}
              onPressEnter={(event) => {
                if (!event.shiftKey) { event.preventDefault(); void send(); }
              }}
            />
            <Button type="primary" icon={<SendOutlined />} aria-label="发送消息" disabled={!draft.trim() || activeSession.status === 'ARCHIVED'} loading={sendMutation.isPending} onClick={() => void send()} />
          </div>
          <Typography.Text className="assistant-disclaimer" type="secondary">目录写入必须确认；数据源仍由原表单测试和保存，连接信息不会发送给 AI。</Typography.Text>
        </>}
      </div>}
    </Drawer>
    {testFailure && (
      <ConnectionTestResultModal
        open
        result={testFailure.result}
        targetLabel={testFailure.targetLabel}
        onClose={() => setTestFailure(null)}
      />
    )}
  </>;
};
