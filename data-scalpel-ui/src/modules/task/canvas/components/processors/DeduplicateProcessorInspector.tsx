import { CompactAlert as Alert } from '../../../../../shared/components/ContextualFeedback';
import {
  DeleteOutlined,
  DownOutlined,
  PlusOutlined,
  UpOutlined,
} from '@ant-design/icons';
import { Button, Form, Input, Modal, Radio, Select, Space, Tag, Typography } from 'antd';
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
  type DeduplicateConfiguration,
  type DeduplicateKeepStrategy,
  type NullOrdering,
  type SortDirection,
  type SortField,
} from '../../canvasTypes';
import type { CanvasNodeInspectorHandle } from '../CanvasNodeInspector';
import { ProcessorValidationIssues } from './ProcessorValidationIssues';

interface DeduplicateProcessorInspectorProps {
  node: Extract<CanvasNodeDefinition, { type: 'DEDUPLICATE' }>;
  executionMode: 'BATCH' | 'STREAMING';
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage: string | null;
  onApply: (update: CanvasNodeConfigurationUpdate) => void;
  onDirtyChange: (dirty: boolean) => void;
  inspectorRef: Ref<CanvasNodeInspectorHandle>;
}

interface DeduplicateFormValues {
  sourceTableName: string;
  outputTableName: string;
}

type KeyMode = 'ALL_COLUMNS' | 'BUSINESS_KEYS';

const move = <T,>(items: T[], from: number, to: number): T[] => {
  const result = [...items];
  const [item] = result.splice(from, 1);
  result.splice(to, 0, item);
  return result;
};

export const DeduplicateProcessorInspector = ({
  node,
  executionMode,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: DeduplicateProcessorInspectorProps) => {
  const [form] = Form.useForm<DeduplicateFormValues>();
  const [keyMode, setKeyMode] = useState<KeyMode>(
    node.configuration.keyColumns.length === 0 ? 'ALL_COLUMNS' : 'BUSINESS_KEYS',
  );
  const [keyColumns, setKeyColumns] = useState<string[]>(
    () => [...node.configuration.keyColumns],
  );
  const [keepStrategy, setKeepStrategy] = useState<DeduplicateKeepStrategy | null>(
    node.configuration.keepStrategy,
  );
  const [orderBy, setOrderBy] = useState<SortField[]>(
    () => structuredClone(node.configuration.orderBy),
  );
  const [draftError, setDraftError] = useState<string | null>(null);
  const sourceTableName = Form.useWatch('sourceTableName', form)
    ?? node.configuration.sourceTableName;
  const inputTables = useMemo(() => validation?.inputTables ?? [], [validation?.inputTables]);
  const sourceTable = inputTables.find((table) => table.name === sourceTableName);
  const sourceTableMissing = Boolean(sourceTableName && validation && !sourceTable);
  const sourceColumns = useMemo(() => sourceTable?.columns ?? [], [sourceTable?.columns]);
  const sourceColumnNames = useMemo(
    () => new Set(sourceColumns.map((column) => column.name)),
    [sourceColumns],
  );
  const tableOptions = useMemo(() => [
    ...(sourceTableMissing
      ? [{ value: sourceTableName, label: `${sourceTableName}（已失效）`, disabled: true }]
      : []),
    ...inputTables.map((table) => ({
      value: table.name,
      label: `${table.name} · ${table.columns.length} 字段`,
    })),
  ], [inputTables, sourceTableMissing, sourceTableName]);
  const availableKeyOptions = sourceColumns
    .filter((column) => !keyColumns.includes(column.name))
    .map((column) => ({
      value: column.name,
      label: `${column.name} · ${column.fieldType}`,
    }));

  const updateKeys = (next: string[]) => {
    setKeyColumns(next);
    setDraftError(null);
    onDirtyChange(true);
  };

  const updateOrder = (next: SortField[]) => {
    setOrderBy(next);
    setDraftError(null);
    onDirtyChange(true);
  };

  const selectKeepStrategy = (next: DeduplicateKeepStrategy) => {
    if (next === 'ANY' && orderBy.length > 0) {
      Modal.confirm({
        title: '切换为“任意一条”？',
        content: `当前 ${orderBy.length} 条排序规则将被清空。`,
        okText: '清空并切换',
        cancelText: '保留当前策略',
        onOk: () => {
          setKeepStrategy('ANY');
          setOrderBy([]);
          setDraftError(null);
          onDirtyChange(true);
        },
      });
      return;
    }
    setKeepStrategy(next);
    setDraftError(null);
    onDirtyChange(true);
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      if (executionMode !== 'BATCH') {
        setDraftError('DEDUPLICATE 仅支持批处理任务');

      }
      try {
        const values = form.getFieldsValue(true);
        void form.validateFields().catch(() => undefined);
        if (!keepStrategy) {
          setDraftError('请选择保留策略');

        }
        const effectiveKeys = keyMode === 'ALL_COLUMNS' ? [] : keyColumns;
        if (keyMode === 'BUSINESS_KEYS' && effectiveKeys.length === 0) {
          setDraftError('至少选择一个业务键字段');

        }
        if (new Set(effectiveKeys).size !== effectiveKeys.length) {
          setDraftError('去重键字段不能重复');

        }
        if (effectiveKeys.length === 0 && keepStrategy !== 'ANY') {
          setDraftError('按全部字段去重只支持任意保留策略');

        }
        if (keepStrategy === 'ANY' && orderBy.length > 0) {
          setDraftError('任意保留策略不能配置排序规则，请显式清空');

        }
        if (keepStrategy !== 'ANY' && orderBy.length === 0) {
          setDraftError('保留第一条或最后一条时至少配置一个排序字段');

        }
        const sortNames = new Set<string>();
        for (const sortField of orderBy) {
          if (!sortField.columnName) {
            setDraftError('排序项中存在未选择字段');

          }
          if (!sortNames.add(sortField.columnName)) {
            setDraftError(`排序字段重复：${sortField.columnName}`);

          }
          if (!sortField.direction || !sortField.nullOrdering) {
            setDraftError(`${sortField.columnName}：排序方向和 NULL 位置不能为空`);

          }
        }
        const configuration: DeduplicateConfiguration = {
          sourceTableName: values.sourceTableName ?? '',
          outputTableName: (values.outputTableName ?? '').trim(),
          keyColumns: [...effectiveKeys],
          keepStrategy,
          orderBy: structuredClone(orderBy),
        };
        onApply({
          id: node.id,
          type: CanvasNodeType.Deduplicate,
          configuration,
        });
        onDirtyChange(false);
        return true;
      } catch {
        return false;
      }
    },
  }), [
    executionMode,
    form,
    keepStrategy,
    keyColumns,
    keyMode,
    node.id,
    onApply,
    onDirtyChange,
    orderBy,
  ]);

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      <ProcessorValidationIssues
        validation={validation}
        unavailableMessage={validationUnavailableMessage}
      />
      {executionMode !== 'BATCH' && (
        <Alert
          showIcon
          type="error"
          title="去重节点仅支持批处理"
          description="实时去重需要事件时间、Watermark 和状态保留，请使用后续的流式去重节点。"
        />
      )}
      <Form<DeduplicateFormValues>
        autoComplete="off"
        form={form}
        layout="vertical"
        initialValues={{
          sourceTableName: node.configuration.sourceTableName,
          outputTableName: node.configuration.outputTableName,
        }}
        onValuesChange={() => onDirtyChange(true)}
      >
        <Form.Item
          name="sourceTableName"
          label="来源表"
          rules={[{ required: true, message: '请选择来源表' }]}
          validateStatus={sourceTableMissing ? 'error' : undefined}
          help={sourceTableMissing ? '原来源表已失效，去重配置已保留。' : undefined}
        >
          <Select
            showSearch
            optionFilterProp="label"
            placeholder={validation ? '选择一张上游表' : '等待 Task Engine 返回上游表'}
            options={tableOptions}
          />
        </Form.Item>
        <Form.Item
          name="outputTableName"
          label="输出表名"
          rules={[
            { required: true, whitespace: true, message: '请输入输出表名' },
            { max: 255, message: '输出表名不能超过 255 个字符' },
          ]}
        >
          <Input placeholder="例如 latest_orders" />
        </Form.Item>
      </Form>

      <section className="canvas-deduplicate-section">
        <Typography.Text strong>去重范围</Typography.Text>
        <Radio.Group
          optionType="button"
          buttonStyle="solid"
          value={keyMode}
          options={[
            { value: 'ALL_COLUMNS', label: '全部字段' },
            { value: 'BUSINESS_KEYS', label: '业务键字段' },
          ]}
          onChange={(event) => {
            const next = event.target.value as KeyMode;
            setKeyMode(next);
            if (next === 'ALL_COLUMNS') setKeyColumns([]);
            setDraftError(null);
            onDirtyChange(true);
          }}
        />
        {keyMode === 'ALL_COLUMNS' ? (
          <Typography.Text type="secondary">
            所有字段完全相同才视为重复，仅支持任意保留一条。
          </Typography.Text>
        ) : (
          <>
            <Select
              showSearch
              optionFilterProp="label"
              value={undefined}
              placeholder={sourceTable ? '添加业务键字段' : '先选择来源表'}
              disabled={!sourceTable}
              options={availableKeyOptions}
              onChange={(columnName: string) => updateKeys([...keyColumns, columnName])}
            />
            <div className="canvas-deduplicate-key-list">
              {keyColumns.map((columnName, index) => {
                const missing = Boolean(sourceTable && !sourceColumnNames.has(columnName));
                return (
                  <div
                    className={`canvas-deduplicate-key-item${missing ? ' is-invalid' : ''}`}
                    key={`${columnName}-${index}`}
                  >
                    <div className="canvas-deduplicate-key-name">
                      <Tag color="blue">{index + 1}</Tag>
                      <Typography.Text ellipsis title={columnName}>{columnName}</Typography.Text>
                      {missing && <Tag color="error">已失效</Tag>}
                    </div>
                    <Space size={0}>
                      <Button
                        type="text"
                        size="small"
                        icon={<UpOutlined />}
                        aria-label={`上移去重键 ${columnName}`}
                        disabled={index === 0}
                        onClick={() => updateKeys(move(keyColumns, index, index - 1))}
                      />
                      <Button
                        type="text"
                        size="small"
                        icon={<DownOutlined />}
                        aria-label={`下移去重键 ${columnName}`}
                        disabled={index === keyColumns.length - 1}
                        onClick={() => updateKeys(move(keyColumns, index, index + 1))}
                      />
                      <Button
                        type="text"
                        size="small"
                        danger
                        icon={<DeleteOutlined />}
                        aria-label={`删除去重键 ${columnName}`}
                        onClick={() => updateKeys(
                          keyColumns.filter((_, itemIndex) => itemIndex !== index),
                        )}
                      />
                    </Space>
                  </div>
                );
              })}
            </div>
          </>
        )}
      </section>

      <section className="canvas-deduplicate-section">
        <Typography.Text strong>保留策略</Typography.Text>
        <Radio.Group
          value={keepStrategy}
          className="canvas-deduplicate-strategy"
          onChange={(event) => selectKeepStrategy(
            event.target.value as DeduplicateKeepStrategy,
          )}
        >
          <Radio value="ANY">
            <span><strong>任意一条</strong> · 最快，不保证具体记录</span>
          </Radio>
          <Radio value="FIRST">
            <span><strong>第一条</strong> · 按下方排序取第一</span>
          </Radio>
          <Radio value="LAST">
            <span><strong>最后一条</strong> · 反转下方排序后取第一</span>
          </Radio>
        </Radio.Group>
      </section>

      {draftError && <Alert showIcon type="error" title={draftError} />}
      {keepStrategy === 'ANY' && orderBy.length > 0 && (
        <Alert
          showIcon
          type="error"
          title={`任意保留策略不能使用现有 ${orderBy.length} 条排序规则`}
          action={(
            <Button size="small" danger onClick={() => updateOrder([])}>
              清空排序
            </Button>
          )}
        />
      )}
      {keepStrategy !== 'ANY' && (
        <section className="canvas-deduplicate-section">
          <div className="canvas-deduplicate-heading">
            <Typography.Text strong>排序规则</Typography.Text>
            <Button
              size="small"
              icon={<PlusOutlined />}
              onClick={() => updateOrder([...orderBy, {
                columnName: sourceColumns.find(
                  (column) => !orderBy.some((sort) => sort.columnName === column.name),
                )?.name ?? '',
                direction: 'DESC',
                nullOrdering: 'LAST',
              }])}
            >
              添加
            </Button>
          </div>
          <div className="canvas-deduplicate-order-list">
            {orderBy.length === 0 && (
              <Typography.Text type="secondary">至少添加一条排序规则。</Typography.Text>
            )}
            {orderBy.map((sortField, index) => {
              const missing = Boolean(
                sortField.columnName
                && sourceTable
                && !sourceColumnNames.has(sortField.columnName),
              );
              return (
                <div
                  className={`canvas-deduplicate-order-item${missing ? ' is-invalid' : ''}`}
                  key={index}
                >
                  <div className="canvas-deduplicate-order-heading">
                    <Tag color="purple">排序 {index + 1}</Tag>
                    <Space size={0}>
                      <Button
                        type="text"
                        size="small"
                        icon={<UpOutlined />}
                        aria-label={`上移排序规则 ${index + 1}`}
                        disabled={index === 0}
                        onClick={() => updateOrder(move(orderBy, index, index - 1))}
                      />
                      <Button
                        type="text"
                        size="small"
                        icon={<DownOutlined />}
                        aria-label={`下移排序规则 ${index + 1}`}
                        disabled={index === orderBy.length - 1}
                        onClick={() => updateOrder(move(orderBy, index, index + 1))}
                      />
                      <Button
                        type="text"
                        size="small"
                        danger
                        icon={<DeleteOutlined />}
                        aria-label={`删除排序规则 ${index + 1}`}
                        onClick={() => updateOrder(
                          orderBy.filter((_, itemIndex) => itemIndex !== index),
                        )}
                      />
                    </Space>
                  </div>
                  <Select
                    showSearch
                    optionFilterProp="label"
                    value={sortField.columnName || undefined}
                    status={missing ? 'error' : undefined}
                    placeholder="排序字段"
                    options={[
                      ...(missing ? [{
                        value: sortField.columnName,
                        label: `${sortField.columnName}（已失效）`,
                        disabled: true,
                      }] : []),
                      ...sourceColumns.map((column) => ({
                        value: column.name,
                        label: `${column.name} · ${column.fieldType}`,
                      })),
                    ]}
                    onChange={(columnName: string) => {
                      const next = [...orderBy];
                      next[index] = { ...sortField, columnName };
                      updateOrder(next);
                    }}
                  />
                  <div className="canvas-deduplicate-order-options">
                    <Select
                      value={sortField.direction}
                      options={[
                        { value: 'ASC', label: '升序 ASC' },
                        { value: 'DESC', label: '降序 DESC' },
                      ]}
                      onChange={(direction: SortDirection) => {
                        const next = [...orderBy];
                        next[index] = { ...sortField, direction };
                        updateOrder(next);
                      }}
                    />
                    <Select
                      value={sortField.nullOrdering}
                      options={[
                        { value: 'FIRST', label: 'NULL 在前' },
                        { value: 'LAST', label: 'NULL 在后' },
                      ]}
                      onChange={(nullOrdering: NullOrdering) => {
                        const next = [...orderBy];
                        next[index] = { ...sortField, nullOrdering };
                        updateOrder(next);
                      }}
                    />
                  </div>
                  {missing && (
                    <Typography.Text type="danger">
                      排序字段已失效，原配置已保留。
                    </Typography.Text>
                  )}
                </div>
              );
            })}
          </div>
        </section>
      )}
    </Space>
  );
};
