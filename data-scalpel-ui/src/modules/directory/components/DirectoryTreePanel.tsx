import { DeleteOutlined, EditOutlined, FolderAddOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, Popconfirm, Spin, Tree, message } from 'antd';
import type { DataNode } from 'antd/es/tree';
import { useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { useDeleteDirectory } from '../hooks/useDirectories';
import type { DirectoryScope, DirectoryTreeNode } from '../model/directory';
import { DirectoryDrawer } from './DirectoryDrawer';

const ALL_DIRECTORY_KEY = '__all__';
const UNCATEGORIZED_DIRECTORY_KEY = '__uncategorized__';

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
  const [drawerState, setDrawerState] = useState<{ directory: DirectoryTreeNode | null; parentId?: string } | null>(null);
  const deleteMutation = useDeleteDirectory(scope);

  const openCreate = (parentId?: string) => setDrawerState({ directory: null, parentId });
  const openEdit = (directory: DirectoryTreeNode) => setDrawerState({ directory });

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
      title: (
        <span className="directory-tree-node-title">
          <span className="directory-tree-node-name">{directory.name} <span className="directory-tree-node-count">({directory.resourceCount})</span></span>
          {canManage && (
            <span className="directory-tree-node-actions" onClick={(event) => event.stopPropagation()}>
              <Button type="text" size="small" icon={<FolderAddOutlined />} title="新建子目录" onClick={() => openCreate(directory.id)} />
              <Button type="text" size="small" icon={<EditOutlined />} title="修改目录" onClick={() => openEdit(directory)} />
              <Popconfirm title="删除目录" description={`确认删除“${directory.name}”吗？`} okText="删除" cancelText="取消" onConfirm={() => void remove(directory)}>
                <Button type="text" size="small" danger icon={<DeleteOutlined />} title="删除目录" />
              </Popconfirm>
            </span>
          )}
        </span>
      ),
      children: buildNodes(directory.children),
    }));
    return [
      { key: ALL_DIRECTORY_KEY, title: '全部' },
      { key: UNCATEGORIZED_DIRECTORY_KEY, title: '未分类' },
      ...buildNodes(tree),
    ];
  })();

  const selectedKeys = selection === undefined
    ? [ALL_DIRECTORY_KEY]
    : selection === null ? [UNCATEGORIZED_DIRECTORY_KEY] : [selection];

  return (
    <aside className="directory-tree-panel">
      {messageContext}
      <div className="directory-tree-panel-header">
        <span>目录</span>
        {canManage && <Button type="text" size="small" icon={<PlusOutlined />} title="新建顶级目录" onClick={() => openCreate()} />}
      </div>
      <Spin spinning={loading} size="small" className="directory-tree-spin">
        <Tree
          blockNode
          showLine={{ showLeafIcon: false }}
          defaultExpandAll
          selectedKeys={selectedKeys}
          treeData={treeData}
          onSelect={(keys) => {
            const key = String(keys[0] ?? ALL_DIRECTORY_KEY);
            onSelectionChange(key === ALL_DIRECTORY_KEY ? undefined : key === UNCATEGORIZED_DIRECTORY_KEY ? null : key);
          }}
        />
      </Spin>
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
