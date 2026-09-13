import { describe, expect, it } from 'vitest';
import {
  CANVAS_SCHEMA_MINOR_VERSION,
  CanvasNodeType,
} from '../../canvasTypes';
import { parseCanvasDefinition } from '../../canvasDefinitionIO';
import { createSpatialClipConfiguration } from '../nodeDefaults';

const definition = (configuration: unknown, minor: number = CANVAS_SCHEMA_MINOR_VERSION) => ({
  schemaVersion: 4,
  schemaMinorVersion: minor,
  nodes: [{
    id: '11111111-1111-4111-8111-111111111111',
    type: CanvasNodeType.SpatialClip,
    name: '空间裁剪',
    layout: { x: 0, y: 0, width: 368, height: 216 },
    configuration,
  }],
  edges: [],
});

describe('spatial clip geometry policy', () => {
  it('defaults new nodes to source-family output and gates explicit policies at 4.51', () => {
    const configuration = createSpatialClipConfiguration();
    expect(configuration.geometryPolicy).toBe('SOURCE_FAMILY_2D');
    delete configuration.maskCombination;
    expect(parseCanvasDefinition(definition(configuration, 50)).success).toBe(false);
    expect(parseCanvasDefinition(definition(configuration, 51)).success).toBe(true);

    expect(parseCanvasDefinition(definition({
      ...configuration,
      geometryPolicy: 'LEGACY_ANY_DIMENSION',
    }, 50)).success).toBe(false);
  });

  it('normalizes missing and null policies to legacy null while rejecting unknown values', () => {
    const configuration = createSpatialClipConfiguration();
    delete configuration.geometryPolicy;
    delete configuration.maskCombination;

    for (const legacy of [configuration, { ...configuration, geometryPolicy: null }]) {
      const parsed = parseCanvasDefinition(definition(legacy, 50));
      expect(parsed.success).toBe(true);
      if (parsed.success) {
        expect(parsed.definition.nodes[0].configuration).toMatchObject({
          geometryPolicy: null,
        });
      }
    }

    for (const geometryPolicy of ['AUTO', {}, []]) {
      expect(parseCanvasDefinition(definition({
        ...configuration,
        geometryPolicy,
      })).success).toBe(false);
    }
  });

  it('defaults new nodes to dissolved masks and gates explicit combinations at 4.77', () => {
    const configuration = createSpatialClipConfiguration();
    expect(configuration.maskCombination).toBe('DISSOLVE_ALL');
    expect(parseCanvasDefinition(definition(configuration, 76)).success).toBe(false);
    expect(parseCanvasDefinition(definition(configuration, 77)).success).toBe(true);

    for (const maskCombination of ['DISSOLVE_ALL', 'PAIRWISE']) {
      const parsed = parseCanvasDefinition(definition({ ...configuration, maskCombination }));
      expect(parsed.success).toBe(true);
      if (parsed.success) {
        expect(parsed.definition.nodes[0].configuration).toMatchObject({ maskCombination });
      }
    }
  });

  it('keeps missing and null mask combinations as legacy pairwise semantics', () => {
    const configuration = createSpatialClipConfiguration();
    delete configuration.maskCombination;

    for (const legacy of [configuration, { ...configuration, maskCombination: null }]) {
      const parsed = parseCanvasDefinition(definition(legacy, 76));
      expect(parsed.success).toBe(true);
      if (parsed.success) {
        expect(parsed.definition.nodes[0].configuration).toMatchObject({
          maskCombination: null,
        });
      }
    }
    for (const maskCombination of ['AUTO', {}, []]) {
      expect(parseCanvasDefinition(definition({
        ...configuration,
        maskCombination,
      })).success).toBe(false);
    }
  });
});
