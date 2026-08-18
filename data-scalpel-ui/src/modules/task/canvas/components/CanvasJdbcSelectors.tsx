import { DatabaseOutlined, ReloadOutlined, TableOutlined } from '@ant-design/icons';
import { Button, Divider, Empty, Select, Spin, Tag, Typography } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import {
  buildDataSourceSearch,
  useDataSource,
  useDataSources,
  useDataSourceTables,
  useDataSourceTypes,
  type DataSource,
  type DataSourcePurpose,
  type DataSourceTable,
} from '../../../datasource';

interface ControlledSelectProps {
  id?: string;
  value?: string;
  onChange?: (value: string) => void;
  onBlur?: () => void;
}

interface CanvasJdbcDataSourceSelectProps extends ControlledSelectProps {
  purpose: Extract<DataSourcePurpose, 'SOURCE' | 'STORAGE' | 'DISTRIBUTION'>;
  placeholder: string;
  allowedTypes?: readonly DataSource['type'][];
}

interface CanvasJdbcTableSelectProps extends ControlledSelectProps {
  dataSourceId: string;
  placeholder: string;
  selectedTableAvailable?: boolean;
}

const SEARCH_DELAY_MS = 300;
const DATA_SOURCE_PAGE_SIZE = 50;

const useDelayedSearch = () => {
  const [keyword, setKeyword] = useState('');
  const timerRef = useRef<number | null>(null);

  useEffect(() => () => {
    if (timerRef.current !== null) window.clearTimeout(timerRef.current);
  }, []);

  const schedule = (value: string) => {
    if (timerRef.current !== null) window.clearTimeout(timerRef.current);
    timerRef.current = window.setTimeout(() => {
      setKeyword(value.trim());
      timerRef.current = null;
    }, SEARCH_DELAY_MS);
  };

  return { keyword, schedule };
};

const dataSourceContext = (dataSource: DataSource) => {
  if (dataSource.connection.kind !== 'JDBC') return dataSource.type;
  return [dataSource.type, dataSource.connection.databaseName, dataSource.connection.schemaName]
    .filter(Boolean)
    .join(' · ');
};

const isSelectableDataSource = (
  dataSource: DataSource,
  purpose: CanvasJdbcDataSourceSelectProps['purpose'],
  metadataTypeIds: Set<string> | null,
  allowedTypes?: readonly DataSource['type'][],
) => dataSource.enabled
  && dataSource.connectionKind === 'JDBC'
  && dataSource.purposes.includes(purpose)
  && (allowedTypes === undefined || allowedTypes.includes(dataSource.type))
  && (metadataTypeIds === null || metadataTypeIds.has(dataSource.type));

const uniqueDataSources = (dataSources: DataSource[]) => (
  [...new Map(dataSources.map((dataSource) => [dataSource.id, dataSource])).values()]
);

export const CanvasJdbcDataSourceSelect = ({
  value,
  id,
  onChange,
  onBlur,
  purpose,
  placeholder,
  allowedTypes,
}: CanvasJdbcDataSourceSelectProps) => {
  const [open, setOpen] = useState(false);
  const { keyword, schedule } = useDelayedSearch();
  const typeQuery = useDataSourceTypes();
  const selectedQuery = useDataSource(value, Boolean(value));
  const request = useMemo(() => ({
    search: buildDataSourceSearch({ keyword, purpose, enabled: true }),
    page: 0,
    size: DATA_SOURCE_PAGE_SIZE,
    sort: 'name',
  }), [keyword, purpose]);
  const dataSourcesQuery = useDataSources(request, open);
  const metadataTypeIds = typeQuery.data
    ? new Set(typeQuery.data.filter((definition) => (
      definition.connectionKind === 'JDBC'
      && definition.driverAvailable
      && definition.capabilities.includes('LIST_TABLES')
      && definition.capabilities.includes('READ_TABLE_METADATA')
    )).map((definition) => definition.id))
    : null;
  const selectable = (dataSourcesQuery.data?.content ?? []).filter((dataSource) => (
    isSelectableDataSource(dataSource, purpose, metadataTypeIds, allowedTypes)
  ));
  const selected = selectedQuery.data;
  const dataSources = uniqueDataSources(selected ? [selected, ...selectable] : selectable);
  const dataSourceById = new Map(dataSources.map((dataSource) => [dataSource.id, dataSource]));
  const selectedUnavailable = selected !== undefined
    && !isSelectableDataSource(selected, purpose, metadataTypeIds, allowedTypes);

  return (
    <Select<string>
      showSearch
      id={id}
      virtual
      value={value || undefined}
      open={open}
      placeholder={placeholder}
      filterOption={false}
      loading={dataSourcesQuery.isFetching || selectedQuery.isFetching || typeQuery.isFetching}
      status={selectedUnavailable || selectedQuery.isError ? 'error' : undefined}
      popupMatchSelectWidth={440}
      options={dataSources.map((dataSource) => ({ value: dataSource.id, label: dataSource.name }))}
      optionRender={(option) => {
        const dataSource = dataSourceById.get(String(option.value));
        if (!dataSource) return option.label;
        const unavailable = !isSelectableDataSource(dataSource, purpose, metadataTypeIds, allowedTypes);
        return (
          <div className="canvas-metadata-option">
            <div className="canvas-metadata-option-title">
              <DatabaseOutlined />
              <Typography.Text ellipsis>{dataSource.name}</Typography.Text>
              {unavailable && <Tag color="error">不可用</Tag>}
            </div>
            <Typography.Text type="secondary" ellipsis className="canvas-metadata-option-description">
              {dataSourceContext(dataSource)} · {dataSource.code}
            </Typography.Text>
          </div>
        );
      }}
      notFoundContent={dataSourcesQuery.isFetching
        ? <Spin size="small" />
        : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有可用的 JDBC 数据源" />}
      popupRender={(menu) => (
        <>
          {menu}
          {(dataSourcesQuery.data?.totalElements ?? 0) > DATA_SOURCE_PAGE_SIZE && (
            <div className="canvas-metadata-popup-footer">
              仅显示前 {DATA_SOURCE_PAGE_SIZE} 个数据源，请输入名称或编码搜索
            </div>
          )}
        </>
      )}
      onOpenChange={setOpen}
      onSearch={schedule}
      onChange={onChange}
      onBlur={onBlur}
    />
  );
};

const tableKey = (table: DataSourceTable) => table.identifier.table;

export const CanvasJdbcTableSelect = ({
  value,
  id,
  onChange,
  onBlur,
  dataSourceId,
  placeholder,
  selectedTableAvailable,
}: CanvasJdbcTableSelectProps) => {
  const [open, setOpen] = useState(false);
  const { keyword, schedule } = useDelayedSearch();
  const tablesQuery = useDataSourceTables(dataSourceId || undefined, {
    keyword: keyword || undefined,
    includeViews: false,
  }, open && Boolean(dataSourceId));
  const listedTables = tablesQuery.data?.tables ?? [];
  const currentTable = value && !listedTables.some((table) => table.identifier.table === value)
    ? {
      identifier: { catalog: null, schema: null, table: value },
      type: 'TABLE',
      comment: null,
    } satisfies DataSourceTable
    : null;
  const tables = currentTable ? [currentTable, ...listedTables] : listedTables;
  const tableByName = new Map(tables.map((table) => [tableKey(table), table]));

  return (
    <Select<string>
      showSearch
      id={id}
      virtual
      value={value || undefined}
      open={open}
      disabled={!dataSourceId}
      placeholder={placeholder}
      filterOption={false}
      loading={tablesQuery.isFetching}
      status={selectedTableAvailable === false ? 'error' : undefined}
      popupMatchSelectWidth={440}
      options={tables.map((table) => ({
        value: table.identifier.table,
        label: table.identifier.table,
      }))}
      optionRender={(option) => {
        const table = tableByName.get(String(option.value));
        if (!table) return option.label;
        const unavailable = table === currentTable && selectedTableAvailable === false;
        return (
          <div className="canvas-metadata-option">
            <div className="canvas-metadata-option-title">
              <TableOutlined />
              <Typography.Text ellipsis code>{table.identifier.table}</Typography.Text>
              <Tag variant="filled" color={table.type === 'SUPERTABLE' ? 'cyan' : undefined}>
                {table.type === 'SUPERTABLE' ? '超级表' : table.type === 'TABLE' ? '表' : table.type}
              </Tag>
              {unavailable && <Tag color="error">不可用</Tag>}
            </div>
            {table.comment && (
              <Typography.Text type="secondary" ellipsis className="canvas-metadata-option-description">
                {table.comment}
              </Typography.Text>
            )}
          </div>
        );
      }}
      notFoundContent={tablesQuery.isFetching
        ? <Spin size="small" />
        : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={tablesQuery.isError ? '读取数据表失败' : '没有匹配的数据表'} />}
      popupRender={(menu) => (
        <>
          {menu}
          <Divider className="canvas-metadata-popup-divider" />
          <div className="canvas-metadata-popup-footer" onMouseDown={(event) => event.preventDefault()}>
            <span>{tablesQuery.data?.truncated
              ? '结果超过 500 项，请输入名称继续筛选'
              : listedTables.some((table) => table.type === 'SUPERTABLE') ? '仅显示 TDengine 超级表' : '仅显示真实物理表'}</span>
            <Button
              type="text"
              size="small"
              icon={<ReloadOutlined />}
              aria-label="刷新物理表"
              loading={tablesQuery.isFetching}
              onClick={() => void tablesQuery.refetch()}
            >
              刷新
            </Button>
          </div>
        </>
      )}
      onOpenChange={setOpen}
      onSearch={schedule}
      onChange={onChange}
      onBlur={onBlur}
    />
  );
};
