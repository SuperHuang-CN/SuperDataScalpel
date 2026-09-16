import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { Modal } from 'antd';
import { createRef } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CanvasNodeType, type CanvasColumnSchema } from '../canvasTypes';
import { parseCanvasDefinition } from '../canvasDefinitionIO';
import { createTrackFindDwellConfiguration, createTrackReconstructConfiguration } from './nodeDefaults';
import type { CanvasNodeInspectorHandle } from './nodeSpec';
import DwellInspector from './trackFindDwell/inspector';
import ReconstructInspector from './trackReconstruct/inspector';
import { TrackSummaryEditor } from './trackShared';

afterEach(() => { Modal.destroyAll(); cleanup(); });
const id = '11111111-1111-4111-8111-111111111111';
const layout = { x: 0, y: 0, width: 360, height: 224 };
const summary = () => ({ statisticId: id, kind: 'COUNT_FIELD' as const, sourceColumnName: 'unavailable', outputColumnName: 'non_null' });
const definition = (type: CanvasNodeType, configuration: unknown, schemaMinorVersion = 35) => ({ schemaVersion: 4, schemaMinorVersion,
  nodes: [{ id, type, name: '轨迹', layout, configuration }], edges: [] });

function mount(dwell: boolean) {
  const apply = vi.fn(); const ref = createRef<CanvasNodeInspectorHandle>();
  const props = { executionMode: 'BATCH' as const, validation: undefined, validationUnavailableMessage: null,
    onApply: apply, onDirtyChange: vi.fn(), inspectorRef: ref };
  if (dwell) {
    const c = createTrackFindDwellConfiguration(); c.summaryStatistics = [summary()];
    render(<DwellInspector {...props} node={{ id, type: CanvasNodeType.TrackFindDwell, name: '驻留', layout, configuration: c }} />);
    return { apply, ref, c };
  }
  const c = createTrackReconstructConfiguration(); c.summaryStatistics = [summary()];
  render(<ReconstructInspector {...props} node={{ id, type: CanvasNodeType.TrackReconstruct, name: '重建', layout, configuration: c }} />);
  return { apply, ref, c };
}

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

describe('track field statistics', () => {
  it('gates new kinds at 4.35 including inactive dwell summaries and rejects malformed structures', () => {
    for (const type of [CanvasNodeType.TrackFindDwell, CanvasNodeType.TrackReconstruct]) {
      for (const kind of ['COUNT_FIELD', 'ANY'] as const) {
        const c = type === CanvasNodeType.TrackFindDwell ? createTrackFindDwellConfiguration() : createTrackReconstructConfiguration();
        c.summaryStatistics = [{ ...summary(), kind }];
        if ('rangeOptions' in c && c.rangeOptions) c.rangeOptions.resultMode = 'ALL_FEATURES';
        const parsed = parseCanvasDefinition(definition(type, c)); expect(parsed.success).toBe(true);
        if (parsed.success) expect(parsed.definition.nodes[0].configuration).toEqual(c);
        expect(parseCanvasDefinition(definition(type, c, 34)).success).toBe(false);
        expect(parseCanvasDefinition(definition(type, { ...c, summaryStatistics: [{ ...summary(), kind: 'UNKNOWN' }] })).success).toBe(false);
        expect(parseCanvasDefinition(definition(type, { ...c, summaryStatistics: {} })).success).toBe(false);
        c.summaryStatistics = [{ ...summary(), kind: 'COUNT', sourceColumnName: null }];
        expect(parseCanvasDefinition(definition(type, c, 34)).success).toBe(true);
      }
    }
  });

  it.each([false, true])('isolates cancel, retains invalid fields and saves an invalid draft (dwell=%s)', async dwell => {
    const { apply, ref, c } = mount(dwell);
    const openName = dwell ? '设置驻留片段汇总' : '设置轨迹片段汇总';
    fireEvent.click(screen.getByRole('button', { name: openName }));
    fireEvent.change(screen.getByRole('textbox', { name: '汇总输出字段 1' }), { target: { value: 'discard' } });
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /取\s*消/ }));
    await act(async () => { await ref.current?.apply(); }); expect(apply.mock.calls[0][0].configuration).toEqual(c);
    fireEvent.click(screen.getByRole('button', { name: openName }));
    await selectOption('汇总类型 1', 'COUNT · 点数');
    await selectOption('汇总类型 1', 'COUNT_FIELD · 非空数');
    fireEvent.change(screen.getByRole('textbox', { name: '汇总输出字段 1' }), { target: { value: '' } });
    fireEvent.click(screen.getByRole('button', { name: '添加汇总' }));
    fireEvent.click(screen.getByRole('button', { name: '上移汇总 2' }));
    fireEvent.click(screen.getByRole('button', { name: '保存汇总草稿' }));
    await act(async () => { expect(await ref.current?.apply()).toBe(true); });
    expect(apply.mock.calls[1][0].configuration.summaryStatistics[1]).toMatchObject({ kind: 'COUNT_FIELD', sourceColumnName: 'unavailable', outputColumnName: '' });
    expect(c.summaryStatistics).toEqual([summary()]);
  }, 30000);

  it('requires confirmation before deleting a summary', async () => {
    const { apply, ref } = mount(false);
    const confirmation = () => {
      // Ant Design's test environment uses the same generated title ID for all dialogs.
      const dialog = screen.getAllByRole('dialog').find(element => within(element).queryAllByText('删除汇总 non_null？').length > 0);
      if (!dialog) throw new Error('删除确认未显示');
      return dialog;
    };
    fireEvent.click(screen.getByRole('button', { name: '设置轨迹片段汇总' }));
    fireEvent.click(screen.getByRole('button', { name: '删除汇总 1' }));
    const confirm = confirmation();
    fireEvent.click(within(confirm).getByRole('button', { name: /取\s*消/ }));
    await waitFor(() => expect(screen.queryAllByText('删除汇总 non_null？')).toHaveLength(0));
    expect(screen.getByRole('textbox', { name: '汇总输出字段 1' })).toHaveValue('non_null');
    fireEvent.click(screen.getByRole('button', { name: '删除汇总 1' }));
    fireEvent.click(within(confirmation()).getByRole('button', { name: /删\s*除/ }));
    fireEvent.click(screen.getByRole('button', { name: '保存汇总草稿' }));
    await act(async () => { await ref.current?.apply(); });
    expect(apply.mock.calls[0][0].configuration.summaryStatistics).toEqual([]);
  });

  it('distinguishes numeric Any candidates while preserving invalid values', () => {
    const amount: CanvasColumnSchema = { name: 'amount', fieldType: 'DECIMAL', length: null, precision: 12, scale: 2,
      nullable: true, defaultValue: null, autoIncrement: false, generated: false, comment: null, geometry: null };
    const value = [{ ...summary(), kind: 'ANY' as const, sourceColumnName: 'amount' }];
    const { rerender } = render(<TrackSummaryEditor value={value} columns={[amount]} validationAvailable onChange={vi.fn()} />);
    expect(screen.getByText('amount（已失效）')).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: '汇总来源字段 1' }).closest('.ant-select')).toHaveClass('ant-select-status-error');
    rerender(<TrackSummaryEditor value={value} columns={[amount]} validationAvailable allowNumericAny onChange={vi.fn()} />);
    expect(screen.getByText('amount · DECIMAL')).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: '汇总来源字段 1' }).closest('.ant-select')).not.toHaveClass('ant-select-status-error');
    expect(value[0].sourceColumnName).toBe('amount');
  });
});
