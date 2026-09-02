import { CompactAlert as Alert } from '../../../../../shared/components/ContextualFeedback';
import {
  DeleteOutlined,
  DownOutlined,
  PlusOutlined,
  UpOutlined,
} from '@ant-design/icons';
import { Button, Card, Form, Input, InputNumber, Select, Space, Tag, Typography } from 'antd';
import {
  useImperativeHandle,
  useMemo,
  useState,
  type Ref,
} from 'react';
import {
  CANVAS_JSON_EXTRACT_MAX_EXTRACTIONS,
  CANVAS_JSON_EXTRACT_MAX_PATH_LENGTH,
  CanvasNodeType,
  type CanvasNodeConfigurationUpdate,
  type CanvasNodeDefinition,
  type CanvasNodeValidationResult,
  type JsonExtractConfiguration,
  type JsonExtractFailureStrategy,
  type JsonExtraction,
  type PlatformDataType,
  type PlatformTypeDefinition,
} from '../../canvasTypes';
import type { CanvasNodeInspectorHandle } from '../CanvasNodeInspector';
import { ProcessorValidationIssues } from './ProcessorValidationIssues';

interface JsonExtractProcessorInspectorProps {
  node: Extract<CanvasNodeDefinition, { type: 'JSON_EXTRACT' }>;
  executionMode: 'BATCH' | 'STREAMING';
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage: string | null;
  onApply: (update: CanvasNodeConfigurationUpdate) => void;
  onDirtyChange: (dirty: boolean) => void;
  inspectorRef: Ref<CanvasNodeInspectorHandle>;
}

interface JsonExtractFormValues {
  sourceTableName: string;
  outputTableName: string;
  sourceColumnName: string;
  failureStrategy: JsonExtractFailureStrategy;
}

const targetTypes: PlatformDataType[] = [
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

const targetTypeOptions = targetTypes.map((type) => ({ value: type, label: type }));

const defaultTypeDefinition = (type: PlatformDataType): PlatformTypeDefinition => ({
  type,
  length: null,
  precision: type === 'DECIMAL' ? 18 : null,
  scale: type === 'DECIMAL' ? 2 : null,
  geometry: null,
});

const validateTargetType = (target: PlatformTypeDefinition): string | null => {
  if (target.type === 'GEOMETRY') return 'JSON 提取暂不支持 GEOMETRY';
  if (target.type === 'STRING' && target.length !== null
    && (!Number.isInteger(target.length) || target.length < 1)) {
    return 'STRING length 必须为正整数';
  }
  if (target.type === 'DECIMAL') {
    if (target.precision === null
      || !Number.isInteger(target.precision)
      || target.precision < 1
      || target.precision > 38) {
      return 'DECIMAL precision 必须在 1..38';
    }
    if (target.scale === null
      || !Number.isInteger(target.scale)
      || target.scale < 0
      || target.scale > target.precision) {
      return 'DECIMAL scale 必须在 0..precision';
    }
  }
  return null;
};

const move = <T,>(items: T[], from: number, to: number) => {
  const result = [...items];
  const [item] = result.splice(from, 1);
  result.splice(to, 0, item);
  return result;
};

export const JsonExtractProcessorInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: JsonExtractProcessorInspectorProps) => {
  const [form] = Form.useForm<JsonExtractFormValues>();
  const [extractions, setExtractions] = useState<JsonExtraction[]>(
    () => structuredClone(node.configuration.extractions),
  );
  const [draftError, setDraftError] = useState<string | null>(null);
  const sourceTableName = Form.useWatch('sourceTableName', form)
    ?? node.configuration.sourceTableName;
  const sourceColumnName = Form.useWatch('sourceColumnName', form)
    ?? node.configuration.sourceColumnName;
  const failureStrategy = Form.useWatch('failureStrategy', form)
    ?? node.configuration.failureStrategy;
  const inputTables = useMemo(() => validation?.inputTables ?? [], [validation?.inputTables]);
  const sourceTable = inputTables.find((table) => table.name === sourceTableName);
  const sourceTableMissing = Boolean(sourceTableName && validation && !sourceTable);
  const sourceColumn = sourceTable?.columns.find(
    (column) => column.name === sourceColumnName,
  );
  const sourceColumnMissing = Boolean(
    sourceColumnName && validation && (!sourceTable || !sourceColumn),
  );
  const sourceColumnTypeInvalid = Boolean(sourceColumn && sourceColumn.fieldType !== 'STRING');
  const stringColumns = sourceTable?.columns.filter(
    (column) => column.fieldType === 'STRING',
  ) ?? [];
  const tableOptions = [
    ...(sourceTableMissing
      ? [{
        value: sourceTableName,
        label: `${sourceTableName}（已失效）`,
        disabled: true,
      }]
      : []),
    ...inputTables.map((table) => ({
      value: table.name,
      label: `${table.name} · ${table.columns.length} 字段`,
    })),
  ];
  const sourceColumnInvalid = sourceColumnMissing || sourceColumnTypeInvalid;
  const sourceColumnOptions = [
    ...(sourceColumnInvalid
      ? [{
        value: sourceColumnName,
        label: sourceColumnMissing
          ? `${sourceColumnName}（已失效）`
          : `${sourceColumnName} · ${sourceColumn?.fieldType}（不是 STRING）`,
        disabled: true,
      }]
      : []),
    ...stringColumns.map((column) => ({
      value: column.name,
      label: column.name,
    })),
  ];

  const updateExtractions = (nextExtractions: JsonExtraction[]) => {
    setExtractions(nextExtractions);
    setDraftError(nextExtractions.length === 0 ? '至少配置一个 JSON 提取项' : null);
    onDirtyChange(true);
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      try {
        const values = form.getFieldsValue(true);
        void form.validateFields().catch(() => undefined);
        if (extractions.length === 0) {
          setDraftError('至少配置一个 JSON 提取项');

        }
        if (extractions.length > CANVAS_JSON_EXTRACT_MAX_EXTRACTIONS) {
          setDraftError(`JSON 提取项不能超过 ${CANVAS_JSON_EXTRACT_MAX_EXTRACTIONS} 项`);

        }
        const outputColumnNames = new Set(
          sourceTable?.columns.map((column) => column.name) ?? [],
        );
        for (let index = 0; index < extractions.length; index += 1) {
          const extraction = extractions[index];
          if (!extraction.jsonPath) {
            setDraftError(`提取 ${index + 1}：请输入 JSON Path`);

          }
          if (!extraction.jsonPath.startsWith('$')) {
            setDraftError(`提取 ${index + 1}：JSON Path 必须以 $ 开头`);

          }
          if (extraction.jsonPath.length > CANVAS_JSON_EXTRACT_MAX_PATH_LENGTH) {
            setDraftError(
              `提取 ${index + 1}：JSON Path 不能超过 ${CANVAS_JSON_EXTRACT_MAX_PATH_LENGTH} 个字符`,
            );

          }
          const outputColumnName = extraction.outputColumnName.trim();
          if (!outputColumnName) {
            setDraftError(`提取 ${index + 1}：请输入输出字段名`);

          }
          if (!outputColumnNames.add(outputColumnName)) {
            setDraftError(`输出字段名与来源字段或其他提取项重复：${outputColumnName}`);

          }
          const typeIssue = validateTargetType(extraction.targetType);
          if (typeIssue) {
            setDraftError(`提取 ${index + 1}：${typeIssue}`);

          }
        }
        const configuration: JsonExtractConfiguration = {
          sourceTableName: values.sourceTableName ?? '',
          outputTableName: (values.outputTableName ?? '').trim(),
          sourceColumnName: values.sourceColumnName,
          failureStrategy: values.failureStrategy,
          extractions: extractions.map((extraction) => ({
            ...structuredClone(extraction),
            jsonPath: extraction.jsonPath.trim(),
            outputColumnName: extraction.outputColumnName.trim(),
          })),
        };
        onApply({
          id: node.id,
          type: CanvasNodeType.JsonExtract,
          configuration,
        });
        onDirtyChange(false);
        return true;
      } catch {
        return false;
      }
    },
  }), [
    extractions,
    form,
    node.id,
    onApply,
    onDirtyChange,
    sourceTable?.columns,
  ]);

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      <ProcessorValidationIssues
        validation={validation}
        unavailableMessage={validationUnavailableMessage}
      />
      <Form<JsonExtractFormValues>
        autoComplete="off"
        form={form}
        layout="vertical"
        initialValues={{
          sourceTableName: node.configuration.sourceTableName,
          outputTableName: node.configuration.outputTableName,
          sourceColumnName: node.configuration.sourceColumnName,
          failureStrategy: node.configuration.failureStrategy,
        }}
        onValuesChange={() => onDirtyChange(true)}
      >
        <Form.Item
          name="sourceTableName"
          label="来源表"
          rules={[{ required: true, message: '请选择来源表' }]}
          validateStatus={sourceTableMissing ? 'error' : undefined}
          help={sourceTableMissing ? '原来源表已失效，提取配置已保留。' : undefined}
        >
          <Select
            showSearch
            optionFilterProp="label"
            placeholder={validation ? '选择一张上游表' : '等待 Task Engine 返回上游表'}
            options={tableOptions}
          />
        </Form.Item>
        <Form.Item
          name="sourceColumnName"
          label="JSON 来源字段"
          rules={[{ required: true, message: '请选择 JSON 来源字段' }]}
          validateStatus={sourceColumnInvalid ? 'error' : undefined}
          help={sourceColumnInvalid
            ? '原字段已失效或不是 STRING，配置已保留。'
            : '仅显示 STRING 字段；字段内容应为 JSON 文本。'}
        >
          <Select
            showSearch
            optionFilterProp="label"
            placeholder={sourceTable ? '选择 STRING 字段' : '请先选择来源表'}
            options={sourceColumnOptions}
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
          <Input placeholder="例如 orders_json_extracted" />
        </Form.Item>
        <Form.Item
          name="failureStrategy"
          label="解析失败策略"
          rules={[{ required: true, message: '请选择解析失败策略' }]}
        >
          <Select options={[
            { value: 'ERROR', label: 'ERROR · 解析或转换失败时终止' },
            { value: 'SET_NULL', label: 'SET_NULL · 解析或转换失败时置空' },
          ]} />
        </Form.Item>
      </Form>

      {failureStrategy === 'SET_NULL' && (
        <Alert
          showIcon
          type="warning"
          title="失败值将写入 NULL"
          description="畸形 JSON 和目标类型转换失败不会终止节点；缺失 Path 在两种策略下都会返回 NULL。"
        />
      )}
      <div className="canvas-processor-editor-section">
        <div className="canvas-processor-editor-heading">
          <span>
            <Typography.Text strong>提取字段</Typography.Text>
            <Typography.Text type="secondary">
              {' '}({extractions.length}/{CANVAS_JSON_EXTRACT_MAX_EXTRACTIONS})
            </Typography.Text>
          </span>
          <Button
            size="small"
            icon={<PlusOutlined />}
            disabled={extractions.length >= CANVAS_JSON_EXTRACT_MAX_EXTRACTIONS}
            onClick={() => updateExtractions([...extractions, {
              jsonPath: '$.',
              outputColumnName: '',
              targetType: defaultTypeDefinition('STRING'),
            }])}
          >
            添加
          </Button>
        </div>
        {draftError && <Alert showIcon type="error" title={draftError} />}
        <div className="canvas-processor-rule-list">
          {extractions.length === 0 && (
            <Typography.Text type="secondary">
              尚未配置提取项。每个 JSON Path 生成一个新的可空字段。
            </Typography.Text>
          )}
          {extractions.map((extraction, index) => {
            const outputNameConflict = Boolean(
              extraction.outputColumnName
              && (
                sourceTable?.columns.some(
                  (column) => column.name === extraction.outputColumnName,
                )
                || extractions.some(
                  (candidate, candidateIndex) => candidateIndex !== index
                    && candidate.outputColumnName === extraction.outputColumnName,
                )
              ),
            );
            const pathInvalid = Boolean(
              extraction.jsonPath
              && (
                !extraction.jsonPath.startsWith('$')
                || extraction.jsonPath.length > CANVAS_JSON_EXTRACT_MAX_PATH_LENGTH
              ),
            );
            const typeIssue = validateTargetType(extraction.targetType);
            const invalid = outputNameConflict || pathInvalid || Boolean(typeIssue);
            const targetHasParameters = extraction.targetType.type === 'STRING'
              || extraction.targetType.type === 'DECIMAL';
            const targetRowClassName = targetHasParameters
              ? 'canvas-json-extract-target-row'
              : 'canvas-json-extract-target-row is-single';
            const updateExtraction = (next: JsonExtraction) => updateExtractions(
              extractions.map((candidate, candidateIndex) => (
                candidateIndex === index ? next : candidate
              )),
            );
            return (
              <Card
                size="small"
                key={index}
                className={`canvas-processor-rule-card${invalid ? ' is-invalid' : ''}`}
                title={<Tag color="purple">提取 {index + 1}</Tag>}
                extra={(
                  <Space size={0}>
                    <Button
                      type="text"
                      size="small"
                      aria-label={`上移提取 ${index + 1}`}
                      icon={<UpOutlined />}
                      disabled={index === 0}
                      onClick={() => updateExtractions(move(extractions, index, index - 1))}
                    />
                    <Button
                      type="text"
                      size="small"
                      aria-label={`下移提取 ${index + 1}`}
                      icon={<DownOutlined />}
                      disabled={index === extractions.length - 1}
                      onClick={() => updateExtractions(move(extractions, index, index + 1))}
                    />
                    <Button
                      type="text"
                      size="small"
                      danger
                      aria-label={`删除提取 ${index + 1}`}
                      icon={<DeleteOutlined />}
                      onClick={() => updateExtractions(
                        extractions.filter((_, candidateIndex) => candidateIndex !== index),
                      )}
                    />
                  </Space>
                )}
              >
                <Input
                  className="canvas-json-path-input"
                  value={extraction.jsonPath}
                  status={pathInvalid ? 'error' : undefined}
                  maxLength={CANVAS_JSON_EXTRACT_MAX_PATH_LENGTH}
                  placeholder="JSON Path，例如 $.customer.id"
                  onChange={(event) => updateExtraction({
                    ...extraction,
                    jsonPath: event.target.value,
                  })}
                />
                <Input
                  value={extraction.outputColumnName}
                  status={outputNameConflict ? 'error' : undefined}
                  maxLength={255}
                  placeholder="输出字段名，例如 customer_id"
                  onChange={(event) => updateExtraction({
                    ...extraction,
                    outputColumnName: event.target.value,
                  })}
                />
                <div className={targetRowClassName}>
                  <Select
                    value={extraction.targetType.type}
                    status={typeIssue ? 'error' : undefined}
                    options={targetTypeOptions}
                    onChange={(type) => updateExtraction({
                      ...extraction,
                      targetType: defaultTypeDefinition(type),
                    })}
                  />
                  {extraction.targetType.type === 'STRING' && (
                    <InputNumber
                      min={1}
                      precision={0}
                      value={extraction.targetType.length}
                      placeholder="可选 length"
                      onChange={(length) => updateExtraction({
                        ...extraction,
                        targetType: { ...extraction.targetType, length },
                      })}
                    />
                  )}
                  {extraction.targetType.type === 'DECIMAL' && (
                    <Space.Compact block>
                      <InputNumber
                        min={1}
                        max={38}
                        precision={0}
                        value={extraction.targetType.precision}
                        placeholder="precision"
                        onChange={(precision) => updateExtraction({
                          ...extraction,
                          targetType: { ...extraction.targetType, precision },
                        })}
                      />
                      <InputNumber
                        min={0}
                        max={extraction.targetType.precision ?? 38}
                        precision={0}
                        value={extraction.targetType.scale}
                        placeholder="scale"
                        onChange={(scale) => updateExtraction({
                          ...extraction,
                          targetType: { ...extraction.targetType, scale },
                        })}
                      />
                    </Space.Compact>
                  )}
                </div>
                {pathInvalid && (
                  <Typography.Text type="danger">
                    JSON Path 必须以 $ 开头，且不能超过 {CANVAS_JSON_EXTRACT_MAX_PATH_LENGTH} 个字符。
                  </Typography.Text>
                )}
                {outputNameConflict && (
                  <Typography.Text type="danger">
                    输出字段名不能与来源字段或其他提取项重复。
                  </Typography.Text>
                )}
                {typeIssue && <Typography.Text type="danger">{typeIssue}</Typography.Text>}
              </Card>
            );
          })}
        </div>
      </div>
    </Space>
  );
};
