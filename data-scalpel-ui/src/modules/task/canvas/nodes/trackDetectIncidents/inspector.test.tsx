import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { Modal } from 'antd';
import { createRef } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CanvasNodeType, type TrackDetectIncidentsConfiguration } from '../../canvasTypes';
import { createTrackDetectIncidentsConfiguration } from '../nodeDefaults';
import type { CanvasNodeInspectorHandle } from '../nodeSpec';
import Inspector from './inspector';

afterEach(() => { Modal.destroyAll(); cleanup(); });

function mount(configuration = createTrackDetectIncidentsConfiguration()) {
  const ref = createRef<CanvasNodeInspectorHandle>();
  const apply = vi.fn();
  const dirty = vi.fn();
  render(<Inspector node={{ id: '11111111-1111-4111-8111-111111111111', type: CanvasNodeType.TrackDetectIncidents,
    name: '事件', layout: { x: 0, y: 0, width: 320, height: 200 }, configuration }} executionMode="BATCH"
    validation={undefined} validationUnavailableMessage={null} onApply={apply} onDirtyChange={dirty} inspectorRef={ref} />);
  return { ref, apply, dirty };
}

describe('incident lifecycle inspector', () => {
  it('preserves condition and unopened output fields when applying an incomplete draft', async () => {
    const configuration = createTrackDetectIncidentsConfiguration();
    configuration.endCondition = { kind: 'GROUP', operator: 'OR', children: [] };
    const { ref, apply } = mount(configuration);
    await act(async () => { expect(await ref.current?.apply()).toBe(true); });
    expect(apply).toHaveBeenCalledWith(expect.objectContaining({ configuration: expect.objectContaining({
      incidentSemantics: 'CONDITION_LIFECYCLE', incidentStatusColumnName: 'incident_status',
      endCondition: configuration.endCondition,
    }) }));
  });

  it('updates nested boundary controls and retains them in the saved draft', async () => {
    const { ref, apply } = mount();
    fireEvent.click(screen.getByRole('button', { name: '设置轨迹边界' }));
    fireEvent.click(screen.getByRole('switch', { name: '启用固定时间边界' }));
    const interval = await screen.findByRole('spinbutton', { name: '固定边界周期' });
    fireEvent.change(interval, { target: { value: '2' } });
    fireEvent.blur(interval);
    fireEvent.click(screen.getByRole('button', { name: /完\s*成/ }));
    await act(async () => { await ref.current?.apply(); });
    expect(apply).toHaveBeenCalledWith(expect.objectContaining({ configuration: expect.objectContaining({
      boundaries: expect.objectContaining({ fixedTimeBoundary: { interval: 2, unit: 'DAYS', timeZone: 'UTC', referenceTime: null } }),
    }) }));
  });

  it('requires confirmation before switching legacy meaning and refreshes dependent fields', async () => {
    const configuration: TrackDetectIncidentsConfiguration = { ...createTrackDetectIncidentsConfiguration(),
      incidentSemantics: 'LEGACY', incidentStatusColumnName: null };
    const { ref, apply } = mount(configuration);
    fireEvent.mouseDown(screen.getByRole('combobox', { name: '事件语义' }));
    const option = await screen.findByText('条件生命周期', { selector: '.ant-select-item-option-content' });
    await act(async () => { fireEvent.click(option); });
    expect(screen.queryByText('同时间顺序')).toBeNull();
    // Ant Design mounts the static confirmation in a separate asynchronously rendered root.
    fireEvent.click(await screen.findByRole('button', { name: '确认切换' }, { timeout: 5000 }));
    await waitFor(() => expect(screen.getByText('同时间顺序')).toBeTruthy());
    await act(async () => { await ref.current?.apply(); });
    expect(apply).toHaveBeenCalledWith(expect.objectContaining({ configuration: expect.objectContaining({
      incidentSemantics: 'CONDITION_LIFECYCLE', incidentStatusColumnName: 'incident_status',
    }) }));
  });
});
