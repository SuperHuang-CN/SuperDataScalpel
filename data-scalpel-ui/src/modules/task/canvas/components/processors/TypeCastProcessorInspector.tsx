import { CompactAlert as Alert } from '../../../../../shared/components/ContextualFeedback';
import {
  DeleteOutlined,
  DownOutlined,
  PlusOutlined,
  UpOutlined,
} from '@ant-design/icons';
import { AutoComplete, Button, Form, Input, InputNumber, Select, Space, Tag, Typography } from 'antd';
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
  type StringTemporalParseOptions,
  type TemporalStringFormatOptions,
  type TypeCastConfiguration,
} from '../../canvasTypes';
import type { CanvasNodeInspectorHandle } from '../CanvasNodeInspector';
import { ProcessorValidationIssues } from './ProcessorValidationIssues';
import { isValidIanaZoneId } from '../../../model/taskScheduleValidation';

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

const epochTimestampUnitOptions = [
  { value: 'MILLISECONDS', label: '毫秒' },
  { value: 'SECONDS', label: '秒' },
  { value: 'MICROSECONDS', label: '微秒' },
];

const commonTimeZoneOptions = [
  'Asia/Shanghai',
  'Asia/Hong_Kong',
  'Asia/Tokyo',
  'Asia/Singapore',
  'UTC',
  'Europe/London',
  'America/New_York',
].map((value) => ({ value }));

const datePatternOptions = [
  'yyyy-MM-dd',
  'yyyy/MM/dd',
  'yyyyMMdd',
].map((value) => ({ value }));

const sourceTimeZoneTimestampPatternOptions = [
  'yyyy-MM-dd HH:mm:ss',
  'yyyy/MM/dd HH:mm:ss',
  'yyyyMMddHHmmss',
  'yyyy-MM-dd HH:mm:ss.SSS',
].map((value) => ({ value }));

const embeddedOffsetTimestampPatternOptions = [
  "yyyy-MM-dd'T'HH:mm:ssXXX",
  "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
].map((value) => ({ value }));

const defaultTypeDefinition = (type: PlatformDataType): PlatformTypeDefinition => ({
  type,
  length: null,
  precision: type === 'DECIMAL' ? 18 : null,
  scale: type === 'DECIMAL' ? 2 : null,
  geometry: null,
});

const withoutEpochTimestampUnit = (cast: ColumnTypeCast): ColumnTypeCast => {
  const next = { ...cast };
  delete next.epochTimestampUnit;
  return next;
};

const withoutStringTemporalParseOptions = (cast: ColumnTypeCast): ColumnTypeCast => {
  const next = { ...cast };
  delete next.stringTemporalParseOptions;
  return next;
};

const withoutTemporalStringFormatOptions = (cast: ColumnTypeCast): ColumnTypeCast => {
  const next = { ...cast };
  delete next.temporalStringFormatOptions;
  return next;
};

const defaultStringTemporalParseOptions = (
  targetType: 'DATE' | 'TIMESTAMP',
): StringTemporalParseOptions => (targetType === 'DATE'
  ? {
    pattern: 'yyyy-MM-dd',
    zoneMode: null,
    sourceTimeZone: null,
  }
  : {
    pattern: 'yyyy-MM-dd HH:mm:ss',
    zoneMode: 'SOURCE_TIME_ZONE',
    sourceTimeZone: null,
  });

const defaultTemporalStringFormatOptions = (
  sourceType: 'DATE' | 'TIMESTAMP' | 'TIMESTAMP_NTZ',
): TemporalStringFormatOptions => ({
  pattern: sourceType === 'DATE' ? 'yyyy-MM-dd' : 'yyyy-MM-dd HH:mm:ss',
  targetTimeZone: sourceType === 'TIMESTAMP' ? 'UTC' : null,
});

const isTemporalType = (
  type: PlatformDataType | undefined,
): type is 'DATE' | 'TIMESTAMP' | 'TIMESTAMP_NTZ' => (
  type === 'DATE' || type === 'TIMESTAMP' || type === 'TIMESTAMP_NTZ'
);

const withSpecialTemporalOptions = (
  cast: ColumnTypeCast,
  sourceType: PlatformDataType | undefined,
): ColumnTypeCast => {
  const epochTimestampUnit = cast.epochTimestampUnit;
  const parseOptions = cast.stringTemporalParseOptions;
  const formatOptions = cast.temporalStringFormatOptions;
  const plain = withoutTemporalStringFormatOptions(
    withoutStringTemporalParseOptions(withoutEpochTimestampUnit(cast)),
  );
  if (sourceType === 'LONG' && cast.targetType.type === 'TIMESTAMP') {
    return { ...plain, epochTimestampUnit: epochTimestampUnit ?? 'MILLISECONDS' };
  }
  if ((sourceType === 'DATE' || sourceType === 'TIMESTAMP')
      && cast.targetType.type === 'LONG') {
    return { ...plain, epochTimestampUnit: epochTimestampUnit ?? 'MILLISECONDS' };
  }
  if (sourceType === 'STRING'
      && (cast.targetType.type === 'DATE' || cast.targetType.type === 'TIMESTAMP')) {
    const defaults = defaultStringTemporalParseOptions(cast.targetType.type);
    return {
      ...plain,
      stringTemporalParseOptions: parseOptions == null
        ? defaults
        : cast.targetType.type === 'DATE'
          ? { ...parseOptions, zoneMode: null, sourceTimeZone: null }
          : {
            ...parseOptions,
            zoneMode: parseOptions.zoneMode ?? 'SOURCE_TIME_ZONE',
          },
    };
  }
  if (isTemporalType(sourceType) && cast.targetType.type === 'STRING') {
    const defaults = defaultTemporalStringFormatOptions(sourceType);
    return {
      ...plain,
      temporalStringFormatOptions: formatOptions == null
        ? defaults
        : {
          ...formatOptions,
          targetTimeZone: sourceType === 'TIMESTAMP'
            ? formatOptions.targetTimeZone ?? 'UTC'
            : null,
        },
    };
  }
  return plain;
};

const containsUnquotedZonePatternSymbol = (pattern: string) => {
  let quoted = false;
  for (let index = 0; index < pattern.length; index += 1) {
    const symbol = pattern[index];
    if (symbol === "'") {
      if (quoted && pattern[index + 1] === "'") index += 1;
      else quoted = !quoted;
      continue;
    }
    if (!quoted && 'XxZOVz'.includes(symbol)) return true;
  }
  return false;
};

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
        const values = form.getFieldsValue(true);
        void form.validateFields().catch(() => undefined);
        if (casts.length === 0) {
          setDraftError('至少配置一个字段类型转换');

        }
        const names = new Set<string>();
        for (const cast of casts) {
          if (!cast.columnName) {
            setDraftError('类型转换项中存在未选择字段');

          }
          if (!names.add(cast.columnName)) {
            setDraftError(`字段重复配置转换：${cast.columnName}`);

          }
          const typeIssue = validateTargetType(cast.targetType);
          if (typeIssue) {
            setDraftError(`${cast.columnName}：${typeIssue}`);

          }
          if (!cast.failureStrategy) {
            setDraftError(`${cast.columnName}：请选择转换失败策略`);

          }
          const temporalOptions = cast.stringTemporalParseOptions;
          if (temporalOptions) {
            if (cast.epochTimestampUnit != null) {
              setDraftError(`${cast.columnName}：Epoch 单位和字符串日期时间解析不能同时配置`);
            }
            if (!temporalOptions.pattern.trim()) {
              setDraftError(`${cast.columnName}：请输入日期时间格式`);
            } else if (temporalOptions.pattern.length > 128) {
              setDraftError(`${cast.columnName}：日期时间格式不能超过 128 个字符`);
            }
            if (cast.targetType.type === 'DATE') {
              if (temporalOptions.zoneMode !== null || temporalOptions.sourceTimeZone !== null) {
                setDraftError(`${cast.columnName}：DATE 解析不能配置时区`);
              }
            } else if (cast.targetType.type === 'TIMESTAMP') {
              if (!temporalOptions.zoneMode) {
                setDraftError(`${cast.columnName}：请选择时间戳时区处理方式`);
              } else if (temporalOptions.zoneMode === 'SOURCE_TIME_ZONE'
                && !isValidIanaZoneId(temporalOptions.sourceTimeZone ?? '')) {
                setDraftError(`${cast.columnName}：请输入有效的来源 IANA 时区`);
              } else if (temporalOptions.zoneMode === 'EMBEDDED_OFFSET'
                && temporalOptions.sourceTimeZone !== null) {
                setDraftError(`${cast.columnName}：字符串自带偏移时不能填写来源时区`);
              }
            } else {
              setDraftError(`${cast.columnName}：字符串日期时间解析仅支持 DATE 或 TIMESTAMP`);
            }
          }
          const formatOptions = cast.temporalStringFormatOptions;
          if (formatOptions) {
            const sourceType = sourceColumns.find(
              (column) => column.name === cast.columnName,
            )?.fieldType;
            if (cast.epochTimestampUnit != null || temporalOptions != null) {
              setDraftError(`${cast.columnName}：时间格式化不能与其他特殊时间转换配置同时使用`);
            }
            if (cast.targetType.type !== 'STRING') {
              setDraftError(`${cast.columnName}：时间格式化仅支持转换为 STRING`);
            }
            if (sourceType && !isTemporalType(sourceType)) {
              setDraftError(`${cast.columnName}：只有 DATE、TIMESTAMP 或 TIMESTAMP_NTZ 可以指定输出格式`);
            }
            if (!formatOptions.pattern.trim()) {
              setDraftError(`${cast.columnName}：请输入输出日期时间格式`);
            } else if (formatOptions.pattern.length > 128) {
              setDraftError(`${cast.columnName}：输出日期时间格式不能超过 128 个字符`);
            } else if (containsUnquotedZonePatternSymbol(formatOptions.pattern)) {
              setDraftError(`${cast.columnName}：输出格式不能包含时区或偏移符号`);
            }
            if (sourceType === 'TIMESTAMP'
              && !isValidIanaZoneId(formatOptions.targetTimeZone ?? '')) {
              setDraftError(`${cast.columnName}：请输入有效的目标 IANA 时区`);
            } else if ((sourceType === 'DATE' || sourceType === 'TIMESTAMP_NTZ')
              && formatOptions.targetTimeZone !== null) {
              setDraftError(`${cast.columnName}：${sourceType} 格式化不能配置目标时区`);
            }
          }
        }
        const configuration: TypeCastConfiguration = {
          sourceTableName: values.sourceTableName ?? '',
          outputTableName: (values.outputTableName ?? '').trim(),
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
  }), [casts, form, node.id, onApply, onDirtyChange, sourceColumns]);

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
          onClick={() => {
            const sourceColumn = sourceColumns.find(
              (column) => !casts.some((cast) => cast.columnName === column.name),
            );
            const newCast: ColumnTypeCast = {
              columnName: sourceColumn?.name ?? '',
              targetType: defaultTypeDefinition('STRING'),
              failureStrategy: 'FAIL',
            };
            updateCasts([
              ...casts,
              withSpecialTemporalOptions(newCast, sourceColumn?.fieldType),
            ]);
          }}
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
          const supportsEpochTimestampUnit = (selectedColumn?.fieldType === 'LONG'
              && cast.targetType.type === 'TIMESTAMP')
            || ((selectedColumn?.fieldType === 'DATE'
                || selectedColumn?.fieldType === 'TIMESTAMP')
              && cast.targetType.type === 'LONG');
          const supportsStringTemporalParse = selectedColumn?.fieldType === 'STRING'
            && (cast.targetType.type === 'DATE' || cast.targetType.type === 'TIMESTAMP');
          const temporalOptions = cast.stringTemporalParseOptions;
          const supportsTemporalStringFormat = cast.targetType.type === 'STRING'
            && (isTemporalType(selectedColumn?.fieldType)
              || (selectedColumn == null && cast.temporalStringFormatOptions != null));
          const formatOptions = cast.temporalStringFormatOptions;
          const formatsTimestamp = selectedColumn?.fieldType === 'TIMESTAMP'
            || (selectedColumn == null && formatOptions?.targetTimeZone != null);
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
                  const nextSource = sourceColumns.find((column) => column.name === columnName);
                  nextCasts[index] = withSpecialTemporalOptions(
                    { ...cast, columnName },
                    nextSource?.fieldType,
                  );
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
                    const targetType = defaultTypeDefinition(type);
                    nextCasts[index] = withSpecialTemporalOptions(
                      { ...cast, targetType },
                      selectedColumn?.fieldType,
                    );
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
              {supportsEpochTimestampUnit && (
                <div className="canvas-type-cast-epoch-unit">
                  <Typography.Text type="secondary">Epoch 单位</Typography.Text>
                  <Select
                    value={cast.epochTimestampUnit ?? 'SECONDS'}
                    options={epochTimestampUnitOptions}
                    onChange={(epochTimestampUnit) => {
                      const nextCasts = [...casts];
                      nextCasts[index] = { ...cast, epochTimestampUnit };
                      updateCasts(nextCasts);
                    }}
                  />
                  {cast.epochTimestampUnit == null && <Tag>秒 · 兼容旧配置</Tag>}
                </div>
              )}
              {supportsStringTemporalParse && temporalOptions == null && (
                <Space size={8} wrap>
                  <Tag>Spark 默认解析 · 兼容旧配置</Tag>
                  <Button
                    size="small"
                    onClick={() => {
                      const targetType = cast.targetType.type;
                      if (targetType !== 'DATE' && targetType !== 'TIMESTAMP') return;
                      const nextCasts = [...casts];
                      nextCasts[index] = withSpecialTemporalOptions(cast, 'STRING');
                      updateCasts(nextCasts);
                    }}
                  >
                    指定格式解析
                  </Button>
                </Space>
              )}
              {supportsStringTemporalParse && temporalOptions != null && (
                <div className="canvas-type-cast-temporal-options">
                  <Typography.Text type="secondary">解析格式</Typography.Text>
                  <AutoComplete
                    value={temporalOptions.pattern}
                    options={cast.targetType.type === 'DATE'
                      ? datePatternOptions
                      : temporalOptions.zoneMode === 'EMBEDDED_OFFSET'
                        ? embeddedOffsetTimestampPatternOptions
                        : sourceTimeZoneTimestampPatternOptions}
                    placeholder="Spark datetime pattern"
                    onChange={(pattern) => {
                      const nextCasts = [...casts];
                      nextCasts[index] = {
                        ...cast,
                        stringTemporalParseOptions: { ...temporalOptions, pattern },
                      };
                      updateCasts(nextCasts);
                    }}
                  />
                  {cast.targetType.type === 'TIMESTAMP' && (
                    <>
                      <Typography.Text type="secondary">时区处理</Typography.Text>
                      <Select
                        value={temporalOptions.zoneMode}
                        options={[
                          { value: 'SOURCE_TIME_ZONE', label: '指定来源时区' },
                          { value: 'EMBEDDED_OFFSET', label: '字符串内含偏移' },
                        ]}
                        onChange={(zoneMode) => {
                          const nextCasts = [...casts];
                          nextCasts[index] = {
                            ...cast,
                            stringTemporalParseOptions: {
                              ...temporalOptions,
                              zoneMode,
                              sourceTimeZone: zoneMode === 'EMBEDDED_OFFSET'
                                ? null : temporalOptions.sourceTimeZone,
                              pattern: zoneMode === 'EMBEDDED_OFFSET'
                                && temporalOptions.pattern === 'yyyy-MM-dd HH:mm:ss'
                                ? "yyyy-MM-dd'T'HH:mm:ssXXX" : temporalOptions.pattern,
                            },
                          };
                          updateCasts(nextCasts);
                        }}
                      />
                    </>
                  )}
                  {cast.targetType.type === 'TIMESTAMP'
                    && temporalOptions.zoneMode === 'SOURCE_TIME_ZONE' && (
                    <>
                      <Typography.Text type="secondary">来源时区</Typography.Text>
                      <AutoComplete
                        value={temporalOptions.sourceTimeZone ?? undefined}
                        options={commonTimeZoneOptions}
                        placeholder="选择或输入 IANA 时区"
                        onChange={(sourceTimeZone) => {
                          const nextCasts = [...casts];
                          nextCasts[index] = {
                            ...cast,
                            stringTemporalParseOptions: { ...temporalOptions, sourceTimeZone },
                          };
                          updateCasts(nextCasts);
                        }}
                      />
                    </>
                  )}
                </div>
              )}
              {supportsTemporalStringFormat && formatOptions == null && (
                <Space size={8} wrap>
                  <Tag>Spark 默认格式 · 兼容旧配置</Tag>
                  <Button
                    size="small"
                    onClick={() => {
                      const sourceType = selectedColumn?.fieldType;
                      if (!isTemporalType(sourceType)) return;
                      const nextCasts = [...casts];
                      nextCasts[index] = withSpecialTemporalOptions(cast, sourceType);
                      updateCasts(nextCasts);
                    }}
                  >
                    指定输出格式
                  </Button>
                </Space>
              )}
              {supportsTemporalStringFormat && formatOptions != null && (
                <div className="canvas-type-cast-format-options">
                  <Typography.Text type="secondary">输出格式</Typography.Text>
                  <AutoComplete
                    value={formatOptions.pattern}
                    options={selectedColumn?.fieldType === 'DATE'
                      ? datePatternOptions
                      : sourceTimeZoneTimestampPatternOptions}
                    placeholder="Spark datetime pattern"
                    onChange={(pattern) => {
                      const nextCasts = [...casts];
                      nextCasts[index] = {
                        ...cast,
                        temporalStringFormatOptions: { ...formatOptions, pattern },
                      };
                      updateCasts(nextCasts);
                    }}
                  />
                  {formatsTimestamp && (
                    <>
                      <Typography.Text type="secondary">目标时区</Typography.Text>
                      <AutoComplete
                        value={formatOptions.targetTimeZone ?? undefined}
                        options={commonTimeZoneOptions}
                        placeholder="选择或输入 IANA 时区"
                        onChange={(targetTimeZone) => {
                          const nextCasts = [...casts];
                          nextCasts[index] = {
                            ...cast,
                            temporalStringFormatOptions: { ...formatOptions, targetTimeZone },
                          };
                          updateCasts(nextCasts);
                        }}
                      />
                    </>
                  )}
                </div>
              )}
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
