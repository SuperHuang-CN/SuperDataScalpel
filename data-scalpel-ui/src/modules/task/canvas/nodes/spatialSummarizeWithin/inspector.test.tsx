import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Modal } from 'antd';
import { createRef } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CanvasNodeType } from '../../canvasTypes';
import { createSpatialSummarizeWithinConfiguration } from '../nodeDefaults';
import type { CanvasNodeInspectorComponentProps, CanvasNodeInspectorHandle } from '../nodeSpec';
import Inspector from './inspector';

afterEach(() => { Modal.destroyAll(); cleanup(); });
function mount(kind: 'MEAN' | 'VARIANCE' | 'STDDEV' = 'MEAN') {
  const configuration = createSpatialSummarizeWithinConfiguration();
  configuration.statistics = [{ statisticId: crypto.randomUUID(), kind, sourceColumnName: 'amount',
    outputColumnName: 'weighted', valueTreatment: 'ORIGINAL_VALUE', weighting: 'INTERSECTION_FRACTION' }];
  const ref = createRef<CanvasNodeInspectorHandle>();
  const apply = vi.fn<CanvasNodeInspectorComponentProps<typeof CanvasNodeType.SpatialSummarizeWithin>['onApply']>();
  render(<Inspector node={{ id: '11111111-1111-4111-8111-111111111111', type: CanvasNodeType.SpatialSummarizeWithin,
    name: '区域汇总', layout: { x: 0, y: 0, width: 376, height: 224 }, configuration }} executionMode="BATCH"
    validation={undefined} validationUnavailableMessage={null} onApply={apply} onDirtyChange={vi.fn()} inspectorRef={ref} />);
  return { ref, apply, configuration };
}

describe('within statistic inspector', () => {
  it.each(['VARIANCE', 'STDDEV'] as const)('retains weighted %s through cancel and invalid draft save', async kind => {
    const { ref, apply, configuration } = mount(kind);
    await act(async () => { expect(await ref.current?.apply()).toBe(true); });
    expect(apply.mock.calls[0][0].configuration.statistics).toEqual(configuration.statistics);
    fireEvent.click(screen.getByRole('button', { name: '设置区域统计项' }));
    let modal = await screen.findByRole('dialog');
    fireEvent.change(within(modal).getByRole('textbox', { name: '统计 1 输出字段' }), { target: { value: 'discard' } });
    fireEvent.click(within(modal).getByRole('button', { name: /取\s*消/ }));
    await act(async () => { await ref.current?.apply(); });
    expect(apply.mock.calls[1][0].configuration.statistics).toEqual(configuration.statistics);
    fireEvent.click(screen.getByRole('button', { name: '设置区域统计项' })); modal = await screen.findByRole('dialog');
    fireEvent.change(within(modal).getByRole('textbox', { name: '统计 1 输出字段' }), { target: { value: '' } });
    fireEvent.click(within(modal).getByRole('button', { name: /完\s*成/ }));
    await act(async () => { expect(await ref.current?.apply()).toBe(true); });
    expect(apply.mock.calls[2][0].configuration.statistics[0]).toMatchObject({ kind, weighting: 'INTERSECTION_FRACTION', sourceColumnName: 'amount', outputColumnName: '' });
  });
  it('saves linked settings through a confirmed legacy switch without clearing them', async () => {
    const { ref, apply } = mount();
    await userEvent.click(screen.getByRole('switch', { name: '启用分组汇总' }));
    await waitFor(() => expect(screen.getByRole('button', { name: '设置区域分组结果' })).toBeEnabled());
    await userEvent.click(screen.getByRole('button', { name: '设置区域分组结果' }));
    const modal = await screen.findByRole('dialog');
    fireEvent.change(within(modal).getByRole('textbox', { name: '关联组表名' }), { target: { value: 'saved_groups' } });
    fireEvent.mouseDown(within(modal).getByRole('combobox', { name: '分组结果模式' }));
    await userEvent.click(await screen.findByText('旧版扁平分组表', { selector: '.ant-select-item-option-content' }));
    fireEvent.click(await screen.findByRole('button', { name: '确认切换' }));
    fireEvent.click(within(modal).getByRole('button', { name: '保存草稿' }));
    await act(async () => { expect(await ref.current?.apply()).toBe(true); });
    expect(apply.mock.calls[0][0].configuration.groupResult).toMatchObject({ mode: 'LEGACY_FLAT', outputTableName: 'saved_groups' });
    fireEvent.click(screen.getByRole('button', { name: '设置区域分组结果' }));
    const reopened = await screen.findByRole('dialog');
    fireEvent.mouseDown(within(reopened).getByRole('combobox', { name: '分组结果模式' }));
    await userEvent.click(await screen.findByText('主表 + 关联组表（推荐）', { selector: '.ant-select-item-option-content' }));
    fireEvent.click(await screen.findByRole('button', { name: '确认切换' }));
    expect(within(reopened).getByRole('textbox', { name: '关联组表名' })).toHaveValue('saved_groups');
  });
  it('preserves unopened statistic configuration and shows its count', async () => {
    const { ref, apply, configuration } = mount();
    await act(async () => { expect(await ref.current?.apply()).toBe(true); });
    expect(apply).toHaveBeenCalledWith(expect.objectContaining({ configuration }));
    fireEvent.click(screen.getByRole('button', { name: '设置区域统计项' }));
    const modal = await screen.findByRole('dialog');
    expect(within(modal).getByRole('textbox', { name: '统计 1 输出字段' })).toHaveValue('weighted');
  });
  it('retains both mode settings in an invalid draft and permits applying it', async () => {
    const { ref, apply } = mount();
    fireEvent.click(screen.getByRole('button', { name: '设置区域统计项' }));
    const modal = await screen.findByRole('dialog');
    fireEvent.mouseDown(within(modal).getByRole('combobox', { name: '统计 1 数量处理' }));
    await userEvent.click(await screen.findByText('总量分摊', { selector: '.ant-select-item-option-content' }));
    fireEvent.change(within(modal).getByRole('textbox', { name: '统计 1 输出字段' }), { target: { value: 'draft_result' } });
    expect(within(modal).getByRole('button', { name: /统计 1 有.*配置问题/ })).toBeTruthy();
    fireEvent.click(within(modal).getByRole('button', { name: /完\s*成/ }));
    await act(async () => { expect(await ref.current?.apply()).toBe(true); });
    const config = apply.mock.calls[0][0].configuration;
    expect(config.statistics[0]).toMatchObject({ outputColumnName: 'draft_result',
      valueTreatment: 'APPORTION_TOTAL', weighting: 'INTERSECTION_FRACTION', sourceColumnName: 'amount' });
  });
});
