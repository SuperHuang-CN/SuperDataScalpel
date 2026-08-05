import {
  DeleteOutlined,
  DownOutlined,
  PlusOutlined,
  UpOutlined,
} from '@ant-design/icons';
import { Button, Select, Space, Tag, Typography } from 'antd';
import type {
  CanvasColumnSchema,
  NullOrdering,
  SortDirection,
  SortField,
} from '../../canvasTypes';

interface SortFieldsEditorProps {
  fields: SortField[];
  columns: CanvasColumnSchema[];
  onChange: (fields: SortField[]) => void;
}

const move = <T,>(items: T[], from: number, to: number) => {
  const result = [...items];
  const [item] = result.splice(from, 1);
  result.splice(to, 0, item);
  return result;
};

export const SortFieldsEditor = ({
  fields,
  columns,
  onChange,
}: SortFieldsEditorProps) => {
  const names = new Set(columns.map((column) => column.name));
  const update = (index: number, patch: Partial<SortField>) => {
    onChange(fields.map((field, itemIndex) => (
      itemIndex === index ? { ...field, ...patch } : field
    )));
  };
  return (
    <section className="canvas-processor-editor-section">
      <div className="canvas-processor-editor-heading">
        <Typography.Text strong>排序规则</Typography.Text>
        <Button
          size="small"
          icon={<PlusOutlined />}
          onClick={() => onChange([...fields, {
            columnName: columns.find(
              (column) => !fields.some((field) => field.columnName === column.name),
            )?.name ?? '',
            direction: 'ASC',
            nullOrdering: 'LAST',
          }])}
        >
          添加
        </Button>
      </div>
      {fields.length === 0 && (
        <Typography.Text type="secondary">至少添加一条排序规则。</Typography.Text>
      )}
      <div className="canvas-processor-sort-list">
        {fields.map((field, index) => {
          const missing = Boolean(field.columnName && !names.has(field.columnName));
          const fieldOptions = [
            ...(missing
              ? [{
                value: field.columnName,
                label: `${field.columnName}（已失效）`,
                disabled: true,
              }]
              : []),
            ...columns.map((column) => ({
              value: column.name,
              label: `${column.name} · ${column.fieldType}`,
              disabled: fields.some(
                (candidate, itemIndex) => itemIndex !== index
                  && candidate.columnName === column.name,
              ),
            })),
          ];
          return (
            <div
              className={`canvas-processor-sort-item${missing ? ' is-invalid' : ''}`}
              key={index}
            >
              <div className="canvas-processor-sort-heading">
                <Tag color="purple">{index + 1}</Tag>
                {missing && <Tag color="error">字段已失效</Tag>}
                <Space size={0}>
                  <Button
                    type="text"
                    size="small"
                    aria-label={`上移排序 ${index + 1}`}
                    icon={<UpOutlined />}
                    disabled={index === 0}
                    onClick={() => onChange(move(fields, index, index - 1))}
                  />
                  <Button
                    type="text"
                    size="small"
                    aria-label={`下移排序 ${index + 1}`}
                    icon={<DownOutlined />}
                    disabled={index === fields.length - 1}
                    onClick={() => onChange(move(fields, index, index + 1))}
                  />
                  <Button
                    type="text"
                    danger
                    size="small"
                    aria-label={`删除排序 ${index + 1}`}
                    icon={<DeleteOutlined />}
                    onClick={() => onChange(fields.filter(
                      (_, itemIndex) => itemIndex !== index,
                    ))}
                  />
                </Space>
              </div>
              <Select
                showSearch
                optionFilterProp="label"
                value={field.columnName || undefined}
                placeholder="排序字段"
                options={fieldOptions}
                onChange={(columnName: string) => update(index, { columnName })}
              />
              <div className="canvas-processor-sort-options">
                <Select
                  value={field.direction}
                  options={[
                    { value: 'ASC', label: '升序' },
                    { value: 'DESC', label: '降序' },
                  ]}
                  onChange={(direction: SortDirection) => update(index, { direction })}
                />
                <Select
                  value={field.nullOrdering}
                  options={[
                    { value: 'FIRST', label: 'NULL 在前' },
                    { value: 'LAST', label: 'NULL 在后' },
                  ]}
                  onChange={(nullOrdering: NullOrdering) => update(
                    index,
                    { nullOrdering },
                  )}
                />
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
};
