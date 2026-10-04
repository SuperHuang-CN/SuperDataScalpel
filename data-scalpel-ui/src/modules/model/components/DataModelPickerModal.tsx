import { ApartmentOutlined, DeleteOutlined, SearchOutlined, TableOutlined } from '@ant-design/icons';
import { OverlayTitle } from '../../../shared/components/OverlayTitle';
import { Button, Empty, Input, Modal, Popover, Space, Table, Tag, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { useCurrentUser } from '../../system';
import { useDataModel, useDataModels } from '../hooks/useDataModels';
import { buildDataModelSearch } from '../model/dataModelSearch';
import type { ModelWarehouseLayerSummary } from '../model/dataModel';

const SEARCH_DELAY_MS = 300;
const CANDIDATE_LIMIT = 100;

export type DataModelPickerSelectionMode = 'single' | 'multiple';

export interface DataModelPickerCandidate {
  id: string;
  name: string;
  code: string;
  storageDataSourceName?: string | null;
  warehouseLayer?: ModelWarehouseLayerSummary | null;
  disabled?: boolean;
  extra?: ReactNode;
}

export interface DataModelPickerSource {
  candidates: readonly DataModelPickerCandidate[];
  loading: boolean;
  error: boolean;
  onSearch: (keyword: string) => void;
  onRetry: () => void;
  heading: string;
  emptyText: string;
  limit: number;
}

interface DataModelPickerModalProps {
  open: boolean;
  value: readonly string[];
  onCancel: () => void;
  onConfirm: (modelIds: string[]) => void;
  title?: string;
  selectionMode?: DataModelPickerSelectionMode;
  rootClassName?: string;
  source?: DataModelPickerSource;
  confirmLoading?: boolean;
}

const modelColumns = (canViewModels: boolean): TableColumnsType<DataModelPickerCandidate> => [
  {
    title: '模型',
    key: 'model',
    render: (_, model) => {
      const detailHref = `/model/${model.id}`;
      return (
        <div className="resource-picker-identity">
          <div className="resource-picker-model-name">
            <Typography.Text ellipsis={{ tooltip: model.name }}>
              {canViewModels ? (
                <a className="resource-picker-model-detail-link" href={detailHref} target="_blank" rel="opener" aria-label={`在新标签页查看模型 ${model.name} 详情`}>
                  {model.name}
                </a>
              ) : model.name}
            </Typography.Text>
            {model.extra}
          </div>
          <Typography.Text type="secondary" ellipsis={{ tooltip: model.code }}>
            {canViewModels ? (
              <a className="resource-picker-model-detail-link" href={detailHref} target="_blank" rel="opener" aria-label={`在新标签页查看模型编码 ${model.code} 详情`}>
                {model.code}
              </a>
            ) : model.code}
          </Typography.Text>
        </div>
      );
    },
  },
  {
    title: '存储 / 分层',
    key: 'storageAndLayer',
    width: 220,
    render: (_, model) => {
      const layer = model.warehouseLayer;
      const layerLabel = layer ? `${layer.code} · ${layer.name}` : '未分层';
      return (
        <div className="resource-picker-identity">
          <Typography.Text ellipsis={{ tooltip: model.storageDataSourceName ?? '—' }}>{model.storageDataSourceName ?? '—'}</Typography.Text>
          <Typography.Text type="secondary" ellipsis={{ tooltip: layerLabel }}>{layerLabel}</Typography.Text>
        </div>
      );
    },
  },
];

const SelectedModelRow = ({ modelId, candidate, externalSource, onRemove }: {
  modelId: string;
  candidate?: DataModelPickerCandidate;
  externalSource: boolean;
  onRemove: () => void;
}) => {
  const detailQuery = useDataModel(modelId, !externalSource && !candidate);
  const model = candidate ?? detailQuery.data?.model;
  const code = model?.code ?? modelId;
  const layer = model?.warehouseLayer;
  return (
    <div className="resource-picker-selected-row">
      <ApartmentOutlined />
      <Popover
        trigger={['hover', 'focus', 'click']}
        title={<OverlayTitle variant="popover" title="模型详情" />}
        content={(
          <div className="resource-picker-selected-details">
            <span className="resource-picker-selected-label">名称</span><strong>{model?.name ?? '模型信息不可用'}</strong>
            <span className="resource-picker-selected-label">编码</span><code>{code}</code>
            <span className="resource-picker-selected-label">存储</span><span>{model?.storageDataSourceName ?? '—'}</span>
            <span className="resource-picker-selected-label">分层</span><span>{layer ? `${layer.code} · ${layer.name}` : '未分层'}</span>
          </div>
        )}
      >
        <Button type="text" className="resource-picker-selected-code" aria-label={`查看模型 ${code} 详情`}>
          {code}
        </Button>
      </Popover>
      <Button type="text" size="small" danger icon={<DeleteOutlined />} aria-label={`移除模型 ${code}`} onClick={onRemove} />
    </div>
  );
};

export const DataModelPickerModal = ({
  open,
  value,
  onCancel,
  onConfirm,
  title = '选择已发布模型',
  selectionMode = 'single',
  rootClassName,
  source,
  confirmLoading = false,
}: DataModelPickerModalProps) => {
  if (!open) return null;
  return (
    <DataModelPickerModalContent
      key={`${selectionMode}:${value.join(',')}`}
      value={value}
      onCancel={onCancel}
      onConfirm={onConfirm}
      title={title}
      selectionMode={selectionMode}
      rootClassName={rootClassName}
      source={source}
      confirmLoading={confirmLoading}
    />
  );
};

type DataModelPickerModalContentProps = Omit<DataModelPickerModalProps, 'open'>;

const DataModelPickerModalContent = ({
  value,
  onCancel,
  onConfirm,
  title,
  selectionMode,
  rootClassName,
  source,
  confirmLoading,
}: DataModelPickerModalContentProps) => {
  const currentUser = useCurrentUser();
  const canViewModels = currentUser.data?.permissions.includes('model.view') ?? false;
  const columns = useMemo(() => modelColumns(canViewModels), [canViewModels]);
  const [search, setSearch] = useState('');
  const [keyword, setKeyword] = useState('');
  const [selectedIds, setSelectedIds] = useState<string[]>(() => [...value]);
  const [knownModels, setKnownModels] = useState<Map<string, DataModelPickerCandidate>>(() => new Map());
  const timerRef = useRef<number | null>(null);
  const request = useMemo(() => ({
    search: buildDataModelSearch({ keyword, status: 'PUBLISHED' }),
    page: 0,
    size: CANDIDATE_LIMIT,
    sort: 'code',
  }), [keyword]);
  const query = useDataModels(request, !source);
  const models = useMemo<readonly DataModelPickerCandidate[]>(
    () => source?.candidates ?? query.data?.content ?? [],
    [source?.candidates, query.data?.content],
  );
  const modelById = useMemo(() => new Map(models.map((model) => [model.id, model])), [models]);
  const hasDisabledSelection = selectedIds.some((modelId) => (modelById.get(modelId) ?? knownModels.get(modelId))?.disabled);
  const loading = source?.loading ?? query.isFetching;
  const error = source?.error ?? query.isError;
  const limit = source?.limit ?? CANDIDATE_LIMIT;

  const rememberModels = (items: readonly DataModelPickerCandidate[]) => {
    if (items.length === 0) return;
    setKnownModels((current) => {
      const next = new Map(current);
      items.forEach((model) => next.set(model.id, model));
      return next;
    });
  };

  useEffect(() => () => {
    if (timerRef.current !== null) window.clearTimeout(timerRef.current);
  }, []);

  const updateSearch = (next: string) => {
    rememberModels(models.filter((model) => selectedIds.includes(model.id)));
    setSearch(next);
    if (timerRef.current !== null) window.clearTimeout(timerRef.current);
    timerRef.current = window.setTimeout(() => {
      const normalized = next.trim();
      if (source) source.onSearch(normalized);
      else setKeyword(normalized);
      timerRef.current = null;
    }, SEARCH_DELAY_MS);
  };

  const select = (modelId: string, checked: boolean) => {
    const model = modelById.get(modelId);
    if (checked && model?.disabled) return;
    if (checked && model) rememberModels([model]);
    setSelectedIds((current) => {
      if (selectionMode === 'single') return checked ? [modelId] : [];
      return checked
        ? current.includes(modelId) ? current : [...current, modelId]
        : current.filter((id) => id !== modelId);
    });
  };

  const selectVisible = () => {
    if (selectionMode === 'single') return;
    const selectable = models.filter((model) => !model.disabled);
    rememberModels(selectable);
    setSelectedIds((current) => [...new Set([...current, ...selectable.map((model) => model.id)])]);
  };

  return (
    <Modal
      open
      width="80vw"
      style={{ top: '10vh', paddingBottom: 0 }}
      title={<OverlayTitle title={title} icon={<TableOutlined />} />}
      rootClassName={`business-overlay business-modal-overlay data-model-picker-modal${rootClassName ? ` ${rootClassName}` : ''}`}
      onCancel={confirmLoading ? undefined : onCancel}
      closable={!confirmLoading}
      keyboard={!confirmLoading}
      maskClosable={!confirmLoading}
      footer={(
        <Space>
          <Button onClick={onCancel} disabled={confirmLoading}>取消</Button>
          <Button type="primary" loading={confirmLoading} disabled={selectedIds.length === 0 || hasDisabledSelection} onClick={() => onConfirm(selectedIds)}>
            {selectionMode === 'single' ? '确定' : `确定 · ${selectedIds.length} 个模型`}
          </Button>
        </Space>
      )}
    >
      <div className="resource-picker-grid">
        <section className="resource-picker-pane">
          <div className="resource-picker-heading">
            <div>
              <strong>{source?.heading ?? '已发布模型'}</strong>
              <Typography.Text type="secondary"> 每次最多返回 {limit} 项</Typography.Text>
            </div>
            {selectionMode === 'multiple' && (
              <Button type="link" size="small" disabled={!models.some((model) => !model.disabled)} onClick={selectVisible}>
                选择当前结果
              </Button>
            )}
          </div>
          <Input
            autoFocus
            allowClear
            autoComplete="off"
            name="data-model-picker-search"
            value={search}
            prefix={<SearchOutlined />}
            placeholder="按名称或编码搜索模型"
            onChange={(event) => updateSearch(event.target.value)}
          />
          <div className="resource-picker-candidates">
            <Table<DataModelPickerCandidate>
              size="small"
              columns={columns}
              dataSource={[...models]}
              rowKey="id"
              rowClassName={(model) => model.disabled ? 'resource-picker-row-disabled' : ''}
              pagination={false}
              loading={loading}
              tableLayout="fixed"
              scroll={{ y: 'max(140px, calc(80vh - 330px))' }}
              locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={error ? '读取模型失败' : source?.emptyText ?? '没有匹配的已发布模型'} /> }}
              rowSelection={{
                type: selectionMode === 'single' ? 'radio' : 'checkbox',
                columnWidth: 40,
                preserveSelectedRowKeys: true,
                selectedRowKeys: selectedIds,
                getCheckboxProps: (model) => ({
                  disabled: model.disabled,
                  'aria-label': model.disabled ? `模型 ${model.name} 不可选择` : `选择模型 ${model.name}`,
                }),
                onSelect: (model, checked) => select(model.id, checked),
                ...(selectionMode === 'multiple' ? {
                  onSelectAll: (checked: boolean, _models: DataModelPickerCandidate[], changedModels: DataModelPickerCandidate[]) => {
                    if (!checked) {
                      const changedIds = new Set(changedModels.map((model) => model.id));
                      setSelectedIds((current) => current.filter((id) => !changedIds.has(id)));
                      return;
                    }
                    const selectable = changedModels.filter((model) => !model.disabled);
                    rememberModels(selectable);
                    setSelectedIds((current) => [...new Set([...current, ...selectable.map((model) => model.id)])]);
                  },
                } : {}),
              }}
              onRow={(model) => ({
                onClick: (event) => {
                  const target = event.target as HTMLElement;
                  if (target.closest('.ant-checkbox-wrapper, .ant-radio-wrapper, button, a')) return;
                  if (model.disabled) return;
                  select(model.id, !selectedIds.includes(model.id));
                },
              })}
            />
          </div>
          <div className="resource-picker-tip">
            <Typography.Text type={error || (source ? models.length >= limit : (query.data?.totalElements ?? 0) > limit) ? 'warning' : 'secondary'}>
              {error ? '模型读取失败。' : source && models.length >= limit
                ? `仅展示前 ${limit} 项，请继续输入名称或编码缩小范围。`
                : !source && (query.data?.totalElements ?? 0) > limit
                  ? `匹配结果超过 ${limit} 项，请继续输入名称或编码缩小范围。`
                  : '已选模型不会因搜索条件变化而丢失。'}
            </Typography.Text>
            {error && <Button type="link" size="small" onClick={() => { if (source) source.onRetry(); else void query.refetch(); }}>重试</Button>}
          </div>
        </section>

        <section className="resource-picker-pane is-selected">
          <div className="resource-picker-heading">
            <div><strong>已选模型</strong> <Tag color="blue">{selectedIds.length}</Tag></div>
            <Button type="link" size="small" danger disabled={selectedIds.length === 0} onClick={() => setSelectedIds([])}>
              清空
            </Button>
          </div>
          <div className="resource-picker-selected-list">
            {selectedIds.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未选择模型" />
            ) : selectedIds.map((modelId) => (
              <SelectedModelRow
                key={modelId}
                modelId={modelId}
                candidate={modelById.get(modelId) ?? knownModels.get(modelId)}
                externalSource={Boolean(source)}
                onRemove={() => select(modelId, false)}
              />
            ))}
          </div>
        </section>
      </div>
    </Modal>
  );
};
