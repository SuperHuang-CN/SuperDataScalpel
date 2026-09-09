import { describe, expect, it } from 'vitest';
import { CANVAS_SCHEMA_MINOR_VERSION, CanvasNodeType } from '../../canvasTypes';
import { parseCanvasDefinition } from '../../canvasDefinitionIO';
import { createSpatialOverlayConfiguration } from '../nodeDefaults';
import { overlayCombinationSupported, overlayOperations, usesOverlayFamily } from './geometryPolicy';

const definition = (configuration: unknown, minor: number = CANVAS_SCHEMA_MINOR_VERSION) => ({
  schemaVersion: 4, schemaMinorVersion: minor,
  nodes: [{ id: '11111111-1111-4111-8111-111111111111', type: CanvasNodeType.SpatialOverlay,
    name: '叠加', layout: { x: 1, y: 2, width: 320, height: 200 }, configuration }], edges: [],
});

describe('overlay family geometry', () => {
  it('round-trips five modes and rejects their disguised old versions', () => {
    for (const operation of overlayOperations) {
      const configuration = { ...createSpatialOverlayConfiguration(), operation };
      const parsed = parseCanvasDefinition(definition(configuration));
      expect(parsed.success).toBe(true);
      if (parsed.success) expect(parsed.definition.nodes[0].configuration).toEqual(configuration);
      expect(parseCanvasDefinition(definition(configuration, 25)).success).toBe(false);
    }
    const configuration = createSpatialOverlayConfiguration();
    delete configuration.geometryPolicy;
    for (const operation of ['IDENTITY', 'SYMMETRICAL_DIFFERENCE'] as const) {
      expect(parseCanvasDefinition(definition({ ...configuration, operation }, 25)).success).toBe(false);
    }
  });
  it('retains legacy semantics, optional nulls and invalid business drafts', () => {
    const configuration = createSpatialOverlayConfiguration();
    delete configuration.geometryPolicy;
    const parsed = parseCanvasDefinition(definition(configuration, 13));
    expect(parsed.success).toBe(true);
    if (parsed.success) expect(parsed.definition.nodes[0].configuration).toEqual(configuration);
    expect(usesOverlayFamily(configuration)).toBe(false);
    expect(parseCanvasDefinition(definition({ ...configuration, operation: null })).success).toBe(true);
    expect(parseCanvasDefinition(definition({ ...configuration, operation: 'IDENTITY', geometryPolicy: 'LEGACY_GEOMETRY' })).success).toBe(true);
    for (const geometryPolicy of ['GUESS', {}, []]) {
      expect(parseCanvasDefinition(definition({ ...configuration, geometryPolicy })).success).toBe(false);
    }
  });
  it('matches the five-mode family matrix including all rejection cases', () => {
    const expected = {
      INTERSECTION: ['11', '12', '13', '21', '22', '23', '31', '32', '33'],
      ERASE: ['11', '22', '33'], UNION: ['33'],
      IDENTITY: ['11', '13', '22', '23', '33'], SYMMETRICAL_DIFFERENCE: ['11', '22', '33'],
    };
    for (const operation of overlayOperations) {
      for (let left = 0; left <= 3; left++) for (let right = 0; right <= 3; right++) {
        expect(overlayCombinationSupported(operation, left, right)).toBe(expected[operation].includes(`${left}${right}`));
      }
    }
  });
});
