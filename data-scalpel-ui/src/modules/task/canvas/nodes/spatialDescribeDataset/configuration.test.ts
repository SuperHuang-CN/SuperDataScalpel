import { describe, expect, it } from 'vitest';
import { parseCanvasDefinition } from '../../canvasDefinitionIO';
import { CANVAS_SCHEMA_MINOR_VERSION, CanvasNodeType } from '../../canvasTypes';
import { createSpatialDescribeDatasetConfiguration } from '../nodeDefaults';

const definition = (schemaMinorVersion: number, configuration: unknown) => ({
  schemaVersion: 4,
  schemaMinorVersion,
  nodes: [{
    id: '11111111-1111-4111-8111-111111111111',
    type: CanvasNodeType.SpatialDescribeDataset,
    name: '描述数据集',
    layout: { x: 10, y: 20, width: 376, height: 232 },
    configuration,
  }],
  edges: [],
});

describe('spatial describe dataset configuration', () => {
  it('round trips at 4.76 and rejects the node below its introduction version', () => {
    const configuration = {
      ...createSpatialDescribeDatasetConfiguration(),
      sourceTableName: 'city_events',
      geometryColumnName: 'shape',
      statisticsTableName: 'city_field_statistics',
      descriptionTableName: 'city_description',
      sampleSize: 100,
      sampleTableName: 'city_sample',
      extentOutput: true,
      extentTableName: 'city_extent',
    };
    const parsed = parseCanvasDefinition(definition(CANVAS_SCHEMA_MINOR_VERSION, configuration));
    expect(parsed.success).toBe(true);
    if (parsed.success) expect(parsed.definition.nodes[0].configuration).toEqual(configuration);
    expect(parseCanvasDefinition(definition(75, configuration)).success).toBe(false);
  });

  it('keeps incomplete drafts but rejects unsafe primitive shapes', () => {
    const draft = createSpatialDescribeDatasetConfiguration();
    expect(parseCanvasDefinition(definition(76, draft)).success).toBe(true);
    expect(parseCanvasDefinition(definition(76, { ...draft, sampleSize: 1.5 })).success).toBe(false);
    expect(parseCanvasDefinition(definition(76, { ...draft, extentOutput: 'true' })).success).toBe(false);
  });
});
