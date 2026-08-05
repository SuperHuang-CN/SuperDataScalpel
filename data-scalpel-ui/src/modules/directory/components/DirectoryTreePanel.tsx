import { AppstoreOutlined, DeleteOutlined, EditOutlined, FolderAddOutlined, FolderOutlined, InboxOutlined, MenuFoldOutlined, MenuUnfoldOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, Popconfirm, Spin, Tooltip, Tree, message } from 'antd';
import type { DataNode } from 'antd/es/tree';
import { useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { useDeleteDirectory } from '../hooks/useDirectories';
import type { DirectoryScope, DirectoryTreeNode } from '../model/directory';
import { DirectoryDrawer } from './DirectoryDrawer';

const ALL_DIRECTORY_KEY = '__all__';
const UNCATEGORIZED_DIRECTORY_KEY = '__uncategorized__';
const DIRECTORY_PANEL_COLLAPSED_STORAGE_KEY_PREFIX = 'data-scalpel.ui.directory-panel.collapsed';

const directoryPanelStorageKey = (scope: DirectoryScope) => `${DIRECTORY_PANEL_COLLAPSED_STORAGE_KEY_PREFIX}.${scope}`;

const readDirectoryPanelCollapsedPreference = (scope: DirectoryScope): boolean => {
  try {
    return window.localStorage.getItem(directoryPanelStorageKey(scope)) === 'true';
  } catch {
    return false;
  }
};

const writeDirectoryPanelCollapsedPreference = (scope: DirectoryScope, collapsed: boolean) => {
  try {
    window.localStorage.setItem(directoryPanelStorageKey(scope), String(collapsed));
  } catch {
    // Local storage may be unavailable in restricted browser environments.
  }
};

export type DirectorySelection = string | null | undefined;

interface DirectoryTreePanelProps {
  scope: DirectoryScope;
  tree: DirectoryTreeNode[];
  loading: boolean;
  selection: DirectorySelection;
  canManage?: boolean;
  onSelectionChange: (selection: DirectorySelection) => void;
}

export const DirectoryTreePanel = ({ scope, tree, loading, selection, canManage = false, onSelectionChange }: DirectoryTreePanelProps) => {
  const [messageApi, messageContext] = message.useMessage();
  const [collapsed, setCollapsed] = useState(() => readDirectoryPanelCollapsedPreference(scope));
  const [drawerState, setDrawerState] = useState<{ directory: DirectoryTreeNode | null; parentId?: string } | null>(null);
  const deleteMutation = useDeleteDirectory(scope);

  const openCreate = (parentId?: string) => setDrawerState({ directory: null, parentId });
  const openEdit = (directory: DirectoryTreeNode) => setDrawerState({ directory });
  const toggleCollapsed = () => {
    const nextCollapsed = !collapsed;
    setCollapsed(nextCollapsed);
    writeDirectoryPanelCollapsedPreference(scope, nextCollapsed);
  };

  const remove = async (directory: DirectoryTreeNode) => {
    try {
      await deleteMutation.mutateAsync(directory.id);
      if (selection === directory.id) onSelectionChange(undefined);
      messageApi.success('目录已删除');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '删除目录失败');
    }
  };

  const treeData: DataNode[] = (() => {
    const buildNodes = (nodes: DirectoryTreeNode[]): DataNode[] => nodes.map((directory) => ({
      key: directory.id,
      icon: <FolderOutlined />,
      title: (
        <span className="directory-tree-node-title">
          <span className="directory-tree-node-name">{directory.name}</span>
          <span className="directory-tree-node-meta">
            <span className="directory-tree-node-count">{directory.resourceCount}</span>
            {canManage && (
              <span className="directory-tree-node-actions" onClick={(event) => event.stopPropagation()}>
                <Tooltip title="新建子目录">
                  <Button type="text" size="small" icon={<FolderAddOutlined />} aria-label={`在“${directory.name}”下新建子目录`} onClick={() => openCreate(directory.id)} />
                </Tooltip>
                <Tooltip title="修改目录">
                  <Button type="text" size="small" icon={<EditOutlined />} aria-label={`修改目录“${directory.name}”`} onClick={() => openEdit(directory)} />
                </Tooltip>
                <Popconfirm title="删除目录" description={`确认删除“${directory.name}”吗？`} okText="删除" cancelText="取消" onConfirm={() => void remove(directory)}>
                  <Tooltip title="删除目录">
                    <Button type="text" size="small" danger icon={<DeleteOutlined />} aria-label={`删除目录“${directory.name}”`} />
                  </Tooltip>
                </Popconfirm>
              </span>
            )}
          </span>
        </span>
      ),
      children: buildNodes(directory.children),
    }));
    return [
      { key: ALL_DIRECTORY_KEY, icon: <AppstoreOutlined />, title: '全部' },
      { key: UNCATEGORIZED_DIRECTORY_KEY, icon: <InboxOutlined />, title: '未分类' },
      ...buildNodes(tree),
    ];
  })();

  const selectedKeys = selection === undefined
    ? [ALL_DIRECTORY_KEY]
    : selection === null ? [UNCATEGORIZED_DIRECTORY_KEY] : [selection];

  return (
    <aside className={`directory-tree-panel${collapsed ? ' directory-tree-panel-collapsed' : ''}`}>
      {messageContext}
      <div className="directory-tree-panel-header">
        {collapsed ? (
          <Tooltip title="展开目录" placement="right">
            <Button
              type="text"
              size="small"
              className="directory-tree-panel-toggle"
              icon={<MenuUnfoldOutlined />}
              aria-label="展开目录"
              onClick={toggleCollapsed}
            />
          </Tooltip>
        ) : (
          <>
            <span>目录</span>
            <span className="directory-tree-panel-header-actions">
              {canManage && (
                <Tooltip title="新建顶级目录">
                  <Button type="text" size="small" icon={<PlusOutlined />} aria-label="新建顶级目录" onClick={() => openCreate()} />
                </Tooltip>
              )}
              <Tooltip title="收起目录">
                <Button
                  type="text"
                  size="small"
                  className="directory-tree-panel-toggle"
                  icon={<MenuFoldOutlined />}
                  aria-label="收起目录"
                  onClick={toggleCollapsed}
                />
              </Tooltip>
            </span>
          </>
        )}
      </div>
      <div className="directory-tree-panel-content">
        <Spin spinning={loading} size="small" className="directory-tree-spin">
          <Tree
            blockNode
            showIcon
            defaultExpandAll
            selectedKeys={selectedKeys}
            treeData={treeData}
            onSelect={(keys) => {
              const key = String(keys[0] ?? ALL_DIRECTORY_KEY);
              onSelectionChange(key === ALL_DIRECTORY_KEY ? undefined : key === UNCATEGORIZED_DIRECTORY_KEY ? null : key);
            }}
          />
        </Spin>
      </div>
      {canManage && (
        <DirectoryDrawer
          scope={scope}
          open={Boolean(drawerState)}
          directory={drawerState?.directory ?? null}
          initialParentId={drawerState?.parentId}
          tree={tree}
          onClose={() => setDrawerState(null)}
        />
      )}
    </aside>
  );
};
