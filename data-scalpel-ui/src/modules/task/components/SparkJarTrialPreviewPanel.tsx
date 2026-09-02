import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import { Button, Empty, Modal, Table, Tabs, Tooltip, Typography, type TableColumnsType } from 'antd';
import { useMemo, useState } from 'react';
import type {
  SparkJarTrialPreview,
  SparkJarTrialWritePreview,
} from '../model/task';

type JsonObject = Record<string, unknown>;

interface ParsedSparkField {
  key: string;
  name: string;
  type: unknown;
}

interface PreviewRow {
  key: string;
  values: JsonObject;
}

interface ParsedWritePreview {
  key: string;
  write: SparkJarTrialWritePreview;
  fields: ParsedSparkField[];
  rows: PreviewRow[];
  error: string | null;
}

interface SelectedComplexValue {
  fieldName: string;
  value: object;
}

const isJsonObject = (value: unknown): value is JsonObject => (
  typeof value === 'object' && value !== null && !Array.isArray(value)
);

const sparkTypeLabel = (value: unknown): string => {
  if (typeof value === 'string') return value.toUpperCase();
  if (!isJsonObject(value) || typeof value.type !== 'string') return 'UNKNOWN';

  const type = value.type.toLowerCase();
  if (type === 'decimal' && typeof value.precision === 'number' && typeof value.scale === 'number') {
    return `DECIMAL(${value.precision}, ${value.scale})`;
  }
  if (type === 'array') return `ARRAY<${sparkTypeLabel(value.elementType)}>`;
  if (type === 'map') return `MAP<${sparkTypeLabel(value.keyType)}, ${sparkTypeLabel(value.valueType)}>`;
  return type.toUpperCase();
};

const parseWritePreview = (write: SparkJarTrialWritePreview): ParsedWritePreview => {
  try {
    const parsedSchema: unknown = JSON.parse(write.schemaJson);
    if (!isJsonObject(parsedSchema) || !Array.isArray(parsedSchema.fields)) {
      throw new Error('schema fields missing');
    }
    const fields = parsedSchema.fields.map((value, index): ParsedSparkField => {
      if (!isJsonObject(value) || typeof value.name !== 'string' || !value.name.trim()) {
        throw new Error('schema field invalid');
      }
      return {
        key: `${index}-${value.name}`,
        name: value.name,
        type: value.type,
      };
    });
    const rows = write.rowsJson.map((rowJson, index): PreviewRow => {
      const value: unknown = JSON.parse(rowJson);
      if (!isJsonObject(value)) throw new Error('preview row invalid');
      return { key: String(index + 1), values: value };
    });
    return { key: String(write.index), write, fields, rows, error: null };
  } catch {
    return {
      key: String(write.index),
      write,
      fields: [],
      rows: [],
      error: '当前 Write 返回的 Schema 或预览行格式无法解析。',
    };
  }
};

const valueSummary = (value: object): string => {
  const json = JSON.stringify(value);
  return json.length > 100 ? `${json.slice(0, 100)}…` : json;
};

interface PreviewValueProps {
  fieldName: string;
  value: unknown;
  onViewComplexValue: (fieldName: string, value: object) => void;
}

const PreviewValue = ({ fieldName, value, onViewComplexValue }: PreviewValueProps) => {
  if (value === null || value === undefined) return <Typography.Text type="secondary">—</Typography.Text>;
  if (Array.isArray(value) || isJsonObject(value)) {
    return (
      <Button
        type="link"
        size="small"
        className="spark-jar-trial-complex-value"
        aria-label={`查看字段 ${fieldName} 的完整 JSON 值`}
        onClick={() => onViewComplexValue(fieldName, value)}
      >
        {valueSummary(value)}
      </Button>
    );
  }
  if (typeof value === 'string') {
    return (
      <Typography.Text className="spark-jar-trial-scalar-value" ellipsis={{ tooltip: value }}>
        {value}
      </Typography.Text>
    );
  }
  return <span className="spark-jar-trial-scalar-value">{String(value)}</span>;
};

interface SparkJarTrialPreviewPanelProps {
  preview: SparkJarTrialPreview;
}

export const SparkJarTrialPreviewPanel = ({ preview }: SparkJarTrialPreviewPanelProps) => {
  const parsedWrites = useMemo(() => preview.writes.map(parseWritePreview), [preview.writes]);
  const [activeWriteKey, setActiveWriteKey] = useState<string | null>(null);
  const [complexValue, setComplexValue] = useState<SelectedComplexValue | null>(null);
  const selectedWriteKey = parsedWrites.some((candidate) => candidate.key === activeWriteKey)
    ? activeWriteKey
    : parsedWrites[0]?.key;

  const previewColumns = (fields: ParsedSparkField[]): TableColumnsType<PreviewRow> => [
    {
      title: '#',
      key: 'rowNumber',
      width: 60,
      render: (_, row) => row.key,
    },
    ...fields.map((field) => ({
      title: (
        <span className="spark-jar-trial-column-title">
          <strong>{field.name}</strong>
          <small>{sparkTypeLabel(field.type)}</small>
        </span>
      ),
      key: field.name,
      width: 180,
      render: (_: unknown, row: PreviewRow) => (
        <PreviewValue
          fieldName={field.name}
          value={row.values[field.name]}
          onViewComplexValue={(fieldName, value) => setComplexValue({ fieldName, value })}
        />
      ),
    })),
  ];

  return (
    <div className="spark-jar-trial-preview-panel">
      {preview.warnings.map((warning) => <Alert key={warning} type="warning" showIcon message={warning} />)}
      <Tabs
        className="spark-jar-trial-write-tabs"
        activeKey={selectedWriteKey ?? undefined}
        onChange={setActiveWriteKey}
        items={parsedWrites.map((parsed) => ({
          key: parsed.key,
          label: (
            <Tooltip
              title={(
                <div className="spark-jar-trial-write-tooltip">
                  <div>资源类型：{parsed.write.resourceKind === 'MODEL'
                    ? '模型'
                    : parsed.write.resourceKind === 'KAFKA' ? 'Kafka' : 'JDBC'}</div>
                  <div>绑定名：{parsed.write.bindingName}</div>
                  <div>目标：{parsed.write.target}</div>
                  <div>写入模式：{parsed.write.writeMode}</div>
                  <div>预览行数：{parsed.rows.length}</div>
                  {parsed.write.truncated && <div>结果已截断，最多展示 100 行</div>}
                </div>
              )}
            >
              <span className="spark-jar-trial-write-tab-label">#{parsed.write.index} · {parsed.write.target}</span>
            </Tooltip>
          ),
          children: parsed.error ? (
            <Alert type="error" showIcon message="输出预览无法展示" description={parsed.error} />
          ) : (
            <div className="spark-jar-trial-write-content">
              <Table<PreviewRow>
                className="spark-jar-trial-preview-table"
                size="small"
                bordered
                tableLayout="fixed"
                rowKey="key"
                columns={previewColumns(parsed.fields)}
                dataSource={parsed.rows}
                pagination={false}
                scroll={{ x: Math.max(720, (parsed.fields.length + 1) * 180), y: '100%' }}
                locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="本次写入结果为空" /> }}
              />
            </div>
          ),
        }))}
      />
      <Modal
        rootClassName="business-overlay business-modal-overlay spark-jar-trial-value-modal"
        open={Boolean(complexValue)}
        title={complexValue ? `字段值 · ${complexValue.fieldName}` : '字段值'}
        footer={<Button onClick={() => setComplexValue(null)}>关闭</Button>}
        onCancel={() => setComplexValue(null)}
      >
        {complexValue && <pre className="spark-jar-trial-complex-value-json">{JSON.stringify(complexValue.value, null, 2)}</pre>}
      </Modal>
    </div>
  );
};
