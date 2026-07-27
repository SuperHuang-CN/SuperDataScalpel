import { ApartmentOutlined } from '@ant-design/icons';
import { Empty, Select, Spin, Tag, Typography } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import {
  buildDataModelSearch,
  dataModelStatusLabels,
  physicalTableModeLabels,
  useDataModel,
  useDataModels,
  type DataModel,
} from '../../../model';

interface CanvasModelSelectProps {
  id?: string;
  value?: string;
  onChange?: (value: string) => void;
  onBlur?: () => void;
  placeholder: string;
  presentation?: 'MODEL' | 'SCHEMA';
}

const SEARCH_DELAY_MS = 300;
const MODEL_PAGE_SIZE = 50;

const uniqueModels = (models: DataModel[]) => (
  [...new Map(models.map((model) => [model.id, model])).values()]
);

const modelContext = (model: DataModel) => [
  model.code,
  model.storageDataSourceName,
  model.schemaName,
  model.physicalTableName,
].filter(Boolean).join(' · ');

export const CanvasModelSelect = ({
  id,
  value,
  onChange,
  onBlur,
  placeholder,
  presentation = 'MODEL',
}: CanvasModelSelectProps) => {
  const [open, setOpen] = useState(false);
  const [keyword, setKeyword] = useState('');
  const timerRef = useRef<number | null>(null);
  const selectedQuery = useDataModel(value, Boolean(value));
  const request = useMemo(() => ({
    search: buildDataModelSearch({ keyword, status: 'PUBLISHED' }),
    page: 0,
    size: MODEL_PAGE_SIZE,
    sort: 'code',
  }), [keyword]);
  const modelsQuery = useDataModels(request, open);

  useEffect(() => () => {
    if (timerRef.current !== null) window.clearTimeout(timerRef.current);
  }, []);

  const scheduleSearch = (search: string) => {
    if (timerRef.current !== null) window.clearTimeout(timerRef.current);
    timerRef.current = window.setTimeout(() => {
      setKeyword(search.trim());
      timerRef.current = null;
    }, SEARCH_DELAY_MS);
  };

  const selected = selectedQuery.data?.model;
  const published = (modelsQuery.data?.content ?? []).filter((model) => model.status === 'PUBLISHED');
  const models = uniqueModels(selected ? [selected, ...published] : published);
  const modelById = new Map(models.map((model) => [model.id, model]));
  const selectedUnavailable = selected !== undefined && selected.status !== 'PUBLISHED';
  const fallbackOption = value && !modelById.has(value)
    ? [{ value, label: `不可用模型 · ${value}`, disabled: true }]
    : [];
  const options = [
    ...models.map((model) => ({
      value: model.id,
      label: `${model.name}（${model.code}）`,
      disabled: model.status !== 'PUBLISHED',
    })),
    ...fallbackOption,
  ];

  return (
    <Select<string>
      showSearch
      id={id}
      virtual
      value={value || undefined}
      open={open}
      placeholder={placeholder}
      filterOption={false}
      loading={modelsQuery.isFetching || selectedQuery.isFetching}
      status={selectedUnavailable || selectedQuery.isError ? 'error' : undefined}
      popupMatchSelectWidth={460}
      options={options}
      optionRender={(option) => {
        const model = modelById.get(String(option.value));
        if (!model) return option.label;
        return (
          <div className="canvas-metadata-option">
            <div className="canvas-metadata-option-title">
              <ApartmentOutlined />
              <Typography.Text ellipsis>{model.name}</Typography.Text>
              <Tag>
                {presentation === 'SCHEMA'
                  ? `Schema v${model.schemaVersion}`
                  : physicalTableModeLabels[model.physicalTableMode]}
              </Tag>
              {model.status !== 'PUBLISHED' && (
                <Tag color="error">{dataModelStatusLabels[model.status]}</Tag>
              )}
            </div>
            <Typography.Text type="secondary" ellipsis className="canvas-metadata-option-description">
              {presentation === 'SCHEMA'
                ? `${model.code} · 仅复制当前已发布字段`
                : modelContext(model)}
            </Typography.Text>
          </div>
        );
      }}
      notFoundContent={modelsQuery.isFetching
        ? <Spin size="small" />
        : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有可用的已发布模型" />}
      popupRender={(menu) => (
        <>
          {menu}
          {(modelsQuery.data?.totalElements ?? 0) > MODEL_PAGE_SIZE && (
            <div className="canvas-metadata-popup-footer">
              仅显示前 {MODEL_PAGE_SIZE} 个模型，请输入名称或编码搜索
            </div>
          )}
        </>
      )}
      onOpenChange={setOpen}
      onSearch={scheduleSearch}
      onChange={onChange}
      onBlur={onBlur}
    />
  );
};
