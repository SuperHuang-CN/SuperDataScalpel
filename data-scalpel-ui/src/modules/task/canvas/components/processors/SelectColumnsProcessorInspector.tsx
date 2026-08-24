import {
  DeleteOutlined,
  DownOutlined,
  HolderOutlined,
  PlusOutlined,
  UpOutlined,
} from '@ant-design/icons';
import {
  Alert,
  Button,
  Empty,
  Form,
  Input,
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
  type CanvasColumnSchema,
  type CanvasNodeConfigurationUpdate,
  type CanvasNodeDefinition,
  type CanvasNodeValidationResult,
  type SelectColumnsConfiguration,
} from '../../canvasTypes';
import type { CanvasNodeInspectorHandle } from '../CanvasNodeInspector';
import { ProcessorValidationIssues } from './ProcessorValidationIssues';

interface SelectColumnsProcessorInspectorProps {
  node: Extract<CanvasNodeDefinition, { type: 'SELECT_COLUMNS' }>;
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage: string | null;
  onApply: (update: CanvasNodeConfigurationUpdate) => void;
  onDirtyChange: (dirty: boolean) => void;
  inspectorRef: Ref<CanvasNodeInspectorHandle>;
}

interface SelectColumnsFormValues {
  sourceTableName: string;
  outputTableName: string;
}

const fieldDescription = (column: CanvasColumnSchema) => (
  `${column.fieldType} · ${column.nullable ? '可空' : '必填'}`
);

export const SelectColumnsProcessorInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: SelectColumnsProcessorInspectorProps) => {
  const [form] = Form.useForm<SelectColumnsFormValues>();
  const [columns, setColumns] = useState<string[]>(() => [...node.configuration.columns]);
  const [searchText, setSearchText] = useState('');
  const [draggedIndex, setDraggedIndex] = useState<number | null>(null);
  const [selectionError, setSelectionError] = useState<string | null>(null);
  const sourceTableName = Form.useWatch('sourceTableName', form)
    ?? node.configuration.sourceTableName;
  const inputTables = useMemo(() => validation?.inputTables ?? [], [validation?.inputTables]);
  const sourceTable = inputTables.find((table) => table.name === sourceTableName);
  const sourceTableMissing = Boolean(sourceTableName && validation && !sourceTable);
  const sourceColumns = useMemo(() => sourceTable?.columns ?? [], [sourceTable?.columns]);
  const sourceColumnIndex = useMemo(
    () => new Map(sourceColumns.map((column) => [column.name, column])),
    [sourceColumns],
  );
  const normalizedSearch = searchText.trim().toLocaleLowerCase();
  const availableColumns = sourceColumns.filter((column) => (
    !columns.includes(column.name)
    && (!normalizedSearch
      || column.name.toLocaleLowerCase().includes(normalizedSearch)
      || column.fieldType.toLocaleLowerCase().includes(normalizedSearch))
  ));
  const duplicateNames = useMemo(() => {
    const seen = new Set<string>();
    const duplicates = new Set<string>();
    columns.forEach((columnName) => {
      if (!seen.add(columnName)) duplicates.add(columnName);
    });
    return duplicates;
  }, [columns]);
  const tableOptions = useMemo(() => [
    ...(sourceTableMissing
      ? [{ value: sourceTableName, label: `${sourceTableName}（已失效）`, disabled: true }]
      : []),
    ...inputTables.map((table) => ({
      value: table.name,
      label: `${table.name} · ${table.columns.length} 字段`,
    })),
  ], [inputTables, sourceTableMissing, sourceTableName]);

  const updateColumns = (nextColumns: string[]) => {
    setColumns(nextColumns);
    setSelectionError(nextColumns.length === 0 ? '至少选择一个字段' : null);
    onDirtyChange(true);
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      try {
        const values = form.getFieldsValue(true);
        void form.validateFields().catch(() => undefined);
        if (columns.length === 0) {
          setSelectionError('至少选择一个字段');

        }
        if (duplicateNames.size > 0) {
          setSelectionError(`字段被重复选择：${[...duplicateNames].join('、')}`);

        }
        const configuration: SelectColumnsConfiguration = {
          sourceTableName: values.sourceTableName ?? '',
          outputTableName: (values.outputTableName ?? '').trim(),
          columns: [...columns],
        };
        onApply({
          id: node.id,
          type: CanvasNodeType.SelectColumns,
          configuration,
        });
        onDirtyChange(false);
        return true;
      } catch {
        return false;
      }
    },
  }), [columns, duplicateNames, form, node.id, onApply, onDirtyChange]);

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      <ProcessorValidationIssues
        validation={validation}
        unavailableMessage={validationUnavailableMessage}
      />
      <Form<SelectColumnsFormValues>
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
          help={sourceTableMissing ? '原来源表已不在当前上游数据中，配置已保留。' : undefined}
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
          <Input placeholder="例如 order_summary" />
        </Form.Item>
      </Form>

      <div className="canvas-select-columns-toolbar">
        <Typography.Text strong>字段选择与排序</Typography.Text>
        <Space size={4}>
          <Button
            type="link"
            size="small"
            disabled={sourceColumns.length === 0}
            onClick={() => updateColumns(sourceColumns.map((column) => column.name))}
          >
            全选
          </Button>
          <Button
            type="link"
            size="small"
            danger
            disabled={columns.length === 0}
            onClick={() => updateColumns([])}
          >
            清空
          </Button>
        </Space>
      </div>
      {selectionError && <Alert showIcon type="error" title={selectionError} />}

      <div className="canvas-select-columns-panel">
        <div className="canvas-select-columns-section is-available">
          <div className="canvas-select-columns-section-heading">
            <Typography.Text strong>可用字段</Typography.Text>
            <Tag>{availableColumns.length}</Tag>
          </div>
          <Input.Search
            allowClear
            autoComplete="off"
            value={searchText}
            placeholder="搜索名称或类型"
            onChange={(event) => setSearchText(event.target.value)}
          />
          <div className="canvas-select-columns-list">
            {availableColumns.length === 0 ? (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={sourceTable ? '没有可添加字段' : '请先选择来源表'}
              />
            ) : availableColumns.map((column) => (
              <div className="canvas-select-column-row" key={column.name}>
                <div className="canvas-select-column-meta">
                  <Typography.Text ellipsis>{column.name}</Typography.Text>
                  <Typography.Text type="secondary">
                    {fieldDescription(column)}
                  </Typography.Text>
                </div>
                <Button
                  type="text"
                  size="small"
                  icon={<PlusOutlined />}
                  aria-label={`添加字段 ${column.name}`}
                  onClick={() => updateColumns([...columns, column.name])}
                />
              </div>
            ))}
          </div>
        </div>

        <div className="canvas-select-columns-section is-selected">
          <div className="canvas-select-columns-section-heading">
            <Typography.Text strong>已选字段</Typography.Text>
            <Tag color={columns.length > 0 ? 'blue' : 'error'}>{columns.length}</Tag>
          </div>
          <div className="canvas-select-columns-list canvas-select-columns-selected">
            {columns.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="至少选择一个字段" />
            ) : columns.map((columnName, index) => {
              const column = sourceColumnIndex.get(columnName);
              const invalid = !column || duplicateNames.has(columnName);
              return (
                <div
                  className={[
                    'canvas-select-column-row',
                    invalid ? 'is-invalid' : '',
                    draggedIndex === index ? 'is-dragging' : '',
                  ].filter(Boolean).join(' ')}
                  key={`${columnName}-${index}`}
                  draggable
                  onDragStart={(event) => {
                    event.dataTransfer.effectAllowed = 'move';
                    setDraggedIndex(index);
                  }}
                  onDragOver={(event) => {
                    event.preventDefault();
                    event.dataTransfer.dropEffect = 'move';
                  }}
                  onDrop={(event) => {
                    event.preventDefault();
                    if (draggedIndex === null || draggedIndex === index) return;
                    const nextColumns = [...columns];
                    const [moved] = nextColumns.splice(draggedIndex, 1);
                    nextColumns.splice(index, 0, moved);
                    updateColumns(nextColumns);
                    setDraggedIndex(null);
                  }}
                  onDragEnd={() => setDraggedIndex(null)}
                >
                  <HolderOutlined className="canvas-select-column-drag-handle" />
                  <div className="canvas-select-column-meta">
                    <Typography.Text ellipsis type={invalid ? 'danger' : undefined}>
                      {columnName}
                    </Typography.Text>
                    <Typography.Text type={invalid ? 'danger' : 'secondary'}>
                      {column
                        ? fieldDescription(column)
                        : '字段已不在当前来源表中，原值已保留'}
                    </Typography.Text>
                  </div>
                  <Space size={0}>
                    <Button
                      type="text"
                      size="small"
                      icon={<UpOutlined />}
                      aria-label={`上移字段 ${columnName}`}
                      disabled={index === 0}
                      onClick={() => {
                        const nextColumns = [...columns];
                        [nextColumns[index - 1], nextColumns[index]] = [
                          nextColumns[index],
                          nextColumns[index - 1],
                        ];
                        updateColumns(nextColumns);
                      }}
                    />
                    <Button
                      type="text"
                      size="small"
                      icon={<DownOutlined />}
                      aria-label={`下移字段 ${columnName}`}
                      disabled={index === columns.length - 1}
                      onClick={() => {
                        const nextColumns = [...columns];
                        [nextColumns[index], nextColumns[index + 1]] = [
                          nextColumns[index + 1],
                          nextColumns[index],
                        ];
                        updateColumns(nextColumns);
                      }}
                    />
                    <Button
                      type="text"
                      size="small"
                      danger
                      icon={<DeleteOutlined />}
                      aria-label={`移除字段 ${columnName}`}
                      onClick={() => updateColumns(
                        columns.filter((_, columnIndex) => columnIndex !== index),
                      )}
                    />
                  </Space>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </Space>
  );
};
