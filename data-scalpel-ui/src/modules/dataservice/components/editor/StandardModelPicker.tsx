import { CompactAlert as Alert } from '../../../../shared/components/ContextualFeedback';
import { LinkOutlined } from '@ant-design/icons';
import { Button, Space, Typography } from 'antd';
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ManagementCode, ManagementStatusIndicator } from '../../../../shared/components/ManagementListCells';
import {
  findDirectoryDescendantIds,
  useDirectoryTree,
  type DirectorySelection,
} from '../../../directory';
import {
  buildDataModelSearch,
  dataModelStatusLabels,
  useDataModel,
  useModelWarehouseLayers,
} from '../../../model';
import { useServiceEngineDataSourceRegistrations } from '../../../serviceengine';
import { useStandardDataServiceModelCandidates } from '../../hooks/useDataServices';
import type { StandardDataServiceModelCandidate } from '../../model/dataService';
import {
  ModelSelectionWorkspace,
  type ModelSelectionCandidate,
  type ModelSelectionFilters,
} from './ModelSelectionWorkspace';

interface StandardModelPickerProps {
  serviceId: string;
  engineId: string;
  value?: string;
  readOnly: boolean;
  canViewModels: boolean;
  canViewEngines: boolean;
  onChange: (modelId: string) => void;
}

const toSelectionCandidate = (model: StandardDataServiceModelCandidate): ModelSelectionCandidate => ({
  id: model.id,
  code: model.code,
  name: model.name,
  status: model.status,
  directoryId: model.directoryId,
  directoryName: model.directoryName,
  warehouseLayerId: model.warehouseLayerId,
  warehouseLayerCode: model.warehouseLayerCode,
  warehouseLayerName: model.warehouseLayerName,
  storageDataSourceId: model.storageDataSourceId,
  storageDataSourceCode: model.storageDataSourceCode,
  storageDataSourceName: model.storageDataSourceName,
  catalogName: model.catalogName,
  schemaName: model.schemaName,
  physicalTableName: model.physicalTableName,
  schemaVersion: model.schemaVersion,
  fieldCount: model.fieldCount,
  selectable: model.selectable,
  unavailableReason: model.unavailableReason,
  updatedAt: model.updatedAt,
});

export const StandardModelPicker = ({
  serviceId,
  engineId,
  value,
  readOnly,
  canViewModels,
  canViewEngines,
  onChange,
}: StandardModelPickerProps) => {
  const navigate = useNavigate();
  const [filters, setFilters] = useState<ModelSelectionFilters>({});
  const [directorySelection, setDirectorySelection] = useState<DirectorySelection>(undefined);
  const [includeUnavailable, setIncludeUnavailable] = useState(false);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const editable = !readOnly && canViewModels && canViewEngines;
  const directoriesQuery = useDirectoryTree('MODEL', editable);
  const layersQuery = useModelWarehouseLayers(
    { page: 0, size: 500, sort: 'sortOrder,code' },
    editable,
  );
  const readyRegistrationsQuery = useServiceEngineDataSourceRegistrations({
    search: `engineId:"${engineId}" AND status:"READY"`,
    page: 0,
    size: 500,
    sort: 'dataSourceId',
  }, editable && Boolean(engineId));
  const selectedModelQuery = useDataModel(value, canViewModels && Boolean(value));
  const selectedModel = selectedModelQuery.data?.model;
  const selectedFields = selectedModelQuery.data?.fields ?? [];
  const selectedRegistrationQuery = useServiceEngineDataSourceRegistrations({
    search: selectedModel?.storageDataSourceId
      ? `engineId:"${engineId}" AND dataSourceId:"${selectedModel.storageDataSourceId}"`
      : undefined,
    page: 0,
    size: 20,
    sort: 'dataSourceId',
  }, canViewEngines && Boolean(engineId) && Boolean(selectedModel?.storageDataSourceId));

  const directoryFilter = useMemo(() => {
    if (directorySelection === undefined) return {};
    if (directorySelection === null) return { uncategorized: true };
    return { directoryIds: findDirectoryDescendantIds(directoriesQuery.data ?? [], directorySelection) };
  }, [directorySelection, directoriesQuery.data]);
  const search = useMemo(
    () => buildDataModelSearch({ ...filters, ...directoryFilter }),
    [directoryFilter, filters],
  );
  const candidatesQuery = useStandardDataServiceModelCandidates(
    serviceId,
    { search, page, size, sort: '-updatedAt,code' },
    includeUnavailable,
    editable,
  );

  const selectedRegistration = selectedRegistrationQuery.data?.content[0];
  const compatibilityChecking = Boolean(value) && canViewModels && canViewEngines
    && (selectedModelQuery.isFetching || selectedRegistrationQuery.isFetching);
  const compatibilityProblem = !value
    ? undefined
    : !canViewModels
      ? '缺少模型查看权限，无法读取当前模型。'
      : !canViewEngines
        ? '缺少 Service Engine 查看权限，无法校验模型兼容性。'
        : selectedModel && !compatibilityChecking
          ? selectedModel.status !== 'PUBLISHED'
            ? '当前模型未发布。'
            : selectedFields.length === 0
              ? '当前模型尚未定义字段。'
              : !selectedRegistration || selectedRegistration.status !== 'READY'
                ? '当前模型的数据源未在所属 Engine 中就绪。'
                : undefined
          : undefined;
  const primaryKeyCount = selectedFields.filter((field) => field.primaryKey).length;
  const readonlyMessage = !canViewModels
    ? '缺少模型查看权限，无法加载当前发布模型的完整摘要。'
    : !canViewEngines
      ? '缺少 Service Engine 查看权限，仅展示当前发布模型，无法校验兼容性。'
      : readOnly
        ? '当前服务定义为只读状态，仅展示已保存的发布模型。'
        : undefined;

  const reset = () => {
    setFilters({});
    setDirectorySelection(undefined);
    setIncludeUnavailable(false);
    setPage(0);
  };

  const selectionSummary = (
    <div className="standard-model-selection-summary">
      {!value ? (
        <Typography.Text type="secondary">尚未选择发布模型</Typography.Text>
      ) : !canViewModels ? (
        <Typography.Text type="secondary">当前服务已保存模型引用，但无权加载模型详情。</Typography.Text>
      ) : selectedModelQuery.isError ? (
        <Typography.Text type="danger">已保存的模型无法加载，模型可能已删除。</Typography.Text>
      ) : !selectedModel ? (
        <Typography.Text type="secondary">正在加载当前模型…</Typography.Text>
      ) : (
        <>
          <div className="standard-model-selection-summary-content">
            <Space size={8} wrap>
              <Typography.Text strong>{selectedModel.name}</Typography.Text>
              <ManagementCode value={selectedModel.code} />
              <ManagementStatusIndicator
                label={dataModelStatusLabels[selectedModel.status]}
                tone={selectedModel.status === 'PUBLISHED' ? 'success' : 'warning'}
              />
              <Typography.Text type="secondary">
                Schema v{selectedModel.schemaVersion} · {selectedFields.length} 个字段 · {primaryKeyCount} 个主键
              </Typography.Text>
            </Space>
            <div className="standard-model-selection-compatibility">
              {compatibilityChecking ? (
                <ManagementStatusIndicator label="正在校验 Engine 兼容性" tone="processing" />
              ) : compatibilityProblem ? (
                <ManagementStatusIndicator label={compatibilityProblem} tone="warning" />
              ) : (
                <ManagementStatusIndicator label="与当前 Engine 兼容" tone="success" />
              )}
            </div>
          </div>
          <Button icon={<LinkOutlined />} onClick={() => navigate(`/model/${selectedModel.id}`)}>查看模型</Button>
        </>
      )}
    </div>
  );

  if (!editable) {
    return (
      <div className="standard-model-picker-readonly">
        <div className="management-results-surface">
          <div className="management-result-toolbar">
            <div className="management-result-title">当前发布模型</div>
          </div>
          {readonlyMessage && <Alert type="info" showIcon message={readonlyMessage} />}
          <div className="standard-model-picker-readonly-content">{selectionSummary}</div>
        </div>
      </div>
    );
  }

  return (
    <div className="standard-model-picker-workspace">
      <ModelSelectionWorkspace
        candidates={(candidatesQuery.data?.content ?? []).map(toSelectionCandidate)}
        total={candidatesQuery.data?.totalElements ?? 0}
        loading={candidatesQuery.isFetching}
        error={candidatesQuery.isError}
        directories={directoriesQuery.data ?? []}
        directoriesLoading={directoriesQuery.isFetching}
        directorySelection={directorySelection}
        filters={filters}
        layers={layersQuery.data?.content ?? []}
        layersLoading={layersQuery.isFetching}
        dataSourceOptions={(readyRegistrationsQuery.data?.content ?? []).map((registration) => ({
          value: registration.dataSourceId,
          label: `${registration.dataSourceName}（${registration.dataSourceCode}）`,
        }))}
        dataSourcesLoading={readyRegistrationsQuery.isFetching}
        showStatusFilter
        showAvailabilityColumn
        resultTitle="可选模型"
        emptyDescription="没有符合条件的模型"
        page={page}
        size={size}
        selectionMode="radio"
        selectedRowKeys={value ? [value] : []}
        includeUnavailable={includeUnavailable}
        showUnavailableToggle
        footer={selectionSummary}
        onDirectorySelectionChange={(selection) => { setDirectorySelection(selection); setPage(0); }}
        onFiltersChange={(nextFilters) => { setFilters(nextFilters); setPage(0); }}
        onReset={reset}
        onPageChange={(nextPage, nextSize) => { setPage(nextPage); setSize(nextSize); }}
        onCandidateToggle={(model, selected) => { if (selected) onChange(model.id); }}
        onIncludeUnavailableChange={(checked) => { setIncludeUnavailable(checked); setPage(0); }}
        onRetry={() => void candidatesQuery.refetch()}
      />
    </div>
  );
};
