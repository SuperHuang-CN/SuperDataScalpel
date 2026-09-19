import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Modal } from 'antd';
import { createRef } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CanvasNodeType } from '../../canvasTypes';
import { createSpatialOverlayConfiguration } from '../nodeDefaults';
import type { CanvasNodeInspectorComponentProps, CanvasNodeInspectorHandle } from '../nodeSpec';
import Inspector from './inspector';

afterEach(() => { Modal.destroyAll(); cleanup(); });
function mount() {
  const configuration = createSpatialOverlayConfiguration();
  configuration.outputColumns = [{ sourceSide: 'RIGHT', sourceColumnName: 'id', outputColumnName: 'saved_right_id', included: true }];
  const ref = createRef<CanvasNodeInspectorHandle>();
  const apply = vi.fn<CanvasNodeInspectorComponentProps<typeof CanvasNodeType.SpatialOverlay>['onApply']>();
  render(<Inspector node={{ id: '11111111-1111-4111-8111-111111111111', type: CanvasNodeType.SpatialOverlay,
    name: '叠加', layout: { x: 0, y: 0, width: 376, height: 216 }, configuration }} executionMode="BATCH"
    validation={undefined} validationUnavailableMessage={null} onApply={apply} onDirtyChange={vi.fn()} inspectorRef={ref} />);
  return { ref, apply, configuration };
}

describe('overlay inspector drafts', () => {
  it('preserves unopened projection and permits invalid ERASE without clearing right fields', async () => {
    const { ref, apply, configuration } = mount();
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    await act(async () => { expect(await ref.current?.apply()).toBe(true); });
    expect(apply.mock.calls[0][0].configuration).toEqual(configuration);
    fireEvent.mouseDown(screen.getByRole('combobox', { name: '叠加方式' }));
    await user.click(await screen.findByText('擦除', { selector: '.ant-select-item-option-content span' }));
    expect(screen.getByText(/当前有 1 个字段需要排除/)).toBeTruthy();
    await act(async () => { expect(await ref.current?.apply()).toBe(true); });
    expect(apply.mock.calls[1][0].configuration).toMatchObject({ operation: 'ERASE', outputColumns: configuration.outputColumns });
  });
  it('requires confirmation to change geometry policy and retains all projection settings', async () => {
    const { ref, apply, configuration } = mount();
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    fireEvent.mouseDown(screen.getByRole('combobox', { name: '叠加几何输出' }));
    await user.click(await screen.findByText('旧版 · 通用 Geometry', { selector: '.ant-select-item-option-content' }));
    expect(await screen.findByText('切换几何输出策略？', { selector: '.ant-modal-confirm-title' })).toBeTruthy();
    fireEvent.click(await screen.findByRole('button', { name: '确认切换' }));
    await act(async () => { expect(await ref.current?.apply()).toBe(true); });
    expect(apply.mock.calls[0][0].configuration).toEqual({ ...configuration, geometryPolicy: 'LEGACY_GEOMETRY' });
  });
});
