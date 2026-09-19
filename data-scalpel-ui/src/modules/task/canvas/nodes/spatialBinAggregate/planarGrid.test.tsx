import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { Modal } from 'antd';
import { createRef } from 'react';
import { afterEach, expect, it, vi } from 'vitest';
import { CANVAS_SCHEMA_MINOR_VERSION, CanvasNodeType, type SpatialBinAggregateConfiguration, type SpatialPlanarGridOptions } from '../../canvasTypes';
import { parseCanvasDefinition } from '../../canvasDefinitionIO';
import { createSpatialBinAggregateConfiguration } from '../nodeDefaults';
import type { CanvasNodeInspectorHandle } from '../nodeSpec';
import Inspector from './inspector';
import { PlanarGridModal } from './PlanarGridModal';
import { parsePlanarGrid, planarGridProblems } from './planarGrid';

afterEach(() => { Modal.destroyAll(); cleanup(); });
const id = '11111111-1111-4111-8111-111111111111';
const layout = { x: 17, y: 22, width: 360, height: 224 };
const grid: SpatialPlanarGridOptions = { originX: 12345, originY: -9876,
  extent: { mode: 'EXPLICIT_BOUNDS', minX: -12, minY: -10, maxX: 200, maxY: 300 } };
const definition = (configuration: unknown, schemaMinorVersion = 38) => ({ schemaVersion: 4, schemaMinorVersion,
  nodes: [{ id, type: CanvasNodeType.SpatialBinAggregate, name: '格网', layout, configuration }], edges: [] });

it('round trips active/inactive planar bounds with a 4.38 gate and no old auto-migration', () => {
  for (const binShape of ['SQUARE', 'HEXAGON', 'H3'] as const) {
    for (const mode of ['EXPLICIT_BOUNDS', 'DATA_BOUNDS'] as const) {
      const config = { ...createSpatialBinAggregateConfiguration(), binShape,
        planarGrid: { ...grid, extent: { ...grid.extent!, mode } } };
      const parsed = parseCanvasDefinition(definition(config)); expect(parsed.success).toBe(true);
      if (parsed.success) {
        expect(parsed.definition.nodes[0].configuration).toEqual(config);
        expect(parsed.definition.nodes[0].layout.x).toBe(17);
        expect(parsed.definition.schemaMinorVersion).toBe(CANVAS_SCHEMA_MINOR_VERSION);
      }
      const old = parseCanvasDefinition(definition(config, 37)); expect(old.success).toBe(false);
      if (!old.success) expect(old.errors.join(' ')).toContain('SPATIAL_PLANAR_GRID_REQUIRE_SCHEMA_VERSION');
    }
  }
  const old = parseCanvasDefinition(definition(createSpatialBinAggregateConfiguration(), 37));
  expect(old.success).toBe(true); if (old.success) expect(old.definition.nodes[0].configuration).not.toHaveProperty('planarGrid');
});

it('rejects structural errors but preserves incomplete and reversed-bound drafts', () => {
  for (const planarGrid of [[], 2, { originX: [] }, { originY: Infinity }, { extent: [] }, { extent: { mode: 'AUTO' } }]) {
    const errors: string[] = []; parsePlanarGrid({ planarGrid }, 'configuration', errors); expect(errors.length).toBeGreaterThan(0);
  }
  const draft = { originX: null, originY: 0, extent: { mode: 'EXPLICIT_BOUNDS' as const, minX: 100, maxX: -100, minY: null, maxY: null } };
  expect(planarGridProblems(draft).length).toBeGreaterThan(0);
  expect(parseCanvasDefinition(definition({ ...createSpatialBinAggregateConfiguration(), planarGrid: draft })).success).toBe(true);
});

it('cancels local edits without saving and can explicitly save invalid bounds', () => {
  const save = vi.fn(), cancel = vi.fn();
  render(<PlanarGridModal initialValue={grid} onSave={save} onCancel={cancel} />);
  fireEvent.change(screen.getByRole('spinbutton', { name: '格网原点 X' }), { target: { value: '42' } });
  fireEvent.click(screen.getByRole('button', { name: /取\s*消/ }));
  expect(cancel).toHaveBeenCalledOnce(); expect(save).not.toHaveBeenCalled(); expect(grid.originX).toBe(12345);
  fireEvent.change(screen.getByRole('spinbutton', { name: '格网范围 maxX' }), { target: { value: '-500' } });
  expect(screen.getByText(/范围最小值必须小于最大值/)).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: '保存范围草稿' }));
  expect(save.mock.calls[0][0].extent.maxX).toBe(-500);
});

it('preserves unopened options and hides planar settings on H3 without clearing them', async () => {
  const config: SpatialBinAggregateConfiguration = { ...createSpatialBinAggregateConfiguration(), binShape: 'H3', planarGrid: grid,
    h3: { mode: 'RESOLUTION', resolution: 6 } };
  const apply = vi.fn(), ref = createRef<CanvasNodeInspectorHandle>();
  render(<Inspector node={{ id, type: CanvasNodeType.SpatialBinAggregate, name: '格网', layout, configuration: config }}
    executionMode="BATCH" validation={undefined} validationUnavailableMessage={null} onApply={apply} onDirtyChange={vi.fn()} inspectorRef={ref} />);
  expect(screen.queryByRole('button', { name: '设置格网范围与对齐' })).toBeNull();
  await act(async () => { expect(await ref.current?.apply()).toBe(true); });
  expect(apply.mock.calls[0][0].configuration.planarGrid).toEqual(grid);
});

it('requires confirmation before restoring legacy IDs and dropping bounds', async () => {
  const save = vi.fn(); render(<PlanarGridModal initialValue={grid} onSave={save} onCancel={vi.fn()} />);
  fireEvent.click(screen.getByRole('button', { name: '恢复旧版' }));
  expect(save).not.toHaveBeenCalled();
  await waitFor(() => expect(screen.getAllByRole('dialog')).toHaveLength(2));
  const dialogs = screen.getAllByRole('dialog');
  fireEvent.click(within(dialogs[dialogs.length - 1]).getByRole('button', { name: '恢复旧版' }));
  expect(save).toHaveBeenCalledWith(null);
});
