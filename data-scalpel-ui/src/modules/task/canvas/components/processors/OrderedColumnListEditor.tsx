import {
  DeleteOutlined,
  DownOutlined,
  PlusOutlined,
  UpOutlined,
} from '@ant-design/icons';
import { Button, Select, Space, Tag, Typography } from 'antd';
import type { CanvasColumnSchema } from '../../canvasTypes';

interface OrderedColumnListEditorProps {
  label: string;
  items: string[];
  columns: CanvasColumnSchema[];
  onChange: (items: string[]) => void;
  addLabel: string;
  emptyText: string;
}

const move = <T,>(items: T[], from: number, to: number) => {
  const result = [...items];
  const [item] = result.splice(from, 1);
  result.splice(to, 0, item);
  return result;
};

export const OrderedColumnListEditor = ({
  label,
  items,
  columns,
  onChange,
  addLabel,
  emptyText,
}: OrderedColumnListEditorProps) => {
  const names = new Set(columns.map((column) => column.name));
  const available = columns
    .filter((column) => !items.includes(column.name))
    .map((column) => ({
      value: column.name,
      label: `${column.name} · ${column.fieldType}`,
    }));
  return (
    <section className="canvas-processor-editor-section">
      <div className="canvas-processor-editor-heading">
        <Typography.Text strong>{label}</Typography.Text>
        <Select
          showSearch
          optionFilterProp="label"
          value={undefined}
          placeholder={addLabel}
          disabled={available.length === 0}
          options={available}
          suffixIcon={<PlusOutlined />}
          onChange={(name: string) => onChange([...items, name])}
        />
      </div>
      {items.length === 0 && (
        <Typography.Text type="secondary">{emptyText}</Typography.Text>
      )}
      <div className="canvas-processor-ordered-list">
        {items.map((name, index) => {
          const missing = !names.has(name);
          return (
            <div
              className={`canvas-processor-ordered-item${missing ? ' is-invalid' : ''}`}
              key={`${name}-${index}`}
            >
              <span className="canvas-processor-ordered-name">
                <Tag color="blue">{index + 1}</Tag>
                <Typography.Text ellipsis title={name}>{name}</Typography.Text>
                {missing && <Tag color="error">已失效</Tag>}
              </span>
              <Space size={0}>
                <Button
                  type="text"
                  size="small"
                  aria-label={`上移 ${name}`}
                  icon={<UpOutlined />}
                  disabled={index === 0}
                  onClick={() => onChange(move(items, index, index - 1))}
                />
                <Button
                  type="text"
                  size="small"
                  aria-label={`下移 ${name}`}
                  icon={<DownOutlined />}
                  disabled={index === items.length - 1}
                  onClick={() => onChange(move(items, index, index + 1))}
                />
                <Button
                  type="text"
                  danger
                  size="small"
                  aria-label={`删除 ${name}`}
                  icon={<DeleteOutlined />}
                  onClick={() => onChange(items.filter((_, itemIndex) => itemIndex !== index))}
                />
              </Space>
            </div>
          );
        })}
      </div>
    </section>
  );
};
