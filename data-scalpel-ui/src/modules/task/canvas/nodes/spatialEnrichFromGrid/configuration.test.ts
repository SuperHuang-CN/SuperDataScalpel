import { describe, expect, it } from 'vitest';
import { parseCanvasDefinition } from '../../canvasDefinitionIO';
import { CANVAS_SCHEMA_MINOR_VERSION, CanvasNodeType } from '../../canvasTypes';
import { createSpatialEnrichFromGridConfiguration } from '../nodeDefaults';

const definition = (schemaMinorVersion: number, configuration: unknown) => ({
  schemaVersion: 4,
  schemaMinorVersion,
  nodes: [{
    id: '11111111-1111-4111-8111-111111111111',
    type: CanvasNodeType.SpatialEnrichFromGrid,
    name: '从多变量格网丰富',
    layout: { x: 10, y: 20, width: 384, height: 232 },
    configuration,
  }],
  edges: [],
});

describe('spatial enrich from grid configuration', () => {
  it('round trips at 4.71 and rejects the node below its introduction version', () => {
    const configuration = {
      ...createSpatialEnrichFromGridConfiguration(),
      pointTableName: 'incidents',
      pointGeometryColumnName: 'shape',
      gridTableName: 'city_grid',
      gridGeometryColumnName: 'bin_geometry',
      gridIdColumnName: 'bin_id',
      enrichFields: [
        { sourceColumnName: 'population_sum', outputColumnName: 'grid_population_sum' },
      ],
      outputTableName: 'enriched_incidents',
    };

    const parsed = parseCanvasDefinition(definition(CANVAS_SCHEMA_MINOR_VERSION, configuration));
    expect(parsed.success).toBe(true);
    if (parsed.success) expect(parsed.definition.nodes[0].configuration).toEqual(configuration);
    expect(parseCanvasDefinition(definition(70, configuration)).success).toBe(false);
  });

  it('retains empty business drafts but rejects unsafe field structures', () => {
    const draft = createSpatialEnrichFromGridConfiguration();
    expect(parseCanvasDefinition(definition(71, draft)).success).toBe(true);
    expect(parseCanvasDefinition(definition(71, { ...draft, enrichFields: 'population' })).success)
      .toBe(false);
    expect(parseCanvasDefinition(definition(71, {
      ...draft,
      enrichFields: [{ sourceColumnName: 7, outputColumnName: 'population' }],
    })).success).toBe(false);
  });
});
