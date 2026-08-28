import { ApartmentOutlined, DeleteOutlined, SearchOutlined } from '@ant-design/icons';
import { Button, Empty, Input, Modal, Space, Table, Tag, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useDataModels } from '../hooks/useDataModels';
import { buildDataModelSearch } from '../model/dataModelSearch';
import type { DataModel } from '../model/dataModel';

const SEARCH_DELAY_MS = 300;
const CANDIDATE_LIMIT = 100;

export type DataModelPickerSelectionMode = 'single' | 'multiple';

interface DataModelPickerModalProps {
  open: boolean;
  value: readonly string[];
  onCancel: () => void;
  onConfirm: (modelIds: string[]) => void;
  title?: string;
  selectionMode?: DataModelPickerSelectionMode;
  rootClassName?: string;
}

const modelColumns: TableColumnsType<DataModel> = [
  {
    title: '模型',
    key: 'model',
    ellipsis: true,
    render: (_, model) => (
      <div className="resource-picker-identity">
        <Typography.Text ellipsis>{model.name}</Typography.Text>
        <Typography.Text type="secondary" ellipsis>{model.code}</Typography.Text>
      </div>
    ),
  },
  {
    title: '数据源',
    dataIndex: 'storageDataSourceName',
    ellipsis: true,
  },
  {
    title: 'Schema',
    key: 'schema',
    width: 104,
    render: (_, model) => <Tag>v{model.schemaVersion}</Tag>,
  },
];

export const DataModelPickerModal = ({
  open,
  value,
  onCancel,
  onConfirm,
  title = '选择已发布模型',
  selectionMode = 'single',
  rootClassName,
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
}: DataModelPickerModalContentProps) => {
  const [search, setSearch] = useState('');
  const [keyword, setKeyword] = useState('');
  const [selectedIds, setSelectedIds] = useState<string[]>(() => [...value]);
  const timerRef = useRef<number | null>(null);
  const request = useMemo(() => ({
    search: buildDataModelSearch({ keyword, status: 'PUBLISHED' }),
    page: 0,
    size: CANDIDATE_LIMIT,
    sort: 'code',
  }), [keyword]);
  const query = useDataModels(request, true);
  const models = useMemo(() => query.data?.content ?? [], [query.data?.content]);
  const modelById = useMemo(() => new Map(models.map((model) => [model.id, model])), [models]);

  useEffect(() => () => {
    if (timerRef.current !== null) window.clearTimeout(timerRef.current);
  }, []);

  const updateSearch = (next: string) => {
    setSearch(next);
    if (timerRef.current !== null) window.clearTimeout(timerRef.current);
    timerRef.current = window.setTimeout(() => {
      setKeyword(next.trim());
      timerRef.current = null;
    }, SEARCH_DELAY_MS);
  };

  const select = (modelId: string, checked: boolean) => {
    setSelectedIds((current) => {
      if (selectionMode === 'single') return checked ? [modelId] : [];
      return checked
        ? current.includes(modelId) ? current : [...current, modelId]
        : current.filter((id) => id !== modelId);
    });
  };

  const selectVisible = () => {
    if (selectionMode === 'single') return;
    setSelectedIds((current) => [...new Set([...current, ...models.map((model) => model.id)])]);
  };

  return (
    <Modal
      open
      width={860}
      title={title}
      rootClassName={rootClassName}
      onCancel={onCancel}
      footer={(
        <Space>
          <Button onClick={onCancel}>取消</Button>
          <Button type="primary" onClick={() => onConfirm(selectedIds)}>
            {selectionMode === 'single' ? '确定' : `确定 · ${selectedIds.length} 个模型`}
          </Button>
        </Space>
      )}
    >
      <div className="resource-picker-grid">
        <section className="resource-picker-pane">
          <div className="resource-picker-heading">
            <div>
              <strong>已发布模型</strong>
              <Typography.Text type="secondary"> 每次最多返回 {CANDIDATE_LIMIT} 项</Typography.Text>
            </div>
            {selectionMode === 'multiple' && (
              <Button type="link" size="small" disabled={models.length === 0} onClick={selectVisible}>
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
            <Table<DataModel>
              size="small"
              columns={modelColumns}
              dataSource={models}
              rowKey="id"
              pagination={false}
              loading={query.isFetching}
              scroll={{ y: 'calc(clamp(360px, 58vh, 520px) - 192px)' }}
              locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={query.isError ? '读取模型失败' : '没有匹配的已发布模型'} /> }}
              rowSelection={{
                type: selectionMode === 'single' ? 'radio' : 'checkbox',
                columnWidth: 40,
                preserveSelectedRowKeys: true,
                selectedRowKeys: selectedIds,
                getCheckboxProps: (model) => ({ 'aria-label': `选择模型 ${model.name}` }),
                onSelect: (model, checked) => select(model.id, checked),
                ...(selectionMode === 'multiple' ? {
                  onSelectAll: (checked: boolean, _models: DataModel[], changedModels: DataModel[]) => {
                    if (!checked) {
                      const changedIds = new Set(changedModels.map((model) => model.id));
                      setSelectedIds((current) => current.filter((id) => !changedIds.has(id)));
                      return;
                    }
                    setSelectedIds((current) => [...new Set([...current, ...changedModels.map((model) => model.id)])]);
                  },
                } : {}),
              }}
              onRow={(model) => ({
                onClick: (event) => {
                  const target = event.target as HTMLElement;
                  if (target.closest('.ant-checkbox-wrapper')) return;
                  select(model.id, !selectedIds.includes(model.id));
                },
              })}
            />
          </div>
          <Typography.Text type={query.data?.totalElements && query.data.totalElements > CANDIDATE_LIMIT ? 'warning' : 'secondary'} className="resource-picker-tip">
            {query.isError ? '模型读取失败，请关闭后重试。' : (query.data?.totalElements ?? 0) > CANDIDATE_LIMIT
              ? `匹配结果超过 ${CANDIDATE_LIMIT} 项，请继续输入名称或编码缩小范围。`
              : '已选模型不会因搜索条件变化而丢失。'}
          </Typography.Text>
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
            ) : selectedIds.map((modelId) => {
              const model = modelById.get(modelId);
              return (
                <div className="resource-picker-selected-row" key={modelId}>
                  <ApartmentOutlined />
                  <Typography.Text ellipsis title={model ? `${model.name} · ${model.code}` : modelId}>
                    {model ? `${model.name} · ${model.code}` : modelId}
                  </Typography.Text>
                  <Button type="text" size="small" danger icon={<DeleteOutlined />} aria-label="移除模型" onClick={() => select(modelId, false)} />
                </div>
              );
            })}
          </div>
        </section>
      </div>
    </Modal>
  );
};
