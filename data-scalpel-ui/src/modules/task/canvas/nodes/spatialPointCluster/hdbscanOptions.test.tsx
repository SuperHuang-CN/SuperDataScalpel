import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { Modal } from 'antd';
import { createRef } from 'react';
import { afterEach, expect, it, vi } from 'vitest';
import { CANVAS_SCHEMA_MINOR_VERSION, CanvasNodeType, type SpatialPointClusterConfiguration } from '../../canvasTypes';
import { parseCanvasDefinition } from '../../canvasDefinitionIO';
import { createSpatialPointClusterConfiguration } from '../nodeDefaults';
import type { CanvasNodeInspectorHandle } from '../nodeSpec';
import Inspector from './inspector';
import { createHdbscanOptions, hdbscanFieldErrors, parseHdbscanOptions } from './hdbscanOptions';
import { spatialPointClusterCanvasView } from './canvasView';

afterEach(() => { Modal.destroyAll(); cleanup(); vi.restoreAllMocks(); });
const id = '11111111-1111-4111-8111-111111111111';
const layout = { x: 17, y: 22, width: 352, height: 216 };
const c: SpatialPointClusterConfiguration = { ...createSpatialPointClusterConfiguration(),
  parameters: { algorithm: 'HDBSCAN', minimumFeatures: 5 }, hdbscan: createHdbscanOptions(),
  dbscan: { mode: 'LINEAR', timeColumnName: 'private_time', searchDuration: 314159, searchDurationUnit: 'SECONDS' } };
const definition = (configuration: unknown, schemaMinorVersion = 45) => ({ schemaVersion: 4, schemaMinorVersion,
  nodes: [{ id, type: CanvasNodeType.SpatialPointCluster, name: '聚类', layout, configuration }], edges: [] });
const mount = (configuration: SpatialPointClusterConfiguration) => {
  const apply = vi.fn(), ref = createRef<CanvasNodeInspectorHandle>();
  render(<Inspector node={{ id, type: CanvasNodeType.SpatialPointCluster, name: '聚类', layout, configuration }}
    executionMode="BATCH" validation={undefined} validationUnavailableMessage={null} onApply={apply} onDirtyChange={vi.fn()} inspectorRef={ref} />);
  return { apply, ref };
};

it('round trips active and inactive diagnostic settings with an explicit 4.45 gate', () => {
  for (const parameters of [c.parameters, createSpatialPointClusterConfiguration().parameters]) {
    const value = { ...c, parameters };
    const current = parseCanvasDefinition(definition(value)); expect(current.success).toBe(true);
    if (current.success) {
      expect(current.definition.nodes[0].configuration).toEqual(value);
      expect(current.definition.schemaMinorVersion).toBe(CANVAS_SCHEMA_MINOR_VERSION);
    }
    const old = parseCanvasDefinition(definition(value, 44)); expect(old.success).toBe(false);
    if (!old.success) expect(old.errors.join(' ')).toContain('SPATIAL_HDBSCAN_OPTIONS_REQUIRE_SCHEMA_VERSION');
  }
  const legacy = { ...c }; delete legacy.hdbscan;
  const parsed = parseCanvasDefinition(definition(legacy, 44)); expect(parsed.success).toBe(true);
  if (parsed.success) expect(parsed.definition.nodes[0].configuration).not.toHaveProperty('hdbscan');
});

it('keeps blank business drafts but rejects malformed diagnostic structure', () => {
  for (const hdbscan of [[], true, 1, 'fields', { probabilityColumnName: [] }, { outlierColumnName: 1 }]) {
    const errors: string[] = []; parseHdbscanOptions({ hdbscan }, 'configuration', errors);
    expect(errors.length).toBeGreaterThan(0);
  }
  expect(parseCanvasDefinition(definition({ ...c, hdbscan: {} })).success).toBe(true);
  const options = { ...createHdbscanOptions(), probabilityColumnName: 'ID', outlierColumnName: 'duplicate', exemplarColumnName: 'Duplicate', stabilityColumnName: '' };
  expect(Object.keys(hdbscanFieldErrors(options, ['id']))).toHaveLength(4);
});

it('cancels diagnostic edits without mutating the inspector', async () => {
  const { apply, ref } = mount(c);
  expect(screen.queryByRole('spinbutton', { name: '聚类时间邻域' })).toBeNull();
  expect(screen.queryByRole('spinbutton', { name: '聚类搜索距离' })).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: '配置 HDBSCAN 诊断字段' }));
  const dialog = await screen.findByRole('dialog');
  fireEvent.change(within(dialog).getByRole('textbox', { name: '成员概率输出字段' }), { target: { value: 'cancelled' } });
  fireEvent.click(within(dialog).getByRole('button', { name: /取\s*消/ }));
  await act(async () => { expect(await ref.current?.apply()).toBe(true); });
  expect(apply.mock.calls.at(-1)?.[0].configuration.hdbscan).toEqual(c.hdbscan);
});

it('applies an invalid diagnostic draft explicitly and preserves inactive DBSCAN settings', async () => {
  const { apply, ref } = mount(c);
  fireEvent.click(screen.getByRole('button', { name: '配置 HDBSCAN 诊断字段' }));
  const dialog = await screen.findByRole('dialog');
  fireEvent.change(within(dialog).getByRole('textbox', { name: '成员概率输出字段' }), { target: { value: '' } });
  expect(within(dialog).getByText('请输入输出字段名')).toBeInTheDocument();
  fireEvent.click(within(dialog).getByRole('button', { name: '保存诊断草稿' }));
  await waitFor(() => expect(screen.getByText('1 个字段问题')).toBeInTheDocument());
  await act(async () => { expect(await ref.current?.apply()).toBe(true); });
  expect(apply.mock.calls.at(-1)?.[0].configuration.hdbscan).toEqual({ ...c.hdbscan, probabilityColumnName: '' });
  expect(apply.mock.calls.at(-1)?.[0].configuration.dbscan).toEqual(c.dbscan);
});

it('offers HDBSCAN for new nodes but keeps DBSCAN after a canceled switch', async () => {
  const original = createSpatialPointClusterConfiguration();
  mount(original);
  expect(screen.getByRole('radio', { name: 'HDBSCAN' })).not.toBeDisabled();
  fireEvent.click(screen.getByRole('radio', { name: 'HDBSCAN' }));
  const dialog = await screen.findByRole('dialog');
  fireEvent.click(within(dialog).getByRole('button', { name: /取\s*消/ }));
  expect(screen.getByRole('spinbutton', { name: '聚类搜索距离' })).toBeInTheDocument();
});

it('confirms HDBSCAN and retains diagnostics when returning to DBSCAN', async () => {
  const original = createSpatialPointClusterConfiguration();
  const { apply, ref } = mount(original);
  fireEvent.click(screen.getByRole('radio', { name: 'HDBSCAN' }));
  let dialog = await screen.findByRole('dialog');
  fireEvent.click(within(dialog).getByRole('button', { name: '确认切换算法' }));
  await waitFor(() => expect(screen.getByText('已配置 4 项诊断')).toBeInTheDocument());
  fireEvent.click(screen.getByRole('radio', { name: 'DBSCAN' }));
  dialog = await screen.findByRole('dialog');
  fireEvent.click(within(dialog).getByRole('button', { name: '确认切换算法' }));
  await act(async () => { expect(await ref.current?.apply()).toBe(true); });
  expect(apply.mock.calls.at(-1)?.[0].configuration.parameters).toEqual(original.parameters);
  expect(apply.mock.calls.at(-1)?.[0].configuration.hdbscan).toEqual(createHdbscanOptions());
});

it('shows a compact diagnostic count without hidden time values or internal diagnostic names', () => {
  const Body = spatialPointClusterCanvasView.Body;
  const { container } = render(<Body data={{ type: CanvasNodeType.SpatialPointCluster, name: '聚类', configuration: {
    ...c, sourceTableName: 'observations', outputTableName: 'clusters', hdbscan: { ...createHdbscanOptions(), probabilityColumnName: 'private_probability' },
  } }} />);
  expect(screen.getByText('HDBSCAN')).toBeInTheDocument(); expect(screen.getByText('4 项诊断')).toBeInTheDocument();
  for (const hidden of ['private_time', '314159', 'private_probability', '暂不可用']) expect(container).not.toHaveTextContent(hidden);
});
