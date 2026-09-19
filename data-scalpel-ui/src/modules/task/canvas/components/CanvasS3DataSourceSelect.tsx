import { CloudServerOutlined } from '@ant-design/icons';
import { Empty, Select, Spin, Tag, Typography } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import {
  buildDataSourceSearch,
  useDataSource,
  useDataSources,
  type DataSource,
} from '../../../datasource';

interface CanvasS3DataSourceSelectProps {
  id?: string;
  value?: string;
  onChange?: (value: string) => void;
  onBlur?: () => void;
  placeholder: string;
}

const isSelectable = (dataSource: DataSource) => (
  dataSource.enabled
  && dataSource.connectionKind === 'S3'
  && dataSource.connection.kind === 'S3'
  && dataSource.purposes.includes('DISTRIBUTION')
);

export const CanvasS3DataSourceSelect = ({
  id,
  value,
  onChange,
  onBlur,
  placeholder,
}: CanvasS3DataSourceSelectProps) => {
  const [open, setOpen] = useState(false);
  const [keyword, setKeyword] = useState('');
  const timerRef = useRef<number | null>(null);
  useEffect(() => () => {
    if (timerRef.current !== null) window.clearTimeout(timerRef.current);
  }, []);
  const request = useMemo(() => ({
    search: buildDataSourceSearch({ keyword, purpose: 'DISTRIBUTION', enabled: true }),
    page: 0,
    size: 50,
    sort: 'name',
  }), [keyword]);
  const listQuery = useDataSources(request, open);
  const selectedQuery = useDataSource(value, Boolean(value));
  const listed = (listQuery.data?.content ?? []).filter(isSelectable);
  const selected = selectedQuery.data;
  const dataSources = [...new Map(
    (selected ? [selected, ...listed] : listed).map((item) => [item.id, item]),
  ).values()];
  const byId = new Map(dataSources.map((item) => [item.id, item]));
  const selectedUnavailable = selected !== undefined && !isSelectable(selected);

  return (
    <Select<string>
      id={id}
      showSearch
      value={value || undefined}
      open={open}
      placeholder={placeholder}
      filterOption={false}
      loading={listQuery.isFetching || selectedQuery.isFetching}
      status={selectedUnavailable || selectedQuery.isError ? 'error' : undefined}
      popupMatchSelectWidth={460}
      options={dataSources.map((item) => ({ value: item.id, label: item.name }))}
      optionRender={(option) => {
        const dataSource = byId.get(String(option.value));
        if (!dataSource || dataSource.connection.kind !== 'S3') return option.label;
        return (
          <div className="canvas-metadata-option">
            <div className="canvas-metadata-option-title">
              <CloudServerOutlined />
              <Typography.Text ellipsis>{dataSource.name}</Typography.Text>
              {!isSelectable(dataSource) && <Tag color="error">不可用</Tag>}
            </div>
            <Typography.Text type="secondary" ellipsis className="canvas-metadata-option-description">
              {dataSource.connection.bucket}
              {dataSource.connection.rootPrefix ? `/${dataSource.connection.rootPrefix}` : ''}
              {' · '}{dataSource.code}
            </Typography.Text>
          </div>
        );
      }}
      notFoundContent={listQuery.isFetching
        ? <Spin size="small" />
        : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有可用的 S3 数据分发数据源" />}
      onOpenChange={setOpen}
      onSearch={(nextKeyword) => {
        if (timerRef.current !== null) window.clearTimeout(timerRef.current);
        timerRef.current = window.setTimeout(() => {
          setKeyword(nextKeyword.trim());
          timerRef.current = null;
        }, 300);
      }}
      onChange={onChange}
      onBlur={onBlur}
    />
  );
};
