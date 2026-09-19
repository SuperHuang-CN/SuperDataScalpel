import { CodeOutlined, SearchOutlined, TableOutlined } from '@ant-design/icons';
import { Button, Empty, Form, Input, Modal, Space, Tag, Tooltip, Typography } from 'antd';
import { useCallback, useImperativeHandle, useRef, useState } from 'react';
import {
  MonacoSqlEditor,
  type MonacoSqlEditorHandle,
} from '../../../../../shared/components/MonacoSqlEditor';
import { configurationFingerprint } from '../../components/CanvasInspectorUtils';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import {
  CanvasNodeType,
  type CanvasColumnSchema,
  type CanvasTableSchema,
  type SqlTransformConfiguration,
} from '../../canvasTypes';
import type {
  CanvasNodeInspectorComponentProps,
  CanvasNodeInspectorHandle,
} from '../nodeSpec';

type Props = CanvasNodeInspectorComponentProps<typeof CanvasNodeType.SqlTransform>;

const quoteIdentifier = (value: string) => `\`${value.replaceAll('`', '``')}\``;

const fieldLabel = (field: CanvasColumnSchema) => (
  field.comment ? `${field.name} · ${field.comment}` : field.name
);

const referencesTable = (sql: string, tableName: string) => (
  sql.includes(quoteIdentifier(tableName))
    || new RegExp(`(^|[^A-Za-z0-9_$])${tableName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}(?=$|[^A-Za-z0-9_$])`, 'u')
      .test(sql)
);

const normalizeConfiguration = (
  values: Partial<SqlTransformConfiguration>,
): SqlTransformConfiguration => ({
  outputTableName: values.outputTableName ?? '',
  sql: values.sql ?? '',
});

const SqlReferenceTable = ({
  table,
  onInsert,
}: {
  table: CanvasTableSchema;
  onInsert: (reference: string) => void;
}) => (
  <div className="canvas-sql-transform-reference-table">
    <Button
      type="text"
      size="small"
      className="canvas-sql-transform-reference-table-name"
      icon={<TableOutlined />}
      onClick={() => onInsert(quoteIdentifier(table.name))}
      aria-label={`插入表 ${table.name}`}
    >
      <Typography.Text ellipsis>{table.name}</Typography.Text>
      <Tag>{table.columns.length}</Tag>
    </Button>
    <div className="canvas-sql-transform-reference-fields">
      {table.columns.map((field) => (
        <Tooltip key={field.name} title={fieldLabel(field)} placement="right">
          <Button
            type="text"
            size="small"
            className="canvas-sql-transform-reference-field"
            onClick={() => onInsert(`${quoteIdentifier(table.name)}.${quoteIdentifier(field.name)}`)}
            aria-label={`插入字段 ${table.name}.${field.name}`}
          >
            <span className="canvas-sql-transform-reference-field-code">{field.name}</span>
            <span className="canvas-sql-transform-reference-field-type">{field.fieldType}</span>
          </Button>
        </Tooltip>
      ))}
    </div>
  </div>
);

const SqlTransformInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: Props) => {
  const [form] = Form.useForm<SqlTransformConfiguration>();
  const editorRef = useRef<MonacoSqlEditorHandle>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [sqlAtOpen, setSqlAtOpen] = useState('');
  const [referenceSearch, setReferenceSearch] = useState('');
  const sql = Form.useWatch('sql', form) ?? '';
  const outputTableName = Form.useWatch('outputTableName', form) ?? '';
  const inputTables = validation?.inputTables ?? [];
  const normalizedSearch = referenceSearch.trim().toLocaleLowerCase();
  const visibleTables = inputTables.filter((table) => (
    !normalizedSearch
    || table.name.toLocaleLowerCase().includes(normalizedSearch)
    || table.columns.some((column) => column.name.toLocaleLowerCase().includes(normalizedSearch)
      || column.comment?.toLocaleLowerCase().includes(normalizedSearch))
  ));
  const referencedTableCount = inputTables.filter((table) => referencesTable(sql, table.name)).length;
  const output = validation?.outputTables.find((table) => table.name === outputTableName);

  const markDirty = (values: Partial<SqlTransformConfiguration>) => {
    onDirtyChange(configurationFingerprint(normalizeConfiguration(values))
      !== configurationFingerprint(node.configuration));
  };

  const apply = useCallback(() => {
    const values = form.getFieldsValue(true);
    void form.validateFields().catch(() => undefined);
    onApply({ id: node.id, type: node.type, configuration: normalizeConfiguration(values) });
    onDirtyChange(false);
  }, [form, node.id, node.type, onApply, onDirtyChange]);

  useImperativeHandle<CanvasNodeInspectorHandle, CanvasNodeInspectorHandle>(inspectorRef, () => ({
    apply: async () => {
      apply();
      return true;
    },
  }), [apply]);

  const openEditor = () => {
    setSqlAtOpen(form.getFieldValue('sql') ?? '');
    setEditorOpen(true);
  };

  const closeAndSaveSql = () => {
    markDirty(form.getFieldsValue(true));
    setEditorOpen(false);
  };

  const cancelSql = () => {
    form.setFieldValue('sql', sqlAtOpen);
    markDirty({ ...form.getFieldsValue(true), sql: sqlAtOpen });
    setEditorOpen(false);
  };

  return (
    <div className="canvas-inspector-content">
      <Space orientation="vertical" size={12} style={{ width: '100%' }}>
        <CanvasNodeValidationIssues
          validation={validation}
          unavailableMessage={validationUnavailableMessage}
        />
        <Form<SqlTransformConfiguration>
          autoComplete="off"
          form={form}
          layout="vertical"
          initialValues={node.configuration}
          onValuesChange={(_changed, values) => markDirty(values)}
        >
          <Form.Item
            name="outputTableName"
            label="输出逻辑表名"
            rules={[{ required: true, message: '请输入输出逻辑表名' }]}
          >
            <Input maxLength={128} placeholder="例如 order_summary" />
          </Form.Item>
          <div className="canvas-sql-transform-inspector-status">
            <Space size={[6, 6]} wrap>
              <Tag color={sql.trim() ? 'success' : 'default'}>{sql.trim() ? 'SQL 已配置' : '待配置 SQL'}</Tag>
              <Tag>{referencedTableCount} 张引用表</Tag>
              <Tag>{output?.columns.length ?? 0} 个输出字段</Tag>
            </Space>
            <Button type="primary" icon={<CodeOutlined />} onClick={openEditor}>
              配置 SQL
            </Button>
          </div>
        </Form>
        <Typography.Text type="secondary" className="canvas-sql-transform-inspector-tip">
          仅支持一条 SELECT 或 WITH … SELECT 查询；可引用当前上游表，不会访问外部 Catalog。
        </Typography.Text>
      </Space>

      <Modal
        open={editorOpen}
        width={1120}
        destroyOnHidden
        className="canvas-sql-transform-modal"
        title="配置 SQL 处理"
        okText="保存 SQL"
        cancelText="取消"
        onOk={closeAndSaveSql}
        onCancel={cancelSql}
        styles={{ body: { height: 'min(680px, calc(100vh - 200px))', overflow: 'hidden' } }}
      >
        <div className="canvas-sql-transform-modal-layout">
          <aside className="canvas-sql-transform-reference-pane">
            <Input
              allowClear
              prefix={<SearchOutlined />}
              placeholder="搜索上游表或字段"
              value={referenceSearch}
              onChange={(event) => setReferenceSearch(event.target.value)}
            />
            <div className="canvas-sql-transform-reference-heading">
              <Typography.Text strong>上游表与字段</Typography.Text>
              <Typography.Text type="secondary">{visibleTables.length} / {inputTables.length}</Typography.Text>
            </div>
            <div className="canvas-sql-transform-reference-list">
              {visibleTables.length === 0 ? (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有可插入的上游表" />
              ) : visibleTables.map((table) => (
                <SqlReferenceTable
                  key={table.name}
                  table={table}
                  onInsert={(reference) => editorRef.current?.insertText(reference)}
                />
              ))}
            </div>
          </aside>
          <section className="canvas-sql-transform-editor-pane">
            <div className="canvas-sql-transform-editor-heading">
              <div>
                <Typography.Text strong>只读 Spark SQL</Typography.Text>
                <Typography.Text type="secondary"> · 点击左侧表或字段插入当前光标</Typography.Text>
              </div>
              <Typography.Text type="secondary">{sql.length} / 100000</Typography.Text>
            </div>
            <Form.Item
              name="sql"
              style={{ flex: 1, minHeight: 0, margin: 0 }}
              rules={[
                { required: true, message: '请输入 SELECT 查询' },
                { max: 100_000, message: 'SQL 不能超过 100000 个字符' },
              ]}
            >
              <MonacoSqlEditor ref={editorRef} height="100%" onChange={() => undefined} />
            </Form.Item>
          </section>
        </div>
      </Modal>
    </div>
  );
};

export default SqlTransformInspector;
