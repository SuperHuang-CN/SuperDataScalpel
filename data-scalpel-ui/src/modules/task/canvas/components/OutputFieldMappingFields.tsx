import { ExclamationCircleOutlined, KeyOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { Button, Form, Select, Table, Tag, Tooltip, Typography, type TableColumnsType } from 'antd';
import { useMemo } from 'react';
import { platformTypeLabel } from '../canvasSchema';
import type {
  CanvasColumnMapping,
  CanvasColumnSchema,
} from '../canvasTypes';
import { autoMatchedSourceColumn, mappingByTarget, orderedMappings } from './outputFieldMappings';

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
  scrollHeight?: number;
  onProgrammaticChange: () => void;
}

const requiredTarget = (column: CanvasColumnSchema) => (
  !column.nullable
  && column.defaultValue === null
  && !column.autoIncrement
  && !column.generated
);

const MappingValueControl = () => null;

const fieldDisplayName = (
  column: CanvasColumnSchema,
  fieldNames?: ReadonlyMap<string, string>,
) => fieldNames?.get(column.name)?.trim() || column.comment?.trim() || column.name;

type MappingTableRow =
  | {
    key: string;
    kind: 'TARGET';
    target: CanvasColumnSchema;
    mapping: CanvasColumnMapping | undefined;
    sourceMissing: boolean;
    generatedMappingInvalid: boolean;
  }
  | {
    key: string;
    kind: 'MISSING_TARGET';
    mapping: CanvasColumnMapping;
  };

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
  scrollHeight,
  onProgrammaticChange,
}: OutputFieldMappingFieldsProps) => {
  const form = Form.useFormInstance<OutputFieldMappingFormValue>();
  const watchedMappings = Form.useWatch('columnMappings', form);
  const mappings = watchedMappings ?? initialMappings;
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
      label: `${column.name} · ${fieldDisplayName(column)} · ${platformTypeLabel(column)}`,
    }));
    if (selected && !sourceNames.has(selected)) {
      options.unshift({ value: selected, label: `${selected}（来源字段已不可用）` });
    }
    return options;
  };

  const tableRows: MappingTableRow[] = [
    ...targetColumns.map((target) => {
      const mapping = indexedMappings.get(target.name);
      const sourceMissing = Boolean(mapping) && sourceReady
        && !sourceNames.has(mapping?.sourceColumnName ?? '');
      const generatedMappingInvalid = Boolean(mapping) && (target.autoIncrement || target.generated);
      return {
        key: target.name,
        kind: 'TARGET' as const,
        target,
        mapping,
        sourceMissing,
        generatedMappingInvalid,
      };
    }),
    ...missingTargetMappings.map((mapping, index) => ({
      key: `missing-${mapping.targetColumnName}-${index}`,
      kind: 'MISSING_TARGET' as const,
      mapping,
    })),
  ];
  const mappedTargetCount = targetColumns.filter((target) => indexedMappings.has(target.name)).length;
  const columns: TableColumnsType<MappingTableRow> = [
    {
      title: '目标字段',
      key: 'target',
      width: 250,
      render: (_, row) => row.kind === 'MISSING_TARGET' ? (
        <div className="canvas-output-field-cell is-invalid">
          <Typography.Text type="danger">目标字段已不存在</Typography.Text>
          <Typography.Text code type="danger">{row.mapping.targetColumnName}</Typography.Text>
        </div>
      ) : (
        <div className="canvas-output-field-cell" title={fieldDisplayName(row.target, targetFieldNames)}>
          <div className="canvas-output-field-code-line">
            {primaryKeyNames.has(row.target.name) && <Tooltip title="主键"><KeyOutlined className="canvas-output-field-status is-primary" /></Tooltip>}
            {!row.target.nullable && <Tooltip title="非空"><ExclamationCircleOutlined className="canvas-output-field-status is-required" /></Tooltip>}
            <Typography.Text ellipsis={{ tooltip: `${row.target.name} · ${fieldDisplayName(row.target, targetFieldNames)}` }}>
              {row.target.name}
            </Typography.Text>
          </div>
        </div>
      ),
    },
    {
      title: '类型',
      key: 'constraints',
      width: 156,
      render: (_, row) => row.kind === 'MISSING_TARGET' ? '—' : (
        <div className="canvas-output-field-constraints">
          <Typography.Text type="secondary">{platformTypeLabel(row.target)}</Typography.Text>
          {row.target.defaultValue !== null && <Tag color="blue">默认</Tag>}
          {row.target.autoIncrement && <Tag color="purple">自增</Tag>}
          {row.target.generated && <Tag color="purple">生成</Tag>}
          {keyNames.has(row.target.name) && <Tag color="cyan">{keyLabel}</Tag>}
        </div>
      ),
    },
    {
      title: '来源字段',
      key: 'source',
      render: (_, row) => {
        if (row.kind === 'MISSING_TARGET') {
          return <div className="canvas-output-field-missing-source">
            <Typography.Text ellipsis={{ tooltip: row.mapping.sourceColumnName }}>
              已保存来源字段：{row.mapping.sourceColumnName}
            </Typography.Text>
            <Button type="link" danger size="small" onClick={() => changeMapping(row.mapping.targetColumnName)}>
              清除
            </Button>
          </div>;
        }
        const databaseGenerated = row.target.autoIncrement || row.target.generated;
        if (databaseGenerated) {
          return row.mapping ? <div className="canvas-output-field-generated-error">
            <Typography.Text type="danger">数据库生成字段不能映射</Typography.Text>
            <Button type="link" danger size="small" onClick={() => changeMapping(row.target.name)}>清除</Button>
          </div> : <Typography.Text type="secondary">数据库生成</Typography.Text>;
        }
        return <Select
          className="canvas-output-field-mapping-source-select"
          allowClear
          showSearch
          aria-label={`目标字段 ${row.target.name} 的来源字段`}
          placeholder={requiredTarget(row.target) ? '请选择来源字段' : '不映射'}
          status={row.sourceMissing ? 'error' : undefined}
          value={row.mapping?.sourceColumnName}
          options={sourceOptions(row.mapping?.sourceColumnName)}
          optionFilterProp="label"
          disabled={!sourceReady}
          onChange={(value) => changeMapping(row.target.name, value)}
        />;
      },
    },
  ];

  return (
    <div className="canvas-output-field-mapping">
      <div className="canvas-output-field-mapping-toolbar">
        <div>
          <Typography.Text strong>字段映射</Typography.Text>
          <Typography.Text type="secondary"> · 已映射 {mappedTargetCount} / {targetColumns.length}</Typography.Text>
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

      <Table<MappingTableRow>
        className="canvas-output-field-mapping-table"
        size="small"
        pagination={false}
        tableLayout="fixed"
        rowKey="key"
        columns={columns}
        dataSource={tableRows}
        scroll={scrollHeight ? { y: scrollHeight } : undefined}
        rowClassName={(row) => row.kind === 'MISSING_TARGET'
          || row.sourceMissing || row.generatedMappingInvalid ? 'is-invalid' : ''}
        locale={{ emptyText: targetLoading ? '正在读取目标字段…' : '暂无目标字段' }}
      />

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
