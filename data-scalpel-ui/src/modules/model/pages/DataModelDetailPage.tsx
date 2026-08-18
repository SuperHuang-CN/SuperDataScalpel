import {
  ArrowLeftOutlined,
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  MoreOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  SendOutlined,
  TableOutlined,
} from '@ant-design/icons';
import { Alert, Button, Dropdown, Modal, Result, Skeleton, Space, Tabs, Tag, Tooltip, Typography, message } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  useBlocker,
  useLocation,
  useNavigate,
  useParams,
  useSearchParams,
  type BlockerFunction,
} from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { downloadBlob } from '../../../shared/browser/downloadBlob';
import { useDirectoryTree, type DirectoryTreeNode } from '../../directory';
import { useCurrentUser } from '../../system';
import { DataModelBasicPanel } from '../components/DataModelBasicPanel';
import { DataModelDrawer } from '../components/DataModelDrawer';
import {
  DataModelFieldsPanel,
  type DataModelFieldsPanelHandle,
} from '../components/DataModelFieldsPanel';
import { DataModelLineagePanel } from '../components/DataModelLineagePanel';
import { DataModelPhysicalChangePanel } from '../components/DataModelPhysicalChangePanel';
import { DataModelReferenceModalContent } from '../components/DataModelReferenceModalContent';
import { DataModelPreviewPanel } from '../components/DataModelPreviewPanel';
import { DataModelQualityRulesPanel } from '../components/DataModelQualityRulesPanel';
import { DataModelTasksPanel } from '../components/DataModelTasksPanel';
import {
  useDataModel,
  useDataModelReferences,
  useDataModelCommand,
  useDeleteDataModel,
  useExportModelMetadata,
} from '../hooks/useDataModels';
import {
  dataModelStatusLabels,
  physicalTableModeLabels,
  type DataModel,
  type DataModelStatus,
} from '../model/dataModel';
import { normalizeModelDetailTab, type ModelDetailTabKey } from '../model/modelDetailMock';

interface ModelDetailLocationState {
  fromModelList?: boolean;
  returnTo?: string;
  returnLabel?: string;
  returnState?: unknown;
}

const statusColor: Record<DataModelStatus, string> = {
  DRAFT: 'default',
  PUBLISHED: 'success',
  DISABLED: 'warning',
};

const lifecycleLabel = (status: DataModelStatus) => {
  if (status === 'DRAFT') return '发布';
  if (status === 'PUBLISHED') return '停用';
  return '启用';
};

const lifecycleIcon = (status: DataModelStatus) => {
  if (status === 'DRAFT') return <SendOutlined />;
  if (status === 'PUBLISHED') return <PauseCircleOutlined />;
  return <PlayCircleOutlined />;
};

export const DataModelDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const locationState = location.state as ModelDetailLocationState | null;
  const [searchParams, setSearchParams] = useSearchParams();
  const [editing, setEditing] = useState(false);
  const [referenceModalOpen, setReferenceModalOpen] = useState(false);
  const [fieldsDirty, setFieldsDirty] = useState(false);
  const fieldsPanelRef = useRef<DataModelFieldsPanelHandle>(null);
  const allowNavigationRef = useRef(false);
  const [messageApi, messageContext] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const detailQuery = useDataModel(id, Boolean(id));
  const currentUserQuery = useCurrentUser();
  const permissions = new Set(currentUserQuery.data?.permissions ?? []);
  const canViewDirectories = permissions.has('directory.view');
  const canUpdate = permissions.has('model.update');
  const canDelete = permissions.has('model.delete');
  const canPublish = permissions.has('model.publish');
  const canViewTasks = permissions.has('task.view');
  const canViewServices = permissions.has('service.view');
  const permissionsLoaded = Boolean(currentUserQuery.data);
  const directoriesQuery = useDirectoryTree('MODEL', canViewDirectories);
  const deleteMutation = useDeleteDataModel();
  const referencesQuery = useDataModelReferences(id, referenceModalOpen);
  const exportMutation = useExportModelMetadata();
  const publishMutation = useDataModelCommand('publish');
  const disableMutation = useDataModelCommand('disable');
  const enableMutation = useDataModelCommand('enable');
  const requestedTab = normalizeModelDetailTab(searchParams.get('tab'));
  const unauthorizedProtectedTab = requestedTab === 'tasks'
    ? !canViewTasks
    : requestedTab === 'lineage' && (!canViewTasks || !canViewServices);
  const protectedTabLoading = !permissionsLoaded && (requestedTab === 'tasks' || requestedTab === 'lineage');
  const activeTab = (protectedTabLoading || unauthorizedProtectedTab)
    ? 'basic'
    : requestedTab;
  const model = detailQuery.data?.model;
  const blocker = useBlocker(useCallback<BlockerFunction>(
    ({ currentLocation, nextLocation }) => !allowNavigationRef.current && fieldsDirty && (
      currentLocation.pathname !== nextLocation.pathname || currentLocation.search !== nextLocation.search
    ),
    [fieldsDirty],
  ));

  useEffect(() => {
    if (permissionsLoaded && unauthorizedProtectedTab) {
      setSearchParams({ tab: 'basic' }, { replace: true });
    }
  }, [permissionsLoaded, setSearchParams, unauthorizedProtectedTab]);

  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => {
      if (!fieldsDirty) return;
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [fieldsDirty]);

  useEffect(() => {
    if (!fieldsDirty && blocker.state === 'blocked') blocker.reset();
  }, [blocker, fieldsDirty]);

  const directoryNameById = useMemo(() => {
    const names = new Map<string, string>();
    const collect = (nodes: DirectoryTreeNode[]) => nodes.forEach((node) => {
      names.set(node.id, node.name);
      collect(node.children);
    });
    collect(directoriesQuery.data ?? []);
    return names;
  }, [directoriesQuery.data]);

  const backToList = () => {
    if (locationState?.returnTo) navigate(locationState.returnTo, { state: locationState.returnState });
    else if (locationState?.fromModelList) navigate(-1);
    else navigate('/model');
  };

  const executeCommand = async (target: DataModel, command: 'publish' | 'disable' | 'enable') => {
    try {
      const mutation = command === 'publish'
        ? publishMutation
        : command === 'disable' ? disableMutation : enableMutation;
      await mutation.mutateAsync(target.id);
      messageApi.success(command === 'publish'
        ? '模型已发布，物理表结构校验通过'
        : command === 'disable' ? '模型已停用' : '模型已启用');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '模型状态操作失败');
      throw error;
    }
  };

  const transition = (target: DataModel) => {
    if (target.status === 'DRAFT') {
      modalApi.confirm({
        rootClassName: 'business-overlay business-modal-overlay',
        title: '发布模型',
        content: fieldsDirty
          ? '当前字段定义有未保存修改。发布只会使用最后保存的字段，成功后当前修改将被放弃。'
          : '发布前会实时检查物理表是否存在且与模型字段一致；发布后字段结构将变为只读。',
        okText: fieldsDirty ? '放弃修改并发布' : '发布',
        okButtonProps: fieldsDirty ? { danger: true } : undefined,
        cancelText: fieldsDirty ? '继续编辑' : '取消',
        onOk: async () => {
          await executeCommand(target, 'publish');
          if (fieldsDirty) fieldsPanelRef.current?.discardChanges();
        },
      });
      return;
    }
    const command = target.status === 'PUBLISHED' ? 'disable' : 'enable';
    if (!fieldsDirty || command === 'disable') {
      void executeCommand(target, command);
      return;
    }
    modalApi.confirm({
      rootClassName: 'business-overlay business-modal-overlay',
      title: '启用模型并放弃字段修改？',
      content: '启用只会使用最后保存的字段定义，成功后当前修改将被放弃。',
      okText: '放弃修改并启用',
      okButtonProps: { danger: true },
      cancelText: '继续编辑',
      onOk: async () => {
        await executeCommand(target, command);
        fieldsPanelRef.current?.discardChanges();
      },
    });
  };

  const remove = () => setReferenceModalOpen(true);

  const confirmRemove = async (target: DataModel) => {
    try {
      await deleteMutation.mutateAsync(target.id);
      messageApi.success('模型已删除');
      allowNavigationRef.current = true;
      setReferenceModalOpen(false);
      navigate('/model', { replace: true });
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '删除模型失败');
      throw error;
    }
  };

  const exportMetadata = async (target: DataModel) => {
    try {
      const blob = await exportMutation.mutateAsync([target.id]);
      downloadBlob(blob, `${target.code}-模型元数据.xlsx`);
      messageApi.success('模型元数据导出已开始');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '导出模型元数据失败');
    }
  };

  const refreshDetail = () => {
    if (!fieldsDirty) {
      void detailQuery.refetch();
      return;
    }
    modalApi.confirm({
      rootClassName: 'business-overlay business-modal-overlay',
      title: '放弃未保存的字段修改？',
      content: '刷新模型详情会重新加载最后保存的字段定义，当前修改会丢失。',
      okText: '放弃修改并刷新',
      okButtonProps: { danger: true },
      cancelText: '继续编辑',
      onOk: () => {
        fieldsPanelRef.current?.discardChanges();
        void detailQuery.refetch();
      },
    });
  };

  if (!id) {
    return <Result status="404" title="模型地址无效" extra={<Button type="primary" onClick={() => navigate('/model')}>返回模型列表</Button>} />;
  }

  if (detailQuery.isPending) {
    return <div className="model-detail-loading"><Skeleton active paragraph={{ rows: 8 }} /></div>;
  }

  if (!model || detailQuery.error) {
    return (
      <Result
        status="error"
        title="模型详情加载失败"
        subTitle={detailQuery.error instanceof ApiError ? detailQuery.error.message : '请确认模型是否存在。'}
        extra={(
          <Space>
            <Button onClick={backToList}>返回列表</Button>
            <Button type="primary" onClick={() => void detailQuery.refetch()}>重试</Button>
          </Space>
        )}
      />
    );
  }

  const commandLoading = publishMutation.isPending || disableMutation.isPending || enableMutation.isPending;
  const tabItems = [
    {
      key: 'basic',
      label: '基本信息',
      children: (
        <DataModelBasicPanel
          model={model}
          directoryName={model.directoryId ? directoryNameById.get(model.directoryId) : undefined}
          canManagePhysicalTable={canUpdate}
        />
      ),
    },
    {
      key: 'fields',
      label: (
        <Space size={4}>
          <span>{`字段定义 ${detailQuery.data.fields.length}`}</span>
          {fieldsDirty && <Tag color="processing">未保存</Tag>}
        </Space>
      ),
      children: (
        <DataModelFieldsPanel
          ref={fieldsPanelRef}
          model={model}
          canUpdate={canUpdate}
          onDirtyChange={setFieldsDirty}
        />
      ),
    },
    {
      key: 'quality',
      label: '质量规则',
      children: (
        <DataModelQualityRulesPanel
          modelId={model.id}
          fields={detailQuery.data.fields}
          canUpdate={canUpdate}
          canViewTasks={canViewTasks}
        />
      ),
    },
    { key: 'changes', label: '物理变更', children: <DataModelPhysicalChangePanel model={model} canUpdate={canUpdate} /> },
    { key: 'data', label: '数据预览', children: <DataModelPreviewPanel model={model} fields={detailQuery.data.fields} /> },
    ...(canViewTasks
      ? [
          { key: 'tasks', label: '关联任务', children: <DataModelTasksPanel modelId={model.id} /> },
          ...(canViewServices ? [{
            key: 'lineage',
            label: '血缘分析',
            children: <DataModelLineagePanel model={model} fields={detailQuery.data.fields} />,
          }] : []),
        ]
      : []),
  ];

  return (
    <div className="model-detail-page business-detail-page">
      {messageContext}
      {modalContext}
      <div className="model-detail-header business-detail-header">
        <div className="model-detail-identity">
          <div className="model-detail-title-row">
            <Button type="text" icon={<ArrowLeftOutlined />} onClick={backToList}>{locationState?.returnLabel ?? '返回列表'}</Button>
            <span className="business-detail-resource-icon business-detail-resource-icon-purple"><TableOutlined /></span>
            <span className="model-detail-title">{model.name}</span>
            <code>{model.code}</code>
            <Tag color={statusColor[model.status]}>{dataModelStatusLabels[model.status]}</Tag>
            <Tag>{physicalTableModeLabels[model.physicalTableMode]}</Tag>
          </div>
          <div className="model-detail-subtitle">
            <span>{model.storageDataSourceName}</span>
            <span>·</span>
            <code>{model.physicalTableName}</code>
          </div>
        </div>
        <Space size={4}>
          {model.physicalTableMode === 'MANAGED' && (
            <Button icon={<DownloadOutlined />} loading={exportMutation.isPending} onClick={() => void exportMetadata(model)}>
              导出 Excel
            </Button>
          )}
          <Tooltip title="刷新模型">
            <Button icon={<ReloadOutlined />} aria-label="刷新模型详情" onClick={refreshDetail} />
          </Tooltip>
          {canUpdate && <Button icon={<EditOutlined />} onClick={() => setEditing(true)}>修改</Button>}
          {canPublish && (
            <Button
              type={model.status === 'DRAFT' ? 'primary' : 'default'}
              icon={lifecycleIcon(model.status)}
              loading={commandLoading}
              onClick={() => transition(model)}
            >
              {lifecycleLabel(model.status)}
            </Button>
          )}
          {canDelete && (
            <Dropdown
              trigger={['click']}
              menu={{
                items: [{ key: 'delete', label: '删除模型', danger: true, icon: <DeleteOutlined /> }],
                onClick: ({ key }) => key === 'delete' && remove(),
              }}
            >
              <Button icon={<MoreOutlined />} aria-label="模型更多操作" />
            </Dropdown>
          )}
        </Space>
      </div>
      <Tabs
        activeKey={activeTab}
        className="model-detail-tabs business-detail-tabs"
        destroyOnHidden
        items={tabItems}
        onChange={(key) => setSearchParams(
          { tab: key as ModelDetailTabKey },
          { replace: true, state: location.state },
        )}
      />
      <DataModelDrawer
        open={editing}
        model={model}
        canViewDirectories={canViewDirectories}
        onClose={() => setEditing(false)}
        onSaved={() => setEditing(false)}
      />
      <Modal
        rootClassName="business-overlay business-modal-overlay"
        open={referenceModalOpen}
        title={`删除模型：${model.name}`}
        width={760}
        okText={fieldsDirty ? '放弃修改并删除' : '确认删除'}
        okButtonProps={{
          danger: true,
          disabled: referencesQuery.isPending || referencesQuery.isError || !referencesQuery.data?.deletable,
          loading: deleteMutation.isPending,
        }}
        cancelText="取消"
        onCancel={() => setReferenceModalOpen(false)}
        onOk={() => confirmRemove(model)}
      >
        {referencesQuery.data?.deletable && (
          <Alert
            type="warning"
            showIcon
            message={fieldsDirty ? '字段定义有未保存修改，删除后将一并丢失' : `确认删除“${model.name}”吗？`}
            description="只删除模型元数据，不操作物理表。"
          />
        )}
        {referencesQuery.isPending && <Typography.Text>正在检查模型引用…</Typography.Text>}
        {referencesQuery.isError && (
          <Alert
            type="error"
            showIcon
            message="模型引用检查失败"
            description={referencesQuery.error instanceof ApiError ? referencesQuery.error.message : '请稍后重试。'}
            action={<Button size="small" onClick={() => void referencesQuery.refetch()}>重试</Button>}
          />
        )}
        {referencesQuery.data && <DataModelReferenceModalContent references={referencesQuery.data} />}
      </Modal>
      <Modal
        rootClassName="business-overlay business-modal-overlay"
        open={blocker.state === 'blocked'}
        title="离开未保存的字段定义？"
        okText="放弃并离开"
        okButtonProps={{ danger: true }}
        cancelText="继续编辑"
        closable={false}
        mask={{ closable: false }}
        onOk={() => {
          fieldsPanelRef.current?.discardChanges();
          blocker.proceed?.();
        }}
        onCancel={() => blocker.reset?.()}
      >
        当前字段定义尚未保存，离开后这些修改会丢失。
      </Modal>
    </div>
  );
};
