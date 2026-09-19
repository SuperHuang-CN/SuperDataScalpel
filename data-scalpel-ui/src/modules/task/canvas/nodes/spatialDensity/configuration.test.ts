import { describe, expect, it } from 'vitest';
import { parseCanvasDefinition } from '../../canvasDefinitionIO';
import { CANVAS_SCHEMA_MINOR_VERSION, CanvasNodeType } from '../../canvasTypes';
import { createSpatialDensityConfiguration } from '../nodeDefaults';

const definition = (schemaMinorVersion: number, configuration: unknown) => ({
  schemaVersion: 4,
  schemaMinorVersion,
  nodes: [{
    id: '11111111-1111-4111-8111-111111111111',
    type: CanvasNodeType.SpatialDensity,
    name: '计算密度',
    layout: { x: 10, y: 20, width: 368, height: 224 },
    configuration,
  }],
  edges: [],
});

describe('spatial density configuration', () => {
  it('round trips at 4.68 and rejects the node below its introduction version', () => {
    const configuration = createSpatialDensityConfiguration();
    configuration.sourceTableName = 'points';
    configuration.pointGeometryColumnName = 'shape';
    configuration.outputTableName = 'density';
    configuration.fields = [{
      fieldId: '22222222-2222-4222-8222-222222222222',
      sourceColumnName: 'population',
      outputColumnName: 'population_density',
    }];

    const parsed = parseCanvasDefinition(definition(CANVAS_SCHEMA_MINOR_VERSION, configuration));
    expect(parsed.success).toBe(true);
    if (parsed.success) expect(parsed.definition.nodes[0].configuration).toEqual(configuration);
    expect(parseCanvasDefinition(definition(67, configuration)).success).toBe(false);
  });

  it('retains incomplete business drafts but rejects unsafe field structure', () => {
    const draft = { ...createSpatialDensityConfiguration(), weighting: null, binShape: null, radius: 0 };
    expect(parseCanvasDefinition(definition(68, draft)).success).toBe(true);
    expect(parseCanvasDefinition(definition(68, { ...draft, fields: 'population' })).success).toBe(false);
  });
});
