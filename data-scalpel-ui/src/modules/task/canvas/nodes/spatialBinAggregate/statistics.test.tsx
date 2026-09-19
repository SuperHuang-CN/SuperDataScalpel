import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { Modal } from 'antd';
import { createRef } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CanvasNodeType, type SpatialBinAggregateConfiguration } from '../../canvasTypes';
import { parseCanvasDefinition } from '../../canvasDefinitionIO';
import { createSpatialBinAggregateConfiguration } from '../nodeDefaults';
import type { CanvasNodeInspectorHandle } from '../nodeSpec';
import Inspector from './inspector';

afterEach(() => { Modal.destroyAll(); cleanup(); });
const id = '11111111-1111-4111-8111-111111111111';
const layout = { x: 0, y: 0, width: 360, height: 224 };
const definition = (configuration: unknown, schemaMinorVersion = 34) => ({ schemaVersion: 4, schemaMinorVersion,
  nodes: [{ id, type: CanvasNodeType.SpatialBinAggregate, name: '格网', layout, configuration }], edges: [] });

const selectOption = async (fieldLabel: string, optionLabel: string) => {
  fireEvent.mouseDown(screen.getByRole('combobox', { name: fieldLabel }));
  const option = await waitFor(() => {
    const visibleOption = screen.getAllByText(optionLabel).find((element) => {
      const dropdown = element.closest<HTMLElement>('.ant-select-dropdown');
      return dropdown && window.getComputedStyle(dropdown).pointerEvents !== 'none';
    });
    if (!visibleOption) throw new Error(`未找到可见选项：${optionLabel}`);
    return visibleOption;
  });
  fireEvent.click(option);
};

function mount(configuration: SpatialBinAggregateConfiguration) {
  const apply = vi.fn(); const ref = createRef<CanvasNodeInspectorHandle>();
  render(<Inspector node={{ id, type: CanvasNodeType.SpatialBinAggregate, name: '格网', layout, configuration }}
    executionMode="BATCH" validation={undefined} validationUnavailableMessage={null} onApply={apply} onDirtyChange={vi.fn()} inspectorRef={ref} />);
  return { apply, ref };
}

describe('bin field statistics', () => {
  it('gates Count Field and Any at 4.34 without changing Count or gap drafts', () => {
    for (const kind of ['COUNT_FIELD', 'ANY'] as const) {
      const c = createSpatialBinAggregateConfiguration(); c.statistics.push({ statisticId: id, kind, sourceColumnName: 'label', outputColumnName: 'result' });
      const result = parseCanvasDefinition(definition(c)); expect(result.success).toBe(true);
      if (result.success) expect(result.definition.nodes[0].configuration).toEqual(c);
      expect(parseCanvasDefinition(definition(c, 33)).success).toBe(false);
    }
    const old = createSpatialBinAggregateConfiguration();
    old.temporalSlicing = { timeColumnName: 'time', interval: 2, intervalUnit: 'MINUTES', repeatInterval: 10, repeatIntervalUnit: 'MINUTES',
      referenceTime: '1970-01-01T00:00:00Z', timeZone: 'UTC', windowStartColumnName: 'start', windowEndColumnName: 'end' };
    expect(parseCanvasDefinition(definition(old, 33)).success).toBe(true);
  });

  it('cancels isolated edits and permits saving invalid business drafts', async () => {
    const c = createSpatialBinAggregateConfiguration(); const { apply, ref } = mount(c);
    fireEvent.click(screen.getByRole('button', { name: '设置格网统计项' }));
    let dialog = await screen.findByRole('dialog');
    fireEvent.change(within(dialog).getByRole('textbox', { name: '统计输出字段 1' }), { target: { value: 'discard' } });
    fireEvent.click(within(dialog).getByRole('button', { name: /取\s*消/ }));
    await act(async () => { await ref.current?.apply(); }); expect(apply.mock.calls[0][0].configuration).toEqual(c);
    fireEvent.click(screen.getByRole('button', { name: '设置格网统计项' }));
    dialog = await screen.findByRole('dialog');
    fireEvent.change(within(dialog).getByRole('textbox', { name: '统计输出字段 1' }), { target: { value: '' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '添加统计' }));
    await selectOption('统计类型 2', 'ANY · 字符串样本');
    fireEvent.click(within(dialog).getByRole('button', { name: '保存统计草稿' }));
    await act(async () => { expect(await ref.current?.apply()).toBe(true); });
    expect(apply.mock.calls[1][0].configuration.statistics[0].outputColumnName).toBe('');
    expect(apply.mock.calls[1][0].configuration.statistics[1]).toMatchObject({ kind: 'ANY', sourceColumnName: null });
    expect(c.statistics).toHaveLength(1);
  });

  it('restores a field when toggling Count and preserves unavailable fields through sorting', async () => {
    const c = createSpatialBinAggregateConfiguration();
    c.statistics.push({ statisticId: id, kind: 'COUNT_FIELD', sourceColumnName: 'unavailable', outputColumnName: 'non_null' });
    const { apply, ref } = mount(c);
    fireEvent.click(screen.getByRole('button', { name: '设置格网统计项' }));
    const dialog = await screen.findByRole('dialog');
    await selectOption('统计类型 2', 'COUNT · 点数');
    await selectOption('统计类型 2', 'COUNT_FIELD · 非空数');
    fireEvent.click(within(dialog).getByRole('button', { name: '上移统计 2' }));
    fireEvent.click(within(dialog).getByRole('button', { name: '保存统计草稿' }));
    await act(async () => { await ref.current?.apply(); });
    expect(apply.mock.calls[0][0].configuration.statistics).toEqual([c.statistics[1], c.statistics[0]]);
  });
});
