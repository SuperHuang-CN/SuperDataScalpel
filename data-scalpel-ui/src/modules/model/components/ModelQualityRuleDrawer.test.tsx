import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { DataModelField } from '../model/dataModel';
import type { ModelQualityRule } from '../model/modelQualityRule';

vi.mock('../hooks/useDataModels', () => ({
  useDataModels: () => ({
    data: { content: [] },
    isFetching: false,
  }),
  useDataModel: () => ({
    data: undefined,
    isFetching: false,
    isError: false,
  }),
}));

import { ModelQualityRuleDrawer } from './ModelQualityRuleDrawer';

const fields: DataModelField[] = [
  {
    id: 'region-code-id',
    modelId: 'model-id',
    code: 'region_code',
    name: '行政区划编码',
    fieldType: 'STRING',
    length: 12,
    precision: null,
    scale: null,
    nullable: false,
    primaryKey: true,
    sortOrder: 10,
    description: null,
    createdAt: '2026-08-01T00:00:00Z',
    updatedAt: '2026-08-01T00:00:00Z',
  },
  {
    id: 'status-id',
    modelId: 'model-id',
    code: 'status',
    name: '状态',
    fieldType: 'STRING',
    length: 16,
    precision: null,
    scale: null,
    nullable: true,
    primaryKey: false,
    sortOrder: 20,
    description: null,
    createdAt: '2026-08-01T00:00:00Z',
    updatedAt: '2026-08-01T00:00:00Z',
  },
];

const storedRule: ModelQualityRule = {
  id: 'quality-rule-id',
  modelId: 'model-id',
  name: '启用行政区划必须有编码',
  description: '仅检查启用状态的数据。',
  ruleType: 'CONDITIONAL_NOT_NULL',
  severity: 'CRITICAL',
  enabled: false,
  invalidCode: null,
  invalidReason: null,
  definition: {
    type: 'CONDITIONAL_NOT_NULL',
    targetFieldId: 'region-code-id',
    condition: {
      fieldId: 'status-id',
      operator: 'EQ',
      values: ['ENABLED'],
    },
    tolerance: { metric: 'COUNT', value: 0 },
  },
  fields: [],
  referenceTarget: null,
  createdAt: '2026-08-01T00:00:00Z',
  updatedAt: '2026-08-01T00:00:00Z',
};

const renderDrawer = (
  rule: ModelQualityRule | null,
  onSubmit = vi.fn(),
) => render(
  <ModelQualityRuleDrawer
    open
    rule={rule}
    fields={fields}
    submitting={false}
    onClose={vi.fn()}
    onSubmit={onSubmit}
  />,
);

describe('ModelQualityRuleDrawer', () => {
  afterEach(() => cleanup());

  it('uses the three-section create layout and exposes the initial enabled state', () => {
    renderDrawer(null);

    expect(screen.getByText('新增质量规则')).toBeInTheDocument();
    expect(screen.getByText('规则信息')).toBeInTheDocument();
    expect(screen.getByText('检查定义')).toBeInTheDocument();
    expect(screen.getByText('异常容忍')).toBeInTheDocument();
    expect(document.querySelectorAll('.model-quality-rule-form .data-model-form-section')).toHaveLength(3);
    expect(screen.getByLabelText('保存后启用质量规则')).toBeChecked();
    expect(screen.getByText('重要 · 启用')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '创建规则' })).toBeInTheDocument();
  });

  it('restores the edit state, locks the rule type and preserves the stored enabled state', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    renderDrawer(storedRule, onSubmit);

    expect(screen.getByText('修改质量规则')).toBeInTheDocument();
    expect(screen.getByLabelText('规则类型')).toBeDisabled();
    expect(screen.queryByLabelText('保存后启用质量规则')).not.toBeInTheDocument();
    expect(screen.getByLabelText('规则名称')).toHaveValue(storedRule.name);
    expect(screen.getByLabelText('规则说明')).toHaveValue(storedRule.description);
    expect(screen.getByLabelText('条件值')).toHaveValue('ENABLED');
    expect(screen.getByText('关键 · 停用')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '保存修改' }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith({
      name: storedRule.name,
      description: storedRule.description,
      severity: 'CRITICAL',
      enabled: false,
      definition: storedRule.definition,
    }));
  });

  it('removes the tolerance section when a no-tolerance rule type is selected', async () => {
    const user = userEvent.setup();
    renderDrawer(null);

    await user.click(screen.getByRole('combobox', { name: '规则类型' }));
    await user.click((await screen.findAllByText('最小行数')).at(-1) as HTMLElement);

    expect(screen.getByRole('spinbutton', { name: '最小行数' })).toBeInTheDocument();
    expect(screen.queryByText('异常容忍')).not.toBeInTheDocument();
  });
});
