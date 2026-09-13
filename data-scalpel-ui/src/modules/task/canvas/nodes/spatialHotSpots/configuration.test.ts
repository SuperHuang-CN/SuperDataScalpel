import { describe, expect, it } from 'vitest';
import { parseCanvasDefinition } from '../../canvasDefinitionIO';
import { CANVAS_SCHEMA_MINOR_VERSION, CanvasNodeType } from '../../canvasTypes';
import { createSpatialHotSpotsConfiguration } from '../nodeDefaults';

const definition = (schemaMinorVersion: number, configuration: unknown) => ({
  schemaVersion: 4,
  schemaMinorVersion,
  nodes: [{
    id: '11111111-1111-4111-8111-111111111111',
    type: CanvasNodeType.SpatialHotSpots,
    name: '寻找热点',
    layout: { x: 10, y: 20, width: 376, height: 224 },
    configuration,
  }],
  edges: [],
});

describe('spatial hot spots configuration', () => {
  it('round trips at 4.69 and rejects the node below its introduction version', () => {
    const configuration = createSpatialHotSpotsConfiguration();
    configuration.sourceTableName = 'points';
    configuration.pointGeometryColumnName = 'shape';
    configuration.analysisSource = 'FIELD_SUM';
    configuration.analysisColumnName = 'incidents';
    configuration.outputTableName = 'hot_spots';
    const parsed = parseCanvasDefinition(definition(CANVAS_SCHEMA_MINOR_VERSION, configuration));
    expect(parsed.success).toBe(true);
    if (parsed.success) expect(parsed.definition.nodes[0].configuration).toEqual(configuration);
    expect(parseCanvasDefinition(definition(68, configuration)).success).toBe(false);
  });

  it('retains incomplete business drafts but rejects unsafe enum structure', () => {
    const draft = { ...createSpatialHotSpotsConfiguration(), analysisSource: null, multipleTesting: null };
    expect(parseCanvasDefinition(definition(69, draft)).success).toBe(true);
    expect(parseCanvasDefinition(definition(69, { ...draft, multipleTesting: 'BONFERRONI' })).success).toBe(false);
  });
});
