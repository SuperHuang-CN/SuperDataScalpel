import { CloudServerOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Empty, Select, Spin, Tag, Typography } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import {
  buildDataSourceSearch,
  fetchKafkaTopics,
  useDataSource,
  useDataSources,
  type DataSource,
} from '../../../datasource';

interface ControlledSelectProps {
  id?: string;
  value?: string;
  onChange?: (value: string) => void;
  onBlur?: () => void;
}

interface CanvasKafkaDataSourceSelectProps extends ControlledSelectProps {
  purpose: 'SOURCE' | 'DISTRIBUTION';
  placeholder: string;
}

interface CanvasKafkaTopicSelectProps extends ControlledSelectProps {
  dataSourceId: string;
  placeholder: string;
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

const selectableKafka = (dataSource: DataSource, purpose: 'SOURCE' | 'DISTRIBUTION') => (
  dataSource.enabled
  && dataSource.connectionKind === 'KAFKA'
  && dataSource.connection.kind === 'KAFKA'
  && dataSource.purposes.includes(purpose)
);

export const CanvasKafkaDataSourceSelect = ({
  id,
  value,
  onChange,
  onBlur,
  purpose,
  placeholder,
}: CanvasKafkaDataSourceSelectProps) => {
  const [open, setOpen] = useState(false);
  const { keyword, schedule } = useDelayedSearch();
  const selectedQuery = useDataSource(value, Boolean(value));
  const request = useMemo(() => ({
    search: buildDataSourceSearch({ keyword, purpose, enabled: true }),
    page: 0,
    size: DATA_SOURCE_PAGE_SIZE,
    sort: 'name',
  }), [keyword, purpose]);
  const dataSourcesQuery = useDataSources(request, open);
  const listed = (dataSourcesQuery.data?.content ?? [])
    .filter((dataSource) => selectableKafka(dataSource, purpose));
  const selected = selectedQuery.data;
  const dataSources = [...new Map(
    (selected ? [selected, ...listed] : listed).map((dataSource) => [dataSource.id, dataSource]),
  ).values()];
  const selectedUnavailable = selected !== undefined && !selectableKafka(selected, purpose);

  return (
    <Select<string>
      showSearch
      id={id}
      value={value || undefined}
      open={open}
      placeholder={placeholder}
      filterOption={false}
      loading={selectedQuery.isFetching || dataSourcesQuery.isFetching}
      status={selectedUnavailable || selectedQuery.isError ? 'error' : undefined}
      popupMatchSelectWidth={440}
      options={dataSources.map((dataSource) => ({
        value: dataSource.id,
        label: dataSource.name,
        disabled: !selectableKafka(dataSource, purpose),
      }))}
      optionRender={(option) => {
        const dataSource = dataSources.find((candidate) => candidate.id === option.value);
        if (!dataSource) return option.label;
        return (
          <div className="canvas-metadata-option">
            <div className="canvas-metadata-option-title">
              <CloudServerOutlined />
              <Typography.Text ellipsis>{dataSource.name}</Typography.Text>
              {!selectableKafka(dataSource, purpose) && <Tag color="error">不可用</Tag>}
            </div>
            <Typography.Text type="secondary" ellipsis className="canvas-metadata-option-description">
              {dataSource.code} · {dataSource.connection.kind === 'KAFKA'
                ? dataSource.connection.bootstrapServers
                : dataSource.type}
            </Typography.Text>
          </div>
        );
      }}
      notFoundContent={dataSourcesQuery.isFetching
        ? <Spin size="small" />
        : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有可用的 Kafka 数据源" />}
      onOpenChange={setOpen}
      onSearch={schedule}
      onChange={onChange}
      onBlur={onBlur}
    />
  );
};

export const CanvasKafkaTopicSelect = ({
  id,
  value,
  onChange,
  onBlur,
  dataSourceId,
  placeholder,
}: CanvasKafkaTopicSelectProps) => {
  const [open, setOpen] = useState(false);
  const { keyword, schedule } = useDelayedSearch();
  const topicsQuery = useQuery({
    queryKey: ['data-sources', dataSourceId, 'kafka-topics', keyword],
    queryFn: () => fetchKafkaTopics(dataSourceId, keyword || undefined),
    enabled: open && Boolean(dataSourceId),
    staleTime: 30_000,
  });
  const topics = topicsQuery.data ?? [];
  const options = value && !topics.some((topic) => topic.name === value)
    ? [{ value, label: value }, ...topics.map((topic) => ({ value: topic.name, label: topic.name }))]
    : topics.map((topic) => ({ value: topic.name, label: topic.name }));

  return (
    <Select<string>
      showSearch
      id={id}
      value={value || undefined}
      open={open}
      disabled={!dataSourceId}
      placeholder={placeholder}
      filterOption={false}
      loading={topicsQuery.isFetching}
      status={topicsQuery.isError ? 'error' : undefined}
      popupMatchSelectWidth={440}
      options={options}
      notFoundContent={topicsQuery.isFetching
        ? <Spin size="small" />
        : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={topicsQuery.isError ? '读取 Topic 失败' : '没有匹配的 Topic'} />}
      onOpenChange={setOpen}
      onSearch={schedule}
      onChange={onChange}
      onBlur={onBlur}
    />
  );
};
