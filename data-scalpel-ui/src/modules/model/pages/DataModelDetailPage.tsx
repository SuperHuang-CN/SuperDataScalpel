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
} from '@ant-design/icons';
import { Button, Dropdown, Modal, Result, Skeleton, Space, Tabs, Tag, Tooltip, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { downloadBlob } from '../../../shared/browser/downloadBlob';
import { useDirectoryTree, type DirectoryTreeNode } from '../../directory';
import { useCurrentUser } from '../../system';
import { DataModelBasicPanel } from '../components/DataModelBasicPanel';
import { DataModelDrawer } from '../components/DataModelDrawer';
import { DataModelFieldsPanel } from '../components/DataModelFieldsPanel';
import { DataModelLineagePanel } from '../components/DataModelLineagePanel';
import { DataModelPhysicalChangePanel } from '../components/DataModelPhysicalChangePanel';
import { DataModelPreviewPanel } from '../components/DataModelPreviewPanel';
import { DataModelTasksPanel } from '../components/DataModelTasksPanel';
import {
  useDataModel,
  useDataModelCommand,
  useDeleteDataModel,
  useExportModelMetadata,
} from '../hooks/useDataModels';
import {
  dataModelStatusLabels,
  physicalLocation,
  physicalTableModeLabels,
  type DataModel,
  type DataModelStatus,
} from '../model/dataModel';
import { normalizeModelDetailTab, type ModelDetailTabKey } from '../model/modelDetailMock';

interface ModelDetailLocationState {
  fromModelList?: boolean;
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
  const [searchParams, setSearchParams] = useSearchParams();
  const [editing, setEditing] = useState(false);
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
  const permissionsLoaded = Boolean(currentUserQuery.data);
  const directoriesQuery = useDirectoryTree('MODEL', canViewDirectories);
  const deleteMutation = useDeleteDataModel();
  const exportMutation = useExportModelMetadata();
  const publishMutation = useDataModelCommand('publish');
  const disableMutation = useDataModelCommand('disable');
  const enableMutation = useDataModelCommand('enable');
  const requestedTab = normalizeModelDetailTab(searchParams.get('tab'));
  const activeTab = requestedTab === 'tasks' && (!permissionsLoaded || !canViewTasks)
    ? 'basic'
    : requestedTab;
  const model = detailQuery.data?.model;

  useEffect(() => {
    if (permissionsLoaded && requestedTab === 'tasks' && !canViewTasks) {
      setSearchParams({ tab: 'basic' }, { replace: true });
    }
  }, [canViewTasks, permissionsLoaded, requestedTab, setSearchParams]);

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
    const state = location.state as ModelDetailLocationState | null;
    if (state?.fromModelList) navigate(-1);
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
        title: '发布模型',
        content: '发布前会实时检查物理表是否存在且与模型字段一致；发布后字段结构将变为只读。',
        okText: '发布',
        cancelText: '取消',
        onOk: () => executeCommand(target, 'publish'),
      });
      return;
    }
    void executeCommand(target, target.status === 'PUBLISHED' ? 'disable' : 'enable');
  };

  const remove = (target: DataModel) => modalApi.confirm({
    title: '删除模型',
    content: `确认删除“${target.name}”吗？只删除模型元数据，不操作物理表。`,
    okText: '删除',
    okButtonProps: { danger: true },
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteMutation.mutateAsync(target.id);
        messageApi.success('模型已删除');
        navigate('/model', { replace: true });
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.message : '删除模型失败');
        throw error;
      }
    },
  });

  const exportMetadata = async (target: DataModel) => {
    try {
      const blob = await exportMutation.mutateAsync([target.id]);
      downloadBlob(blob, `${target.code}-模型元数据.xlsx`);
      messageApi.success('模型元数据导出已开始');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '导出模型元数据失败');
    }
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
      label: `字段定义 ${detailQuery.data.fields.length}`,
      children: <DataModelFieldsPanel model={model} canUpdate={canUpdate} />,
    },
    { key: 'changes', label: '物理变更', children: <DataModelPhysicalChangePanel model={model} canUpdate={canUpdate} /> },
    { key: 'data', label: '数据预览', children: <DataModelPreviewPanel model={model} fields={detailQuery.data.fields} /> },
    ...(canViewTasks
      ? [{ key: 'tasks', label: '关联任务', children: <DataModelTasksPanel modelId={model.id} /> }]
      : []),
    { key: 'lineage', label: '血缘分析', children: <DataModelLineagePanel model={model} /> },
  ];

  return (
    <div className="model-detail-page">
      {messageContext}
      {modalContext}
      <div className="model-detail-header">
        <div className="model-detail-identity">
          <div className="model-detail-title-row">
            <Button type="text" icon={<ArrowLeftOutlined />} onClick={backToList}>返回列表</Button>
            <span className="model-detail-title">{model.name}</span>
            <code>{model.code}</code>
            <Tag color={statusColor[model.status]}>{dataModelStatusLabels[model.status]}</Tag>
            <Tag>{physicalTableModeLabels[model.physicalTableMode]}</Tag>
          </div>
          <div className="model-detail-subtitle">
            <span>{model.storageDataSourceName}</span>
            <span>·</span>
            <code>{physicalLocation(model)}</code>
          </div>
        </div>
        <Space size={4}>
          {model.physicalTableMode === 'MANAGED' && (
            <Button icon={<DownloadOutlined />} loading={exportMutation.isPending} onClick={() => void exportMetadata(model)}>
              导出 Excel
            </Button>
          )}
          <Tooltip title="刷新模型">
            <Button icon={<ReloadOutlined />} aria-label="刷新模型详情" onClick={() => void detailQuery.refetch()} />
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
                onClick: ({ key }) => key === 'delete' && remove(model),
              }}
            >
              <Button icon={<MoreOutlined />} aria-label="模型更多操作" />
            </Dropdown>
          )}
        </Space>
      </div>
      <Tabs
        activeKey={activeTab}
        className="model-detail-tabs"
        destroyOnHidden
        items={tabItems}
        onChange={(key) => setSearchParams({ tab: key as ModelDetailTabKey }, { replace: true })}
      />
      <DataModelDrawer
        open={editing}
        model={model}
        canViewDirectories={canViewDirectories}
        onClose={() => setEditing(false)}
        onSaved={() => setEditing(false)}
      />
    </div>
  );
};
