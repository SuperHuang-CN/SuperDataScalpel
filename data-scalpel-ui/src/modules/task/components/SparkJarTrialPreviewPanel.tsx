import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import { Button, Empty, Modal, Table, Tooltip, Typography, type TableColumnsType } from 'antd';
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

interface PreviewOutput {
  key: string;
  write: SparkJarTrialWritePreview;
  fields: ParsedSparkField[];
  rows: PreviewRow[];
  error: string | null;
  warning: string | null;
  truncated: boolean;
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

const streamingOutputKey = (write: SparkJarTrialWritePreview): string => JSON.stringify([
  write.resourceKind,
  write.bindingName,
  write.target,
]);

const asSingleOutput = (parsed: ParsedWritePreview): PreviewOutput => ({
  ...parsed,
  warning: null,
  truncated: parsed.write.truncated,
});

const groupStreamingOutputs = (parsedWrites: ParsedWritePreview[]): PreviewOutput[] => {
  const grouped = new Map<string, ParsedWritePreview[]>();
  parsedWrites.forEach((parsed) => {
    const key = streamingOutputKey(parsed.write);
    const entries = grouped.get(key);
    if (entries) entries.push(parsed);
    else grouped.set(key, [parsed]);
  });

  return [...grouped.entries()].map(([key, entries]) => {
    const latest = entries[entries.length - 1];
    const reference = [...entries].reverse().find((entry) => !entry.error) ?? latest;
    const compatible = entries.filter((entry) => (
      !entry.error && entry.write.schemaJson === reference.write.schemaJson
    ));
    const capturedRows = compatible.flatMap((entry) => entry.rows);
    const rows = capturedRows.slice(-100).map((row, index) => ({
      ...row,
      key: String(index + 1),
    }));
    const skippedBatches = entries.length - compatible.length;
    return {
      key,
      write: reference.write,
      fields: reference.fields,
      rows,
      error: compatible.length ? null : reference.error,
      warning: skippedBatches > 0
        ? `${skippedBatches} 个微批次的 Schema 或预览数据无法与当前结果合并。`
        : null,
      truncated: capturedRows.length > 100
        || compatible.some((entry) => entry.write.truncated)
        || skippedBatches > 0,
    };
  });
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
  streaming?: boolean;
}

export const SparkJarTrialPreviewPanel = ({ preview, streaming = false }: SparkJarTrialPreviewPanelProps) => {
  const parsedWrites = useMemo(() => preview.writes.map(parseWritePreview), [preview.writes]);
  const outputs = useMemo(
    () => streaming ? groupStreamingOutputs(parsedWrites) : parsedWrites.map(asSingleOutput),
    [parsedWrites, streaming],
  );
  const [complexValue, setComplexValue] = useState<SelectedComplexValue | null>(null);

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

  const renderOutput = (output: PreviewOutput) => (
    <div className="spark-jar-trial-output">
      {(streaming || outputs.length > 1) && (
        <div className="spark-jar-trial-output-summary">
          <Tooltip title={`绑定名：${output.write.bindingName} · 写入模式：${output.write.writeMode}`}>
            <Typography.Text strong>
              {streaming ? output.write.target : `#${output.write.index} · ${output.write.target}`}
            </Typography.Text>
          </Tooltip>
          <Typography.Text type="secondary">
            {streaming ? `最近 ${output.rows.length} 条已捕获样例` : `${output.rows.length} 条样例`}
            {output.truncated ? '（已截断）' : ''}
          </Typography.Text>
        </div>
      )}
      {output.warning && <Alert type="warning" showIcon message={output.warning} />}
      {output.error ? (
        <Alert type="error" showIcon message="输出预览无法展示" description={output.error} />
      ) : (
        <div className="spark-jar-trial-write-content">
          <Table<PreviewRow>
            className="spark-jar-trial-preview-table"
            size="small"
            bordered
            tableLayout="fixed"
            rowKey="key"
            columns={previewColumns(output.fields)}
            dataSource={output.rows}
            pagination={false}
            scroll={{ x: Math.max(720, (output.fields.length + 1) * 180) }}
            locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="本次写入结果为空" /> }}
          />
        </div>
      )}
    </div>
  );

  return (
    <div className="spark-jar-trial-preview-panel">
      {preview.warnings.map((warning) => <Alert key={warning} type="warning" showIcon message={warning} />)}
      <div className="spark-jar-trial-output-list">
        {outputs.map((output) => <div key={output.key}>{renderOutput(output)}</div>)}
      </div>
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
