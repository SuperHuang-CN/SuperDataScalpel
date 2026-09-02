import { CompactAlert as Alert } from '../../../../shared/components/ContextualFeedback';
import { Card, Form, InputNumber, Modal, Select, Switch } from 'antd';
import type { ReactNode } from 'react';
import type { CanvasColumnSchema, CanvasTableSchema, SnapshotSyncConfiguration } from '../canvasTypes';
import type { TaskCompilationMetadataUniqueKey } from '../taskCompilationTypes';
import { platformTypeLabel } from '../canvasSchema';
import {
  OutputFieldMappingFields,
} from './OutputFieldMappingFields';
import type { SnapshotSyncFormValues } from './snapshotSyncConfiguration';
import { CanvasInspectorFieldLabel } from './CanvasInspectorFieldLabel';

interface SnapshotSyncConfigurationFieldsProps {
  inputTables: readonly CanvasTableSchema[];
  validationReady: boolean;
  targetColumns: readonly CanvasColumnSchema[];
  targetFieldNames?: ReadonlyMap<string, string>;
  uniqueKeys: readonly TaskCompilationMetadataUniqueKey[];
  targetMetadataLoading: boolean;
  initialConfiguration: SnapshotSyncConfiguration;
  onProgrammaticChange: () => void;
  targetSelector: ReactNode;
}

const retainedOption = (
  options: { value: string; label: string; disabled?: boolean }[],
  value: string,
  label: string,
) => value && !options.some((option) => option.value === value)
  ? [{ value, label, disabled: true }, ...options]
  : options;

export const SnapshotSyncConfigurationFields = ({
  inputTables,
  validationReady,
  targetColumns,
  targetFieldNames,
  uniqueKeys,
  targetMetadataLoading,
  initialConfiguration,
  onProgrammaticChange,
  targetSelector,
}: SnapshotSyncConfigurationFieldsProps) => {
  const form = Form.useFormInstance<SnapshotSyncFormValues>();
  const sourceTableName = Form.useWatch('sourceTableName', form) ?? '';
  const mappings = Form.useWatch('columnMappings', form) ?? [];
  const keyColumns = Form.useWatch('keyColumns', form) ?? [];
  const deleteAction = Form.useWatch(['deletePolicy', 'action'], form) ?? 'KEEP';
  const deleteRatio = Form.useWatch(['deletePolicy', 'maxDeleteRatio'], form);
  const sourceTable = inputTables.find((table) => table.name === sourceTableName);

  const sourceOptions = retainedOption(
    inputTables.map((table) => ({ value: table.name, label: table.name })),
    sourceTableName,
    `${sourceTableName}（上游已不可用）`,
  );
  const mappedTargetNames = new Set(
    mappings.map((mapping) => mapping.targetColumnName).filter(Boolean),
  );
  const recommendedColumns = new Set(uniqueKeys.flatMap((key) => key.columns));
  const targetByName = new Map(targetColumns.map((column) => [column.name, column]));
  const keyOptions = targetColumns
    .filter((column) => mappedTargetNames.has(column.name))
    .filter((column) => column.geometry === null && !column.autoIncrement && !column.generated)
    .map((column) => ({
      value: column.name,
      label: `${column.name} · ${platformTypeLabel(column)}${recommendedColumns.has(column.name) ? ' · 数据库推荐' : ''}`,
    }));
  const retainedKeyOptions = keyColumns.reduce<{ value: string; label: string }[]>(
    (options, value) => options.some((option) => option.value === value)
      ? options
      : [{ value, label: `${value}（已保存但当前不可用，请移除）` }, ...options],
    keyOptions,
  );
  const changeDeletePolicy = (enabled: boolean) => {
    if (!enabled) {
      form.setFieldValue('deletePolicy', {
        action: 'KEEP',
        maxDeleteRows: null,
        maxDeleteRatio: null,
      });
      onProgrammaticChange();
      return;
    }
    Modal.confirm({
      title: '启用目标独有行删除？',
      content: '启用后，来源必须代表目标表的完整快照。运行前仍会执行空来源拦截和删除数量、比例双重保护。',
      okText: '确认启用',
      cancelText: '保持关闭',
      okButtonProps: { danger: true },
      onOk: () => {
        form.setFieldValue('deletePolicy', {
          action: 'DELETE',
          maxDeleteRows: 1000,
          maxDeleteRatio: 0.2,
        });
        onProgrammaticChange();
      },
    });
  };

  return (
    <>
      <Form.Item name="sourceTableName" label="来源表" rules={[{ required: true, message: '请选择来源表' }]}>
        <Select
          disabled={!validationReady}
          placeholder={validationReady ? '选择上游有界表' : '等待 Task Engine 计算上游表'}
          options={sourceOptions}
        />
      </Form.Item>

      {targetSelector}
      <OutputFieldMappingFields
        sourceColumns={sourceTable?.columns ?? []}
        targetColumns={targetColumns}
        targetFieldNames={targetFieldNames}
        primaryKeyColumns={uniqueKeys.find((key) => key.type === 'PRIMARY_KEY')?.columns ?? []}
        initialMappings={initialConfiguration.columnMappings}
        sourceReady={Boolean(validationReady && sourceTable)}
        targetReady={targetColumns.length > 0}
        targetLoading={targetMetadataLoading}
        keyColumns={keyColumns}
        keyLabel="Snapshot Key"
        onProgrammaticChange={onProgrammaticChange}
      />

      <Form.Item
        name="keyColumns"
        label={(
          <CanvasInspectorFieldLabel
            label="对比 Key"
            tooltip="由实施人员明确指定，不要求匹配数据库唯一约束。运行时会校验来源和目标 Key 非空且各自唯一；下拉项中的“数据库推荐”仅表示该字段属于现有唯一约束。"
          />
        )}
        rules={[
          { required: true, message: '请选择 1 至 32 个目标字段作为复合 Key' },
          {
            validator: async (_, value: string[] | undefined) => {
              if ((value?.length ?? 0) > 32) throw new Error('复合 Key 不能超过 32 个字段');
              const unavailable = (value ?? []).filter((column) => {
                const target = targetByName.get(column);
                return !target || target.geometry !== null || target.autoIncrement
                  || target.generated || !mappedTargetNames.has(column);
              });
              if (unavailable.length > 0) throw new Error('Key 包含未映射或不允许使用的目标字段');
            },
          },
        ]}
      >
        <Select
          mode="multiple"
          maxCount={32}
          loading={targetMetadataLoading}
          placeholder="选择目标字段作为复合 Key"
          options={retainedKeyOptions}
        />
      </Form.Item>
      <Form.Item name={['deletePolicy', 'action']} hidden><Select /></Form.Item>
      <div className="canvas-snapshot-delete-switch">
        <CanvasInspectorFieldLabel
          label="删除目标独有行"
          tooltip="默认保留。只有来源代表目标表完整快照时才能启用；启用时还会应用空来源拦截和删除熔断阈值。"
        />
        <Switch
          checked={deleteAction === 'DELETE'}
          checkedChildren="DELETE"
          unCheckedChildren="KEEP"
          onChange={changeDeletePolicy}
        />
      </div>
      {deleteAction === 'DELETE' && (
        <Card size="small" className="canvas-snapshot-delete-policy" title="删除熔断">
          <Alert
            showIcon
            type="warning"
            className="canvas-compact-risk-alert"
            title="空来源禁止删除；候选删除数超过任一阈值时，整次同步在 DML 前失败。"
          />
          <Form.Item
            name={['deletePolicy', 'maxDeleteRows']}
            label="最大删除行数"
            rules={[{ required: true, message: '请输入最大删除行数' }]}
          >
            <InputNumber min={1} precision={0} className="canvas-snapshot-number-input" addonAfter="行" />
          </Form.Item>
          <Form.Item
            name={['deletePolicy', 'maxDeleteRatio']}
            label="最大删除比例"
            rules={[{ required: true, message: '请输入最大删除比例' }]}
            extra={typeof deleteRatio === 'number' ? `当前为 ${(deleteRatio * 100).toFixed(2)}%` : undefined}
          >
            <InputNumber min={0.000001} max={1} step={0.05} className="canvas-snapshot-number-input" />
          </Form.Item>
        </Card>
      )}
    </>
  );
};
