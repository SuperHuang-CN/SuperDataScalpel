import {
  DeleteOutlined,
  DownOutlined,
  EyeOutlined,
  PlusOutlined,
  SettingOutlined,
  TableOutlined,
  UpOutlined,
} from '@ant-design/icons';
import { Alert, Badge, Button, Form, Popconfirm, Space, Spin, Tag, Tooltip, Typography } from 'antd';
import { useImperativeHandle, useState } from 'react';
import {
  useDataSource,
  useTableMetadata,
  type DataSource,
  type TableMetadata,
} from '../../../../datasource';
import { CanvasJdbcDataSourceSelect } from '../../components/CanvasJdbcSelectors';
import { configurationFingerprint } from '../../components/CanvasInspectorUtils';
import { FieldPreview } from '../../components/CanvasLegacyInspectors';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import {
  CanvasNodeType,
  type CanvasColumnSchema,
  type JdbcInputConfiguration,
  type JdbcInputReadOption,
  type JdbcInputTableSelection,
} from '../../canvasTypes';
import type { CanvasNodeInspectorComponentProps, CanvasNodeInspectorHandle } from '../nodeSpec';
import { JdbcInputTablePickerModal } from './JdbcInputTablePickerModal';
import { JdbcInputTableReadOptionsModal } from './JdbcInputTableReadOptionsModal';

interface JdbcInputFormValues {
  dataSourceId: string;
}

const dataSourceAvailable = (dataSource: DataSource | undefined) => dataSource !== undefined
  && dataSource.enabled
  && dataSource.connectionKind === 'JDBC'
  && dataSource.purposes.includes('SOURCE');

const canvasColumns = (metadata: TableMetadata | undefined): CanvasColumnSchema[] => (
  metadata?.columns.flatMap((column): CanvasColumnSchema[] => column.platformTypeDefinition ? [{
    name: column.name,
    fieldType: column.platformTypeDefinition.type,
    length: column.platformTypeDefinition.length,
    precision: column.platformTypeDefinition.precision,
    scale: column.platformTypeDefinition.scale,
    nullable: column.nullable,
    defaultValue: column.defaultValue,
    autoIncrement: column.autoIncrement,
    generated: column.generated,
    comment: column.comment,
    geometry: column.platformTypeDefinition.geometry ?? null,
  }] : []) ?? []
);

interface SelectedTableConfigurationProps {
  dataSourceId: string;
  table: JdbcInputTableSelection;
  index: number;
  total: number;
  onConfigure: () => void;
  onRemove: () => void;
  onMove: (direction: -1 | 1) => void;
}

const SelectedTableConfiguration = ({
  dataSourceId,
  table,
  index,
  total,
  onConfigure,
  onRemove,
  onMove,
}: SelectedTableConfigurationProps) => {
  const [expanded, setExpanded] = useState(false);
  const tableQuery = useTableMetadata(
    dataSourceId || undefined,
    dataSourceId && table.tableName
      ? { catalog: null, schema: null, table: table.tableName }
      : undefined,
    Boolean(dataSourceId && table.tableName),
  );
  const columns = canvasColumns(tableQuery.data);
  const primaryKeyColumns = tableQuery.data?.uniqueKeys?.find(
    (key) => key.type === 'PRIMARY_KEY',
  )?.columns ?? [];

  return (
    <div className={`canvas-jdbc-input-selected-table${tableQuery.isError ? ' is-invalid' : ''}`}>
      <div className="canvas-jdbc-input-selected-table-main">
        <span className="canvas-jdbc-input-selected-table-index">{index + 1}</span>
        <TableOutlined className="canvas-jdbc-input-selected-table-icon" />
        <div className="canvas-jdbc-input-selected-table-identity">
          <Typography.Text ellipsis title={table.tableName}>{table.tableName}</Typography.Text>
          <div className="canvas-jdbc-input-selected-table-meta">
            {tableQuery.isFetching && !tableQuery.data ? <><Spin size="small" /> 正在读取字段</> : null}
            {tableQuery.data && <Tag>{columns.length} 个字段</Tag>}
            {primaryKeyColumns.length > 0 && <Tag color="blue">{primaryKeyColumns.length} 个主键字段</Tag>}
            {table.readOptions.length > 0 && <Tag color="cyan">读取参数 {table.readOptions.length} 项</Tag>}
            {tableQuery.isError && <Tag color="error">物理表不存在或元数据读取失败</Tag>}
          </div>
        </div>
        <Space size={0} className="canvas-jdbc-input-selected-table-actions">
          <Tooltip title="上移">
            <Button type="text" size="small" disabled={index === 0} icon={<UpOutlined />} aria-label={`上移 ${table.tableName}`} onClick={() => onMove(-1)} />
          </Tooltip>
          <Tooltip title="下移">
            <Button type="text" size="small" disabled={index === total - 1} icon={<DownOutlined />} aria-label={`下移 ${table.tableName}`} onClick={() => onMove(1)} />
          </Tooltip>
          <Tooltip title={expanded ? '收起字段' : '查看字段'}>
            <Button type="text" size="small" icon={<EyeOutlined />} aria-label={`查看 ${table.tableName} 字段`} onClick={() => setExpanded((current) => !current)} />
          </Tooltip>
          <Tooltip title="配置读取参数">
            <Badge dot={table.readOptions.length > 0} color="#1677ff" offset={[-1, 2]}>
              <Button type="text" size="small" icon={<SettingOutlined />} aria-label={`配置 ${table.tableName} 的读取参数`} onClick={onConfigure} />
            </Badge>
          </Tooltip>
          {table.readOptions.length > 0 ? (
            <Popconfirm
              title="移除物理表？"
              description={`同时删除 ${table.readOptions.length} 项读取参数。`}
              okText="移除"
              cancelText="取消"
              okButtonProps={{ danger: true }}
              onConfirm={onRemove}
            >
              <Tooltip title="移除"><Button type="text" size="small" danger icon={<DeleteOutlined />} aria-label={`移除 ${table.tableName}`} /></Tooltip>
            </Popconfirm>
          ) : (
            <Tooltip title="移除">
              <Button type="text" size="small" danger icon={<DeleteOutlined />} aria-label={`移除 ${table.tableName}`} onClick={onRemove} />
            </Tooltip>
          )}
        </Space>
      </div>
      {expanded && (
        <div className="canvas-jdbc-input-selected-table-fields">
          {tableQuery.isError ? (
            <Alert
              showIcon
              type="error"
              title="无法读取该物理表的字段信息"
              action={<Button type="link" size="small" onClick={() => void tableQuery.refetch()}>重试</Button>}
            />
          ) : (
            <FieldPreview columns={columns} loading={tableQuery.isFetching} />
          )}
        </div>
      )}
    </div>
  );
};

const JdbcInputInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.JdbcInput>) => {
  const [form] = Form.useForm<JdbcInputFormValues>();
  const [tables, setTables] = useState<JdbcInputTableSelection[]>(
    node.configuration.tables.map((table) => ({ ...table, readOptions: table.readOptions.map((option) => ({ ...option })) })),
  );
  const [pickerOpen, setPickerOpen] = useState(false);
  const [readOptionsTableIndex, setReadOptionsTableIndex] = useState<number | null>(null);
  const dataSourceId = Form.useWatch('dataSourceId', form) ?? '';
  const dataSourceQuery = useDataSource(dataSourceId || undefined, Boolean(dataSourceId));
  const selectedDataSourceAvailable = dataSourceQuery.data
    ? dataSourceAvailable(dataSourceQuery.data)
    : dataSourceQuery.isError ? false : undefined;

  const configuration = (
    values: Partial<JdbcInputFormValues>,
    selectedTables: JdbcInputTableSelection[],
  ): JdbcInputConfiguration => ({
    dataSourceId: values.dataSourceId ?? '',
    tables: selectedTables.map((table) => ({
      ...table,
      readOptions: table.readOptions.map((option) => ({ ...option })),
    })),
  });

  const markDirty = (
    values: Partial<JdbcInputFormValues>,
    selectedTables: JdbcInputTableSelection[],
  ) => {
    onDirtyChange(
      configurationFingerprint(configuration(values, selectedTables))
      !== configurationFingerprint(node.configuration),
    );
  };

  const updateTables = (next: JdbcInputTableSelection[]) => {
    setTables(next);
    markDirty(form.getFieldsValue(true), next);
  };

  const submit = () => {
    onApply({
      id: node.id,
      type: node.type,
      configuration: configuration(form.getFieldsValue(true), tables),
    });
    onDirtyChange(false);
  };

  useImperativeHandle<CanvasNodeInspectorHandle, CanvasNodeInspectorHandle>(inspectorRef, () => ({
    apply: async () => {
      const values = form.getFieldsValue(true);
      void form.validateFields().catch(() => undefined);
      if (values.dataSourceId && selectedDataSourceAvailable !== true) {
        form.setFields([{
          name: 'dataSourceId',
          errors: [dataSourceQuery.isFetching
            ? '正在读取数据源信息，请稍候'
            : '数据源不存在、已停用或不具有 SOURCE 用途'],
        }]);
      }
      submit();
      return true;
    },
  }));

  const moveTable = (index: number, direction: -1 | 1) => {
    const target = index + direction;
    if (target < 0 || target >= tables.length) return;
    const next = [...tables];
    [next[index], next[target]] = [next[target], next[index]];
    updateTables(next);
  };

  const updateReadOptions = (index: number, readOptions: JdbcInputReadOption[]) => {
    updateTables(tables.map((table, tableIndex) => (
      tableIndex === index ? { ...table, readOptions } : table
    )));
  };
  const configuredTable = readOptionsTableIndex === null ? null : tables[readOptionsTableIndex] ?? null;

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      <CanvasNodeValidationIssues
        validation={validation}
        unavailableMessage={validationUnavailableMessage}
      />
      <Form<JdbcInputFormValues>
        autoComplete="off"
        form={form}
        layout="vertical"
        initialValues={{ dataSourceId: node.configuration.dataSourceId }}
        onFinish={submit}
        onValuesChange={(_changed, values) => markDirty(values, tables)}
      >
        <Form.Item
          name="dataSourceId"
          label="来源数据源"
          rules={[{ required: true, message: '请选择来源数据源' }]}
        >
          <CanvasJdbcDataSourceSelect
            purpose="SOURCE"
            placeholder="选择 JDBC SOURCE 数据源"
          />
        </Form.Item>
      </Form>

      {dataSourceId && dataSourceQuery.isError && (
        <Alert
          showIcon
          type="error"
          title="读取数据源信息失败"
          action={<Button type="link" size="small" onClick={() => void dataSourceQuery.refetch()}>重试</Button>}
        />
      )}

      <div className="canvas-jdbc-input-table-section">
        <div className="canvas-jdbc-input-table-section-heading">
          <div>
            <Typography.Text strong>已选物理表</Typography.Text>
            <Tag color={tables.length > 0 ? 'blue' : 'default'}>{tables.length}</Tag>
          </div>
          <Button
            size="small"
            icon={<PlusOutlined />}
            disabled={!dataSourceId}
            onClick={() => setPickerOpen(true)}
          >
            管理物理表
          </Button>
        </div>
        <Typography.Text type="secondary" className="canvas-jdbc-input-table-section-tip">
          每张物理表会独立产生同名 Canvas 表；列表顺序会保存在任务定义中。
        </Typography.Text>
        {tables.length === 0 ? (
          <button
            type="button"
            className="canvas-jdbc-input-empty-selection"
            disabled={!dataSourceId}
            onClick={() => setPickerOpen(true)}
          >
            <TableOutlined />
            <span>{dataSourceId ? '选择一张或多张物理表' : '请先选择来源数据源'}</span>
          </button>
        ) : (
          <div className="canvas-jdbc-input-selected-tables">
            {tables.map((table, index) => (
              <SelectedTableConfiguration
                key={`${table.tableName}-${index}`}
                dataSourceId={dataSourceId}
                table={table}
                index={index}
                total={tables.length}
                onConfigure={() => setReadOptionsTableIndex(index)}
                onMove={(direction) => moveTable(index, direction)}
                onRemove={() => updateTables(tables.filter((_item, itemIndex) => itemIndex !== index))}
              />
            ))}
          </div>
        )}
      </div>

      <JdbcInputTablePickerModal
        open={pickerOpen}
        dataSourceId={dataSourceId}
        value={tables}
        onCancel={() => setPickerOpen(false)}
        onConfirm={(next) => {
          updateTables(next);
          setPickerOpen(false);
        }}
      />
      {configuredTable && readOptionsTableIndex !== null && (
        <JdbcInputTableReadOptionsModal
          open
          tableName={configuredTable.tableName}
          value={configuredTable.readOptions}
          onCancel={() => setReadOptionsTableIndex(null)}
          onConfirm={(readOptions) => {
            updateReadOptions(readOptionsTableIndex, readOptions);
            setReadOptionsTableIndex(null);
          }}
        />
      )}
    </Space>
  );
};

export default JdbcInputInspector;
