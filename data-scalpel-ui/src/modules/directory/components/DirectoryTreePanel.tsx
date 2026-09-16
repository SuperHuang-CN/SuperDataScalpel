import { AppstoreOutlined, DeleteOutlined, DownOutlined, EditOutlined, EllipsisOutlined, ExportOutlined, FolderAddOutlined, ImportOutlined, InboxOutlined, MenuFoldOutlined, MenuUnfoldOutlined, MoreOutlined, PlusOutlined, RightOutlined } from '@ant-design/icons';
import { Button, Dropdown, Modal, Segmented, Spin, Tooltip, Tree, message } from 'antd';
import type { DataNode } from 'antd/es/tree';
import { useRef, useState, type KeyboardEvent, type PointerEvent, type ReactNode } from 'react';
import { ApiError } from '../../../shared/api/http';
import { downloadBlob } from '../../../shared/browser/downloadBlob';
import { useDeleteDirectory, useExportDirectoryTree } from '../hooks/useDirectories';
import type { DirectoryScope, DirectoryTreeNode } from '../model/directory';
import { DirectoryDrawer } from './DirectoryDrawer';
import { DirectoryImportModal } from './DirectoryImportModal';

const DIRECTORY_PANEL_COLLAPSED_STORAGE_KEY_PREFIX = 'data-scalpel.ui.directory-panel.collapsed';
const DIRECTORY_PANEL_WIDTH_STORAGE_KEY = 'data-scalpel.ui.directory-panel.width';
const DIRECTORY_PANEL_DEFAULT_WIDTH = 244;
const DIRECTORY_PANEL_MIN_WIDTH = 200;
const DIRECTORY_PANEL_MAX_WIDTH = 360;
const DIRECTORY_PANEL_MAX_WIDTH_RATIO = 0.35;
const DIRECTORY_PANEL_KEYBOARD_STEP = 8;

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

const readDirectoryPanelWidthPreference = (): number => {
  try {
    const storedValue = window.localStorage.getItem(DIRECTORY_PANEL_WIDTH_STORAGE_KEY);
    const storedWidth = storedValue === null ? Number.NaN : Number(storedValue);
    if (Number.isFinite(storedWidth)) {
      return Math.min(DIRECTORY_PANEL_MAX_WIDTH, Math.max(DIRECTORY_PANEL_MIN_WIDTH, storedWidth));
    }
  } catch {
    // Local storage may be unavailable in restricted browser environments.
  }
  return DIRECTORY_PANEL_DEFAULT_WIDTH;
};

const writeDirectoryPanelWidthPreference = (width: number) => {
  try {
    window.localStorage.setItem(DIRECTORY_PANEL_WIDTH_STORAGE_KEY, String(Math.round(width)));
  } catch {
    // Local storage may be unavailable in restricted browser environments.
  }
};

export type DirectorySelection = string | null | undefined;

interface DirectoryTreePanelProps {
  scope: DirectoryScope;
  label?: string;
  tree: DirectoryTreeNode[];
  loading: boolean;
  selection: DirectorySelection;
  canManage?: boolean;
  showResourceCounts?: boolean;
  showVirtualNodes?: boolean;
  navigationTabs?: {
    activeKey: 'directories' | 'resources';
    resourceLabel: string;
    resourceContent: ReactNode;
    onChange: (key: 'directories' | 'resources') => void;
  };
  onSelectionChange: (selection: DirectorySelection) => void;
}

export const DirectoryTreePanel = ({
  scope,
  label = '目录',
  tree,
  loading,
  selection,
  canManage = false,
  showResourceCounts = true,
  showVirtualNodes = true,
  navigationTabs,
  onSelectionChange,
}: DirectoryTreePanelProps) => {
  const [messageApi, messageContext] = message.useMessage();
  const [modal, modalContext] = Modal.useModal();
  const [collapsed, setCollapsed] = useState(() => readDirectoryPanelCollapsedPreference(scope));
  const [panelWidth, setPanelWidth] = useState(readDirectoryPanelWidthPreference);
  const [resizing, setResizing] = useState(false);
  const [drawerState, setDrawerState] = useState<{ directory: DirectoryTreeNode | null; parentId?: string } | null>(null);
  const [importOpen, setImportOpen] = useState(false);
  const [contextMenuId, setContextMenuId] = useState<string>();
  const panelRef = useRef<HTMLElement>(null);
  const resizeStateRef = useRef<{
    pointerId: number;
    startX: number;
    startWidth: number;
    maxWidth: number;
    currentWidth: number;
  } | null>(null);
  const deleteMutation = useDeleteDirectory(scope);
  const exportMutation = useExportDirectoryTree();

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
      messageApi.success(`${label}已删除`);
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : `删除${label}失败`);
    }
  };

  const confirmRemove = (directory: DirectoryTreeNode) => {
    modal.confirm({
      title: `删除${label}`,
      content: `确认删除${label}“${directory.name}”吗？`,
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => remove(directory),
    });
  };

  const exportTree = async () => {
    try {
      const blob = await exportMutation.mutateAsync(scope);
      downloadBlob(blob, `DataScalpel-${scope}-${label}-${new Date().toISOString().slice(0, 10)}.xlsx`);
      messageApi.success(`${label}已导出`);
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : `导出${label}失败`);
    }
  };

  const getMaximumPanelWidth = () => {
    const availableWidth = panelRef.current?.parentElement?.getBoundingClientRect().width;
    if (!availableWidth) return DIRECTORY_PANEL_MAX_WIDTH;
    return Math.max(
      DIRECTORY_PANEL_MIN_WIDTH,
      Math.min(DIRECTORY_PANEL_MAX_WIDTH, availableWidth * DIRECTORY_PANEL_MAX_WIDTH_RATIO),
    );
  };

  const updatePanelWidth = (nextWidth: number, maximumWidth = getMaximumPanelWidth()) => {
    const boundedWidth = Math.round(Math.min(maximumWidth, Math.max(DIRECTORY_PANEL_MIN_WIDTH, nextWidth)));
    setPanelWidth(boundedWidth);
    writeDirectoryPanelWidthPreference(boundedWidth);
  };

  const startResize = (event: PointerEvent<HTMLDivElement>) => {
    if (event.button !== 0) return;
    const currentWidth = panelRef.current?.getBoundingClientRect().width ?? panelWidth;
    const maximumWidth = getMaximumPanelWidth();
    resizeStateRef.current = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startWidth: currentWidth,
      maxWidth: maximumWidth,
      currentWidth,
    };
    event.currentTarget.setPointerCapture(event.pointerId);
    setResizing(true);
    event.preventDefault();
  };

  const resize = (event: PointerEvent<HTMLDivElement>) => {
    const resizeState = resizeStateRef.current;
    if (!resizeState || resizeState.pointerId !== event.pointerId) return;
    const nextWidth = Math.round(Math.min(
      resizeState.maxWidth,
      Math.max(DIRECTORY_PANEL_MIN_WIDTH, resizeState.startWidth + event.clientX - resizeState.startX),
    ));
    resizeState.currentWidth = nextWidth;
    setPanelWidth(nextWidth);
  };

  const finishResize = (event: PointerEvent<HTMLDivElement>) => {
    const resizeState = resizeStateRef.current;
    if (!resizeState || resizeState.pointerId !== event.pointerId) return;
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
    writeDirectoryPanelWidthPreference(resizeState.currentWidth);
    resizeStateRef.current = null;
    setResizing(false);
  };

  const resizeWithKeyboard = (event: KeyboardEvent<HTMLDivElement>) => {
    const maximumWidth = getMaximumPanelWidth();
    let nextWidth: number | undefined;
    if (event.key === 'ArrowLeft') nextWidth = panelWidth - DIRECTORY_PANEL_KEYBOARD_STEP;
    if (event.key === 'ArrowRight') nextWidth = panelWidth + DIRECTORY_PANEL_KEYBOARD_STEP;
    if (event.key === 'Home') nextWidth = DIRECTORY_PANEL_MIN_WIDTH;
    if (event.key === 'End') nextWidth = maximumWidth;
    if (nextWidth === undefined) return;
    event.preventDefault();
    updatePanelWidth(nextWidth, maximumWidth);
  };

  const treeData: DataNode[] = (() => {
    const buildNodes = (nodes: DirectoryTreeNode[]): DataNode[] => nodes.map((directory) => ({
      key: directory.id,
      title: (
        <Dropdown
          classNames={{ root: 'directory-context-menu' }}
          trigger={['contextMenu']}
          disabled={!canManage}
          open={canManage && contextMenuId === directory.id}
          onOpenChange={(open) => setContextMenuId(open ? directory.id : undefined)}
          autoFocus
          menu={{
            id: `directory-context-menu-${directory.id}`,
            items: [
              { key: 'create', icon: <FolderAddOutlined />, label: `新增子${label}` },
              { key: 'edit', icon: <EditOutlined />, label: `修改${label}` },
              { type: 'divider' },
              { key: 'delete', icon: <DeleteOutlined />, label: `删除${label}`, danger: true },
            ],
            onClick: ({ key, domEvent }) => {
              domEvent.stopPropagation();
              setContextMenuId(undefined);
              if (!canManage) return;
              if (key === 'create') openCreate(directory.id);
              if (key === 'edit') openEdit(directory);
              if (key === 'delete') confirmRemove(directory);
            },
          }}
        >
          <span
            className="directory-tree-node-title"
            tabIndex={canManage ? 0 : undefined}
            aria-haspopup={canManage ? 'menu' : undefined}
            aria-expanded={canManage ? contextMenuId === directory.id : undefined}
            onKeyDown={(event) => {
              if (canManage && ((event.shiftKey && event.key === 'F10') || event.key === 'ContextMenu')) {
                event.preventDefault();
                event.stopPropagation();
                setContextMenuId(directory.id);
              }
              if (event.target === event.currentTarget && contextMenuId === directory.id
                && (event.key === 'ArrowDown' || event.key === 'ArrowUp')) {
                event.preventDefault();
                event.stopPropagation();
                const items = document.getElementById(`directory-context-menu-${directory.id}`)
                  ?.querySelectorAll<HTMLElement>('[role="menuitem"]');
                const item = event.key === 'ArrowUp' ? items?.[items.length - 1] : items?.[0];
                item?.focus();
              }
            }}
          >
            <Tooltip title={canManage ? `${directory.name} · 右键或 Shift+F10 管理` : directory.name} mouseEnterDelay={0.5}>
              <span className="directory-tree-node-name">{directory.name}</span>
            </Tooltip>
            {showResourceCounts && directory.resourceCount > 0 && (
              <span className="directory-tree-node-meta">
                <Tooltip title={`${directory.resourceCount} 个资源`}>
                  <span className="directory-tree-node-count">{directory.resourceCount}</span>
                </Tooltip>
              </span>
            )}
            {canManage && (
              <Button
                className="directory-tree-node-touch-actions"
                type="text"
                size="small"
                icon={<EllipsisOutlined />}
                aria-label={`${label}“${directory.name}”的更多操作`}
                aria-haspopup="menu"
                onClick={(event) => {
                  event.stopPropagation();
                  setContextMenuId(directory.id);
                }}
              />
            )}
          </span>
        </Dropdown>
      ),
      children: buildNodes(directory.children),
    }));
    return buildNodes(tree);
  })();

  const selectedKeys = selection === null || selection === undefined ? [] : [selection];
  const totalResourceCount = tree.reduce((total, directory) => total + directory.resourceCount, 0);

  return (
    <aside
      ref={panelRef}
      className={`directory-tree-panel${collapsed ? ' directory-tree-panel-collapsed' : ''}${resizing ? ' directory-tree-panel-resizing' : ''}`}
      style={collapsed ? undefined : { flexBasis: panelWidth, width: panelWidth }}
    >
      {messageContext}
      {modalContext}
      <div className="directory-tree-panel-header">
        {collapsed ? (
          <Tooltip title={`展开${label}`} placement="right">
            <Button
              type="text"
              size="small"
              className="directory-tree-panel-toggle"
              icon={<MenuUnfoldOutlined />}
              aria-label={`展开${label}`}
              onClick={toggleCollapsed}
            />
          </Tooltip>
        ) : (
          <>
            {navigationTabs ? (
              <Segmented
                size="small"
                value={navigationTabs.activeKey}
                options={[{ label: '目录', value: 'directories' }, { label: navigationTabs.resourceLabel, value: 'resources' }]}
                onChange={(value) => navigationTabs.onChange(value as 'directories' | 'resources')}
              />
            ) : <span>{label}</span>}
            <span className="directory-tree-panel-header-actions">
              {canManage && (!navigationTabs || navigationTabs.activeKey === 'directories') && (
                <Tooltip title={`新建顶级${label}`}>
                  <Button type="text" size="small" icon={<PlusOutlined />} aria-label={`新建顶级${label}`} onClick={() => openCreate()} />
                </Tooltip>
              )}
              {(!navigationTabs || navigationTabs.activeKey === 'directories') && <Tooltip title={`${label}导入导出`}>
                <Dropdown
                  trigger={['click']}
                  placement="bottomRight"
                  menu={{
                    items: [
                      ...(canManage ? [{ key: 'import', icon: <ImportOutlined />, label: `导入${label}` }] : []),
                      { key: 'export', icon: <ExportOutlined />, label: `导出${label}` },
                    ],
                    onClick: ({ key }) => {
                      if (key === 'import') setImportOpen(true);
                      if (key === 'export') void exportTree();
                    },
                  }}
                >
                  <Button
                    type="text"
                    size="small"
                    icon={<MoreOutlined />}
                    loading={exportMutation.isPending}
                    aria-label={`${label}导入导出`}
                  />
                </Dropdown>
              </Tooltip>}
              <Tooltip title={`收起${label}`}>
                <Button
                  type="text"
                  size="small"
                  className="directory-tree-panel-toggle"
                  icon={<MenuFoldOutlined />}
                  aria-label={`收起${label}`}
                  onClick={toggleCollapsed}
                />
              </Tooltip>
            </span>
          </>
        )}
      </div>
      <div className="directory-tree-panel-content">
        {navigationTabs?.activeKey === 'resources' ? navigationTabs.resourceContent : <>
        {showVirtualNodes && (
          <div className="directory-tree-panel-quick-filters" aria-label={`${label}快捷筛选`}>
            <Button
              type="text"
              className={selection === undefined ? 'directory-tree-quick-filter-active' : undefined}
              icon={<AppstoreOutlined />}
              aria-pressed={selection === undefined}
              onClick={() => onSelectionChange(undefined)}
            >
              全部
              {showResourceCounts && totalResourceCount > 0 && (
                <span className="directory-tree-quick-filter-count">{totalResourceCount}</span>
              )}
            </Button>
            <Button
              type="text"
              className={selection === null ? 'directory-tree-quick-filter-active' : undefined}
              icon={<InboxOutlined />}
              aria-pressed={selection === null}
              onClick={() => onSelectionChange(null)}
            >
              未分类
            </Button>
          </div>
        )}
        <Spin spinning={loading} size="small" className="directory-tree-spin">
          <Tree
            blockNode
            showLine={{ showLeafIcon: false }}
            switcherIcon={({ expanded }) => expanded ? <DownOutlined /> : <RightOutlined />}
            defaultExpandAll
            selectedKeys={selectedKeys}
            treeData={treeData}
            onSelect={(keys) => onSelectionChange(keys[0] === undefined ? undefined : String(keys[0]))}
          />
        </Spin>
        </>}
      </div>
      {!collapsed && (
        <div
          className="directory-tree-panel-resizer"
          role="separator"
          aria-label={`调整${label}面板宽度`}
          aria-orientation="vertical"
          aria-valuemin={DIRECTORY_PANEL_MIN_WIDTH}
          aria-valuemax={DIRECTORY_PANEL_MAX_WIDTH}
          aria-valuenow={Math.round(panelWidth)}
          tabIndex={0}
          title={`拖动调整${label}面板宽度，双击恢复默认宽度`}
          onDoubleClick={() => updatePanelWidth(DIRECTORY_PANEL_DEFAULT_WIDTH)}
          onKeyDown={resizeWithKeyboard}
          onPointerDown={startResize}
          onPointerMove={resize}
          onPointerUp={finishResize}
          onPointerCancel={finishResize}
        />
      )}
      {canManage && (
        <>
          <DirectoryDrawer
            scope={scope}
            label={label}
            open={Boolean(drawerState)}
            directory={drawerState?.directory ?? null}
            initialParentId={drawerState?.parentId}
            tree={tree}
            onClose={() => setDrawerState(null)}
          />
          <DirectoryImportModal
            scope={scope}
            label={label}
            open={importOpen}
            onClose={() => setImportOpen(false)}
          />
        </>
      )}
    </aside>
  );
};
