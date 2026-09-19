import { CompactAlert as Alert } from '../../../../../shared/components/ContextualFeedback';
import {
  DeleteOutlined,
  DownOutlined,
  ReloadOutlined,
  SettingOutlined,
  UpOutlined,
} from '@ant-design/icons';
import {
  Button,
  Form,
  Input,
  Modal,
  Radio,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import {
  useImperativeHandle,
  useMemo,
  useState,
  type Ref,
} from 'react';
import {
  CanvasNodeType,
  type CanvasNodeConfigurationUpdate,
  type CanvasNodeDefinition,
  type CanvasNodeValidationResult,
  type CanvasTableSchema,
  type PlatformDataType,
  type UnionConfiguration,
  type UnionMergeFieldAction,
  type UnionMergeFieldRule,
  type UnionMergeTable,
  type UnionMode,
} from '../../canvasTypes';
import type { CanvasNodeInspectorHandle } from '../CanvasNodeInspector';
import { ProcessorValidationIssues } from './ProcessorValidationIssues';

interface UnionProcessorInspectorProps {
  node: Extract<CanvasNodeDefinition, { type: 'UNION' }>;
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage: string | null;
  onApply: (update: CanvasNodeConfigurationUpdate) => void;
  onDirtyChange: (dirty: boolean) => void;
  inspectorRef: Ref<CanvasNodeInspectorHandle>;
}

interface UnionFormValues {
  outputTableName: string;
}

interface SchemaDifference {
  missing: string[];
  extra: string[];
  typeDifferences: number;
}

const move = <T,>(items: T[], from: number, to: number): T[] => {
  const result = [...items];
  const [item] = result.splice(from, 1);
  result.splice(to, 0, item);
  return result;
};

const schemaDifference = (
  baseline: CanvasTableSchema | undefined,
  candidate: CanvasTableSchema | undefined,
): SchemaDifference | null => {
  if (!baseline || !candidate) return null;
  const baselineColumns = new Map(baseline.columns.map((column) => [column.name, column]));
  const candidateColumns = new Map(candidate.columns.map((column) => [column.name, column]));
  const missing = [...baselineColumns.keys()].filter((name) => !candidateColumns.has(name));
  const extra = [...candidateColumns.keys()].filter((name) => !baselineColumns.has(name));
  const typeDifferences = [...baselineColumns.entries()].filter(([name, column]) => {
    const candidateColumn = candidateColumns.get(name);
    return candidateColumn && (
      candidateColumn.fieldType !== column.fieldType
      || candidateColumn.length !== column.length
      || candidateColumn.precision !== column.precision
      || candidateColumn.scale !== column.scale
    );
  }).length;
  return { missing, extra, typeDifferences };
};

const compactNames = (names: string[]): string => (
  names.length <= 3
    ? names.join('、')
    : `${names.slice(0, 3).join('、')} 等 ${names.length} 项`
);

const isGeometry = (column: CanvasTableSchema['columns'][number]): boolean => (
  column.fieldType === 'GEOMETRY'
);

const numericTypes = new Set<PlatformDataType>([
  'BYTE', 'SHORT', 'INTEGER', 'LONG', 'FLOAT', 'DOUBLE', 'DECIMAL',
]);

const sameGeometry = (
  left: CanvasTableSchema['columns'][number],
  right: CanvasTableSchema['columns'][number],
): boolean => (
  left.fieldType === 'GEOMETRY'
  && right.fieldType === 'GEOMETRY'
  && JSON.stringify(left.geometry) === JSON.stringify(right.geometry)
);

const canMatch = (
  source: CanvasTableSchema['columns'][number],
  target: CanvasTableSchema['columns'][number],
): boolean => {
  if (isGeometry(source) || isGeometry(target)) return sameGeometry(source, target);
  return source.fieldType === target.fieldType
    || (numericTypes.has(source.fieldType) && numericTypes.has(target.fieldType));
};

const preferredMatch = (
  source: CanvasTableSchema['columns'][number],
  outputs: CanvasTableSchema['columns'],
  currentTargetName?: string | null,
) => {
  const current = currentTargetName
    ? outputs.find((target) => target.name.toLowerCase() === currentTargetName.toLowerCase()
      && canMatch(source, target))
    : undefined;
  if (current) return current;
  const sameName = outputs.find(
    (target) => target.name.toLowerCase() === source.name.toLowerCase()
      && canMatch(source, target),
  );
  if (sameName) return sameName;
  const compatible = outputs.filter((target) => canMatch(source, target));
  return compatible.length === 1 ? compatible[0] : undefined;
};

const outputColumnsBefore = (
  tableIndex: number,
  inputTableNames: string[],
  tableByName: Map<string, CanvasTableSchema>,
  mergingTables: UnionMergeTable[],
): CanvasTableSchema['columns'] => {
  const base = tableByName.get(inputTableNames[0] ?? '');
  const output = base ? base.columns.map((column) => ({ ...column })) : [];
  for (let index = 1; index < tableIndex; index += 1) {
    const table = tableByName.get(inputTableNames[index] ?? '');
    if (!table) continue;
    const tableRules = mergingTables.find((item) => item.tableName === table.name)?.fieldRules ?? [];
    const ruleBySource = new Map(
      tableRules.map((rule) => [rule.sourceColumnName.toLowerCase(), rule]),
    );
    table.columns.forEach((column) => {
      const rule = ruleBySource.get(column.name.toLowerCase());
      if (rule?.action === 'REMOVE' || rule?.action === 'MATCH') return;
      const targetName = rule?.action === 'RENAME'
        ? rule.targetColumnName
        : output.some((target) => target.name.toLowerCase() === column.name.toLowerCase())
          ? null
          : column.name;
      if (targetName && !output.some((target) => target.name.toLowerCase() === targetName.toLowerCase())) {
        output.push({ ...column, name: targetName, nullable: true });
      }
    });
  }
  return output;
};

const suggestedMergeRules = (
  source: CanvasTableSchema,
  outputs: CanvasTableSchema['columns'],
  saved: UnionMergeFieldRule[],
): UnionMergeFieldRule[] => {
  const savedBySource = new Map(
    saved.map((rule) => [rule.sourceColumnName.toLowerCase(), rule]),
  );
  return source.columns.map((column) => {
    const existing = savedBySource.get(column.name.toLowerCase());
    if (existing) return { ...existing, sourceColumnName: column.name };
    const target = preferredMatch(column, outputs);
    return target
      ? { sourceColumnName: column.name, action: 'MATCH', targetColumnName: target.name }
      : { sourceColumnName: column.name, action: 'RENAME', targetColumnName: column.name };
  });
};

const customMergeRules = (
  source: CanvasTableSchema,
  outputs: CanvasTableSchema['columns'],
  rules: UnionMergeFieldRule[],
): UnionMergeFieldRule[] => {
  const defaults = new Map(
    source.columns.map((column): [string, UnionMergeFieldRule] => {
      const sameName = outputs.find(
        (target) => target.name.toLowerCase() === column.name.toLowerCase(),
      );
      return [column.name.toLowerCase(), sameName
        ? { sourceColumnName: column.name, action: 'MATCH', targetColumnName: sameName.name }
        : { sourceColumnName: column.name, action: 'RENAME', targetColumnName: column.name }];
    }),
  );
  return rules.filter((rule) => {
    const expected = defaults.get(rule.sourceColumnName.toLowerCase());
    return !expected
      || rule.action !== expected.action
      || rule.targetColumnName !== expected.targetColumnName;
  });
};

const UnionMergeFieldsModal = ({
  open,
  table,
  outputColumns,
  savedRules,
  onCancel,
  onSave,
}: {
  open: boolean;
  table: CanvasTableSchema | undefined;
  outputColumns: CanvasTableSchema['columns'];
  savedRules: UnionMergeFieldRule[];
  onCancel: () => void;
  onSave: (rules: UnionMergeFieldRule[]) => void;
}) => {
  const [rules, setRules] = useState<UnionMergeFieldRule[]>([]);

  const reset = () => {
    setRules(table ? suggestedMergeRules(table, outputColumns, savedRules) : []);
  };

  const rebuildSuggestions = () => {
    if (!table) return;
    Modal.confirm({
      title: '按当前基准层重建字段建议？',
      content: '本窗口中已经调整的 Match、Rename 和 Remove 将被新的自动建议替换。',
      okText: '重建建议',
      cancelText: '保留当前配置',
      onOk: () => setRules(suggestedMergeRules(table, outputColumns, [])),
    });
  };

  return <Modal
    open={open}
    width={820}
    title={`合并字段 · ${table?.name ?? '已失效表'}`}
    okText="保存字段处理"
    cancelText="取消"
    onCancel={onCancel}
    onOk={() => onSave(rules)}
    afterOpenChange={(visible) => {
      if (visible) reset();
    }}
  >
    <Space orientation="vertical" size={10} style={{ width: '100%' }}>
      <div className="canvas-union-heading">
        <Typography.Text type="secondary">
          基准层字段始终保留；Match 写入已有字段，Rename 追加新字段，Remove 排除字段。任一输入缺少的输出字段会自动补 NULL。
        </Typography.Text>
        <Button
          size="small"
          icon={<ReloadOutlined />}
          disabled={!table}
          onClick={rebuildSuggestions}
        >
          重建建议
        </Button>
      </div>
      <Table<UnionMergeFieldRule>
        size="small"
        pagination={false}
        rowKey="sourceColumnName"
        dataSource={rules}
        scroll={{ y: 480 }}
        columns={[
          {
            title: '合并层字段',
            dataIndex: 'sourceColumnName',
            width: 220,
            ellipsis: true,
            render: (value: string) => {
              const column = table?.columns.find((item) => item.name === value);
              return <Space size={6}>
                <Typography.Text ellipsis title={value}>{value}</Typography.Text>
                {column && <Tag>{column.fieldType}</Tag>}
              </Space>;
            },
          },
          {
            title: '处理',
            dataIndex: 'action',
            width: 150,
            render: (value: UnionMergeFieldAction | null, row) => {
              const column = table?.columns.find((item) => item.name === row.sourceColumnName);
              return <Select
                value={value}
                style={{ width: '100%' }}
                options={[
                  { value: 'MATCH', label: 'Match 已有字段' },
                  { value: 'RENAME', label: 'Rename 新字段', disabled: column ? isGeometry(column) : false },
                  { value: 'REMOVE', label: 'Remove 排除', disabled: column ? isGeometry(column) : false },
                ]}
                onChange={(action: UnionMergeFieldAction) => setRules((current) => current.map(
                  (item) => {
                    if (item.sourceColumnName !== row.sourceColumnName) return item;
                    const source = table?.columns.find(
                      (candidate) => candidate.name === row.sourceColumnName,
                    );
                    return {
                      ...item,
                      action,
                      targetColumnName: action === 'REMOVE'
                        ? null
                        : action === 'RENAME'
                          ? item.sourceColumnName
                          : source
                            ? preferredMatch(source, outputColumns, item.targetColumnName)?.name ?? null
                            : null,
                    };
                  },
                ))}
              />;
            },
          },
          {
            title: '输出字段',
            dataIndex: 'targetColumnName',
            render: (value: string | null, row) => {
              const duplicateTarget = value
                ? rules.filter((item) => item.action !== 'REMOVE'
                  && item.targetColumnName?.toLowerCase() === value.toLowerCase()).length > 1
                : false;
              if (row.action === 'REMOVE') {
                return <Typography.Text type="secondary">不输出</Typography.Text>;
              }
              if (row.action === 'MATCH') {
                const source = table?.columns.find(
                  (item) => item.name === row.sourceColumnName,
                );
                const target = value
                  ? outputColumns.find(
                    (item) => item.name.toLowerCase() === value.toLowerCase(),
                  )
                  : undefined;
                return <Select
                  showSearch
                  optionFilterProp="label"
                  value={value}
                  status={!source || !target || !canMatch(source, target) || duplicateTarget
                    ? 'error'
                    : undefined}
                  style={{ width: '100%' }}
                  options={outputColumns.map((column) => ({
                    value: column.name,
                    label: `${column.name} · ${column.fieldType}`,
                    disabled: source ? !canMatch(source, column) : false,
                  }))}
                  onChange={(targetColumnName) => setRules((current) => current.map(
                    (item) => item.sourceColumnName === row.sourceColumnName
                      ? { ...item, targetColumnName }
                      : item,
                  ))}
                />;
              }
              const existingOutput = value
                ? outputColumns.some(
                  (item) => item.name.toLowerCase() === value.toLowerCase(),
                )
                : false;
              return <Input
                value={value ?? ''}
                status={!value || existingOutput || duplicateTarget ? 'error' : undefined}
                placeholder="新输出字段名"
                onChange={(event) => setRules((current) => current.map(
                  (item) => item.sourceColumnName === row.sourceColumnName
                    ? { ...item, targetColumnName: event.target.value }
                    : item,
                ))}
              />;
            },
          },
        ]}
      />
    </Space>
  </Modal>;
};

export const UnionProcessorInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: UnionProcessorInspectorProps) => {
  const [form] = Form.useForm<UnionFormValues>();
  const [inputTableNames, setInputTableNames] = useState<string[]>(
    () => [...node.configuration.inputTableNames],
  );
  const [mode, setMode] = useState<UnionMode | null>(node.configuration.mode);
  const [mergeEnabled, setMergeEnabled] = useState(
    node.configuration.mergingTables !== null,
  );
  const [mergingTables, setMergingTables] = useState<UnionMergeTable[]>(
    () => node.configuration.mergingTables?.map((table) => ({
      ...table,
      fieldRules: table.fieldRules.map((rule) => ({ ...rule })),
    })) ?? [],
  );
  const [editingTableName, setEditingTableName] = useState<string | null>(null);
  const [draftError, setDraftError] = useState<string | null>(null);
  const inputTables = useMemo(() => validation?.inputTables ?? [], [validation?.inputTables]);
  const tableByName = useMemo(
    () => new Map(inputTables.map((table) => [table.name, table])),
    [inputTables],
  );
  const baseline = tableByName.get(inputTableNames[0] ?? '');
  const selectedTables = inputTableNames
    .map((name) => tableByName.get(name))
    .filter((table): table is CanvasTableSchema => Boolean(table));
  const hasUnboundedInput = selectedTables.some((table) => table.datasetKind === 'UNBOUNDED');
  const mixedDatasetKind = new Set(selectedTables.map((table) => table.datasetKind)).size > 1;
  const candidateOptions = inputTables
    .filter((table) => !inputTableNames.includes(table.name))
    .map((table) => ({
      value: table.name,
      label: `${table.name} · ${table.columns.length} 字段 · ${table.datasetKind}`,
    }));

  const updateInputTables = (next: string[]) => {
    setInputTableNames(next);
    setMergingTables((current) => current.filter(
      (item) => next.slice(1).includes(item.tableName),
    ));
    setEditingTableName((current) => (
      current && next.slice(1).includes(current) ? current : null
    ));
    setDraftError(next.length < 2 ? '至少选择两张输入表' : null);
    onDirtyChange(true);
  };

  const moveInputTable = (index: number, nextIndex: number) => {
    const next = move(inputTableNames, index, nextIndex);
    if (next[0] === inputTableNames[0]) {
      updateInputTables(next);
      return;
    }
    Modal.confirm({
      title: `将 ${next[0]} 设为新的基准层？`,
      content: '第一张表决定初始输出字段和顺序。新基准层原有的自定义字段规则会被移除，其余规则将保留并由 Task Engine 重新校验。',
      okText: '更换基准层',
      cancelText: '取消',
      onOk: () => updateInputTables(next),
    });
  };

  const removeInputTable = (index: number) => {
    const tableName = inputTableNames[index];
    const next = inputTableNames.filter((_, itemIndex) => itemIndex !== index);
    const customRuleCount = mergingTables.find(
      (item) => item.tableName === tableName,
    )?.fieldRules.length ?? 0;
    const changesBaseline = index === 0 && next.length > 0;
    if (!changesBaseline && customRuleCount === 0) {
      updateInputTables(next);
      return;
    }
    const effects = [
      changesBaseline
        ? `${next[0]} 将成为新的基准层，其自定义字段规则会被移除`
        : null,
      customRuleCount > 0 ? `当前表的 ${customRuleCount} 条自定义字段规则会一并删除` : null,
    ].filter((item): item is string => Boolean(item));
    Modal.confirm({
      title: `移除输入表 ${tableName}？`,
      content: `${effects.join('；')}。其余规则将保留并由 Task Engine 重新校验。`,
      okText: '移除',
      cancelText: '保留',
      okButtonProps: { danger: true },
      onOk: () => updateInputTables(next),
    });
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      try {
        const values = form.getFieldsValue(true);
        void form.validateFields().catch(() => undefined);
        if (inputTableNames.length < 2) {
          setDraftError('至少选择两张输入表');

        }
        const uniqueNames = new Set(inputTableNames);
        if (uniqueNames.size !== inputTableNames.length) {
          setDraftError('输入表不能重复');

        }
        if (!mode) {
          setDraftError('请选择 Union 模式');

        }
        if (hasUnboundedInput && mode === 'DISTINCT') {
          setDraftError('无界输入不支持 UNION DISTINCT');

        }
        const normalizedMergingTables: UnionMergeTable[] = inputTableNames
          .slice(1)
          .flatMap((tableName, mergeIndex) => {
            const saved = mergingTables.find((item) => item.tableName === tableName);
            if (!saved) return [];
            const table = tableByName.get(tableName);
            const fieldRules = table
              ? customMergeRules(
                table,
                outputColumnsBefore(
                  mergeIndex + 1,
                  inputTableNames,
                  tableByName,
                  mergingTables,
                ),
                saved.fieldRules,
              )
              : saved.fieldRules;
            return fieldRules.length === 0 ? [] : [{ tableName, fieldRules }];
          });
        const configuration: UnionConfiguration = {
          inputTableNames: [...inputTableNames],
          outputTableName: (values.outputTableName ?? '').trim(),
          mode,
          mergingTables: mergeEnabled ? normalizedMergingTables : null,
        };
        onApply({
          id: node.id,
          type: CanvasNodeType.Union,
          configuration,
        });
        onDirtyChange(false);
        return true;
      } catch {
        return false;
      }
    },
  }), [
    form,
    hasUnboundedInput,
    inputTableNames,
    mergeEnabled,
    mergingTables,
    mode,
    node.id,
    onApply,
    onDirtyChange,
    tableByName,
  ]);

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      <ProcessorValidationIssues
        validation={validation}
        unavailableMessage={validationUnavailableMessage}
      />
      <Form<UnionFormValues>
        autoComplete="off"
        form={form}
        layout="vertical"
        initialValues={{ outputTableName: node.configuration.outputTableName }}
        onValuesChange={() => onDirtyChange(true)}
      >
        <Form.Item
          name="outputTableName"
          label="输出表名"
          rules={[
            { required: true, whitespace: true, message: '请输入输出表名' },
            { max: 255, message: '输出表名不能超过 255 个字符' },
          ]}
        >
          <Input placeholder="例如 all_orders" />
        </Form.Item>
      </Form>

      <section className="canvas-union-section">
        <div className="canvas-union-heading">
          <div>
            <Typography.Text strong>输入表</Typography.Text>
            <Typography.Text type="secondary"> · 至少两张</Typography.Text>
          </div>
          <Tag>{inputTableNames.length}</Tag>
        </div>
        <Select
          showSearch
          optionFilterProp="label"
          value={undefined}
          placeholder={validation ? '添加输入表' : '等待 Task Engine 返回上游表'}
          options={candidateOptions}
          onChange={(tableName: string) => updateInputTables([
            ...inputTableNames,
            tableName,
          ])}
        />
        {draftError && <Alert showIcon type="error" title={draftError} />}
        <div className="canvas-union-table-list">
          {inputTableNames.map((tableName, index) => {
            const table = tableByName.get(tableName);
            const difference = schemaDifference(baseline, table);
            const mismatch = Boolean(
              !mergeEnabled
              &&
              difference
              && (difference.missing.length > 0 || difference.extra.length > 0),
            );
            return (
              <div
                className={`canvas-union-table-item${!table || mismatch ? ' is-invalid' : ''}`}
                key={`${tableName}-${index}`}
              >
                <div className="canvas-union-table-heading">
                  <div className="canvas-union-table-name">
                    <Tag color={index === 0 ? 'blue' : 'default'}>{index + 1}</Tag>
                    <Typography.Text ellipsis title={tableName}>{tableName}</Typography.Text>
                    {index === 0 && <Tag color="blue">顺序基准</Tag>}
                    {!table && <Tag color="error">已失效</Tag>}
                  </div>
                  <Space size={0}>
                    <Tooltip title={index === 0
                      ? '基准层字段无需配置'
                      : !mergeEnabled
                        ? '启用灵活对齐后可配置字段'
                        : !table
                          ? '上游表失效，暂时无法配置字段'
                          : '配置字段处理'}>
                      <span>
                        <Button
                          type="text"
                          size="small"
                          icon={<SettingOutlined />}
                          aria-label={`配置 Union 合并字段 ${tableName}`}
                          disabled={index === 0 || !mergeEnabled || !table}
                          onClick={() => setEditingTableName(tableName)}
                        />
                      </span>
                    </Tooltip>
                    <Tooltip title="上移">
                      <span>
                        <Button
                          type="text"
                          size="small"
                          icon={<UpOutlined />}
                          aria-label={`上移 Union 输入表 ${tableName}`}
                          disabled={index === 0}
                          onClick={() => moveInputTable(index, index - 1)}
                        />
                      </span>
                    </Tooltip>
                    <Tooltip title="下移">
                      <span>
                        <Button
                          type="text"
                          size="small"
                          icon={<DownOutlined />}
                          aria-label={`下移 Union 输入表 ${tableName}`}
                          disabled={index === inputTableNames.length - 1}
                          onClick={() => moveInputTable(index, index + 1)}
                        />
                      </span>
                    </Tooltip>
                    <Tooltip title="移除">
                      <Button
                        type="text"
                        size="small"
                        danger
                        icon={<DeleteOutlined />}
                        aria-label={`删除 Union 输入表 ${tableName}`}
                        onClick={() => removeInputTable(index)}
                      />
                    </Tooltip>
                  </Space>
                </div>
                {table && (
                  <div className="canvas-union-schema-summary">
                    <Tag>{table.columns.length} 字段</Tag>
                    <Tag color={table.datasetKind === 'UNBOUNDED' ? 'processing' : 'default'}>
                      {table.datasetKind}
                    </Tag>
                    {index > 0 && difference && !mismatch && (
                      <Tag color="success">{mergeEnabled ? '自动对齐' : '字段集合一致'}</Tag>
                    )}
                    {index > 0 && mergeEnabled && (
                      <Tag color="blue">
                        {mergingTables.find((item) => item.tableName === tableName)?.fieldRules.length ?? 0} 条字段规则
                      </Tag>
                    )}
                    {difference && difference.typeDifferences > 0 && (
                      <Tag color="warning">
                        {difference.typeDifferences} 个类型差异{mergeEnabled ? '' : '待引擎分析'}
                      </Tag>
                    )}
                  </div>
                )}
                {!mergeEnabled && difference && difference.missing.length > 0 && (
                  <Typography.Text type="danger">
                    缺少：{compactNames(difference.missing)}
                  </Typography.Text>
                )}
                {!mergeEnabled && difference && difference.extra.length > 0 && (
                  <Typography.Text type="danger">
                    额外：{compactNames(difference.extra)}
                  </Typography.Text>
                )}
                {!table && (
                  <Typography.Text type="danger">
                    上游已不再提供该表，原配置已保留。
                  </Typography.Text>
                )}
              </div>
            );
          })}
        </div>
      </section>

      <section className="canvas-union-section">
        <div className="canvas-union-heading">
          <div>
            <Typography.Text strong>字段合并</Typography.Text>
            <Typography.Text type="secondary"> · Merge Layers</Typography.Text>
          </div>
          <Radio.Group
            size="small"
            optionType="button"
            buttonStyle="solid"
            value={mergeEnabled ? 'MERGE_LAYERS' : 'STRICT'}
            options={[
              { value: 'MERGE_LAYERS', label: '灵活对齐' },
              { value: 'STRICT', label: '严格同 Schema' },
            ]}
            onChange={(event) => {
              setMergeEnabled(event.target.value === 'MERGE_LAYERS');
              onDirtyChange(true);
            }}
          />
        </div>
        <Typography.Text type="secondary">
          灵活对齐保留第一张基准表的全部字段，合并表的同名字段自动 Match，新字段自动追加，缺失位置补 NULL。通过各表设置按钮可改为 Rename/Remove。
        </Typography.Text>
      </section>

      <section className="canvas-union-section">
        <Typography.Text strong>合并模式</Typography.Text>
        <Radio.Group
          optionType="button"
          buttonStyle="solid"
          value={mode}
          options={[
            { value: 'ALL', label: 'ALL · 保留重复行' },
            { value: 'DISTINCT', label: 'DISTINCT · 全字段去重' },
          ]}
          onChange={(event) => {
            setMode(event.target.value as UnionMode);
            setDraftError(null);
            onDirtyChange(true);
          }}
        />
        {mixedDatasetKind && (
          <Alert
            showIcon
            type="error"
            title="不能混合有界表与无界表"
          />
        )}
        {hasUnboundedInput && mode === 'DISTINCT' && (
          <Alert
            showIcon
            type="error"
            title="无界输入不支持 DISTINCT"
            description="全字段去重会形成无边界状态；请使用 ALL，或等待后续带 Watermark 的去重节点。"
          />
        )}
      </section>
      {editingTableName && (() => {
        const tableIndex = inputTableNames.indexOf(editingTableName);
        const table = tableByName.get(editingTableName);
        const outputColumns = outputColumnsBefore(
          tableIndex,
          inputTableNames,
          tableByName,
          mergingTables,
        );
        const savedRules = mergingTables.find(
          (item) => item.tableName === editingTableName,
        )?.fieldRules ?? [];
        return <UnionMergeFieldsModal
          open
          table={table}
          outputColumns={outputColumns}
          savedRules={savedRules}
          onCancel={() => setEditingTableName(null)}
          onSave={(fieldRules) => {
            const customRules = table
              ? customMergeRules(table, outputColumns, fieldRules)
              : fieldRules;
            setMergingTables((current) => {
              const byName = new Map(current.map((item) => [item.tableName, item]));
              if (customRules.length === 0) {
                byName.delete(editingTableName);
              } else {
                byName.set(editingTableName, {
                  tableName: editingTableName,
                  fieldRules: customRules,
                });
              }
              return inputTableNames.slice(1).flatMap((tableName) => {
                const item = byName.get(tableName);
                return item ? [item] : [];
              });
            });
            setEditingTableName(null);
            onDirtyChange(true);
          }}
        />;
      })()}
    </Space>
  );
};
