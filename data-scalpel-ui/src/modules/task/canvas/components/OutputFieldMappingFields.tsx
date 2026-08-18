import { ThunderboltOutlined } from '@ant-design/icons';
import { Button, Form, Select, Space, Tag, Typography } from 'antd';
import { useMemo } from 'react';
import { platformTypeLabel } from '../canvasSchema';
import type {
  CanvasColumnMapping,
  CanvasColumnSchema,
} from '../canvasTypes';

interface OutputFieldMappingFormValue {
  columnMappings: CanvasColumnMapping[];
}

interface OutputFieldMappingFieldsProps {
  sourceColumns: readonly CanvasColumnSchema[];
  targetColumns: readonly CanvasColumnSchema[];
  targetFieldNames?: ReadonlyMap<string, string>;
  primaryKeyColumns?: readonly string[];
  initialMappings: readonly CanvasColumnMapping[];
  sourceReady: boolean;
  targetReady: boolean;
  targetLoading?: boolean;
  keyColumns?: readonly string[];
  keyLabel?: string;
  onProgrammaticChange: () => void;
}

const canonicalFieldName = (name: string) => name.toLocaleLowerCase('en-US').replaceAll('_', '');

const uniqueMatch = (
  candidates: readonly CanvasColumnSchema[],
  predicate: (column: CanvasColumnSchema) => boolean,
) => {
  const matches = candidates.filter(predicate);
  return matches.length === 1 ? matches[0] : null;
};

export const autoMatchedSourceColumn = (
  targetName: string,
  sourceColumns: readonly CanvasColumnSchema[],
): string | null => {
  const exact = uniqueMatch(sourceColumns, (column) => column.name === targetName);
  if (exact) return exact.name;
  const caseInsensitive = uniqueMatch(
    sourceColumns,
    (column) => column.name.toLocaleLowerCase('en-US') === targetName.toLocaleLowerCase('en-US'),
  );
  if (caseInsensitive) return caseInsensitive.name;
  const canonicalTarget = canonicalFieldName(targetName);
  const canonical = uniqueMatch(
    sourceColumns,
    (column) => canonicalFieldName(column.name) === canonicalTarget,
  );
  return canonical?.name ?? null;
};

const requiredTarget = (column: CanvasColumnSchema) => (
  !column.nullable
  && column.defaultValue === null
  && !column.autoIncrement
  && !column.generated
);

const mappingByTarget = (mappings: readonly CanvasColumnMapping[]) => {
  const indexed = new Map<string, CanvasColumnMapping>();
  mappings.forEach((mapping) => indexed.set(mapping.targetColumnName, mapping));
  return indexed;
};

const orderedMappings = (
  targetColumns: readonly CanvasColumnSchema[],
  mappings: ReadonlyMap<string, CanvasColumnMapping>,
) => {
  const targetNames = new Set(targetColumns.map((column) => column.name));
  const current = targetColumns.flatMap((column) => {
    const mapping = mappings.get(column.name);
    return mapping ? [mapping] : [];
  });
  const missingTargets = [...mappings.values()]
    .filter((mapping) => !targetNames.has(mapping.targetColumnName));
  return [...current, ...missingTargets];
};

export const orderOutputFieldMappings = (
  targetColumns: readonly CanvasColumnSchema[],
  mappings: readonly CanvasColumnMapping[] | undefined,
) => orderedMappings(targetColumns, mappingByTarget(mappings ?? []));

const MappingValueControl = (_props: {
  value?: CanvasColumnMapping[];
  onChange?: (value: CanvasColumnMapping[]) => void;
}) => null;

const fieldDisplayName = (
  column: CanvasColumnSchema,
  fieldNames?: ReadonlyMap<string, string>,
) => fieldNames?.get(column.name)?.trim() || column.comment?.trim() || column.name;

const FieldSummary = ({
  column,
  displayName,
  primaryKey = false,
  target = false,
  businessKey = false,
  businessKeyLabel,
}: {
  column: CanvasColumnSchema;
  displayName: string;
  primaryKey?: boolean;
  target?: boolean;
  businessKey?: boolean;
  businessKeyLabel?: string;
}) => (
  <div className="canvas-output-field-summary">
    <Typography.Text className="canvas-output-field-name" ellipsis={{ tooltip: displayName }}>
      {displayName}
    </Typography.Text>
    <Typography.Text code className="canvas-output-field-code" ellipsis={{ tooltip: column.name }}>
      {column.name}
    </Typography.Text>
    <div className="canvas-output-field-meta">
      <Typography.Text type="secondary" className="canvas-output-field-type">
        {platformTypeLabel(column)}
      </Typography.Text>
      {target && primaryKey && <Tag color="geekblue">主键</Tag>}
      {target && !column.nullable && <Tag color="red">非空</Tag>}
      {target && column.nullable && <Tag>可空</Tag>}
      {target && column.defaultValue !== null && <Tag color="blue">默认值</Tag>}
      {target && column.autoIncrement && <Tag color="purple">自增</Tag>}
      {target && column.generated && <Tag color="purple">生成字段</Tag>}
      {target && businessKey && <Tag color="cyan">{businessKeyLabel}</Tag>}
    </div>
  </div>
);

export const OutputFieldMappingFields = ({
  sourceColumns,
  targetColumns,
  targetFieldNames,
  primaryKeyColumns = [],
  initialMappings,
  sourceReady,
  targetReady,
  targetLoading = false,
  keyColumns = [],
  keyLabel = 'Key',
  onProgrammaticChange,
}: OutputFieldMappingFieldsProps) => {
  const form = Form.useFormInstance<OutputFieldMappingFormValue>();
  const watchedMappings = Form.useWatch('columnMappings', form);
  const mappings = watchedMappings ?? [...initialMappings];
  const indexedMappings = useMemo(() => mappingByTarget(mappings), [mappings]);
  const targetNames = useMemo(
    () => new Set(targetColumns.map((column) => column.name)),
    [targetColumns],
  );
  const sourceNames = useMemo(
    () => new Set(sourceColumns.map((column) => column.name)),
    [sourceColumns],
  );
  const keyNames = useMemo(() => new Set(keyColumns), [keyColumns]);
  const primaryKeyNames = useMemo(() => new Set(primaryKeyColumns), [primaryKeyColumns]);
  const missingTargetMappings = mappings.filter(
    (mapping) => !targetNames.has(mapping.targetColumnName),
  );

  const setMappings = (next: Map<string, CanvasColumnMapping>) => {
    form.setFieldValue('columnMappings', orderedMappings(targetColumns, next));
    void form.validateFields(['columnMappings']).catch(() => undefined);
    onProgrammaticChange();
  };

  const changeMapping = (targetColumnName: string, sourceColumnName?: string) => {
    const next = mappingByTarget(mappings);
    if (!sourceColumnName) {
      next.delete(targetColumnName);
    } else {
      next.set(targetColumnName, { sourceColumnName, targetColumnName });
    }
    setMappings(next);
  };

  const autoMatch = () => {
    const next = mappingByTarget(mappings);
    let changed = false;
    targetColumns.forEach((target) => {
      if (target.autoIncrement || target.generated || next.has(target.name)) return;
      const sourceColumnName = autoMatchedSourceColumn(target.name, sourceColumns);
      if (sourceColumnName) {
        next.set(target.name, { sourceColumnName, targetColumnName: target.name });
        changed = true;
      }
    });
    if (changed) setMappings(next);
  };

  const sourceOptions = (selected: string | undefined) => {
    const options = sourceColumns.map((column) => ({
      value: column.name,
      label: `${fieldDisplayName(column)} · ${column.name} · ${platformTypeLabel(column)}`,
    }));
    if (selected && !sourceNames.has(selected)) {
      options.unshift({ value: selected, label: `${selected}（来源字段已不可用）` });
    }
    return options;
  };

  return (
    <div className="canvas-output-field-mapping">
      <div className="canvas-output-field-mapping-toolbar">
        <div>
          <Typography.Text strong>字段映射</Typography.Text>
          <Typography.Text type="secondary"> · 目标字段 ← 来源字段</Typography.Text>
        </div>
        <Button
          size="small"
          icon={<ThunderboltOutlined />}
          disabled={!sourceReady || !targetReady || targetColumns.length === 0}
          loading={targetLoading}
          onClick={autoMatch}
        >
          自动匹配空白字段
        </Button>
      </div>

      <div className="canvas-output-field-mapping-list">
        {targetColumns.map((target) => {
          const mapping = indexedMappings.get(target.name);
          const sourceMissing = Boolean(mapping) && sourceReady
            && !sourceNames.has(mapping?.sourceColumnName ?? '');
          const databaseGenerated = target.autoIncrement || target.generated;
          const generatedMappingInvalid = Boolean(mapping) && databaseGenerated;
          return (
            <div
              className={`canvas-output-field-mapping-row${sourceMissing || generatedMappingInvalid ? ' is-invalid' : ''}`}
              key={target.name}
            >
              <div className="canvas-output-field-mapping-target">
                <FieldSummary
                  column={target}
                  displayName={fieldDisplayName(target, targetFieldNames)}
                  primaryKey={primaryKeyNames.has(target.name)}
                  target
                  businessKey={keyNames.has(target.name)}
                  businessKeyLabel={keyLabel}
                />
              </div>
              <div className="canvas-output-field-mapping-arrow">←</div>
              <div className="canvas-output-field-mapping-source">
                {databaseGenerated ? (
                  mapping ? (
                    <Space orientation="vertical" size={2}>
                      <Typography.Text type="danger">该字段由数据库生成，不能映射</Typography.Text>
                      <Button type="link" danger size="small" onClick={() => changeMapping(target.name)}>
                        清除失效映射
                      </Button>
                    </Space>
                  ) : <Typography.Text type="secondary">数据库生成</Typography.Text>
                ) : (
                  <Select
                    className="canvas-output-field-mapping-source-select"
                    allowClear
                    showSearch
                    aria-label={`目标字段 ${target.name} 的来源字段`}
                    placeholder={requiredTarget(target) ? '请选择来源字段' : '不映射'}
                    status={sourceMissing ? 'error' : undefined}
                    value={mapping?.sourceColumnName}
                    options={sourceOptions(mapping?.sourceColumnName)}
                    optionFilterProp="label"
                    labelRender={({ value, label }) => {
                      const column = sourceColumns.find((candidate) => candidate.name === value);
                      return column ? (
                        <FieldSummary
                          column={column}
                          displayName={fieldDisplayName(column)}
                        />
                      ) : (
                        <div className="canvas-output-field-summary is-invalid">
                          <Typography.Text type="danger">来源字段已不可用</Typography.Text>
                          <Typography.Text code>{String(value || label || '')}</Typography.Text>
                          <Typography.Text type="secondary">—</Typography.Text>
                        </div>
                      );
                    }}
                    optionRender={(option) => {
                      const column = sourceColumns.find((candidate) => candidate.name === option.value);
                      return column ? (
                        <FieldSummary
                          column={column}
                          displayName={fieldDisplayName(column)}
                        />
                      ) : option.label;
                    }}
                    disabled={!sourceReady}
                    onChange={(value) => changeMapping(target.name, value)}
                  />
                )}
              </div>
            </div>
          );
        })}

        {missingTargetMappings.map((mapping, index) => (
          <div
            className="canvas-output-field-mapping-row is-invalid"
            key={`missing-${mapping.targetColumnName}-${index}`}
          >
            <div className="canvas-output-field-mapping-target">
              <div className="canvas-output-field-summary is-invalid">
                <Typography.Text type="danger">目标字段已不存在</Typography.Text>
                <Typography.Text code type="danger">{mapping.targetColumnName}</Typography.Text>
                <Typography.Text type="secondary">—</Typography.Text>
              </div>
            </div>
            <div className="canvas-output-field-mapping-arrow">←</div>
            <div className="canvas-output-field-mapping-source">
              <div className="canvas-output-field-summary">
                <Typography.Text>已保存来源字段</Typography.Text>
                <Typography.Text code>{mapping.sourceColumnName}</Typography.Text>
                <Typography.Text type="secondary">—</Typography.Text>
              </div>
              <Button
                type="link"
                danger
                size="small"
                onClick={() => changeMapping(mapping.targetColumnName)}
              >
                清除失效映射
              </Button>
            </div>
          </div>
        ))}
      </div>

      <Form.Item
        name="columnMappings"
        className="canvas-output-field-mapping-validation"
        rules={[{
          validator: async (_, value: CanvasColumnMapping[] | undefined) => {
            const current = value ?? [];
            if (current.length === 0) throw new Error('至少需要配置一个字段映射');
            const observed = new Set<string>();
            for (const mapping of current) {
              if (!mapping.sourceColumnName || !mapping.targetColumnName) {
                throw new Error('字段映射不完整');
              }
              if (observed.has(mapping.targetColumnName)) {
                throw new Error(`目标字段被重复映射：${mapping.targetColumnName}`);
              }
              observed.add(mapping.targetColumnName);
              if (targetReady && !targetNames.has(mapping.targetColumnName)) {
                throw new Error(`目标字段已不存在：${mapping.targetColumnName}`);
              }
              const target = targetColumns.find(
                (column) => column.name === mapping.targetColumnName,
              );
              if (target?.autoIncrement || target?.generated) {
                throw new Error(`数据库生成字段不能配置映射：${mapping.targetColumnName}`);
              }
              if (sourceReady && !sourceNames.has(mapping.sourceColumnName)) {
                throw new Error(`来源字段已不存在：${mapping.sourceColumnName}`);
              }
            }
            const missingRequired = targetColumns
              .filter(requiredTarget)
              .filter((column) => !observed.has(column.name));
            if (missingRequired.length > 0) {
              throw new Error(`目标必填字段未映射：${missingRequired.map((column) => column.name).join('、')}`);
            }
            const missingKeys = keyColumns.filter((column) => !observed.has(column));
            if (missingKeys.length > 0) {
              throw new Error(`${keyLabel} 未映射：${missingKeys.join('、')}`);
            }
          },
        }]}
      >
        <MappingValueControl />
      </Form.Item>
    </div>
  );
};
