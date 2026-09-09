import { describe, expect, it } from 'vitest';
import { CANVAS_SCHEMA_MINOR_VERSION, CanvasNodeType } from '../../canvasTypes';
import { parseCanvasDefinition } from '../../canvasDefinitionIO';
import { createTrackReconstructConfiguration } from '../nodeDefaults';
import { usesMethodPath, usesOrderedReconstruction, usesSplitExpression } from './reconstruction';
import { createAreaGeometryOptions, usesAreaGeometry } from './areaGeometry';

const definition = (configuration: unknown, minor: number = CANVAS_SCHEMA_MINOR_VERSION) => ({
  schemaVersion: 4, schemaMinorVersion: minor,
  nodes: [{ id: '11111111-1111-4111-8111-111111111111', type: CanvasNodeType.TrackReconstruct,
    name: '轨迹重建', layout: { x: 1, y: 2, width: 352, height: 216 }, configuration }], edges: [],
});

describe('reconstruction options', () => {
  it('gates geodesic area sampling at 44 even in inactive branches, without migrating old areas', () => {
    const c = createTrackReconstructConfiguration();
    c.reconstruction!.areaGeometry = createAreaGeometryOptions(true);
    expect(parseCanvasDefinition(definition(c, 43)).success).toBe(true);
    for (const enabled of [true, false]) {
      c.reconstruction!.areaGeometry = { ...c.reconstruction!.areaGeometry, enabled,
        geodesicBoundary: { maximumSegmentLength: -1, maximumSegmentLengthUnit: null } };
      const old = parseCanvasDefinition(definition(c, 43));
      expect(old.success).toBe(false);
      if (!old.success) expect(old.errors.join(' ')).toContain('TRACK_GEODESIC_AREA_REQUIRE_SCHEMA_VERSION');
      const current = parseCanvasDefinition(definition(c));
      expect(current.success).toBe(true);
      if (current.success) expect(current.definition.nodes[0].configuration).toEqual(c);
    }
    for (const geodesicBoundary of [[], true, { maximumSegmentLength: '100' }, { maximumSegmentLengthUnit: 'DEGREES' }]) {
      expect(parseCanvasDefinition(definition({ ...c, reconstruction: { ...c.reconstruction,
        areaGeometry: { ...c.reconstruction!.areaGeometry, geodesicBoundary } } })).success).toBe(false);
    }
  });
  it('gates area geometry including inactive drafts at 42 without changing existing line definitions', () => {
    const c = createTrackReconstructConfiguration();
    expect(c.reconstruction?.areaGeometry).toBeUndefined();
    expect(parseCanvasDefinition(definition(c, 41)).success).toBe(true);
    for (const enabled of [true, false]) {
      c.reconstruction!.areaGeometry = { ...createAreaGeometryOptions(false), enabled, bufferField: 'missing',
        bufferExpression: 'radius * 2', bufferUnit: null };
      expect(usesAreaGeometry(c.reconstruction)).toBe(enabled);
      const parsed = parseCanvasDefinition(definition(c));
      expect(parsed.success).toBe(true);
      if (parsed.success) expect(parsed.definition.nodes[0].configuration).toEqual(c);
      expect(parseCanvasDefinition(definition(c, 41)).success).toBe(false);
    }
    c.reconstruction!.semantics = 'LEGACY_POINTS';
    c.reconstruction!.areaGeometry!.enabled = true;
    expect(usesAreaGeometry(c.reconstruction)).toBe(false);
    expect(parseCanvasDefinition(definition(c, 41)).success).toBe(false);
  });
  it('rejects malformed buffer structures but permits empty business drafts', () => {
    const c = createTrackReconstructConfiguration();
    c.reconstruction!.areaGeometry = { bufferMode: null, bufferField: null, bufferExpression: '', bufferUnit: null };
    expect(parseCanvasDefinition(definition(c)).success).toBe(true);
    for (const areaGeometry of [true, [], { enabled: 'yes' }, { bufferMode: 'AUTO' }, { bufferField: 5 },
      { bufferExpression: [] }, { bufferUnit: 'DEGREES' }]) {
      expect(parseCanvasDefinition(definition({ ...c, reconstruction: { ...c.reconstruction, areaGeometry } })).success).toBe(false);
    }
  });
  it('gates explicit paths at 28 and does not opt old ordered definitions in', () => {
    const config = createTrackReconstructConfiguration();
    expect(usesMethodPath(config.reconstruction)).toBe(true);
    expect(parseCanvasDefinition(definition(config, 27)).success).toBe(false);
    expect(parseCanvasDefinition(definition(config, 28)).success).toBe(true);
    delete config.reconstruction!.pathGeometry;
    const parsed = parseCanvasDefinition(definition(config, 27));
    expect(parsed.success).toBe(true);
    if (parsed.success) expect(parsed.definition.nodes[0].configuration).toEqual(config);
    expect(usesMethodPath(config.reconstruction)).toBe(false);
    config.reconstruction!.pathGeometry = { mode: 'LEGACY_VERTEX_LINE', maximumGeodesicSegmentLength: -1, maximumGeodesicSegmentLengthUnit: null };
    expect(parseCanvasDefinition(definition(config, 27)).success).toBe(false);
    const inactive = parseCanvasDefinition(definition(config));
    expect(inactive.success).toBe(true);
    if (inactive.success) expect(inactive.definition.nodes[0].configuration).toEqual(config);
    for (const pathGeometry of [[], true, { mode: 'OTHER' }, { maximumGeodesicSegmentLength: '10' }, { maximumGeodesicSegmentLengthUnit: 'DEGREES' }]) {
      expect(parseCanvasDefinition(definition({ ...config, reconstruction: { ...config.reconstruction, pathGeometry } })).success).toBe(false);
    }
  });
  it('round-trips the three boundary modes and preserves old minor semantics', () => {
    const config = createTrackReconstructConfiguration();
    for (const splitBoundaryOption of ['GAP', 'FINISH_LAST', 'START_NEXT'] as const) {
      config.reconstruction = { ...config.reconstruction!, splitBoundaryOption };
      const parsed = parseCanvasDefinition(definition(config));
      expect(parsed.success).toBe(true);
      if (parsed.success) expect(parsed.definition.nodes[0].configuration).toEqual(config);
      expect(parseCanvasDefinition(definition(config, 26)).success).toBe(false);
    }
    delete config.reconstruction;
    const old = parseCanvasDefinition(definition(config, 14));
    expect(old.success).toBe(true);
    if (old.success) expect(old.definition.nodes[0].configuration).toEqual(config);
    expect(usesOrderedReconstruction(config.reconstruction)).toBe(false);
  });
  it('keeps disabled expressions and invalid drafts but rejects unsafe structures', () => {
    const config = createTrackReconstructConfiguration();
    config.reconstruction!.splitExpression = { expression: '', enabled: false,
      bindings: [{ name: '', sourceColumnName: '', offset: null }] };
    const parsed = parseCanvasDefinition(definition(config));
    expect(parsed.success).toBe(true);
    if (parsed.success) expect(parsed.definition.nodes[0].configuration).toEqual(config);
    expect(usesSplitExpression(config.reconstruction)).toBe(false);
    for (const reconstruction of [true, [], { semantics: 'OTHER' }, { splitExpression: { expression: 123 } },
      { orderByColumns: [true] }, { splitExpression: { expression: '', bindings: [null] } },
      { splitExpression: { expression: '', bindings: [{ name: 'x', sourceColumnName: 'y', offset: 0.2 }] } }]) {
      expect(parseCanvasDefinition(definition({ ...config, reconstruction })).success).toBe(false);
    }
  });
});
