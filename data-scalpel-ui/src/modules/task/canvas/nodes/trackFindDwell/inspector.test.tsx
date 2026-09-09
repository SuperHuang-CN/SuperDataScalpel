import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Modal } from 'antd';
import { createRef } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CanvasNodeType } from '../../canvasTypes';
import { createTrackFindDwellConfiguration } from '../nodeDefaults';
import type { CanvasNodeInspectorHandle } from '../nodeSpec';
import Inspector from './inspector';

afterEach(() => { Modal.destroyAll(); cleanup(); });
function mount(configuration = createTrackFindDwellConfiguration()) {
  const ref = createRef<CanvasNodeInspectorHandle>();
  const apply = vi.fn();
  render(<Inspector node={{ id: '11111111-1111-4111-8111-111111111111', type: CanvasNodeType.TrackFindDwell,
    name: '驻留', layout: { x: 0, y: 0, width: 352, height: 216 }, configuration }} executionMode="BATCH"
    validation={undefined} validationUnavailableMessage={null} onApply={apply} onDirtyChange={vi.fn()} inspectorRef={ref} />);
  return { ref, apply };
}
describe('dwell inspector', () => {
  it('retains unopened modal fields and permits incomplete drafts', async () => {
    const configuration = createTrackFindDwellConfiguration();
    const { ref, apply } = mount(configuration);
    await act(async () => { expect(await ref.current?.apply()).toBe(true); });
    expect(apply).toHaveBeenCalledWith(expect.objectContaining({ configuration }));
  });
  it('switches output mode without dropping aggregate fields and edits the point flag', async () => {
    const configuration = createTrackFindDwellConfiguration();
    const { ref, apply } = mount(configuration);
    fireEvent.mouseDown(screen.getByRole('combobox', { name: '输出类型' }));
    await userEvent.click(await screen.findByText('全部点', { selector: '.ant-select-item-option-content' }));
    await waitFor(() => expect(screen.queryByText('片段汇总')).toBeNull());
    fireEvent.click(screen.getByRole('button', { name: '设置驻留结果字段' }));
    fireEvent.change(await screen.findByRole('textbox', { name: '驻留标记' }), { target: { value: 'dwell_flag' } });
    fireEvent.click(screen.getByRole('button', { name: /完\s*成/ }));
    await act(async () => { await ref.current?.apply(); });
    expect(apply).toHaveBeenCalledWith(expect.objectContaining({ configuration: expect.objectContaining({
      outputGeometryColumnName: configuration.outputGeometryColumnName, durationColumnName: configuration.durationColumnName,
      rangeOptions: expect.objectContaining({ resultMode: 'ALL_FEATURES', dwellFlagColumnName: 'dwell_flag',
        meanDistanceColumnName: 'mean_distance', durationUnit: 'MILLISECONDS' }),
    }) }));
  });
  it('requires confirmation to activate new semantics on old configurations', async () => {
    const configuration = createTrackFindDwellConfiguration();
    delete configuration.dwellSemantics; delete configuration.rangeOptions;
    const { ref, apply } = mount(configuration);
    fireEvent.mouseDown(screen.getByRole('combobox', { name: '识别语义' }));
    await userEvent.click(await screen.findByText('参考点与均值中心', { selector: '.ant-select-item-option-content' }));
    expect(screen.queryByText('输出类型')).toBeNull();
    fireEvent.click(await screen.findByRole('button', { name: '确认切换' }));
    await screen.findByRole('combobox', { name: '输出类型' });
    await act(async () => { await ref.current?.apply(); });
    expect(apply).toHaveBeenCalledWith(expect.objectContaining({ configuration: expect.objectContaining({
      dwellSemantics: 'REFERENCE_CENTER', rangeOptions: expect.objectContaining({ resultMode: 'MEAN_CENTERS' }),
    }) }));
  });
});
