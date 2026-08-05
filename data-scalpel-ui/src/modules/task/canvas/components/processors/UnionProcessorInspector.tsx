import {
  DeleteOutlined,
  DownOutlined,
  UpOutlined,
} from '@ant-design/icons';
import {
  Alert,
  Button,
  Form,
  Input,
  Radio,
  Select,
  Space,
  Tag,
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
  type UnionConfiguration,
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
    setDraftError(next.length < 2 ? '至少选择两张输入表' : null);
    onDirtyChange(true);
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      try {
        const values = await form.validateFields();
        if (inputTableNames.length < 2) {
          setDraftError('至少选择两张输入表');
          return false;
        }
        const uniqueNames = new Set(inputTableNames);
        if (uniqueNames.size !== inputTableNames.length) {
          setDraftError('输入表不能重复');
          return false;
        }
        if (!mode) {
          setDraftError('请选择 Union 模式');
          return false;
        }
        if (hasUnboundedInput && mode === 'DISTINCT') {
          setDraftError('无界输入不支持 UNION DISTINCT');
          return false;
        }
        const configuration: UnionConfiguration = {
          inputTableNames: [...inputTableNames],
          outputTableName: values.outputTableName.trim(),
          mode,
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
    mode,
    node.id,
    onApply,
    onDirtyChange,
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
                    <Button
                      type="text"
                      size="small"
                      icon={<UpOutlined />}
                      aria-label={`上移 Union 输入表 ${tableName}`}
                      disabled={index === 0}
                      onClick={() => updateInputTables(move(inputTableNames, index, index - 1))}
                    />
                    <Button
                      type="text"
                      size="small"
                      icon={<DownOutlined />}
                      aria-label={`下移 Union 输入表 ${tableName}`}
                      disabled={index === inputTableNames.length - 1}
                      onClick={() => updateInputTables(move(inputTableNames, index, index + 1))}
                    />
                    <Button
                      type="text"
                      size="small"
                      danger
                      icon={<DeleteOutlined />}
                      aria-label={`删除 Union 输入表 ${tableName}`}
                      onClick={() => updateInputTables(
                        inputTableNames.filter((_, itemIndex) => itemIndex !== index),
                      )}
                    />
                  </Space>
                </div>
                {table && (
                  <div className="canvas-union-schema-summary">
                    <Tag>{table.columns.length} 字段</Tag>
                    <Tag color={table.datasetKind === 'UNBOUNDED' ? 'processing' : 'default'}>
                      {table.datasetKind}
                    </Tag>
                    {index > 0 && difference && !mismatch && (
                      <Tag color="success">字段集合一致</Tag>
                    )}
                    {difference && difference.typeDifferences > 0 && (
                      <Tag color="warning">{difference.typeDifferences} 个类型差异待引擎分析</Tag>
                    )}
                  </div>
                )}
                {difference && difference.missing.length > 0 && (
                  <Typography.Text type="danger">
                    缺少：{compactNames(difference.missing)}
                  </Typography.Text>
                )}
                {difference && difference.extra.length > 0 && (
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
    </Space>
  );
};
