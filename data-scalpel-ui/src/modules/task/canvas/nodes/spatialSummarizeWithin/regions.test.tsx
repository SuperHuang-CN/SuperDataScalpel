import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Modal } from 'antd';
import { afterEach, expect, it, vi } from 'vitest';
import { createRef } from 'react';
import { CanvasNodeType, type SpatialSummarizeWithinConfiguration } from '../../canvasTypes';
import { parseCanvasDefinition } from '../../canvasDefinitionIO';
import { createSpatialSummarizeWithinConfiguration } from '../nodeDefaults';
import type { CanvasNodeInspectorHandle } from '../nodeSpec';
import { createWithinRegions } from './regions';
import Inspector from './inspector';
import { spatialSummarizeWithinCanvasView } from './canvasView';

afterEach(() => { Modal.destroyAll(); cleanup(); });
const id = '11111111-1111-4111-8111-111111111111';
const layout = { x: 15, y: 20, width: 240, height: 120 };
const c: SpatialSummarizeWithinConfiguration = { ...createSpatialSummarizeWithinConfiguration(),
  areaTableName: 'old_regions', areaGeometryColumnName: 'old_shape', summaryTableName: 'features', outputTableName: 'summary',
  regions: { ...createWithinRegions(), mode: 'PLANAR_GRID', binSize: 10 } };
const definition = (configuration: unknown, schemaMinorVersion = 41) => ({ schemaVersion: 4, schemaMinorVersion,
  nodes: [{ id, type: CanvasNodeType.SpatialSummarizeWithin, name: '汇总', layout, configuration }], edges: [] });
const findVisibleOption = async (text: string) => {
  let option: HTMLElement | undefined;
  await waitFor(() => {
    option = Array.from(document.querySelectorAll<HTMLElement>('.ant-select-dropdown .ant-select-item-option-content'))
      .find(element => element.textContent === text
        && !element.closest('.ant-select-dropdown')?.classList.contains('ant-select-dropdown-hidden'));
    expect(option).toBeDefined();
  });
  return option as HTMLElement;
};

it('gates all region objects at 4.41 without migrating legacy region-table definitions', () => {
  for (const mode of ['AREA_TABLE', 'PLANAR_GRID'] as const) {
    const configuration = { ...c, regions: { ...c.regions!, mode } };
    const parsed = parseCanvasDefinition(definition(configuration)); expect(parsed.success).toBe(true);
    if (parsed.success) expect(parsed.definition.nodes[0].configuration).toEqual(configuration);
    const old = parseCanvasDefinition(definition(configuration, 40)); expect(old.success).toBe(false);
    if (!old.success) expect(old.errors.join(' ')).toContain('SPATIAL_WITHIN_REGIONS_REQUIRE_SCHEMA_VERSION');
  }
  const old = { ...c }; delete old.regions;
  const parsed = parseCanvasDefinition(definition(old,40)); expect(parsed.success).toBe(true);
  if (parsed.success) expect(parsed.definition.nodes[0].configuration).not.toHaveProperty('regions');
});

it('accepts invalid business drafts while rejecting malformed grid objects', () => {
  expect(parseCanvasDefinition(definition({ ...c, regions: { ...c.regions!, binSize: 0, mode: null, binShape: null } })).success).toBe(true);
  for (const regions of [[], { mode: 'CENTROIDS' }, { binShape: 'H3' }, { binSize: '10' }, { planarGrid: [] }, { binIdColumnName: [] }])
    expect(parseCanvasDefinition(definition({ ...c, regions })).success).toBe(false);
});

it('cancels grid edits and saves invalid drafts without erasing the area table', async () => {
  const apply = vi.fn(), ref = createRef<CanvasNodeInspectorHandle>();
  render(<Inspector node={{ id,type: CanvasNodeType.SpatialSummarizeWithin,name: '汇总',layout,configuration:c }} executionMode="BATCH"
    validation={undefined} validationUnavailableMessage={null} onApply={apply} onDirtyChange={vi.fn()} inspectorRef={ref} />);
  fireEvent.click(screen.getByRole('button', { name: '设置汇总格网' }));
  fireEvent.change(screen.getByRole('spinbutton', { name: '汇总格网大小' }), { target: { value: '99' } });
  fireEvent.click(screen.getByRole('button', { name: /取\s*消/ }));
  fireEvent.click(screen.getByRole('button', { name: '设置汇总格网' }));
  expect(screen.getByRole('spinbutton', { name: '汇总格网大小' })).toHaveValue('10');
  fireEvent.change(screen.getByRole('spinbutton', { name: '汇总格网大小' }), { target: { value: '' } });
  fireEvent.click(screen.getByRole('button', { name: '保存格网草稿' }));
  await act(async () => { expect(await ref.current?.apply()).toBe(true); });
  expect(apply.mock.calls.at(-1)?.[0].configuration.regions.binSize).toBeNull();
  expect(apply.mock.calls.at(-1)?.[0].configuration.areaTableName).toBe('old_regions');
});

it('preserves explicit extent coordinates when toggled to data bounds and reopened', async () => {
  const apply = vi.fn(), ref = createRef<CanvasNodeInspectorHandle>();
  const configuration = { ...c, regions: { ...c.regions!, planarGrid: { originX: 3,originY:7,
    extent: { mode:'EXPLICIT_BOUNDS' as const,minX:0,minY:0,maxX:20,maxY:30 } } } };
  render(<Inspector node={{ id,type:CanvasNodeType.SpatialSummarizeWithin,name:'汇总',layout,configuration }} executionMode="BATCH"
    validation={undefined} validationUnavailableMessage={null} onApply={apply} onDirtyChange={vi.fn()} inspectorRef={ref} />);
  const user = userEvent.setup({ pointerEventsCheck: 0 });
  fireEvent.click(screen.getByRole('button',{name:'设置汇总格网'}));
  fireEvent.mouseDown(screen.getByRole('combobox',{name:'汇总格网范围'}));
  await user.click(await findVisibleOption('被汇总要素范围'));
  expect(screen.queryByRole('spinbutton',{name:'最大 X'})).toBeNull();
  fireEvent.click(screen.getByRole('button',{name:'保存格网草稿'}));
  await act(async () => { expect(await ref.current?.apply()).toBe(true); });
  expect(apply.mock.calls.at(-1)?.[0].configuration.regions.planarGrid).toEqual({originX:3,originY:7,
    extent:{mode:'DATA_BOUNDS',minX:0,minY:0,maxX:20,maxY:30}});
  fireEvent.click(screen.getByRole('button',{name:'设置汇总格网'}));
  fireEvent.mouseDown(screen.getByRole('combobox',{name:'汇总格网范围'}));
  await user.click(await findVisibleOption('指定业务范围'));
  expect(screen.getByRole('spinbutton',{name:'最大 X'})).toHaveValue('20');
});

it('confirms region source changes and keeps table and grid drafts on apply', async () => {
  const apply = vi.fn(), ref = createRef<CanvasNodeInspectorHandle>();
  render(<Inspector node={{ id,type: CanvasNodeType.SpatialSummarizeWithin,name: '汇总',layout,configuration:c }} executionMode="BATCH"
    validation={undefined} validationUnavailableMessage={null} onApply={apply} onDirtyChange={vi.fn()} inspectorRef={ref} />);
  const selectTable = async () => {
    fireEvent.mouseDown(screen.getByRole('combobox', { name: '汇总区域来源' }));
    const option = await findVisibleOption('区域表');
    fireEvent.click(option.parentElement as HTMLElement);
  };
  await selectTable();
  const confirmationTitle = await screen.findByText('切换汇总区域来源？', { selector: '.ant-modal-title' });
  const confirmation = confirmationTitle.closest<HTMLElement>('.ant-modal');
  expect(confirmation).not.toBeNull();
  fireEvent.click(within(confirmation as HTMLElement).getByRole('button', { name: /取\s*消/ }));
  expect(screen.getByRole('button', { name: '设置汇总格网' })).toBeInTheDocument();
  await waitFor(() => expect(within(confirmation as HTMLElement)
    .queryByRole('button', { name: '确认切换区域' })).toBeNull());
  await selectTable();
  const confirmButton = await within(confirmation as HTMLElement).findByRole('button', { name: '确认切换区域' });
  fireEvent.click(confirmButton);
  await waitFor(() => expect(screen.queryByRole('button', { name: '设置汇总格网' })).toBeNull());
  await act(async () => { expect(await ref.current?.apply()).toBe(true); });
  const result = apply.mock.calls.at(-1)?.[0].configuration;
  expect(result.areaTableName).toBe('old_regions'); expect(result.areaGeometryColumnName).toBe('old_shape');
  expect(result.regions).toEqual({ ...c.regions, mode:'AREA_TABLE' });
});

it('renders grid-only cards with derived size and no scope coordinate values', () => {
  const configuration = { ...c, areaTableName:'', regions: { ...c.regions!, planarGrid: { originX: 123456,originY:789012,extent:null } } };
  const Body = spatialSummarizeWithinCanvasView.Body;
  const { container } = render(<Body data={{ type: CanvasNodeType.SpatialSummarizeWithin,name:'汇总',configuration }} />);
  expect(screen.getByText('方格区域')).toBeInTheDocument(); expect(screen.getByText('features')).toBeInTheDocument();
  expect(container).not.toHaveTextContent('123456'); expect(container).not.toHaveTextContent('789012');
  expect(spatialSummarizeWithinCanvasView.resolveSize(configuration).height).toBeGreaterThan(104);
});
