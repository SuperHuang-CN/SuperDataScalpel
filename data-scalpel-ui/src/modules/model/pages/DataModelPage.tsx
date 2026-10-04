import {
  DatabaseOutlined,
  DownOutlined,
  ReloadOutlined,
  FileExcelOutlined,
  FileTextOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import { Button, ConfigProvider, Dropdown, Tooltip, Typography, type MenuProps } from 'antd';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { workspaceResourceTheme } from '../../../shared/theme/workspaceResourceTheme';
import { orSearch, searchEquals } from '../../../shared/search';
import { useDataSources } from '../../datasource';
import { DirectoryTreePanel } from '../../directory';
import { useCurrentUser } from '../../system';
import { DataModelDrawer } from '../components/DataModelDrawer';
import { DataModelListFilters } from '../components/DataModelListFilters';
import { DataModelListTable } from '../components/DataModelListTable';
import { DataModelListToolbar } from '../components/DataModelListToolbar';
import { FileDatasetModelImportDrawer } from '../components/FileDatasetModelImportDrawer';
import { ManagedTableModelImportDrawer } from '../components/ManagedTableModelImportDrawer';
import { ModelMetadataImportDrawer } from '../components/ModelMetadataImportDrawer';
import { useDataModelListActions } from '../hooks/useDataModelListActions';
import { useDataModels } from '../hooks/useDataModels';
import { useDataModelListState } from '../hooks/useDataModelListState';
import type { DataModel } from '../model/dataModel';
import '../../../shared/theme/resource-workspace.css';
import './model-list.css';

export const DataModelPage = () => {
  const navigate = useNavigate();
  const [createDrawerOpen, setCreateDrawerOpen] = useState(false);
  const [importDrawerOpen, setImportDrawerOpen] = useState(false);
  const [fileDatasetImportDrawerOpen, setFileDatasetImportDrawerOpen] = useState(false);
  const [metadataImportDrawerOpen, setMetadataImportDrawerOpen] = useState(false);
  const [editingModel, setEditingModel] = useState<DataModel | null>(null);
  const currentUserQuery = useCurrentUser();
  const permissions = new Set(currentUserQuery.data?.permissions ?? []);
  const canViewDirectories = permissions.has('directory.view');
  const canManageDirectories = permissions.has('directory.manage');
  const canCreate = permissions.has('model.create');
  const canViewDataSources = permissions.has('datasource.view');
  const canReadDataSourceMetadata = permissions.has('datasource.metadata');
  const canViewFileDatasets = permissions.has('filedataset.view');
  const canUpdate = permissions.has('model.update');
  const canDelete = permissions.has('model.delete');
  const canPublish = permissions.has('model.publish');
  const list = useDataModelListState(canViewDirectories);
  const actions = useDataModelListActions(list);
  const allModelsQuery = useDataModels({ page: 0, size: 1, sort: 'code' }, canViewDirectories);
  const storageIds = [...new Set(list.modelsQuery.data?.content.map((model) => model.storageDataSourceId) ?? [])].sort();
  const storageSourcesQuery = useDataSources({
    page: 0,
    size: Math.max(storageIds.length, 1),
    search: orSearch(...storageIds.map((id) => searchEquals('id', id))),
  }, canViewDataSources && storageIds.length > 0);
  const storageTypes = new Map(storageSourcesQuery.data?.content.map((source) => [source.id, source.type]));

  const createMenuItems: MenuProps['items'] = [
    {
      key: 'manual',
      icon: <PlusOutlined />,
      label: (
        <div className="model-create-menu-label">
          <Typography.Text strong>手动创建</Typography.Text>
          <Typography.Text type="secondary">从空白模型开始配置</Typography.Text>
        </div>
      ),
      onClick: () => setCreateDrawerOpen(true),
    },
  ];
  if (canViewDataSources && canReadDataSourceMetadata) {
    createMenuItems.push({
      key: 'jdbc',
      icon: <DatabaseOutlined />,
      label: (
        <div className="model-create-menu-label">
          <Typography.Text strong>从数据源表创建</Typography.Text>
          <Typography.Text type="secondary">复制 JDBC 表结构，不导入数据</Typography.Text>
        </div>
      ),
      onClick: () => setImportDrawerOpen(true),
    });
  }
  if (canViewDataSources && canViewFileDatasets) {
    createMenuItems.push({
      key: 'file-dataset',
      icon: <FileTextOutlined />,
      label: (
        <div className="model-create-menu-label">
          <Typography.Text strong>从文件数据集创建</Typography.Text>
          <Typography.Text type="secondary">复制已解析逻辑表 Schema</Typography.Text>
        </div>
      ),
      onClick: () => setFileDatasetImportDrawerOpen(true),
    });
  }
  if (canViewDataSources) {
    createMenuItems.push(
      { type: 'divider' },
      {
        key: 'excel',
        icon: <FileExcelOutlined />,
        label: (
          <div className="model-create-menu-label">
            <Typography.Text strong>从 Excel 模板导入</Typography.Text>
            <Typography.Text type="secondary">按照固定模板批量创建模型</Typography.Text>
          </div>
        ),
        onClick: () => setMetadataImportDrawerOpen(true),
      },
    );
  }

  const openDetail = (model: DataModel, tab: 'basic' | 'fields' = 'basic') => {
    navigate(`/model/${model.id}?tab=${tab}`, { state: { fromModelList: true } });
  };

  return (
    <ConfigProvider theme={workspaceResourceTheme}>
      {actions.messageContext}
      {actions.modalContext}
      {actions.referenceModal}
      <div className={`model-list-page resource-workspace-list ${canViewDirectories ? 'directory-management-layout' : 'page-stack'}`}>
        {canViewDirectories && (
          <DirectoryTreePanel
            scope="MODEL"
            totalResourceCount={allModelsQuery.data?.totalElements}
            tree={list.directoriesQuery.data ?? []}
            loading={list.directoriesQuery.isFetching}
            selection={list.directorySelection}
            canManage={canManageDirectories}
            onSelectionChange={list.selectDirectory}
          />
        )}
        <section className="management-workbench">
          <DataModelListFilters list={list} commands={(
            <div className="resource-list-commands">
              <Tooltip title="刷新列表">
                <Button icon={<ReloadOutlined />} aria-label="刷新模型列表"
                  onClick={() => { void list.modelsQuery.refetch(); if (canViewDirectories) void allModelsQuery.refetch(); }} />
              </Tooltip>
              {canCreate && (
                <Dropdown menu={{ items: createMenuItems }} trigger={['click']} placement="bottomRight"
                  classNames={{ root: 'workspace-directory-menu model-create-menu' }}>
                  <Button type="primary" icon={<PlusOutlined />}>新建模型 <DownOutlined /></Button>
                </Dropdown>
              )}
            </div>
          )} />
          <div className="management-results-surface">
            <DataModelListToolbar
              list={list}
              actions={actions}
              canPublish={canPublish}
            />
            {list.modelsQuery.isError && (
              <InlineFeedback className="model-list-error" tone="error" label="模型列表加载失败"
                action={<Button type="link" onClick={() => void list.modelsQuery.refetch()}>重试</Button>} />
            )}
            <DataModelListTable
              storageTypes={storageTypes}
              list={list}
              actions={actions}
              canUpdate={canUpdate}
              canDelete={canDelete}
              canPublish={canPublish}
              onOpenDetail={openDetail}
              onEdit={setEditingModel}
            />
          </div>
        </section>
      </div>
      <DataModelDrawer
        open={createDrawerOpen || Boolean(editingModel)}
        model={editingModel}
        initialDirectoryId={typeof list.directorySelection === 'string'
          ? list.directorySelection
          : undefined}
        canViewDirectories={canViewDirectories}
        onClose={() => {
          setCreateDrawerOpen(false);
          setEditingModel(null);
        }}
        onSaved={(savedModel, created) => created && navigate(
          `/model/${savedModel.id}?tab=fields`,
          { state: { fromModelList: true } },
        )}
      />
      {importDrawerOpen && (
        <ManagedTableModelImportDrawer
          open
          canViewDirectories={canViewDirectories}
          initialDirectoryId={typeof list.directorySelection === 'string'
            ? list.directorySelection
            : undefined}
          initialTargetStorageDataSourceId={list.filters.storageDataSourceId}
          onClose={() => setImportDrawerOpen(false)}
          onAdjustFields={(modelId) => {
            setImportDrawerOpen(false);
            navigate(`/model/${modelId}?tab=fields`, { state: { fromModelList: true } });
          }}
        />
      )}
      {metadataImportDrawerOpen && (
        <ModelMetadataImportDrawer
          open
          initialTargetStorageDataSourceId={list.filters.storageDataSourceId}
          onClose={() => setMetadataImportDrawerOpen(false)}
          onAdjustFields={(modelId) => {
            setMetadataImportDrawerOpen(false);
            navigate(`/model/${modelId}?tab=fields`, { state: { fromModelList: true } });
          }}
        />
      )}
      {fileDatasetImportDrawerOpen && (
        <FileDatasetModelImportDrawer
          open
          canViewDirectories={canViewDirectories}
          initialDirectoryId={typeof list.directorySelection === 'string'
            ? list.directorySelection
            : undefined}
          initialTargetStorageDataSourceId={list.filters.storageDataSourceId}
          onClose={() => setFileDatasetImportDrawerOpen(false)}
          onViewModel={(modelId) => {
            setFileDatasetImportDrawerOpen(false);
            navigate(`/model/${modelId}?tab=basic`, { state: { fromModelList: true } });
          }}
          onAdjustFields={(modelId) => {
            setFileDatasetImportDrawerOpen(false);
            navigate(`/model/${modelId}?tab=fields`, { state: { fromModelList: true } });
          }}
        />
      )}
    </ConfigProvider>
  );
};
