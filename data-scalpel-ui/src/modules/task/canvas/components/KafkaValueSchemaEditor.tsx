import { CompactAlert as Alert } from '../../../../shared/components/ContextualFeedback';
import { Button, Card, Checkbox, Collapse, Input, InputNumber, Select, Space, Typography } from 'antd';
import { useState } from 'react';
import { useDataModel, type PlatformDataType } from '../../../model';
import {
  emptyKafkaValueColumn,
  modelFieldsToKafkaValueSchema,
  parseKafkaJsonSchema,
} from '../kafkaValueSchema';
import type { KafkaValueColumn, KafkaValueSchema } from '../canvasTypes';
import { CanvasModelSelect } from './CanvasModelSelect';

interface KafkaValueSchemaEditorProps {
  value?: KafkaValueSchema;
  onChange?: (value: KafkaValueSchema) => void;
}

const platformTypes: PlatformDataType[] = [
  'BOOLEAN',
  'BYTE',
  'SHORT',
  'INTEGER',
  'LONG',
  'FLOAT',
  'DOUBLE',
  'DECIMAL',
  'STRING',
  'BINARY',
  'DATE',
  'TIMESTAMP',
  'TIMESTAMP_NTZ',
];

const normalizedColumn = (
  column: KafkaValueColumn,
  fieldType: PlatformDataType,
): KafkaValueColumn => ({
  ...column,
  fieldType,
  length: fieldType === 'STRING' ? column.length : null,
  precision: fieldType === 'DECIMAL' ? (column.precision ?? 38) : null,
  scale: fieldType === 'DECIMAL' ? (column.scale ?? 18) : null,
});

export const KafkaValueSchemaEditor = ({
  value,
  onChange,
}: KafkaValueSchemaEditorProps) => {
  const schema = value ?? { columns: [] };
  const [modelId, setModelId] = useState('');
  const [jsonSchema, setJsonSchema] = useState('');
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
  const modelQuery = useDataModel(modelId || undefined, Boolean(modelId));

  const updateColumns = (columns: KafkaValueColumn[]) => {
    onChange?.({ columns });
    setMessage(null);
  };
  const updateColumn = (index: number, next: KafkaValueColumn) => {
    updateColumns(schema.columns.map((column, current) => current === index ? next : column));
  };
  const moveColumn = (index: number, direction: -1 | 1) => {
    const target = index + direction;
    if (target < 0 || target >= schema.columns.length) return;
    const columns = [...schema.columns];
    [columns[index], columns[target]] = [columns[target], columns[index]];
    updateColumns(columns);
  };
  const importModel = () => {
    const detail = modelQuery.data;
    if (!detail || detail.model.status !== 'PUBLISHED' || detail.fields.length === 0) {
      setMessage({ type: 'error', text: '请选择包含字段的已发布模型' });
      return;
    }
    onChange?.(modelFieldsToKafkaValueSchema(detail.fields));
    setMessage({
      type: 'success',
      text: `已复制 ${detail.model.name} Schema v${detail.model.schemaVersion}，后续模型变更不会自动影响当前节点`,
    });
  };
  const importJsonSchema = () => {
    const result = parseKafkaJsonSchema(jsonSchema);
    if (!result.success) {
      setMessage({ type: 'error', text: result.errors.slice(0, 5).join('；') });
      return;
    }
    onChange?.(result.schema);
    setMessage({ type: 'success', text: `已导入 ${result.schema.columns.length} 个字段` });
  };

  return (
    <Space orientation="vertical" size={10} className="canvas-kafka-schema-editor">
      <Alert
        showIcon
        type="info"
        title="Schema 归当前节点所有"
        description="模型只用于一次性复制字段；不会保存模型引用，也不会访问模型物理表。"
      />
      <Card size="small" title="从模型 Schema 导入">
        <Space orientation="vertical" size={8} style={{ width: '100%' }}>
          <CanvasModelSelect
            value={modelId}
            onChange={setModelId}
            placeholder="选择已发布模型 Schema"
            presentation="SCHEMA"
          />
          <Button
            onClick={importModel}
            loading={modelQuery.isFetching}
            disabled={!modelId}
          >
            复制 Schema 到节点
          </Button>
        </Space>
      </Card>
      <Card
        size="small"
        title={`自定义字段 · ${schema.columns.length}`}
        extra={<Button size="small" onClick={() => updateColumns([...schema.columns, emptyKafkaValueColumn()])}>添加字段</Button>}
      >
        <Space orientation="vertical" size={8} style={{ width: '100%' }}>
          {schema.columns.length === 0 && (
            <Typography.Text type="secondary">尚未定义字段，可以手动添加或从模型、JSON Schema 导入。</Typography.Text>
          )}
          {schema.columns.map((column, index) => (
            <Card
              key={index}
              size="small"
              title={`字段 ${index + 1}`}
              extra={(
                <Space size={2}>
                  <Button type="text" size="small" disabled={index === 0} onClick={() => moveColumn(index, -1)}>上移</Button>
                  <Button type="text" size="small" disabled={index === schema.columns.length - 1} onClick={() => moveColumn(index, 1)}>下移</Button>
                  <Button
                    type="text"
                    size="small"
                    danger
                    onClick={() => updateColumns(schema.columns.filter((_, current) => current !== index))}
                  >
                    删除
                  </Button>
                </Space>
              )}
            >
              <Space orientation="vertical" size={8} style={{ width: '100%' }}>
                <Input
                  value={column.name}
                  placeholder="字段名"
                  onChange={(event) => updateColumn(index, { ...column, name: event.target.value })}
                />
                <Select
                  value={column.fieldType}
                  options={platformTypes.map((type) => ({ value: type, label: type }))}
                  onChange={(fieldType: PlatformDataType) => updateColumn(
                    index,
                    normalizedColumn(column, fieldType),
                  )}
                />
                {column.fieldType === 'STRING' && (
                  <InputNumber
                    min={1}
                    precision={0}
                    value={column.length}
                    placeholder="最大长度（可选）"
                    style={{ width: '100%' }}
                    onChange={(length) => updateColumn(index, { ...column, length })}
                  />
                )}
                {column.fieldType === 'DECIMAL' && (
                  <Space.Compact block>
                    <InputNumber
                      min={1}
                      max={38}
                      precision={0}
                      value={column.precision}
                      placeholder="precision"
                      onChange={(precision) => updateColumn(index, { ...column, precision })}
                    />
                    <InputNumber
                      min={0}
                      max={column.precision ?? 38}
                      precision={0}
                      value={column.scale}
                      placeholder="scale"
                      onChange={(scale) => updateColumn(index, { ...column, scale })}
                    />
                  </Space.Compact>
                )}
                <Checkbox
                  checked={column.nullable}
                  onChange={(event) => updateColumn(index, { ...column, nullable: event.target.checked })}
                >
                  允许为空
                </Checkbox>
                <Input
                  value={column.comment ?? ''}
                  placeholder="字段说明（可选）"
                  onChange={(event) => updateColumn(index, {
                    ...column,
                    comment: event.target.value || null,
                  })}
                />
              </Space>
            </Card>
          ))}
        </Space>
      </Card>
      <Collapse
        size="small"
        items={[{
          key: 'json-schema',
          label: '粘贴扁平 JSON Schema',
          children: (
            <Space orientation="vertical" size={8} style={{ width: '100%' }}>
              <Input.TextArea
                value={jsonSchema}
                rows={8}
                placeholder='{"type":"object","properties":{"id":{"type":"integer"}},"required":["id"]}'
                onChange={(event) => setJsonSchema(event.target.value)}
              />
              <Typography.Text type="secondary">
                支持 boolean、integer、number、string、date、date-time；暂不支持嵌套 object 和 array。
              </Typography.Text>
              <Button disabled={!jsonSchema.trim()} onClick={importJsonSchema}>解析并替换字段</Button>
            </Space>
          ),
        }]}
      />
      {message && <Alert showIcon type={message.type} title={message.text} />}
    </Space>
  );
};
