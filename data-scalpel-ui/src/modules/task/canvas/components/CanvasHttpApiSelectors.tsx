import { ApiOutlined, DatabaseOutlined } from '@ant-design/icons';
import { Empty, Select, Spin, Tag, Typography } from 'antd';
import { useMemo, useState } from 'react';
import {
  buildDataSourceSearch,
  useApiResources,
  useDataSource,
  useDataSources,
  type ApiResource,
  type DataSource,
} from '../../../datasource';

interface ControlledSelectProps {
  id?: string;
  value?: string;
  onChange?: (value: string) => void;
  onBlur?: () => void;
}

const selectableDataSource = (value: DataSource) => value.enabled
  && value.connectionKind === 'HTTP_API'
  && value.purposes.includes('SOURCE');

export const CanvasHttpApiDataSourceSelect = ({ value, id, onChange, onBlur }: ControlledSelectProps) => {
  const [open, setOpen] = useState(false);
  const selectedQuery = useDataSource(value, Boolean(value));
  const request = useMemo(() => ({
    search: buildDataSourceSearch({ purpose: 'SOURCE', enabled: true }),
    page: 0,
    size: 100,
    sort: 'name',
  }), []);
  const query = useDataSources(request, open);
  const listed = (query.data?.content ?? []).filter(selectableDataSource);
  const selected = selectedQuery.data;
  const sources = [...new Map((selected ? [selected, ...listed] : listed).map((item) => [item.id, item])).values()];
  const byId = new Map(sources.map((item) => [item.id, item]));
  return <Select<string>
    showSearch
    id={id}
    value={value || undefined}
    open={open}
    optionFilterProp="label"
    placeholder="选择 HTTP API SOURCE 数据源"
    loading={query.isFetching || selectedQuery.isFetching}
    status={selected && !selectableDataSource(selected) ? 'error' : undefined}
    popupMatchSelectWidth={440}
    options={sources.map((item) => ({ value: item.id, label: item.name }))}
    optionRender={(option) => {
      const source = byId.get(String(option.value));
      return source ? <div className="canvas-metadata-option">
        <div className="canvas-metadata-option-title"><DatabaseOutlined /><Typography.Text ellipsis>{source.name}</Typography.Text>{!selectableDataSource(source) && <Tag color="error">不可用</Tag>}</div>
        <Typography.Text type="secondary" ellipsis className="canvas-metadata-option-description">{source.code} · {source.connection.kind === 'HTTP_API' ? source.connection.configuration.baseUrl : source.type}</Typography.Text>
      </div> : option.label;
    }}
    notFoundContent={query.isFetching ? <Spin size="small" /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有可用的 HTTP API 数据源" />}
    onOpenChange={setOpen}
    onChange={onChange}
    onBlur={onBlur}
  />;
};

const selectableResource = (resource: ApiResource) => resource.enabled;

export const CanvasHttpApiResourceSelect = ({ dataSourceId, value, id, onChange, onBlur }: ControlledSelectProps & {
  dataSourceId: string;
}) => {
  const [open, setOpen] = useState(false);
  const query = useApiResources(dataSourceId || undefined, open || Boolean(value));
  const resources = query.data ?? [];
  const byId = new Map(resources.map((item) => [item.id, item]));
  return <Select<string>
    showSearch
    id={id}
    value={value || undefined}
    open={open}
    disabled={!dataSourceId}
    optionFilterProp="label"
    placeholder="选择已配置的 API 资源"
    loading={query.isFetching}
    status={value && byId.get(value) && !selectableResource(byId.get(value) as ApiResource) ? 'error' : undefined}
    popupMatchSelectWidth={440}
    options={resources.map((item) => ({ value: item.id, label: item.name, disabled: !selectableResource(item) }))}
    optionRender={(option) => {
      const resource = byId.get(String(option.value));
      return resource ? <div className="canvas-metadata-option">
        <div className="canvas-metadata-option-title"><ApiOutlined /><Typography.Text ellipsis>{resource.name}</Typography.Text>{!resource.enabled && <Tag color="error">停用</Tag>}</div>
        <Typography.Text type="secondary" ellipsis className="canvas-metadata-option-description">{resource.request.method} {resource.request.path} · {resource.outputFields.length} 个字段</Typography.Text>
      </div> : option.label;
    }}
    notFoundContent={query.isFetching ? <Spin size="small" /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有 API 资源" />}
    onOpenChange={setOpen}
    onChange={onChange}
    onBlur={onBlur}
  />;
};
