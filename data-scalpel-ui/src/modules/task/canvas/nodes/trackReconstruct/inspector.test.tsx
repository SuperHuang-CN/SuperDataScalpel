import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Modal } from 'antd';
import { createRef } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CanvasNodeType, type TrackReconstructConfiguration } from '../../canvasTypes';
import { createTrackReconstructConfiguration } from '../nodeDefaults';
import type { CanvasNodeInspectorComponentProps, CanvasNodeInspectorHandle } from '../nodeSpec';
import Inspector from './inspector';

afterEach(() => { Modal.destroyAll(); cleanup(); });
function mount(configure?: (configuration: TrackReconstructConfiguration) => void) {
  const configuration = createTrackReconstructConfiguration();
  configuration.reconstruction!.orderByColumns = ['seq'];
  configuration.reconstruction!.splitExpression = { expression: 'old_value * 2 < value',
    bindings: [{ name: 'old_value', sourceColumnName: 'value', offset: -1 }], enabled: true };
  configure?.(configuration);
  const ref = createRef<CanvasNodeInspectorHandle>();
  const apply = vi.fn<CanvasNodeInspectorComponentProps<typeof CanvasNodeType.TrackReconstruct>['onApply']>();
  render(<Inspector node={{ id: '11111111-1111-4111-8111-111111111111', type: CanvasNodeType.TrackReconstruct,
    name: '重建', layout: { x: 0, y: 0, width: 352, height: 216 }, configuration }} executionMode="BATCH"
    validation={undefined} validationUnavailableMessage={null} onApply={apply} onDirtyChange={vi.fn()} inspectorRef={ref} />);
  return { ref, apply, configuration };
}

describe('reconstruction inspector', () => {
  it('cancels geodesic sampling edits and applies invalid sampling drafts without changing hidden line settings', async () => {
    const { ref, apply, configuration } = mount(c => {
      c.distanceMethod = 'GEODESIC';
      c.reconstruction!.areaGeometry = { enabled: true, bufferMode: 'NONE', bufferField: null, bufferExpression: null, bufferUnit: null };
    });
    await userEvent.click(screen.getByRole('button', { name: '设置面轨迹' }));
    fireEvent.change(screen.getByRole('spinbutton', { name: '面边界采样最大段长' }), { target: { value: '200' } });
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /取\s*消/ }));
    await act(async () => { await ref.current?.apply(); });
    expect(apply.mock.calls.at(-1)?.[0].configuration.reconstruction?.areaGeometry?.geodesicBoundary).toBeUndefined();
    await userEvent.click(screen.getByRole('button', { name: '设置面轨迹' }));
    fireEvent.change(screen.getByRole('spinbutton', { name: '面边界采样最大段长' }), { target: { value: '-1' } });
    fireEvent.click(screen.getByRole('button', { name: '保存面轨迹草稿' }));
    await act(async () => { expect(await ref.current?.apply()).toBe(true); });
    expect(apply.mock.calls.at(-1)?.[0].configuration.reconstruction).toMatchObject({
      pathGeometry: configuration.reconstruction?.pathGeometry,
      areaGeometry: { geodesicBoundary: { maximumSegmentLength: -1, maximumSegmentLengthUnit: 'METERS' } },
    });
  });
  it('confirms area shape changes, isolates modal drafts and restores hidden buffer expressions', async () => {
    const { ref, apply, configuration } = mount();
    const choose = async (name: string, text: string) => {
      fireEvent.mouseDown(screen.getByRole('combobox', { name }));
      await userEvent.click(await screen.findByText(text, { selector: '.ant-select-item-option-content' }));
    };
    await choose('轨迹输出形态', '面轨迹 · XY');
    fireEvent.click(await screen.findByRole('button', { name: /取\s*消/ }));
    await act(async () => { await ref.current?.apply(); });
    expect(apply.mock.calls.at(-1)?.[0].configuration).toEqual(configuration);
    await choose('轨迹输出形态', '面轨迹 · XY');
    fireEvent.click(await screen.findByRole('button', { name: '确认切换' }));
    await waitFor(() => expect(screen.queryByRole('combobox', { name: '轨迹路径几何' })).toBeNull());
    await userEvent.click(screen.getByRole('button', { name: '设置面轨迹' }));
    await choose('面轨迹缓冲距离来源', '受控数值表达式');
    fireEvent.change(screen.getByRole('textbox', { name: '轨迹缓冲距离表达式' }), { target: { value: 'discard_me' } });
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: /取\s*消/ }));
    await act(async () => { await ref.current?.apply(); });
    expect(apply.mock.calls.at(-1)?.[0].configuration.reconstruction?.areaGeometry?.bufferExpression).toBeNull();
    await userEvent.click(screen.getByRole('button', { name: '设置面轨迹' }));
    await choose('面轨迹缓冲距离来源', '受控数值表达式');
    fireEvent.change(screen.getByRole('textbox', { name: '轨迹缓冲距离表达式' }), { target: { value: 'radius * 2' } });
    await choose('面轨迹缓冲距离来源', '数值字段');
    fireEvent.click(screen.getByRole('button', { name: '保存面轨迹草稿' }));
    await act(async () => { expect(await ref.current?.apply()).toBe(true); });
    expect(apply.mock.calls.at(-1)?.[0].configuration.reconstruction?.areaGeometry).toMatchObject({ bufferMode: 'FIELD', bufferField: null, bufferExpression: 'radius * 2' });
    await choose('轨迹输出形态', '线轨迹');
    fireEvent.click(await screen.findByRole('button', { name: '确认切换' }));
    await act(async () => { await ref.current?.apply(); });
    expect(apply.mock.calls.at(-1)?.[0].configuration.reconstruction).toMatchObject({
      pathGeometry: configuration.reconstruction?.pathGeometry, areaGeometry: { enabled: false, bufferExpression: 'radius * 2' },
    });
    await choose('轨迹输出形态', '面轨迹 · XY');
    fireEvent.click(await screen.findByRole('button', { name: '确认切换' }));
    await userEvent.click(await screen.findByRole('button', { name: '设置面轨迹' }));
    await choose('面轨迹缓冲距离来源', '受控数值表达式');
    expect(screen.getByRole('textbox', { name: '轨迹缓冲距离表达式' })).toHaveValue('radius * 2');
  }, 60_000);
  it('preserves invalid geodesic drafts when hidden and after confirmed path switches', async () => {
    const { ref, apply, configuration } = mount();
    await userEvent.click(screen.getByText('测地线'));
    fireEvent.change(screen.getByRole('spinbutton', { name: '测地最大段长' }), { target: { value: '-2' } });
    await act(async () => { expect(await ref.current?.apply()).toBe(true); });
    expect(apply.mock.calls[0][0].configuration.reconstruction?.pathGeometry?.maximumGeodesicSegmentLength).toBe(-2);
    await userEvent.click(screen.getByText('平面'));
    expect(screen.queryByRole('spinbutton', { name: '测地最大段长' })).toBeNull();
    fireEvent.mouseDown(screen.getByRole('combobox', { name: '轨迹路径几何' }));
    await userEvent.click(await screen.findByText('旧版顶点连线', { selector: '.ant-select-item-option-content' }));
    fireEvent.click(await screen.findByRole('button', { name: '确认切换' }));
    await act(async () => { expect(await ref.current?.apply()).toBe(true); });
    expect(apply.mock.calls[1][0].configuration.reconstruction?.pathGeometry).toEqual({
      ...configuration.reconstruction!.pathGeometry, mode: 'LEGACY_VERTEX_LINE', maximumGeodesicSegmentLength: -2,
    });
  });
  it('preserves unopened expression and inactive settings through a confirmed strategy switch', async () => {
    const { ref, apply, configuration } = mount();
    await act(async () => { expect(await ref.current?.apply()).toBe(true); });
    expect(apply.mock.calls[0][0].configuration).toEqual(configuration);
    fireEvent.mouseDown(screen.getByRole('combobox', { name: '轨迹重建策略' }));
    await userEvent.click(await screen.findByText('旧版点连线', { selector: '.ant-select-item-option-content' }));
    fireEvent.click(await screen.findByRole('button', { name: '确认切换' }));
    await act(async () => { expect(await ref.current?.apply()).toBe(true); });
    expect(apply.mock.calls[1][0].configuration.reconstruction).toEqual({ ...configuration.reconstruction, semantics: 'LEGACY_POINTS' });
  });
  it('cancels local expression edits without committing and saves invalid drafts when requested', async () => {
    const { ref, apply, configuration } = mount();
    await userEvent.click(screen.getByRole('button', { name: '设置轨迹拆分表达式' }));
    let dialog = await screen.findByRole('dialog');
    fireEvent.change(within(dialog).getByRole('textbox', { name: '轨迹拆分表达式' }), { target: { value: 'discard_me' } });
    fireEvent.click(within(dialog).getByRole('button', { name: /取\s*消/ }));
    await act(async () => { expect(await ref.current?.apply()).toBe(true); });
    expect(apply.mock.calls[0][0].configuration).toEqual(configuration);
    await userEvent.click(screen.getByRole('button', { name: '设置轨迹拆分表达式' }));
    dialog = await screen.findByRole('dialog');
    fireEvent.change(within(dialog).getByRole('textbox', { name: '轨迹拆分表达式' }), { target: { value: '' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '添加窗口绑定' }));
    await userEvent.click(within(dialog).getByRole('switch', { name: '启用轨迹表达式拆分' }));
    fireEvent.click(within(dialog).getByRole('button', { name: '保存草稿' }));
    await act(async () => { expect(await ref.current?.apply()).toBe(true); });
    expect(apply.mock.calls[1][0].configuration.reconstruction?.splitExpression).toEqual({ expression: '', enabled: false,
      bindings: [...configuration.reconstruction!.splitExpression!.bindings, { name: '', sourceColumnName: '', offset: -1 }] });
  });
});
