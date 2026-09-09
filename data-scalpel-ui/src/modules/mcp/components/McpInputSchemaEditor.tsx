import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, Checkbox, Input, Select, Table, Tabs, Typography, message } from 'antd';
import { useMemo } from 'react';

type Property = { type: string; description?: string };
type SimpleSchema = {
  type: 'object'; properties: Record<string, Property>; required?: string[];
  additionalProperties?: boolean; title?: string; description?: string; $schema?: string;
};
const TYPES = ['string', 'integer', 'number', 'boolean'];
const isObject = (value: unknown): value is Record<string, unknown> =>
  value !== null && typeof value === 'object' && !Array.isArray(value);

// Complex schemas must never pass through a lossy visual conversion.
function parseSimpleSchema(value: string): SimpleSchema | null {
  try {
    const schema: unknown = JSON.parse(value);
    if (!isObject(schema) || schema.type !== 'object' || !isObject(schema.properties)) return null;
    const properties = schema.properties;
    const allowed = ['type', 'properties', 'required', 'additionalProperties', 'title', 'description', '$schema'];
    if (Object.keys(schema).some(key => !allowed.includes(key))) return null;
    if (schema.additionalProperties !== undefined && typeof schema.additionalProperties !== 'boolean') return null;
    if (['title', 'description', '$schema'].some(key => schema[key] !== undefined && typeof schema[key] !== 'string')) return null;
    if (Object.values(properties).some(property => !isObject(property)
      || typeof property.type !== 'string' || !TYPES.includes(property.type)
      || Object.keys(property).some(key => !['type', 'description'].includes(key))
      || (property.description !== undefined && typeof property.description !== 'string'))) return null;
    if (schema.required !== undefined && (!Array.isArray(schema.required)
      || schema.required.some(name => typeof name !== 'string' || !Object.hasOwn(properties, name))
      || new Set(schema.required).size !== schema.required.length)) return null;
    return schema as SimpleSchema;
  } catch { return null; }
}

export function McpInputSchemaEditor({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const schema = useMemo(() => parseSimpleSchema(value), [value]);
  const [messageApi, context] = message.useMessage();
  const update = (next: SimpleSchema) => onChange(JSON.stringify(next, null, 2));
  const rows = schema ? Object.entries(schema.properties).map(([name, definition]) => ({ name, ...definition })) : [];

  return <>{context}<Tabs size="small" items={[
    { key: 'visual', label: '可视化参数', children: schema ? <>
      <Table size="small" rowKey="name" pagination={false} dataSource={rows} columns={[
        { title: '参数名', render: (_, row) => <Input autoComplete="off" defaultValue={row.name} onBlur={event => {
          const name = event.target.value.trim();
          if (name === row.name) return;
          if (!name || Object.hasOwn(schema.properties, name)) {
            messageApi.error('参数名不能为空或重复'); event.target.value = row.name; return;
          }
          update({ ...schema, properties: Object.fromEntries(Object.entries(schema.properties).map(([key, property]) => [key === row.name ? name : key, property])),
            ...(schema.required ? { required: schema.required.map(key => key === row.name ? name : key) } : {}) });
        }} /> },
        { title: '类型', width: 105, render: (_, row) => <Select value={row.type} style={{ width: '100%' }} options={TYPES.map(type => ({ value: type, label: type }))}
          onChange={type => update({ ...schema, properties: { ...schema.properties, [row.name]: { ...schema.properties[row.name], type } } })} /> },
        { title: '必填', width: 58, render: (_, row) => <Checkbox checked={schema.required?.includes(row.name) ?? false} onChange={event => update({ ...schema,
          required: event.target.checked ? [...(schema.required ?? []), row.name] : (schema.required ?? []).filter(name => name !== row.name) })} /> },
        { title: '说明', render: (_, row) => <Input autoComplete="off" value={row.description ?? ''} onChange={event => update({ ...schema,
          properties: { ...schema.properties, [row.name]: { ...schema.properties[row.name], description: event.target.value } } })} /> },
        { title: '', width: 40, render: (_, row) => <Button danger type="text" icon={<DeleteOutlined />} aria-label={'删除参数 ' + row.name} onClick={() => update({ ...schema,
          properties: Object.fromEntries(Object.entries(schema.properties).filter(([name]) => name !== row.name)),
          ...(schema.required ? { required: schema.required.filter(name => name !== row.name) } : {}) })} /> },
      ]} />
      <Button block type="dashed" icon={<PlusOutlined />} style={{ marginTop: 8 }} onClick={() => {
        let index = rows.length + 1;
        while (Object.hasOwn(schema.properties, 'param' + index)) index++;
        update({ ...schema, properties: { ...schema.properties, ['param' + index]: { type: 'string' } } });
      }}>添加参数</Button>
    </> : <Typography.Text type="secondary">该 Schema 含复杂结构或格式错误，请在 JSON Schema 页签编辑；原始定义会完整保留。</Typography.Text> },
    { key: 'json', label: 'JSON Schema', children: <Input.TextArea autoComplete="off" value={value} onChange={event => onChange(event.target.value)} autoSize={{ minRows: 12, maxRows: 24 }} spellCheck={false} /> },
  ]} /></>;
}
