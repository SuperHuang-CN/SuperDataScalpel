import { SearchOutlined,UndoOutlined } from '@ant-design/icons';
import { Button,Checkbox,Form,Input,Select,Space,Table,Tooltip,Typography,type TableColumnsType } from 'antd';
import { useImperativeHandle,useMemo,useState,type Ref } from 'react';
import { platformTypeLabel } from '../../canvasSchema';
import {
CanvasNodeType,type CanvasColumnSchema,type CanvasExecutionMode,type CanvasNodeConfigurationUpdate,
type CanvasNodeDefinition,type CanvasNodeValidationResult,
type JdbcColumnMapping,type RenameConfiguration
} from '../../canvasTypes';
import { configurationFingerprint,focusFirstInvalidField } from '../../components/CanvasInspectorUtils';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import { adaptCanvasNodeInspector } from '../inspectorAdapter';

interface CanvasNodeInspectorProps {
  node: CanvasNodeDefinition | null;
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage?: string | null;
  executionMode?: CanvasExecutionMode;
  onApply: (update: CanvasNodeConfigurationUpdate) => void;
  onDirtyChange: (dirty: boolean) => void;
}


export interface CanvasNodeInspectorHandle {
  apply: () => Promise<boolean>;
}


interface RenameFormValues {
  sourceTableName: string;
  outputTableName: string;
}


interface RenameFieldRow {
  key: string;
  sourceColumnName: string;
  targetColumnName: string;
  column: CanvasColumnSchema | null;
  changed: boolean;
  issue: string | null;
}


export const RenameInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: {
  node: Extract<CanvasNodeDefinition, { type: 'RENAME' }>;
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage: string | null;
  onApply: CanvasNodeInspectorProps['onApply'];
  onDirtyChange: CanvasNodeInspectorProps['onDirtyChange'];
  inspectorRef: Ref<CanvasNodeInspectorHandle>;
}) => {
  const [form] = Form.useForm<RenameFormValues>();
  const [search, setSearch] = useState('');
  const [changedOnly, setChangedOnly] = useState(false);
  const [mappingTargets, setMappingTargets] = useState<Record<string, string>>(() => Object.fromEntries(
    node.configuration.columnMappings.map((mapping) => [
      mapping.sourceColumnName,
      mapping.targetColumnName,
    ]),
  ));
  const sourceName = Form.useWatch('sourceTableName', form) ?? '';
  const source = validation?.inputTables.find((table) => table.name === sourceName);
  const tableOptions = (validation?.inputTables ?? [])
    .map((table) => ({ value: table.name, label: table.name }));

  const rows = useMemo<RenameFieldRow[]>(() => {
    const sourceColumns = source?.columns ?? [];
    const sourceColumnMap = new Map(sourceColumns.map((column) => [column.name, column]));
    const sourceColumnNames = [
      ...sourceColumns.map((column) => column.name),
      ...Object.keys(mappingTargets).filter((name) => !sourceColumnMap.has(name)),
    ];
    const targetNames = sourceColumnNames.map((name) => mappingTargets[name] ?? name);
    const targetCounts = targetNames.reduce((counts, name) => {
      if (name) counts.set(name, (counts.get(name) ?? 0) + 1);
      return counts;
    }, new Map<string, number>());
    return sourceColumnNames.map((sourceColumnName) => {
      const targetColumnName = mappingTargets[sourceColumnName] ?? sourceColumnName;
      const column = sourceColumnMap.get(sourceColumnName) ?? null;
      const issue = !column
        ? '来源字段已失效，可恢复原名以移除这条旧映射'
        : !targetColumnName.trim()
          ? '新字段名不能为空'
          : (targetCounts.get(targetColumnName) ?? 0) > 1
            ? `新字段名重复：${targetColumnName}`
            : null;
      return {
        key: sourceColumnName,
        sourceColumnName,
        targetColumnName,
        column,
        changed: targetColumnName !== sourceColumnName,
        issue,
      };
    });
  }, [mappingTargets, source?.columns]);

  const changedCount = rows.filter((row) => row.changed).length;
  const invalidCount = rows.filter((row) => row.issue !== null).length;
  const visibleRows = useMemo(() => {
    const keyword = search.trim().toLocaleLowerCase();
    return rows.filter((row) => {
      if (changedOnly && !row.changed) return false;
      if (!keyword) return true;
      return [row.sourceColumnName, row.targetColumnName, row.column?.comment]
        .some((value) => value?.toLocaleLowerCase().includes(keyword));
    });
  }, [changedOnly, rows, search]);

  const buildMappings = (): JdbcColumnMapping[] => rows
    .filter((row) => row.changed)
    .map((row) => ({
      sourceColumnName: row.sourceColumnName,
      targetColumnName: row.targetColumnName,
    }));

  const toConfiguration = (values: RenameFormValues): RenameConfiguration => ({
    sourceTableName: values.sourceTableName ?? '',
    outputTableName: values.outputTableName?.trim() ?? '',
    columnMappings: buildMappings(),
  });

  const submit = (values: RenameFormValues) => {
    onApply({ id: node.id, type: node.type, configuration: toConfiguration(values) });
    onDirtyChange(false);
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      try {
        void form.validateFields().catch(() => undefined);
        submit(form.getFieldsValue(true));
        return true;
      } catch (error) {
        focusFirstInvalidField(form, error);
        return false;
      }
    },
  }));

  const updateTargetName = (sourceColumnName: string, targetColumnName: string) => {
    setMappingTargets((current) => {
      const next = { ...current };
      if (targetColumnName === sourceColumnName) delete next[sourceColumnName];
      else next[sourceColumnName] = targetColumnName;
      return next;
    });
    onDirtyChange(true);
  };

  const columns: TableColumnsType<RenameFieldRow> = [
    {
      title: '描述',
      key: 'description',
      width: '50%',
      ellipsis: true,
      render: (_, row) => (
        row.column?.comment ? (
          <Typography.Text
            type="secondary"
            className="canvas-rename-source-comment"
            ellipsis={{ tooltip: row.column.comment }}
          >
            {row.column.comment}
          </Typography.Text>
        ) : <Typography.Text type="secondary">—</Typography.Text>
      ),
    },
    {
      title: '源字段名',
      key: 'sourceColumnName',
      width: '25%',
      ellipsis: true,
      render: (_, row) => (
        <Tooltip
          title={row.column
            ? `${platformTypeLabel(row.column)} · ${row.column.nullable ? '可空' : '非空'}`
            : '来源字段已失效'}
        >
          <Typography.Text className="canvas-rename-source-code" ellipsis>
            {row.sourceColumnName}
          </Typography.Text>
        </Tooltip>
      ),
    },
    {
      title: '新字段名',
      key: 'targetColumnName',
      width: '25%',
      render: (_, row) => (
        <Tooltip title={row.issue} open={row.issue ? undefined : false}>
          <Input
            size="small"
            status={row.issue ? 'error' : undefined}
            value={row.targetColumnName}
            aria-label={`重命名 ${row.sourceColumnName}`}
            onChange={(event) => updateTargetName(row.sourceColumnName, event.target.value)}
            suffix={row.changed ? (
              <Tooltip title="恢复原名">
                <Button
                  type="text"
                  size="small"
                  className="canvas-rename-reset-button"
                  icon={<UndoOutlined />}
                  aria-label={`恢复 ${row.sourceColumnName} 原名`}
                  onClick={() => updateTargetName(row.sourceColumnName, row.sourceColumnName)}
                />
              </Tooltip>
            ) : null}
          />
        </Tooltip>
      ),
    },
  ];

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      <CanvasNodeValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} />
      <Form<RenameFormValues> autoComplete="off"
        form={form}
        layout="vertical"
        initialValues={node.configuration}
        onFinish={submit}
        onValuesChange={(_changed, values) => {
          onDirtyChange(
            configurationFingerprint(toConfiguration(values))
              !== configurationFingerprint(node.configuration),
          );
        }}
      >
        <Form.Item name="sourceTableName" label="来源表" rules={[{ required: true }]}>
          <Select
            disabled={!validation}
            placeholder={validation ? '选择需要重命名的上游表' : '等待 Task Engine 计算上游表'}
            options={tableOptions}
            onChange={(value) => {
              if (!form.getFieldValue('outputTableName')) {
                form.setFieldValue('outputTableName', value);
              }
            }}
          />
        </Form.Item>
        <Form.Item
          name="outputTableName"
          label="输出逻辑表名"
          extra="只重命名字段时保持与来源表相同"
          rules={[{ required: true, whitespace: true, message: '请输入输出逻辑表名' }]}
        >
          <Input placeholder="例如 source_orders" />
        </Form.Item>
        <div className="canvas-rename-field-heading">
          <Typography.Text strong>字段重命名</Typography.Text>
          <Typography.Text type="secondary">
            直接修改右侧名称；未修改字段不会写入映射。
          </Typography.Text>
        </div>
        <div className="canvas-rename-field-toolbar">
          <Input
            size="small"
            allowClear
            prefix={<SearchOutlined />}
            value={search}
            placeholder="搜索字段或描述"
            onChange={(event) => setSearch(event.target.value)}
          />
          <Checkbox
            checked={changedOnly}
            disabled={changedCount === 0}
            onChange={(event) => setChangedOnly(event.target.checked)}
          >
            仅显示已修改
          </Checkbox>
          <Typography.Text type={invalidCount > 0 ? 'danger' : 'secondary'}>
            已修改 {changedCount} / 总字段 {rows.length}{invalidCount > 0 ? ` · ${invalidCount} 项有误` : ''}
          </Typography.Text>
        </div>
        <Table<RenameFieldRow>
          className="canvas-rename-field-table"
          size="small"
          rowKey="key"
          columns={columns}
          dataSource={visibleRows}
          pagination={false}
          scroll={{ y: 480 }}
          locale={{ emptyText: source ? '没有符合条件的字段' : '等待来源表 Schema' }}
          rowClassName={(row) => row.issue ? 'is-invalid' : row.changed ? 'is-changed' : ''}
        />
      </Form>
    </Space>
  );
};
const RenameCanvasNodeInspector = adaptCanvasNodeInspector(CanvasNodeType.Rename, RenameInspector);
export default RenameCanvasNodeInspector;

