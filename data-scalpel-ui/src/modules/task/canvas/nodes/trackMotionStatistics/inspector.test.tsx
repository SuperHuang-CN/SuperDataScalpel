import { createUuid } from '../../../../../shared/browser/createUuid';
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Modal } from 'antd';
import { createRef } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CanvasNodeType } from '../../canvasTypes';
import { createTrackMotionStatisticsConfiguration } from '../nodeDefaults';
import type { CanvasNodeInspectorComponentProps, CanvasNodeInspectorHandle } from '../nodeSpec';
import Inspector from './inspector';

afterEach(() => { Modal.destroyAll(); cleanup(); });
function mount(configuration = createTrackMotionStatisticsConfiguration()) {
  const ref = createRef<CanvasNodeInspectorHandle>();
  const apply = vi.fn<CanvasNodeInspectorComponentProps<typeof CanvasNodeType.TrackMotionStatistics>['onApply']>();
  render(<Inspector node={{ id: '11111111-1111-4111-8111-111111111111', type: CanvasNodeType.TrackMotionStatistics,
    name: '运动', layout: { x: 0, y: 0, width: 360, height: 224 }, configuration }} executionMode="BATCH"
    validation={undefined} validationUnavailableMessage={null} onApply={apply} onDirtyChange={vi.fn()} inspectorRef={ref} />);
  return { ref, apply };
}
describe('motion window inspector', () => {
  it('saves all unopened advanced fields in an incomplete draft', async () => {
    const configuration = createTrackMotionStatisticsConfiguration();
    const { ref, apply } = mount(configuration);
    await act(async () => { expect(await ref.current?.apply()).toBe(true); });
    expect(apply).toHaveBeenCalledWith(expect.objectContaining({ configuration }));
  });
  it('edits a group output and exposes both Idle thresholds while preserving hidden settings', async () => {
    const { ref, apply } = mount();
    fireEvent.click(screen.getByRole('button', { name: '设置运动指标' }));
    const modal = await screen.findByRole('dialog');
    await userEvent.click(within(modal).getByRole('checkbox', { name: '静止' }));
    fireEvent.change(within(modal).getByRole('textbox', { name: '输出字段 TOT_DISTANCE' }), { target: { value: 'travelled' } });
    fireEvent.click(within(modal).getByRole('button', { name: /完\s*成/ }));
    const threshold = await screen.findByRole('spinbutton', { name: '静止时间阈值' });
    fireEvent.change(threshold, { target: { value: '30' } }); fireEvent.blur(threshold);
    await act(async () => { await ref.current?.apply(); });
    const config = apply.mock.calls[0][0].configuration;
    expect(config.windowOptions?.idleTimeThreshold).toBe(30);
    expect(config.windowOptions?.statistics).toHaveLength(12);
    expect(config.windowOptions?.statistics.find(s => s.kind === 'TOT_DISTANCE')?.outputColumnName).toBe('travelled');
    expect(config.windowOptions?.speedUnit).toBe('METERS_PER_SECOND');
  });
  it('confirms changing legacy meaning and preserves old metrics', async () => {
    const configuration = createTrackMotionStatisticsConfiguration();
    delete configuration.motionSemantics; delete configuration.windowOptions;
    configuration.metrics = [{ kind: 'BEARING', metricId: createUuid(), outputColumnName: 'old_bearing', outputUnit: 'DEGREES' }];
    const { ref, apply } = mount(configuration);
    fireEvent.mouseDown(screen.getByRole('combobox', { name: '计算语义' }));
    await userEvent.click(await screen.findByText('观测历史窗口', { selector: '.ant-select-item-option-content' }));
    expect(screen.queryByRole('spinbutton', { name: '历史窗口观测数' })).toBeNull();
    fireEvent.click(await screen.findByRole('button', { name: '确认切换' }));
    await waitFor(() => expect(screen.getByRole('spinbutton', { name: '历史窗口观测数' })).toBeTruthy());
    await act(async () => { await ref.current?.apply(); });
    expect(apply).toHaveBeenCalledWith(expect.objectContaining({ configuration: expect.objectContaining({
      motionSemantics: 'OBSERVATION_WINDOW', metrics: configuration.metrics,
      windowOptions: expect.objectContaining({ observationCount: 3 }),
    }) }));
  });
});
