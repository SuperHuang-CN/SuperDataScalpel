import { createUuid } from '../../../../shared/browser/createUuid';
import { DeleteOutlined, DownOutlined, PlusOutlined, SettingOutlined, UpOutlined } from '@ant-design/icons';
import { Button, Empty, Input, List, Modal, Popconfirm, Radio, Space, Tag, Tooltip, Typography } from 'antd';
import {
  useCallback,
  useEffect,
  useImperativeHandle,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type ComponentType,
} from 'react';
import type {
  CanvasExecutionMode,
  CanvasNodeByType,
  CanvasNodeConfigurationUpdate,
  CanvasNodeType,
  CanvasNodeValidationResult,
} from '../canvasTypes';
import type {
  CanvasNodeInspectorComponent,
  CanvasNodeInspectorComponentProps,
  CanvasNodeInspectorHandle,
} from './nodeSpec';
import type { Ref } from 'react';
import { ProcessorValidationIssues } from '../components/processors/ProcessorValidationIssues';

const simpleProcessorTypes = new Set<CanvasNodeType>([
  'FILTER', 'DEDUPLICATE', 'NULL_HANDLING', 'TOP_N', 'RENAME', 'SELECT_COLUMNS',
  'TYPE_CAST', 'VALUE_MAPPING', 'MASK_FIELDS', 'JSON_EXTRACT',
]);

export type ProcessorOperationDraft = {
  operationId: string;
  sourceTableName: string;
  output: { mode: 'REPLACE_SOURCE'; outputTableName: string | null }
    | { mode: 'CREATE_NEW_TABLE'; outputTableName: string };
  [key: string]: unknown;
};

const createProcessorOperation = (type: CanvasNodeType, sourceTableName: string): ProcessorOperationDraft => {
  const base: ProcessorOperationDraft = {
    operationId: createUuid(),
    sourceTableName,
    output: { mode: 'REPLACE_SOURCE', outputTableName: null },
  };
  switch (type) {
    case 'FILTER': return {
      ...base,
      mode: 'STRUCTURED',
      condition: { kind: 'GROUP', operator: 'AND', children: [] },
      sqlExpression: '',
    };
    case 'DEDUPLICATE': return { ...base, keyColumns: [], keepStrategy: 'ANY', orderBy: [] };
    case 'NULL_HANDLING': return { ...base, rules: [] };
    case 'TOP_N': return { ...base, partitionByColumns: [], orderBy: [], limit: 10, tieStrategy: 'EXACT' };
    case 'RENAME': return { ...base, columnMappings: [] };
    case 'SELECT_COLUMNS': return { ...base, columns: [] };
    case 'DERIVE_COLUMNS': return { ...base, derivations: [] };
    case 'TYPE_CAST': return { ...base, casts: [] };
    case 'VALUE_MAPPING': return { ...base, rules: [] };
    case 'MASK_FIELDS': return { ...base, fieldRules: [] };
    case 'JSON_EXTRACT': return { ...base, sourceColumnName: '', extractions: [], failureStrategy: 'ERROR' };
    default: return base;
  }
};

const legacyOperationConfiguration = (
  operation: ProcessorOperationDraft,
  operations: ProcessorOperationDraft[],
) => ({
  ...operation,
  operations,
  outputTableName: operation.output.outputTableName ?? operation.sourceTableName,
});

const operationRuleCount = (operation: ProcessorOperationDraft): number => {
  if (operation.mode === 'SQL_EXPRESSION') {
    return typeof operation.sqlExpression === 'string' && operation.sqlExpression.trim() ? 1 : 0;
  }
  const listKeys = ['rules', 'columns', 'derivations', 'casts', 'fieldRules', 'extractions', 'columnMappings', 'keyColumns'];
  const direct = listKeys.reduce((count, key) => count + (Array.isArray(operation[key]) ? operation[key].length : 0), 0);
  if (direct > 0) return direct;
  const condition = operation.condition as { children?: unknown[] } | undefined;
  return Array.isArray(condition?.children) ? condition.children.length : 0;
};

const operationHasConfiguredRules = (
  type: CanvasNodeType,
  operation: ProcessorOperationDraft,
): boolean => {
  if (operationRuleCount(operation) > 0) return true;
  if (type === 'DEDUPLICATE') {
    return Array.isArray(operation.orderBy) && operation.orderBy.length > 0;
  }
  if (type === 'TOP_N') {
    return (Array.isArray(operation.partitionByColumns) && operation.partitionByColumns.length > 0)
      || (Array.isArray(operation.orderBy) && operation.orderBy.length > 0)
      || operation.limit !== 10
      || operation.tieStrategy !== 'EXACT';
  }
  if (type === 'JSON_EXTRACT') {
    return Boolean(operation.sourceColumnName) || operation.failureStrategy !== 'ERROR';
  }
  return false;
};

const operationTargetLabel = (type: CanvasNodeType, operation: ProcessorOperationDraft): string => {
  if (operation.output.mode === 'CREATE_NEW_TABLE') return operation.output.outputTableName || '待命名新表';
  if (type === 'RENAME' && operation.output.outputTableName) return operation.output.outputTableName;
  return '原表更新';
};

const filterPredicateCount = (condition: unknown): number => {
  if (!condition || typeof condition !== 'object') return 0;
  const value = condition as { kind?: string; children?: unknown[] };
  if (value.kind === 'PREDICATE') return 1;
  return Array.isArray(value.children)
    ? value.children.reduce<number>((count, child) => count + filterPredicateCount(child), 0)
    : 0;
};

const operationSummary = (
  type: CanvasNodeType,
  operation: ProcessorOperationDraft,
  fieldCount?: number,
): string => {
  switch (type) {
    case 'RENAME':
      return `字段映射 ${Array.isArray(operation.columnMappings) ? operation.columnMappings.length : 0} 项`;
    case 'FILTER':
      return operation.mode === 'SQL_EXPRESSION'
        ? 'SQL 表达式'
        : `筛选条件 ${filterPredicateCount(operation.condition)} 项`;
    case 'SELECT_COLUMNS':
      return `已选 ${Array.isArray(operation.columns) ? operation.columns.length : 0} / 总字段 ${fieldCount ?? '—'}`;
    case 'TYPE_CAST':
      return `类型转换 ${Array.isArray(operation.casts) ? operation.casts.length : 0} 项`;
    case 'DEDUPLICATE': {
      const keys = Array.isArray(operation.keyColumns) ? operation.keyColumns.length : 0;
      const order = Array.isArray(operation.orderBy) ? operation.orderBy.length : 0;
      return `${keys > 0 ? `业务键 ${keys}` : '全部字段'} · 排序 ${order}`;
    }
    case 'NULL_HANDLING':
      return `空值规则 ${Array.isArray(operation.rules) ? operation.rules.length : 0} 项`;
    case 'VALUE_MAPPING': {
      const rules = Array.isArray(operation.rules) ? operation.rules as Array<{ entries?: unknown[] }> : [];
      return `字段规则 ${rules.length} · 值映射 ${rules.reduce((count, rule) => count + (Array.isArray(rule.entries) ? rule.entries.length : 0), 0)}`;
    }
    case 'MASK_FIELDS':
      return `脱敏字段 ${Array.isArray(operation.fieldRules) ? operation.fieldRules.length : 0} 项`;
    case 'JSON_EXTRACT':
      return `${typeof operation.sourceColumnName === 'string' && operation.sourceColumnName ? operation.sourceColumnName : 'JSON 字段待选'} · 提取 ${Array.isArray(operation.extractions) ? operation.extractions.length : 0}`;
    case 'TOP_N':
      return `${Array.isArray(operation.partitionByColumns) && operation.partitionByColumns.length > 0 ? '每组' : '全局'} · 排序 ${Array.isArray(operation.orderBy) ? operation.orderBy.length : 0} · 前 ${typeof operation.limit === 'number' ? operation.limit : '—'} 行`;
    default:
      return `${operationRuleCount(operation)} 条规则`;
  }
};

const splitProcessorTypes = new Set<CanvasNodeType>([
  'FILTER', 'SELECT_COLUMNS', 'TYPE_CAST', 'NULL_HANDLING',
  'VALUE_MAPPING', 'MASK_FIELDS', 'JSON_EXTRACT',
]);

const processorModalWidth = (type: CanvasNodeType): number => {
  if (type === 'VALUE_MAPPING' || type === 'MASK_FIELDS') return 1120;
  if (splitProcessorTypes.has(type)) return 1040;
  return type === 'RENAME' ? 920 : 880;
};

const processorTypeClass = (type: CanvasNodeType): string => type.toLocaleLowerCase().replaceAll('_', '-');

const ruleSummaries = (type: CanvasNodeType, operation: ProcessorOperationDraft): string[] => {
  switch (type) {
    case 'TYPE_CAST':
      return (Array.isArray(operation.casts) ? operation.casts : []).map((item, index) => {
        const cast = item as { columnName?: string; targetType?: { type?: string } };
        return `${cast.columnName || `转换 ${index + 1}`} → ${cast.targetType?.type ?? '待设置'}`;
      });
    case 'NULL_HANDLING':
      return (Array.isArray(operation.rules) ? operation.rules : []).map((item, index) => {
        const rule = item as { kind?: string; columnName?: string; columnNames?: string[] };
        return rule.kind === 'DROP_ROW'
          ? `删除行 · ${rule.columnNames?.length ?? 0} 字段`
          : `填充 · ${rule.columnName || `规则 ${index + 1}`}`;
      });
    case 'VALUE_MAPPING':
      return (Array.isArray(operation.rules) ? operation.rules : []).map((item, index) => {
        const rule = item as {
          columnName?: string;
          entries?: Array<{ sourceValue?: { dataType?: string } }>;
          unmatchedValue?: { dataType?: string } | null;
        };
        const dataType = rule.entries?.[0]?.sourceValue?.dataType ?? rule.unmatchedValue?.dataType;
        return `${rule.columnName || `字段 ${index + 1}`}${dataType ? ` · ${dataType}` : ''} · ${rule.entries?.length ?? 0} 项`;
      });
    case 'MASK_FIELDS':
      return (Array.isArray(operation.fieldRules) ? operation.fieldRules : []).map((item, index) => {
        const rule = item as { fieldName?: string; ruleSource?: string };
        return `${rule.fieldName || `字段 ${index + 1}`} · ${rule.ruleSource === 'GLOBAL' ? '全局规则' : '内联规则'}`;
      });
    case 'JSON_EXTRACT':
      return (Array.isArray(operation.extractions) ? operation.extractions : []).map((item, index) => {
        const extraction = item as { outputColumnName?: string; jsonPath?: string };
        return `${extraction.outputColumnName || `提取 ${index + 1}`} · ${extraction.jsonPath || '$'}`;
      });
    case 'FILTER': {
      if (operation.mode === 'SQL_EXPRESSION') return ['SQL 表达式'];
      const summaries: string[] = [];
      const visit = (condition: unknown) => {
        if (!condition || typeof condition !== 'object') return;
        const value = condition as { kind?: string; columnName?: string; operator?: string; children?: unknown[] };
        if (value.kind === 'PREDICATE') summaries.push(`${value.columnName || '字段待选'} · ${value.operator ?? '条件待选'}`);
        else value.children?.forEach(visit);
      };
      visit(operation.condition);
      return summaries;
    }
    default:
      return [];
  }
};

const ruleDetailSelector = (
  type: CanvasNodeType,
  operation: ProcessorOperationDraft | null,
): string => {
  if (type === 'FILTER') {
    return operation?.mode === 'SQL_EXPRESSION'
      ? '.canvas-filter-sql-expression'
      : '.canvas-filter-predicate';
  }
  return type === 'TYPE_CAST' ? '.canvas-type-cast-item' : '.canvas-processor-rule-card';
};

interface ProcessorTablePickerModalProps {
  open: boolean;
  type: CanvasNodeType;
  inputTables: readonly { name: string; columns: readonly unknown[] }[];
  operations: readonly ProcessorOperationDraft[];
  onCancel: () => void;
  onConfirm: (operations: ProcessorOperationDraft[]) => void;
}

/** Keeps candidate searching separate from the complete, ordered operation selection. */
export const ProcessorTablePickerModal = ({
  open,
  type,
  inputTables,
  operations,
  onCancel,
  onConfirm,
}: ProcessorTablePickerModalProps) => {
  const [search, setSearch] = useState('');
  const [draft, setDraft] = useState<ProcessorOperationDraft[]>(() => [...operations]);

  const knownTables = useMemo(() => new Map(inputTables.map((table) => [table.name, table])), [inputTables]);
  const candidates = useMemo(() => {
    const keyword = search.trim().toLocaleLowerCase();
    const selected = new Set(draft.map((operation) => operation.sourceTableName));
    return inputTables.filter((table) => !selected.has(table.name)
      && (!keyword || table.name.toLocaleLowerCase().includes(keyword)));
  }, [draft, inputTables, search]);

  const add = (sourceTableName: string) => {
    setDraft((current) => current.some((operation) => operation.sourceTableName === sourceTableName)
      ? current
      : [...current, createProcessorOperation(type, sourceTableName)]);
  };
  const remove = (operationId: string) => setDraft((current) => current.filter((operation) => operation.operationId !== operationId));
  const move = (index: number, offset: number) => setDraft((current) => {
    const target = index + offset;
    if (target < 0 || target >= current.length) return current;
    const next = [...current];
    [next[index], next[target]] = [next[target], next[index]];
    return next;
  });

  return (
    <Modal
      open={open}
      width={860}
      className="canvas-processor-table-picker"
      title="管理处理表"
      onCancel={onCancel}
      destroyOnHidden
      footer={<Space><Button onClick={onCancel}>取消</Button><Button type="primary" onClick={() => onConfirm(draft)}>确定 · {draft.length} 张表</Button></Space>}
    >
      <div className="canvas-jdbc-input-picker-grid">
        <section className="canvas-jdbc-input-picker-pane">
          <div className="canvas-jdbc-input-picker-heading">
            <div><strong>上游表</strong> <Typography.Text type="secondary">从节点入口选择</Typography.Text></div>
            <Button type="link" size="small" disabled={candidates.length === 0} onClick={() => setDraft((current) => [
              ...current,
              ...candidates.map((table) => createProcessorOperation(type, table.name)),
            ])}>选择当前结果</Button>
          </div>
          <Input autoComplete="off" name="canvas-processor-table-search" allowClear value={search} placeholder="搜索上游逻辑表" onChange={(event) => setSearch(event.target.value)} />
          <List
            className="canvas-jdbc-input-picker-list"
            size="small"
            dataSource={candidates}
            locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={inputTables.length === 0 ? '等待上游表解析' : '没有可添加的表'} /> }}
            renderItem={(table) => <List.Item actions={[<Button key="add" type="link" size="small" icon={<PlusOutlined />} onClick={() => add(table.name)}>添加</Button>]}><Space size={7}><Typography.Text ellipsis title={table.name}>{table.name}</Typography.Text><Tag>{table.columns.length} 字段</Tag></Space></List.Item>}
          />
        </section>
        <section className="canvas-jdbc-input-picker-pane is-selected">
          <div className="canvas-jdbc-input-picker-heading"><div><strong>已配置处理</strong> <Tag color="blue">{draft.length}</Tag></div><Typography.Text type="secondary">排序决定新表的追加顺序</Typography.Text></div>
          <List
            className="canvas-jdbc-input-picker-list is-selected"
            size="small"
            dataSource={draft}
            locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未添加处理表" /> }}
            renderItem={(operation, index) => {
              const sourceTable = knownTables.get(operation.sourceTableName);
              const sourceMissing = !sourceTable;
              const summary = sourceMissing
                ? '上游表已失效'
                : `→ ${operationTargetLabel(type, operation)} · ${operationSummary(type, operation, sourceTable.columns.length)}`;
              const content = (
                <List.Item className={sourceMissing ? 'canvas-processor-operation-item is-invalid' : 'canvas-processor-operation-item'}>
                  <span className="canvas-jdbc-input-picker-index">{index + 1}</span>
                  <div className="canvas-processor-operation-identity"><Typography.Text ellipsis title={operation.sourceTableName}>{operation.sourceTableName}</Typography.Text><Typography.Text type={sourceMissing ? 'danger' : 'secondary'} ellipsis title={summary}>{summary}</Typography.Text></div>
                  <Space size={0}>
                    <Tooltip title="上移"><Button type="text" size="small" disabled={index === 0} icon={<UpOutlined />} onClick={() => move(index, -1)} /></Tooltip>
                    <Tooltip title="下移"><Button type="text" size="small" disabled={index === draft.length - 1} icon={<DownOutlined />} onClick={() => move(index, 1)} /></Tooltip>
                    {operationHasConfiguredRules(type, operation) ? <Popconfirm title="移除这张处理表？" description="该表的处理规则也会一并删除。" okText="移除" cancelText="保留" okButtonProps={{ danger: true }} onConfirm={() => remove(operation.operationId)}><Button type="text" size="small" danger icon={<DeleteOutlined />} aria-label={`移除 ${operation.sourceTableName}`} /></Popconfirm> : <Tooltip title="移除"><Button type="text" size="small" danger icon={<DeleteOutlined />} aria-label={`移除 ${operation.sourceTableName}`} onClick={() => remove(operation.operationId)} /></Tooltip>}
                  </Space>
                </List.Item>
              );
              return content;
            }}
          />
        </section>
      </div>
    </Modal>
  );
};

const stripDeprecatedOutputFields = <T extends CanvasNodeType>(type: T, configuration: unknown): unknown => {
  if (type === 'JDBC_OUTPUT' || type === 'MODEL_OUTPUT' || type === 'KAFKA_OUTPUT' || type === 'FILE_OUTPUT') {
    const value = configuration as Record<string, unknown>;
    const normalized = { ...value };
    ['sourceTableName', 'targetTableName', 'targetModelId', 'writeMode', 'columnMappings', 'upsertKeyColumns',
      'topic', 'valueSchema', 'keyColumnName', 'targetPath', 'conflictPolicy', 'formatOptions']
      .forEach((key) => delete normalized[key]);
    return normalized;
  }
  return configuration;
};

interface LegacyInspectorProps<T extends CanvasNodeType> {
  node: CanvasNodeByType<T>;
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage: string | null;
  executionMode: CanvasExecutionMode;
  onApply: (update: CanvasNodeConfigurationUpdate) => void;
  onDirtyChange: (dirty: boolean) => void;
  inspectorRef: Ref<CanvasNodeInspectorHandle>;
}

const MultiTableProcessorInspector = <T extends CanvasNodeType>({
  type,
  Inspector,
  props,
}: {
  type: T;
  Inspector: ComponentType<LegacyInspectorProps<T>>;
  props: CanvasNodeInspectorComponentProps<T>;
}) => {
  const configuration = props.node.configuration as unknown as { operations?: ProcessorOperationDraft[] };
  const [operations, setOperations] = useState<ProcessorOperationDraft[]>(
    () => structuredClone(configuration.operations ?? []),
  );
  const [pickerOpen, setPickerOpen] = useState(false);
  const [editorDraft, setEditorDraft] = useState<ProcessorOperationDraft | null>(null);
  const [selectedRuleIndex, setSelectedRuleIndex] = useState(0);
  const editorRef = useRef<CanvasNodeInspectorHandle>(null);
  const capturedEditorOperationRef = useRef<ProcessorOperationDraft | null>(null);
  const editorHostRef = useRef<HTMLDivElement>(null);
  const [editorHostElement, setEditorHostElement] = useState<HTMLDivElement | null>(null);
  const editorSessionRef = useRef(0);
  const editorSyncFrameRef = useRef<number | null>(null);
  const inputTables = props.validation?.inputTables ?? [];
  const activeIndex = editorDraft == null
    ? -1
    : operations.findIndex((operation) => operation.operationId === editorDraft.operationId);
  const syntheticNode = editorDraft == null ? null : {
    ...props.node,
    configuration: legacyOperationConfiguration(editorDraft, operations),
  } as CanvasNodeByType<T>;
  const scopedValidation = props.validation == null || activeIndex < 0 ? props.validation : {
    ...props.validation,
    issues: props.validation.issues.filter((issue) => (
      issue.path?.startsWith(`configuration.operations[${activeIndex}]`) ?? false
    )),
  };
  const summaries = editorDraft ? ruleSummaries(type, editorDraft) : [];
  const typeClass = processorTypeClass(type);

  const updateOperations = (nextOperations: ProcessorOperationDraft[]) => {
    setOperations(structuredClone(nextOperations));
    props.onDirtyChange(true);
  };

  const bindEditorHost = useCallback((element: HTMLDivElement | null) => {
    editorHostRef.current = element;
    setEditorHostElement(element);
  }, []);

  useEffect(() => () => {
    if (editorSyncFrameRef.current !== null) {
      cancelAnimationFrame(editorSyncFrameRef.current);
    }
  }, []);

  useLayoutEffect(() => {
    if (!splitProcessorTypes.has(type) || type === 'SELECT_COLUMNS') return;
    const selector = ruleDetailSelector(type, editorDraft);
    const editorHost = editorHostElement;
    if (!editorHost) return;
    const syncVisibility = () => {
      editorHost.querySelectorAll<HTMLElement>(selector).forEach((element, index) => {
        element.classList.toggle('is-rule-detail-hidden', index !== selectedRuleIndex);
      });
    };
    syncVisibility();
    const observer = new MutationObserver(syncVisibility);
    observer.observe(editorHost, {
      attributes: true,
      attributeFilter: ['class'],
      childList: true,
      subtree: true,
    });
    return () => observer.disconnect();
  }, [editorDraft, editorHostElement, selectedRuleIndex, type]);

  useImperativeHandle(props.inspectorRef, () => ({
    apply: async () => {
      props.onApply({
        id: props.node.id,
        type,
        configuration: { operations: structuredClone(operations) } as unknown as CanvasNodeConfigurationUpdate['configuration'],
      } as Parameters<typeof props.onApply>[0]);
      props.onDirtyChange(false);
      return true;
    },
  }), [operations, props, type]);

  const captureEditorUpdate = (update: CanvasNodeConfigurationUpdate) => {
    if (!editorDraft) return;
    const updated = update.configuration as unknown as Record<string, unknown>;
    const rules = { ...updated };
    delete rules.operations;
    delete rules.sourceTableName;
    delete rules.outputTableName;
    capturedEditorOperationRef.current = {
      ...editorDraft,
      ...rules,
      operationId: editorDraft.operationId,
      sourceTableName: editorDraft.sourceTableName,
      output: structuredClone(editorDraft.output),
    };
  };

  const closeEditor = () => {
    editorSessionRef.current += 1;
    if (editorSyncFrameRef.current !== null) {
      cancelAnimationFrame(editorSyncFrameRef.current);
      editorSyncFrameRef.current = null;
    }
    capturedEditorOperationRef.current = null;
    setEditorDraft(null);
  };

  const openEditor = (operation: ProcessorOperationDraft) => {
    editorSessionRef.current += 1;
    setSelectedRuleIndex(0);
    setEditorDraft(structuredClone(operation));
  };

  const syncEditorNavigation = (dirty: boolean) => {
    if (!dirty) return;
    if (editorSyncFrameRef.current !== null) {
      cancelAnimationFrame(editorSyncFrameRef.current);
    }
    const session = editorSessionRef.current;
    editorSyncFrameRef.current = requestAnimationFrame(() => {
      editorSyncFrameRef.current = null;
      void (async () => {
        capturedEditorOperationRef.current = null;
        await editorRef.current?.apply();
        if (session !== editorSessionRef.current) return;
        const liveOperation = capturedEditorOperationRef.current;
        if (liveOperation) {
          const nextSummaries = ruleSummaries(type, liveOperation);
          const selectedSummary = summaries[selectedRuleIndex];
          setSelectedRuleIndex((current) => {
            if (nextSummaries.length > summaries.length) return nextSummaries.length - 1;
            const movedIndex = selectedSummary ? nextSummaries.indexOf(selectedSummary) : -1;
            return movedIndex >= 0
              ? movedIndex
              : Math.max(0, Math.min(current, nextSummaries.length - 1));
          });
          setEditorDraft(structuredClone(liveOperation));
        }
      })();
    });
  };

  const saveEditor = async () => {
    if (!editorDraft) return;
    if (editorSyncFrameRef.current !== null) {
      cancelAnimationFrame(editorSyncFrameRef.current);
      editorSyncFrameRef.current = null;
    }
    capturedEditorOperationRef.current = null;
    const valid = await editorRef.current?.apply();
    if (valid === false) return;
    const nextOperation = capturedEditorOperationRef.current ?? editorDraft;
    setOperations((current) => current.map((operation) => (
      operation.operationId === nextOperation.operationId
        ? structuredClone(nextOperation)
        : operation
    )));
    closeEditor();
    props.onDirtyChange(true);
  };

  const selectRule = (index: number) => {
    setSelectedRuleIndex(index);
    const selector = ruleDetailSelector(type, editorDraft);
    requestAnimationFrame(() => {
      editorHostRef.current?.querySelectorAll<HTMLElement>(selector)[index]?.scrollIntoView({
        block: 'nearest',
        behavior: 'smooth',
      });
    });
  };

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      <ProcessorValidationIssues
        validation={props.validation}
        unavailableMessage={props.validationUnavailableMessage}
      />
      <div className="canvas-processor-operation-toolbar">
        <div><Typography.Text strong>处理表</Typography.Text><Typography.Text type="secondary"> 每张表独立配置规则</Typography.Text></div>
        <Space size={6} wrap>
          <Button icon={<SettingOutlined />} disabled={!props.validation && inputTables.length === 0} onClick={() => setPickerOpen(true)}>管理处理表</Button>
          <Tag color="blue">{operations.length} 张表</Tag>
        </Space>
      </div>
      {operations.length > 0 && (
        <div className="canvas-processor-operation-list">
          {operations.map((operation, index) => {
            const sourceTable = inputTables.find((table) => table.name === operation.sourceTableName);
            const operationPath = `configuration.operations[${index}]`;
            const sourceMissing = Boolean(props.validation && !sourceTable);
            const invalid = sourceMissing || Boolean(props.validation?.issues.some((issue) => (
              issue.severity === 'ERROR' && issue.path?.startsWith(operationPath)
            )));
            const summary = sourceMissing
              ? '上游表已失效'
              : `${operationSummary(type, operation, sourceTable?.columns.length)} · ${operationTargetLabel(type, operation)}`;
            return <div
              className={`canvas-processor-operation-row${invalid ? ' is-invalid' : ''}`}
              key={operation.operationId}
            >
              <span className="canvas-jdbc-input-picker-index">{index + 1}</span>
              <span className="canvas-processor-operation-identity">
                <Typography.Text ellipsis title={operation.sourceTableName}>{operation.sourceTableName}</Typography.Text>
                <Typography.Text type={invalid ? 'danger' : 'secondary'} ellipsis title={summary}>
                  {summary}
                </Typography.Text>
              </span>
              <Space size={2}>
                <Tag color={operation.output.mode === 'REPLACE_SOURCE' ? 'blue' : 'purple'}>{operation.output.mode === 'REPLACE_SOURCE' ? '更新' : '新表'}</Tag>
                <Tooltip title="配置当前表">
                  <Button
                    type="text"
                    size="small"
                    icon={<SettingOutlined />}
                    aria-label={`配置 ${operation.sourceTableName}`}
                    onClick={() => openEditor(operation)}
                  />
                </Tooltip>
              </Space>
            </div>;
          })}
        </div>
      )}
      {operations.length === 0 && (
        <Typography.Text type="secondary">请先添加至少一张需要处理的上游表。</Typography.Text>
      )}

      <Modal
        open={Boolean(editorDraft)}
        width={processorModalWidth(type)}
        className={`canvas-processor-operation-modal is-${typeClass}${splitProcessorTypes.has(type) ? ' is-split' : ' is-single'}`}
        title={editorDraft ? `配置处理表 · ${editorDraft.sourceTableName}` : '配置处理表'}
        styles={{ body: { overflow: 'hidden' } }}
        destroyOnHidden
        onCancel={closeEditor}
        footer={<Space><Button onClick={closeEditor}>取消</Button><Button type="primary" onClick={() => void saveEditor()}>保存此项</Button></Space>}
      >
        {editorDraft && syntheticNode && <>
          <div className="canvas-processor-operation-output-settings">
            <Typography.Text type="secondary">来源表</Typography.Text>
            <Typography.Text code>{editorDraft.sourceTableName}</Typography.Text>
            <Radio.Group
              size="small"
              optionType="button"
              buttonStyle="solid"
              value={editorDraft.output.mode}
              options={[
                { value: 'REPLACE_SOURCE', label: '更新当前表' },
                { value: 'CREATE_NEW_TABLE', label: '生成新表' },
              ]}
              onChange={(event) => {
                const mode = event.target.value as ProcessorOperationDraft['output']['mode'];
                setEditorDraft({
                  ...editorDraft,
                  output: mode === 'REPLACE_SOURCE'
                    ? { mode, outputTableName: type === 'RENAME' ? editorDraft.output.outputTableName : null }
                    : { mode, outputTableName: editorDraft.output.outputTableName ?? '' },
                });
              }}
            />
            {(editorDraft.output.mode === 'CREATE_NEW_TABLE' || type === 'RENAME') && <Input
              className="canvas-processor-operation-output-name"
              autoComplete="off"
              name={`canvas-${typeClass}-output-table-name`}
              value={editorDraft.output.outputTableName ?? (type === 'RENAME' ? editorDraft.sourceTableName : '')}
              placeholder="输出逻辑表名"
              onChange={(event) => setEditorDraft({
                ...editorDraft,
                output: { ...editorDraft.output, outputTableName: event.target.value },
              })}
            />}
          </div>
          <div className={`canvas-processor-operation-editor-layout${splitProcessorTypes.has(type) && type !== 'SELECT_COLUMNS' ? ' has-rule-list' : ''}`}>
            {splitProcessorTypes.has(type) && type !== 'SELECT_COLUMNS' && <aside className="canvas-processor-operation-rule-nav">
              <div className="canvas-processor-operation-rule-nav-heading">
                <Typography.Text strong>已配置规则</Typography.Text>
                <Tag>{summaries.length}</Tag>
              </div>
              <div className="canvas-processor-operation-rule-nav-list">
                {summaries.length === 0
                  ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未配置规则" />
                  : summaries.map((summary, index) => <button
                    type="button"
                    className={`canvas-processor-operation-rule-nav-row${selectedRuleIndex === index ? ' is-active' : ''}`}
                    key={`${index}:${summary}`}
                    onClick={() => selectRule(index)}
                  ><span>{index + 1}</span><Typography.Text ellipsis title={summary}>{summary}</Typography.Text></button>)}
              </div>
            </aside>}
            <div ref={bindEditorHost} className="canvas-processor-editor-host">
              <Inspector
                key={editorDraft.operationId}
                {...props}
                node={syntheticNode}
                validation={scopedValidation}
                onApply={captureEditorUpdate as LegacyInspectorProps<T>['onApply']}
                onDirtyChange={syncEditorNavigation}
                inspectorRef={editorRef}
              />
            </div>
          </div>
        </>}
      </Modal>
      {pickerOpen && <ProcessorTablePickerModal
        key={`${props.node.id}:${JSON.stringify(operations)}`}
        open
        type={type}
        inputTables={inputTables}
        operations={operations}
        onCancel={() => setPickerOpen(false)}
        onConfirm={(next) => {
          updateOperations(next);
          setPickerOpen(false);
        }}
      />}
    </Space>
  );
};

// This module intentionally exports both the shared picker component and the inspector adapter.
// eslint-disable-next-line react-refresh/only-export-components
export const adaptCanvasNodeInspector = <T extends CanvasNodeType>(
  type: T,
  Inspector: ComponentType<LegacyInspectorProps<T>>,
): CanvasNodeInspectorComponent<T> => {
  const AdaptedInspector = (props: CanvasNodeInspectorComponentProps<T>) => {
    if (simpleProcessorTypes.has(type)) {
      return <MultiTableProcessorInspector key={props.node.id} type={type} Inspector={Inspector} props={props} />;
    }
    return (
      <Inspector
        {...props}
        onApply={(update) => {
          if (update.type !== type) {
            throw new Error(`Inspector ${type} 返回了错误节点类型 ${update.type}`);
          }
          props.onApply({
            ...update,
            configuration: stripDeprecatedOutputFields(type, update.configuration),
          } as Parameters<typeof props.onApply>[0]);
        }}
      />
    );
  };
  AdaptedInspector.displayName = `${type}CanvasNodeInspector`;
  return AdaptedInspector;
};
