import { Button, message, Select, Space, Typography } from 'antd';

export interface LineageFieldOption {
  key: string;
  label: string;
}

interface LineageFieldSelectorProps {
  options: LineageFieldOption[];
  value: string[];
  loading?: boolean;
  placeholder: string;
  onChange: (value: string[]) => void;
}

const MAXIMUM_FIELDS = 50;

export const LineageFieldSelector = ({
  options, value, loading, placeholder, onChange,
}: LineageFieldSelectorProps) => {
  const apply = (next: string[]) => {
    if (next.length > MAXIMUM_FIELDS) {
      void message.warning('一次最多展示 50 个字段');
      return;
    }
    onChange(next);
  };
  return (
    <Space size={6} wrap>
      <Select
        mode="multiple"
        showSearch
        allowClear
        value={value}
        loading={loading}
        maxTagCount="responsive"
        className="model-lineage-field-select model-lineage-field-multi-select"
        placeholder={placeholder}
        optionFilterProp="label"
        onChange={apply}
        options={options.map((option) => ({ value: option.key, label: option.label }))}
      />
      <Button size="small" onClick={() => apply(options.slice(0, 20).map((option) => option.key))}>前 20 个</Button>
      <Button size="small" onClick={() => apply(options.slice(0, MAXIMUM_FIELDS).map((option) => option.key))}>
        全选（最多 50）
      </Button>
      <Button size="small" type="text" onClick={() => apply([])}>清空</Button>
      <Typography.Text type="secondary">已选 {value.length} / 50</Typography.Text>
    </Space>
  );
};
