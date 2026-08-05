import {
  DeleteOutlined,
  DownOutlined,
  PlusOutlined,
  UpOutlined,
} from '@ant-design/icons';
import {
  Alert,
  Button,
  Form,
  Input,
  InputNumber,
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
  type CastFailureStrategy,
  type ColumnTypeCast,
  type PlatformTypeDefinition,
  type PlatformDataType,
  type TypeCastConfiguration,
} from '../../canvasTypes';
import type { CanvasNodeInspectorHandle } from '../CanvasNodeInspector';
import { ProcessorValidationIssues } from './ProcessorValidationIssues';

interface TypeCastProcessorInspectorProps {
  node: Extract<CanvasNodeDefinition, { type: 'TYPE_CAST' }>;
  executionMode: 'BATCH' | 'STREAMING';
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage: string | null;
  onApply: (update: CanvasNodeConfigurationUpdate) => void;
  onDirtyChange: (dirty: boolean) => void;
  inspectorRef: Ref<CanvasNodeInspectorHandle>;
}

interface TypeCastFormValues {
  sourceTableName: string;
  outputTableName: string;
}

const castTargetTypes: PlatformDataType[] = [
  'BOOLEAN',
  'BYTE',
  'SHORT',
  'INTEGER',
  'LONG',
  'FLOAT',
  'DOUBLE',
  'DECIMAL',
  'STRING',
  'BINARY',
  'DATE',
  'TIMESTAMP',
  'TIMESTAMP_NTZ',
];

const targetTypeOptions = castTargetTypes.map((type) => ({ value: type, label: type }));

const defaultTypeDefinition = (type: PlatformDataType): PlatformTypeDefinition => ({
  type,
  length: null,
  precision: type === 'DECIMAL' ? 18 : null,
  scale: type === 'DECIMAL' ? 2 : null,
  geometry: null,
});

const validateTargetType = (target: PlatformTypeDefinition): string | null => {
  if (target.type === 'GEOMETRY') return '类型转换暂不支持 GEOMETRY';
  if (target.type === 'STRING' && target.length !== null && target.length < 1) {
    return 'STRING length 必须为正整数';
  }
  if (target.type === 'DECIMAL') {
    if (target.precision === null || target.precision < 1 || target.precision > 38) {
      return 'DECIMAL precision 必须在 1..38';
    }
    if (target.scale === null || target.scale < 0 || target.scale > target.precision) {
      return 'DECIMAL scale 必须在 0..precision';
    }
  }
  return null;
};

export const TypeCastProcessorInspector = ({
  node,
  executionMode,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: TypeCastProcessorInspectorProps) => {
  const [form] = Form.useForm<TypeCastFormValues>();
  const [casts, setCasts] = useState<ColumnTypeCast[]>(
    () => structuredClone(node.configuration.casts),
  );
  const [draftError, setDraftError] = useState<string | null>(null);
  const sourceTableName = Form.useWatch('sourceTableName', form)
    ?? node.configuration.sourceTableName;
  const inputTables = useMemo(() => validation?.inputTables ?? [], [validation?.inputTables]);
  const sourceTable = inputTables.find((table) => table.name === sourceTableName);
  const sourceTableMissing = Boolean(sourceTableName && validation && !sourceTable);
  const sourceColumns = useMemo(() => sourceTable?.columns ?? [], [sourceTable?.columns]);
  const tableOptions = useMemo(() => [
    ...(sourceTableMissing
      ? [{ value: sourceTableName, label: `${sourceTableName}（已失效）`, disabled: true }]
      : []),
    ...inputTables.map((table) => ({
      value: table.name,
      label: `${table.name} · ${table.columns.length} 字段`,
    })),
  ], [inputTables, sourceTableMissing, sourceTableName]);
  const setNullCount = casts.filter((cast) => cast.failureStrategy === 'SET_NULL').length;

  const updateCasts = (nextCasts: ColumnTypeCast[]) => {
    setCasts(nextCasts);
    setDraftError(nextCasts.length === 0 ? '至少配置一个字段类型转换' : null);
    onDirtyChange(true);
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      try {
        const values = await form.validateFields();
        if (casts.length === 0) {
          setDraftError('至少配置一个字段类型转换');
          return false;
        }
        const names = new Set<string>();
        for (const cast of casts) {
          if (!cast.columnName) {
            setDraftError('类型转换项中存在未选择字段');
            return false;
          }
          if (!names.add(cast.columnName)) {
            setDraftError(`字段重复配置转换：${cast.columnName}`);
            return false;
          }
          const typeIssue = validateTargetType(cast.targetType);
          if (typeIssue) {
            setDraftError(`${cast.columnName}：${typeIssue}`);
            return false;
          }
          if (!cast.failureStrategy) {
            setDraftError(`${cast.columnName}：请选择转换失败策略`);
            return false;
          }
        }
        const configuration: TypeCastConfiguration = {
          sourceTableName: values.sourceTableName,
          outputTableName: values.outputTableName.trim(),
          casts: structuredClone(casts),
        };
        onApply({
          id: node.id,
          type: CanvasNodeType.TypeCast,
          configuration,
        });
        onDirtyChange(false);
        return true;
      } catch {
        return false;
      }
    },
  }), [casts, form, node.id, onApply, onDirtyChange]);

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      <ProcessorValidationIssues
        validation={validation}
        unavailableMessage={validationUnavailableMessage}
      />
      <Form<TypeCastFormValues>
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
          help={sourceTableMissing ? '原来源表已失效，转换配置已保留。' : undefined}
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
          <Input placeholder="例如 typed_orders" />
        </Form.Item>
      </Form>

      <div className="canvas-type-cast-heading">
        <Typography.Text strong>字段类型转换</Typography.Text>
        <Button
          size="small"
          icon={<PlusOutlined />}
          onClick={() => updateCasts([...casts, {
            columnName: sourceColumns.find(
              (column) => !casts.some((cast) => cast.columnName === column.name),
            )?.name ?? '',
            targetType: defaultTypeDefinition('STRING'),
            failureStrategy: 'FAIL',
          }])}
        >
          添加
        </Button>
      </div>
      {draftError && <Alert showIcon type="error" title={draftError} />}
      {setNullCount > 0 && (
        <Alert
          showIcon
          type="warning"
          title={`${setNullCount} 项使用 SET_NULL`}
          description="真实值转换失败时不会终止节点，而会写入 NULL；预检无法统计受影响行数。"
        />
      )}
      <div className="canvas-type-cast-list">
        {casts.length === 0 && (
          <Typography.Text type="secondary">尚未配置类型转换。</Typography.Text>
        )}
        {casts.map((cast, index) => {
          const selectedColumn = sourceColumns.find(
            (column) => column.name === cast.columnName,
          );
          const missing = Boolean(cast.columnName && sourceTable && !selectedColumn);
          const eventTimeBlocked = executionMode === 'STREAMING'
            && cast.columnName === sourceTable?.eventTimeColumn;
          const typeIssue = validateTargetType(cast.targetType);
          return (
            <div
              className={`canvas-type-cast-item${missing || eventTimeBlocked || typeIssue ? ' is-invalid' : ''}`}
              key={index}
            >
              <div className="canvas-type-cast-item-heading">
                <Tag color="purple">转换 {index + 1}</Tag>
                <Space size={0}>
                  <Button
                    type="text"
                    size="small"
                    icon={<UpOutlined />}
                    disabled={index === 0}
                    onClick={() => {
                      const nextCasts = [...casts];
                      [nextCasts[index - 1], nextCasts[index]] = [
                        nextCasts[index],
                        nextCasts[index - 1],
                      ];
                      updateCasts(nextCasts);
                    }}
                  />
                  <Button
                    type="text"
                    size="small"
                    icon={<DownOutlined />}
                    disabled={index === casts.length - 1}
                    onClick={() => {
                      const nextCasts = [...casts];
                      [nextCasts[index], nextCasts[index + 1]] = [
                        nextCasts[index + 1],
                        nextCasts[index],
                      ];
                      updateCasts(nextCasts);
                    }}
                  />
                  <Button
                    type="text"
                    size="small"
                    danger
                    icon={<DeleteOutlined />}
                    onClick={() => updateCasts(
                      casts.filter((_, castIndex) => castIndex !== index),
                    )}
                  />
                </Space>
              </div>
              <Select
                showSearch
                optionFilterProp="label"
                value={cast.columnName || undefined}
                status={missing || eventTimeBlocked ? 'error' : undefined}
                placeholder="选择字段"
                options={[
                  ...(missing ? [{
                    value: cast.columnName,
                    label: `${cast.columnName}（已失效）`,
                    disabled: true,
                  }] : []),
                  ...sourceColumns.map((column) => ({
                    value: column.name,
                    label: `${column.name} · ${column.fieldType}`,
                  })),
                ]}
                onChange={(columnName) => {
                  const nextCasts = [...casts];
                  nextCasts[index] = { ...cast, columnName };
                  updateCasts(nextCasts);
                }}
              />
              <div className="canvas-type-cast-target-row">
                <Select
                  value={cast.targetType.type}
                  options={targetTypeOptions}
                  status={typeIssue ? 'error' : undefined}
                  onChange={(type) => {
                    const nextCasts = [...casts];
                    nextCasts[index] = {
                      ...cast,
                      targetType: defaultTypeDefinition(type),
                    };
                    updateCasts(nextCasts);
                  }}
                />
                <Select
                  value={cast.failureStrategy}
                  placeholder="失败策略"
                  options={[
                    { value: 'FAIL', label: '失败即终止' },
                    { value: 'SET_NULL', label: '失败值置空' },
                  ]}
                  onChange={(failureStrategy: CastFailureStrategy) => {
                    const nextCasts = [...casts];
                    nextCasts[index] = { ...cast, failureStrategy };
                    updateCasts(nextCasts);
                  }}
                />
              </div>
              {cast.targetType.type === 'STRING' && (
                <InputNumber
                  min={1}
                  precision={0}
                  value={cast.targetType.length}
                  placeholder="可选 length"
                  className="canvas-type-cast-parameter"
                  onChange={(length) => {
                    const nextCasts = [...casts];
                    nextCasts[index] = {
                      ...cast,
                      targetType: { ...cast.targetType, length },
                    };
                    updateCasts(nextCasts);
                  }}
                />
              )}
              {cast.targetType.type === 'DECIMAL' && (
                <Space.Compact block>
                  <InputNumber
                    min={1}
                    max={38}
                    precision={0}
                    value={cast.targetType.precision}
                    placeholder="precision"
                    onChange={(precision) => {
                      const nextCasts = [...casts];
                      nextCasts[index] = {
                        ...cast,
                        targetType: { ...cast.targetType, precision },
                      };
                      updateCasts(nextCasts);
                    }}
                  />
                  <InputNumber
                    min={0}
                    max={cast.targetType.precision ?? 38}
                    precision={0}
                    value={cast.targetType.scale}
                    placeholder="scale"
                    onChange={(scale) => {
                      const nextCasts = [...casts];
                      nextCasts[index] = {
                        ...cast,
                        targetType: { ...cast.targetType, scale },
                      };
                      updateCasts(nextCasts);
                    }}
                  />
                </Space.Compact>
              )}
              {missing && (
                <Typography.Text type="danger">
                  字段已不在当前来源表中，原配置已保留。
                </Typography.Text>
              )}
              {eventTimeBlocked && (
                <Typography.Text type="danger">
                  流模式不能转换事件时间字段。
                </Typography.Text>
              )}
              {typeIssue && <Typography.Text type="danger">{typeIssue}</Typography.Text>}
            </div>
          );
        })}
      </div>
    </Space>
  );
};
