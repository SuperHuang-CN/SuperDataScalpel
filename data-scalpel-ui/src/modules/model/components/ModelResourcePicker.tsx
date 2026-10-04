import { DatabaseOutlined, FileOutlined, SearchOutlined } from '@ant-design/icons';
import { Button, ConfigProvider, Input, Modal, Select, Space, Table, Typography } from 'antd';
import { useState } from 'react';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { andSearch, orSearch, searchContains, searchEquals } from '../../../shared/search';
import { workspaceResourceTheme } from '../../../shared/theme/workspaceResourceTheme';
import {
  DataSourceTypeIcon, useDataSource, useDataSources, useDataSourceTypes,
  type DataSource, type DataSourceType,
} from '../../datasource';
import {
  FileDatasetTypeIcon, fileDatasetTypeLabels, useFileDataset, useFileDatasets,
  type FileDataset, type FileDatasetType,
} from '../../filedataset';
import { isModelDataSourceSelectable } from '../model/managedTableImport';
import './model-create.css';

interface PickerProps {
  id?: string;
  value?: string;
  onChange?: (value: string) => void;
  disabled?: boolean;
  className?: string;
  placeholder?: string;
}

/** Selection is staged locally; cancel never changes the parent form or its previews. */
export const ModelDataSourcePicker = ({
  id, value, onChange, disabled, className, placeholder,
  storageOnly = false, currentDataSourceId,
}: PickerProps & { storageOnly?: boolean; currentDataSourceId?: string }) => {
  const [open, setOpen] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [search, setSearch] = useState('');
  const [type, setType] = useState<DataSourceType>();
  const [page, setPage] = useState(1);
  const [selected, setSelected] = useState<DataSource>();
  const detail = useDataSource(value);
  const types = useDataSourceTypes();
  const jdbcTypes = types.data?.filter((item) => item.connectionKind === 'JDBC') ?? [];
  const query = useDataSources({
    page: page - 1, size: 20, sort: 'name,code',
    search: andSearch(
      type ? searchEquals('type', type) : orSearch(...jdbcTypes.map((item) => searchEquals('type', item.id))),
      orSearch(searchEquals('enabled', 'true'), searchEquals('id', currentDataSourceId)),
      storageOnly ? searchEquals('storageEnabled', 'true') : undefined,
      orSearch(searchContains('name', search), searchContains('code', search)),
    ),
  }, open && jdbcTypes.length > 0);
  const selectable = (source: DataSource) => isModelDataSourceSelectable(
    source, storageOnly ? 'MANAGED' : 'EXTERNAL', currentDataSourceId,
  );
  const title = storageOnly ? '选择目标数据存储' : '选择 JDBC 数据源';
  return (
    <ConfigProvider theme={workspaceResourceTheme}>
      <Button id={id} className={`model-resource-trigger ${className ?? ''}`} disabled={disabled}
        aria-haspopup="dialog" aria-label={placeholder ?? title}
        onClick={() => { setSelected(detail.data); setOpen(true); }}>
        {detail.data ? <DataSourceTypeIcon type={detail.data.type} /> : <DatabaseOutlined />}
        <span className="model-resource-trigger-copy">{detail.data?.name ?? (value ? '读取已选数据源…' : placeholder ?? title)}</span>
        <SearchOutlined />
      </Button>
      {detail.isError && <InlineFeedback tone="error" label="已选数据源读取失败"
        action={<Button size="small" onClick={() => void detail.refetch()}>重试</Button>} />}
      <Modal open={open} title={title} width={920} destroyOnHidden
        rootClassName="workspace-resource-overlay model-create-overlay model-resource-picker"
        onCancel={() => setOpen(false)}
        footer={<div className="model-picker-footer"><Typography.Text type="secondary">{selected ? `已选：${selected.name}` : '请选择一个数据源'}</Typography.Text><Space>
          <Button onClick={() => setOpen(false)}>取消</Button>
          <Button type="primary" disabled={!selected || !selectable(selected)} onClick={() => {
            if (selected && selectable(selected)) {
              if (selected.id !== value) onChange?.(selected.id);
              setOpen(false);
            }
          }}>确认选择</Button>
        </Space></div>}>
        <p className="model-picker-hint">{storageOnly ? '仅显示已启用且具备数据存储用途的 JDBC 数据源。' : '选择数据源后，继续选择其中的物理表。'}</p>
        <div className="model-picker-filters">
          <Input.Search autoComplete="off" allowClear aria-label="搜索数据源名称或编码" placeholder="搜索数据源名称或编码" value={keyword}
            onChange={(event) => { setKeyword(event.target.value); if (!event.target.value) { setSearch(''); setPage(1); } }}
            onSearch={(next) => { setSearch(next.trim()); setPage(1); }} />
          <Select allowClear aria-label="数据库类型" placeholder="全部数据库类型" value={type} popupMatchSelectWidth={280}
            options={jdbcTypes.map((item) => ({ value: item.id, label: item.displayName }))}
            onChange={(next) => { setType(next); setPage(1); }} />
        </div>
        {(query.isError || types.isError) && <InlineFeedback tone="error" label="读取数据源失败"
          action={<Button size="small" onClick={() => { void query.refetch(); void types.refetch(); }}>重试</Button>} />}
        <Table<DataSource> size="small" rowKey="id" loading={query.isFetching || types.isFetching}
          dataSource={query.data?.content ?? []} scroll={{ x: 640, y: 360 }}
          locale={{ emptyText: '没有符合条件的数据源，请调整搜索条件' }}
          columns={[
            { title: '数据源 / 编码', width: 300, render: (_, source) => <div className="model-picker-identity"><DataSourceTypeIcon type={source.type} /><div><strong>{source.name}</strong><Typography.Text type="secondary">{source.code}</Typography.Text></div></div> },
            { title: '数据库类型', width: 150, render: (_, source) => jdbcTypes.find((item) => item.id === source.type)?.displayName ?? source.type },
            { title: '数据库 / 地址', render: (_, source) => source.connection.kind === 'JDBC' ? <div className="model-picker-lines"><span>{source.connection.databaseName || '—'}</span><Typography.Text type="secondary">{source.connection.host}:{source.connection.port}</Typography.Text></div> : '—' },
          ]}
          rowSelection={{ type: 'radio', selectedRowKeys: selected ? [selected.id] : [], preserveSelectedRowKeys: true,
            getCheckboxProps: (source) => ({ disabled: !selectable(source) }), onSelect: setSelected }}
          onRow={(source) => ({ onClick: () => { if (selectable(source)) setSelected(source); } })}
          pagination={{ current: page, pageSize: 20, total: query.data?.totalElements ?? 0, showSizeChanger: false, showTotal: (total) => `共 ${total} 项`, onChange: setPage }} />
      </Modal>
    </ConfigProvider>
  );
};

export const ModelFileDatasetPicker = ({ id, value, onChange, disabled, className, placeholder }: PickerProps) => {
  const [open, setOpen] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [search, setSearch] = useState('');
  const [type, setType] = useState<FileDatasetType>();
  const [page, setPage] = useState(1);
  const [selected, setSelected] = useState<FileDataset>();
  const detail = useFileDataset(value);
  const query = useFileDatasets({ page: page - 1, size: 20, sort: '-updatedAt,name',
    search: andSearch(searchContains('name', search), searchEquals('type', type)),
  }, open);
  return (
    <ConfigProvider theme={workspaceResourceTheme}>
      <Button id={id} className={`model-resource-trigger ${className ?? ''}`} disabled={disabled}
        aria-haspopup="dialog" aria-label="选择文件数据集" onClick={() => { setSelected(detail.data); setOpen(true); }}>
        {detail.data ? <FileDatasetTypeIcon type={detail.data.type} /> : <FileOutlined />}
        <span className="model-resource-trigger-copy">{detail.data?.name ?? (value ? '读取已选数据集…' : placeholder ?? '选择文件数据集')}</span><SearchOutlined />
      </Button>
      {detail.isError && <InlineFeedback tone="error" label="已选数据集读取失败" action={<Button size="small" onClick={() => void detail.refetch()}>重试</Button>} />}
      <Modal open={open} title="选择文件数据集" width={920} destroyOnHidden
        rootClassName="workspace-resource-overlay model-create-overlay model-resource-picker" onCancel={() => setOpen(false)}
        footer={<div className="model-picker-footer"><Typography.Text type="secondary">{selected ? `已选：${selected.name}` : '请选择一个文件数据集'}</Typography.Text><Space>
          <Button onClick={() => setOpen(false)}>取消</Button><Button type="primary" disabled={!selected || selected.readyTableCount === 0}
            onClick={() => {
              if (selected && selected.readyTableCount > 0) {
                if (selected.id !== value) onChange?.(selected.id);
                setOpen(false);
              }
            }}>确认选择</Button>
        </Space></div>}>
        <p className="model-picker-hint">选择数据集后，可勾选其中的逻辑表。没有已就绪表的数据集暂不可选。</p>
        <div className="model-picker-filters">
          <Input.Search autoComplete="off" allowClear aria-label="搜索文件数据集" placeholder="搜索文件数据集名称" value={keyword}
            onChange={(event) => { setKeyword(event.target.value); if (!event.target.value) { setSearch(''); setPage(1); } }}
            onSearch={(next) => { setSearch(next.trim()); setPage(1); }} />
          <Select allowClear aria-label="文件类型" placeholder="全部文件类型" value={type} options={Object.entries(fileDatasetTypeLabels).map(([value, label]) => ({ value, label }))}
            onChange={(next) => { setType(next); setPage(1); }} />
        </div>
        {query.isError && <InlineFeedback tone="error" label="读取文件数据集失败" action={<Button size="small" onClick={() => void query.refetch()}>重试</Button>} />}
        <Table<FileDataset> size="small" rowKey="id" dataSource={query.data?.content ?? []} loading={query.isFetching} scroll={{ x: 640, y: 360 }}
          locale={{ emptyText: '没有符合条件的数据集，请调整搜索条件' }}
          columns={[
            { title: '文件数据集', width: 340, render: (_, dataset) => <div className="model-picker-identity"><FileDatasetTypeIcon type={dataset.type} /><div><strong>{dataset.name}</strong><Typography.Text type="secondary">{dataset.description || '暂无说明'}</Typography.Text></div></div> },
            { title: '文件类型', width: 150, render: (_, dataset) => fileDatasetTypeLabels[dataset.type] },
            { title: '逻辑表', render: (_, dataset) => <div className="model-picker-lines"><span>{dataset.readyTableCount} / {dataset.tableCount} 张已就绪</span>{dataset.readyTableCount === 0 && <Typography.Text type="secondary">请先完成文件解析</Typography.Text>}</div> },
          ]}
          rowSelection={{ type: 'radio', selectedRowKeys: selected ? [selected.id] : [], preserveSelectedRowKeys: true,
            getCheckboxProps: (dataset) => ({ disabled: dataset.readyTableCount === 0 }), onSelect: setSelected }}
          onRow={(dataset) => ({ onClick: () => { if (dataset.readyTableCount > 0) setSelected(dataset); } })}
          pagination={{ current: page, pageSize: 20, total: query.data?.totalElements ?? 0, showSizeChanger: false, showTotal: (total) => `共 ${total} 项`, onChange: setPage }} />
      </Modal>
    </ConfigProvider>
  );
};
