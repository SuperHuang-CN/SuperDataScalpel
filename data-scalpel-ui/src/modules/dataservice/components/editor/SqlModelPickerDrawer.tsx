import { Button, Drawer, Segmented, Space, Typography } from 'antd';
import { useMemo, useState } from 'react';
import {
  findDirectoryDescendantIds,
  useDirectoryTree,
  type DirectorySelection,
  type DirectoryTreeNode,
} from '../../../directory';
import {
  buildDataModelSearch,
  useDataModels,
  useModelWarehouseLayers,
  type DataModel,
} from '../../../model';
import {
  ModelSelectionWorkspace,
  type ModelSelectionCandidate,
  type ModelSelectionFilters,
} from './ModelSelectionWorkspace';

interface SqlModelPickerDrawerProps {
  open: boolean;
  dataSourceId: string;
  dataSourceName?: string;
  selectedModelIds: string[];
  selectedCandidates: ModelSelectionCandidate[];
  readOnly: boolean;
  onClose: () => void;
  onConfirm: (modelIds: string[], candidates: ModelSelectionCandidate[]) => void;
}

const escapeDslText = (value: string) => value.replaceAll('\\', '\\\\').replaceAll('"', '\\"');

const directoryNames = (nodes: DirectoryTreeNode[]): Map<string, string> => {
  const result = new Map<string, string>();
  const visit = (items: DirectoryTreeNode[]) => items.forEach((item) => {
    result.set(item.id, item.name);
    visit(item.children);
  });
  visit(nodes);
  return result;
};

const sqlCandidateSearch = (
  dataSourceId: string,
  filters: ModelSelectionFilters,
  directoryFilter: { directoryIds?: string[]; uncategorized?: boolean },
) => {
  const base = buildDataModelSearch({
    storageDataSourceId: dataSourceId,
    status: filters.status,
    warehouseLayerId: filters.warehouseLayerId,
    ...directoryFilter,
  });
  const keyword = filters.keyword?.trim();
  if (!keyword) return base;
  const escaped = escapeDslText(keyword);
  const keywordSearch = `(name:*"${escaped}"* OR code:*"${escaped}"* OR physicalTableName:*"${escaped}"*)`;
  return base ? `${base} AND ${keywordSearch}` : keywordSearch;
};

const toCandidate = (
  model: DataModel,
  names: Map<string, string>,
): ModelSelectionCandidate => ({
  id: model.id,
  code: model.code,
  name: model.name,
  status: model.status,
  directoryId: model.directoryId,
  directoryName: model.directoryId ? names.get(model.directoryId) ?? null : null,
  warehouseLayerId: model.warehouseLayer?.id ?? null,
  warehouseLayerCode: model.warehouseLayer?.code ?? null,
  warehouseLayerName: model.warehouseLayer?.name ?? null,
  storageDataSourceId: model.storageDataSourceId,
  storageDataSourceCode: null,
  storageDataSourceName: model.storageDataSourceName,
  catalogName: model.catalogName,
  schemaName: model.schemaName,
  physicalTableName: model.physicalTableName,
  schemaVersion: model.schemaVersion,
  selectable: true,
  unavailableReason: null,
  updatedAt: model.updatedAt,
});

export const SqlModelPickerDrawer = ({
  open,
  dataSourceId,
  dataSourceName,
  selectedModelIds,
  selectedCandidates,
  readOnly,
  onClose,
  onConfirm,
}: SqlModelPickerDrawerProps) => {
  const [view, setView] = useState<'all' | 'selected'>(readOnly ? 'selected' : 'all');
  const [filters, setFilters] = useState<ModelSelectionFilters>({});
  const [directorySelection, setDirectorySelection] = useState<DirectorySelection>(undefined);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [selectedPage, setSelectedPage] = useState(0);
  const [selectedSize, setSelectedSize] = useState(20);
  const [draftModelIds, setDraftModelIds] = useState<string[]>(selectedModelIds);
  const [draftCandidateOverrides, setDraftCandidateOverrides] = useState<Map<string, ModelSelectionCandidate>>(
    new Map(),
  );
  const directoriesQuery = useDirectoryTree('MODEL', open && !readOnly);
  const layersQuery = useModelWarehouseLayers(
    { page: 0, size: 500, sort: 'sortOrder,code' },
    open && !readOnly,
  );
  const directoryFilter = useMemo(() => {
    if (directorySelection === undefined) return {};
    if (directorySelection === null) return { uncategorized: true };
    return { directoryIds: findDirectoryDescendantIds(directoriesQuery.data ?? [], directorySelection) };
  }, [directorySelection, directoriesQuery.data]);
  const search = useMemo(
    () => sqlCandidateSearch(dataSourceId, filters, directoryFilter),
    [dataSourceId, directoryFilter, filters],
  );
  const candidatesQuery = useDataModels(
    { search, page, size, sort: '-updatedAt,code' },
    open && !readOnly && Boolean(dataSourceId),
  );
  const names = useMemo(() => directoryNames(directoriesQuery.data ?? []), [directoriesQuery.data]);
  const candidateRows = useMemo(
    () => (candidatesQuery.data?.content ?? []).map((model) => toCandidate(model, names)),
    [candidatesQuery.data?.content, names],
  );
  const draftCandidates = useMemo(() => {
    const candidates = new Map(selectedCandidates.map((candidate) => [candidate.id, candidate]));
    draftCandidateOverrides.forEach((candidate, id) => candidates.set(id, candidate));
    return candidates;
  }, [draftCandidateOverrides, selectedCandidates]);
  const selectedRows = draftModelIds.map((id) => draftCandidates.get(id)).filter(
    (candidate): candidate is ModelSelectionCandidate => candidate !== undefined,
  );
  const invalidSelections = selectedRows.filter((candidate) => !candidate.selectable);

  const initialize = () => {
    setView(readOnly ? 'selected' : 'all');
    setDraftModelIds(selectedModelIds);
    setDraftCandidateOverrides(new Map());
    setSelectedPage(0);
  };

  const close = () => {
    initialize();
    onClose();
  };

  const toggleCandidate = (candidate: ModelSelectionCandidate, selected: boolean) => {
    setDraftCandidateOverrides((current) => new Map(current).set(candidate.id, candidate));
    setDraftModelIds((current) => selected
      ? current.includes(candidate.id) ? current : [...current, candidate.id]
      : current.filter((id) => id !== candidate.id));
  };

  const toggleCandidates = (candidates: ModelSelectionCandidate[], selected: boolean) => {
    setDraftCandidateOverrides((current) => {
      const next = new Map(current);
      candidates.forEach((candidate) => next.set(candidate.id, candidate));
      return next;
    });
    setDraftModelIds((current) => {
      if (!selected) {
        const removed = new Set(candidates.map((candidate) => candidate.id));
        return current.filter((id) => !removed.has(id));
      }
      const existing = new Set(current);
      return [...current, ...candidates.map((candidate) => candidate.id).filter((id) => !existing.has(id))];
    });
  };

  const selectedPageRows = selectedRows.slice(
    selectedPage * selectedSize,
    selectedPage * selectedSize + selectedSize,
  );
  const displayRows = view === 'all' ? candidateRows : selectedPageRows;
  const displayTotal = view === 'all' ? candidatesQuery.data?.totalElements ?? 0 : selectedRows.length;
  const displayPage = view === 'all' ? page : selectedPage;
  const displaySize = view === 'all' ? size : selectedSize;

  return (
    <Drawer
      rootClassName="business-overlay business-drawer-overlay sql-model-picker-drawer"
      open={open}
      size="min(1080px, calc(100vw - 24px))"
      title={(
        <div>
          <Typography.Text strong>{readOnly ? '查看关联模型' : '选择关联模型'}</Typography.Text>
          <div><Typography.Text type="secondary">当前数据源：{dataSourceName ?? dataSourceId}</Typography.Text></div>
        </div>
      )}
      destroyOnHidden
      onClose={close}
      styles={{ body: { padding: 0, overflow: 'hidden' } }}
      footer={(
        <div className="sql-model-picker-footer">
          <Space size={8}>
            <Typography.Text>已选择 {draftModelIds.length} 个模型</Typography.Text>
            {invalidSelections.length > 0 && (
              <Typography.Text type="danger">其中 {invalidSelections.length} 个需要移除或修复</Typography.Text>
            )}
          </Space>
          <Space>
            <Button onClick={close}>{readOnly ? '关闭' : '取消'}</Button>
            {!readOnly && (
              <Button
                type="primary"
                disabled={draftModelIds.length === 0 || invalidSelections.length > 0}
                onClick={() => {
                  onConfirm(draftModelIds, selectedRows);
                  onClose();
                }}
              >
                确定
              </Button>
            )}
          </Space>
        </div>
      )}
    >
      <div className="sql-model-picker-body">
        <div className="sql-model-picker-view-switch">
          <Segmented
            value={view}
            options={readOnly
              ? [{ label: `已选模型 ${draftModelIds.length}`, value: 'selected' }]
              : [
                { label: '全部模型', value: 'all' },
                { label: `已选模型 ${draftModelIds.length}`, value: 'selected' },
              ]}
            onChange={(value) => setView(value as 'all' | 'selected')}
          />
        </div>
        <ModelSelectionWorkspace
          candidates={displayRows}
          total={displayTotal}
          loading={view === 'all' && candidatesQuery.isFetching}
          error={view === 'all' && candidatesQuery.isError}
          directories={directoriesQuery.data ?? []}
          directoriesLoading={directoriesQuery.isFetching}
          directorySelection={directorySelection}
          filters={filters}
          layers={layersQuery.data?.content ?? []}
          layersLoading={layersQuery.isFetching}
          showStatusFilter
          showAvailabilityColumn={view === 'selected'}
          showBrowserControls={view === 'all' && !readOnly}
          keywordPlaceholder="搜索名称、编码或物理表"
          resultTitle={view === 'all' ? '当前数据源模型' : '已选模型'}
          emptyDescription={view === 'all' ? '没有符合条件的模型' : '尚未选择模型'}
          page={displayPage}
          size={displaySize}
          selectionMode={readOnly ? 'none' : 'checkbox'}
          selectedRowKeys={draftModelIds}
          allowRemovingUnavailableSelection
          onDirectorySelectionChange={(selection) => { setDirectorySelection(selection); setPage(0); }}
          onFiltersChange={(nextFilters) => { setFilters(nextFilters); setPage(0); }}
          onReset={() => { setFilters({}); setDirectorySelection(undefined); setPage(0); }}
          onPageChange={(nextPage, nextSize) => {
            if (view === 'all') {
              setPage(nextPage);
              setSize(nextSize);
            } else {
              setSelectedPage(nextPage);
              setSelectedSize(nextSize);
            }
          }}
          onCandidateToggle={toggleCandidate}
          onCandidatesToggle={toggleCandidates}
          onRetry={() => void candidatesQuery.refetch()}
        />
      </div>
    </Drawer>
  );
};
