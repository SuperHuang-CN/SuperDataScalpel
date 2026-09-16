import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Modal } from 'antd';
import { createRef } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CANVAS_SCHEMA_MINOR_VERSION, CanvasNodeType, type CanvasNodeValidationResult, type GeometryDerivation } from '../canvasTypes';
import { parseCanvasDefinition } from '../canvasDefinitionIO';
import { createGeometryDeriveConfiguration, createGeometrySimplifyConfiguration } from './nodeDefaults';
import type { CanvasNodeInspectorHandle } from './nodeSpec';
import Derive from './geometryDerive/inspector';
import Simplify from './geometrySimplify/inspector';

afterEach(() => { Modal.destroyAll(); cleanup(); });
const layout = { x: 0, y: 0, width: 352, height: 216 };
const nodeId = '11111111-1111-4111-8111-111111111111';
const definition = (type: string, configuration: unknown, minor: number = CANVAS_SCHEMA_MINOR_VERSION) => ({
  schemaVersion: 4, schemaMinorVersion: minor, nodes: [{ id: nodeId, type, name: '几何', layout, configuration }], edges: [],
});
describe('explicit unary geometry policies', () => {
  it('round-trips incomplete drafts and gates explicit strategies without upgrading old semantics', () => {
    const simplify = createGeometrySimplifyConfiguration();
    expect(simplify.tolerance).toBeNull(); expect(simplify.algorithm).toBe('TOPOLOGY_PRESERVING');
    const derive = { ...createGeometryDeriveConfiguration(), derivations: [{ derivationId: nodeId, kind: null,
      sourceColumnName: '', outputColumnName: '', geometryPolicy: 'PRESERVE_DIMENSION' }] };
    for (const [type, configuration] of [[CanvasNodeType.GeometrySimplify, simplify], [CanvasNodeType.GeometryDerive, derive]] as const) {
      const current = parseCanvasDefinition(definition(type, configuration));
      expect(current.success).toBe(true);
      if (current.success) expect(current.definition.nodes[0].configuration).toEqual(configuration);
      expect(parseCanvasDefinition(definition(type, configuration, 28)).success).toBe(false);
    }
    delete simplify.geometryPolicy;
    const old = parseCanvasDefinition(definition(CanvasNodeType.GeometrySimplify, simplify, 10));
    expect(old.success).toBe(true);
    if (old.success) expect(old.definition.nodes[0].configuration).toEqual(simplify);
    for (const geometryPolicy of ['OTHER', [], true]) expect(parseCanvasDefinition(definition(CanvasNodeType.GeometrySimplify, { ...simplify, geometryPolicy })).success).toBe(false);
    expect(parseCanvasDefinition(definition(CanvasNodeType.GeometrySimplify, { ...simplify, tolerance: -3, algorithm: null, toleranceUnit: null })).success).toBe(true);
    expect(parseCanvasDefinition(definition(CanvasNodeType.GeometrySimplify, { ...simplify, tolerance: '1' })).success).toBe(false);
  });
  it('simplify preserves invalid drafts and confirms explicit dimensional changes', async () => {
    const ref = createRef<CanvasNodeInspectorHandle>(); const apply = vi.fn();
    const config = createGeometrySimplifyConfiguration();
    render(<Simplify node={{ id: nodeId, type: CanvasNodeType.GeometrySimplify, name: '简化', layout, configuration: config }}
      executionMode="BATCH" validation={undefined} validationUnavailableMessage={null} onApply={apply} onDirtyChange={vi.fn()} inspectorRef={ref} />);
    fireEvent.change(screen.getByRole('spinbutton', { name: '简化容差' }), { target: { value: '-3' } });
    fireEvent.mouseDown(screen.getByRole('combobox', { name: '简化结果维度' }));
    await userEvent.click(await screen.findByText('输出 XY', { selector: '.ant-select-item-option-content' }));
    fireEvent.click(await screen.findByRole('button', { name: '确认切换' }));
    await act(async () => { expect(await ref.current?.apply()).toBe(true); });
    expect(apply.mock.calls[0][0].configuration).toEqual({ ...config, tolerance: -3, geometryPolicy: 'OUTPUT_XY' });
  });
  it('derive adds an unselected draft and cancel does not commit local edits', async () => {
    const ref = createRef<CanvasNodeInspectorHandle>(); const apply = vi.fn();
    const config = createGeometryDeriveConfiguration();
    render(<Derive node={{ id: nodeId, type: CanvasNodeType.GeometryDerive, name: '派生', layout, configuration: config }}
      executionMode="STREAMING" validation={undefined} validationUnavailableMessage={null} onApply={apply} onDirtyChange={vi.fn()} inspectorRef={ref} />);
    fireEvent.click(screen.getByRole('button', { name: /配\s*置/ }));
    let dialog = await screen.findByRole('dialog');
    fireEvent.click(within(dialog).getByRole('button', { name: /添加派生/ }));
    fireEvent.click(within(dialog).getByRole('button', { name: /取\s*消/ }));
    await act(async () => { await ref.current?.apply(); });
    expect(apply.mock.calls[0][0].configuration).toEqual(config);
    fireEvent.click(screen.getByRole('button', { name: /配\s*置/ }));
    dialog = await screen.findByRole('dialog');
    fireEvent.click(within(dialog).getByRole('button', { name: /添加派生/ }));
    fireEvent.click(within(dialog).getByRole('button', { name: '保存草稿' }));
    await act(async () => { expect(await ref.current?.apply()).toBe(true); });
    expect(apply.mock.calls[1][0].configuration.derivations[0]).toMatchObject({ kind: null, sourceColumnName: '', outputColumnName: '', geometryPolicy: 'PRESERVE_DIMENSION' });
  });

  it('shows missing rule fields as a compact accessible issue count without blocking drafts', async () => {
    const ref = createRef<CanvasNodeInspectorHandle>(); const apply = vi.fn();
    const config = createGeometryDeriveConfiguration();
    render(<Derive node={{ id: nodeId, type: CanvasNodeType.GeometryDerive, name: '派生', layout, configuration: config }}
      executionMode="BATCH" validation={undefined} validationUnavailableMessage={null} onApply={apply} onDirtyChange={vi.fn()} inspectorRef={ref} />);
    fireEvent.click(screen.getByRole('button', { name: /配\s*置/ }));
    const dialog = await screen.findByRole('dialog');
    fireEvent.click(within(dialog).getByRole('button', { name: /添加派生/ }));
    const issues = within(dialog).getByRole('status', { name: '第 1 项配置问题' });
    expect(issues.textContent).toContain('3 个配置问题');
    fireEvent.click(issues);
    expect(await screen.findByText('请选择来源 Geometry 字段')).toBeTruthy();
    expect(screen.getByText('请选择派生函数')).toBeTruthy();
    expect(screen.getByText('请输入派生输出字段名')).toBeTruthy();
    fireEvent.click(within(dialog).getByRole('button', { name: '保存草稿' }));
    await act(async () => { expect(await ref.current?.apply()).toBe(true); });
    expect(apply.mock.calls[0][0].configuration.derivations[0].sourceColumnName).toBe('');
  });

  it('keeps saved geometry references waiting for compiler context instead of marking them invalid', async () => {
    const config = { ...createGeometryDeriveConfiguration(), sourceTableName: 'parcels', derivations: [{
      derivationId: nodeId, kind: 'CENTROID' as const, sourceColumnName: 'shape', outputColumnName: 'centroid',
    }] };
    const view = render(<Derive node={{ id: nodeId, type: CanvasNodeType.GeometryDerive, name: '派生', layout, configuration: config }}
      executionMode="BATCH" validation={undefined} validationUnavailableMessage={null} onApply={vi.fn()} onDirtyChange={vi.fn()} inspectorRef={createRef()} />);
    expect(screen.getByText('parcels（等待解析）')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /配\s*置/ }));
    expect(await screen.findByText('shape（等待解析）')).toBeTruthy();
    expect(screen.queryByText(/已失效/)).toBeNull();
    view.unmount();
    render(<Simplify node={{ id: nodeId, type: CanvasNodeType.GeometrySimplify, name: '简化', layout,
      configuration: { ...createGeometrySimplifyConfiguration(), sourceTableName: 'parcels', geometryColumnName: 'shape' } }}
      executionMode="BATCH" validation={undefined} validationUnavailableMessage={null} onApply={vi.fn()} onDirtyChange={vi.fn()} inspectorRef={createRef()} />);
    expect(screen.getByText('parcels（等待解析）')).toBeTruthy();
    expect(screen.getByText('shape（等待解析）')).toBeTruthy();
    expect(screen.queryByText(/已失效/)).toBeNull();
  });

  it.each([
    ['CENTROID', 'POLYGON', 'XYZ', '该函数不能可靠保留 Z/M，请显式选择输出 XY'],
    ['BOUNDARY', 'GEOMETRYCOLLECTION', 'XY', '边界不支持 GeometryCollection 来源'],
  ] as const)('exposes %s limitations beside the affected rule and still applies the draft', async (kind, geometryKind, dimension, message) => {
    const ref = createRef<CanvasNodeInspectorHandle>(); const apply = vi.fn();
    const derivation: GeometryDerivation = { derivationId: nodeId, kind, sourceColumnName: 'shape',
      outputColumnName: 'result', geometryPolicy: 'PRESERVE_DIMENSION' };
    const config = { sourceTableName: 'parcels', outputTableName: 'derived', derivations: [derivation] };
    const validation: CanvasNodeValidationResult = { nodeId, issues: [], outputTables: [], inputTables: [{
      name: 'parcels', origin: null, datasetKind: 'BOUNDED', eventTimeColumn: null, watermarkDelay: null,
      columns: [{ name: 'shape', fieldType: 'GEOMETRY', length: null, precision: null, scale: null, nullable: true,
        defaultValue: null, autoIncrement: false, generated: false, comment: null,
        geometry: { kind: geometryKind, dimension, crs: { authority: 'EPSG', code: 3857 } } }],
    }] };
    render(<Derive node={{ id: nodeId, type: CanvasNodeType.GeometryDerive, name: '派生', layout, configuration: config }}
      executionMode="BATCH" validation={validation} validationUnavailableMessage={null} onApply={apply} onDirtyChange={vi.fn()} inspectorRef={ref} />);
    fireEvent.click(screen.getByRole('button', { name: /配\s*置/ }));
    const dialog = await screen.findByRole('dialog');
    const issues = within(dialog).getByRole('status', { name: '第 1 项配置问题' });
    expect(issues.textContent).toContain('1 个配置问题');
    fireEvent.click(issues);
    expect(await screen.findByText(message)).toBeTruthy();
    fireEvent.click(within(dialog).getByRole('button', { name: '保存草稿' }));
    await act(async () => { expect(await ref.current?.apply()).toBe(true); });
    expect(apply.mock.calls[0][0].configuration).toEqual(config);
  });

  it('labels compiler-confirmed angular CRS without assuming degrees or changing the tolerance', async () => {
    const ref = createRef<CanvasNodeInspectorHandle>(); const apply = vi.fn();
    const configuration = { ...createGeometrySimplifyConfiguration(), sourceTableName: 'parcels', geometryColumnName: 'shape', tolerance: 0.2 };
    const validation: CanvasNodeValidationResult = { nodeId, issues: [{ nodeId, code: 'GEOMETRY_SIMPLIFY_USES_ANGULAR_UNITS',
      severity: 'WARNING', message: '使用来源角度单位', path: 'configuration.toleranceUnit' }], outputTables: [], inputTables: [{
      name: 'parcels', origin: null, datasetKind: 'BOUNDED', eventTimeColumn: null, watermarkDelay: null,
      columns: [{ name: 'shape', fieldType: 'GEOMETRY', length: null, precision: null, scale: null, nullable: true,
        defaultValue: null, autoIncrement: false, generated: false, comment: null,
        geometry: { kind: 'POLYGON', dimension: 'XY', crs: { authority: 'EPSG', code: 4807 } } }],
    }] };
    render(<Simplify node={{ id: nodeId, type: CanvasNodeType.GeometrySimplify, name: '简化', layout, configuration }}
      executionMode="BATCH" validation={validation} validationUnavailableMessage={null} onApply={apply} onDirtyChange={vi.fn()} inspectorRef={ref} />);
    expect(screen.getByText('来源 CRS 角度单位')).toBeTruthy();
    expect(screen.getByText('使用来源 CRS 角度单位，简化尺度会随纬度变化；不会自动换算为米。')).toBeTruthy();
    expect(screen.queryByText('来源 CRS 单位（度）')).toBeNull();
    await act(async () => { await ref.current?.apply(); });
    expect(apply.mock.calls[0][0].configuration).toEqual(configuration);
  });
});
