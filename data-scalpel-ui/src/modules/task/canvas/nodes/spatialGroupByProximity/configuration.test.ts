import { describe, expect, it } from 'vitest';
import { parseCanvasDefinition } from '../../canvasDefinitionIO';
import { CANVAS_SCHEMA_MINOR_VERSION, CanvasNodeType } from '../../canvasTypes';
import { createSpatialGroupByProximityConfiguration } from '../nodeDefaults';

const definition = (schemaMinorVersion: number, configuration: unknown) => ({
  schemaVersion: 4,
  schemaMinorVersion,
  nodes: [{
    id: '11111111-1111-4111-8111-111111111111',
    type: CanvasNodeType.SpatialGroupByProximity,
    name: '按邻近分组',
    layout: { x: 10, y: 20, width: 384, height: 224 },
    configuration,
  }],
  edges: [],
});

describe('spatial group by proximity configuration', () => {
  it('round trips all controlled relationships at 4.72 and rejects earlier versions', () => {
    const configuration = {
      ...createSpatialGroupByProximityConfiguration(),
      sourceTableName: 'events',
      geometryColumnName: 'shape',
      spatialRelationship: 'NEAR_GEODESIC' as const,
      spatialNearDistance: 1,
      spatialNearDistanceUnit: 'KILOMETERS' as const,
      temporalCondition: {
        relationship: 'NEAR' as const,
        startColumnName: 'started_at',
        endColumnName: 'ended_at',
        nearDistance: 1,
        nearDistanceUnit: 'MONTHS' as const,
      },
      attributeConditions: [{
        columnName: 'region', relationship: 'EQUALS' as const, maximumDifference: null,
      }, {
        columnName: 'accuracy',
        relationship: 'ABSOLUTE_DIFFERENCE_AT_MOST' as const,
        maximumDifference: 2,
      }],
      outputTableName: 'event_groups',
    };

    const parsed = parseCanvasDefinition(definition(CANVAS_SCHEMA_MINOR_VERSION, configuration));
    expect(parsed.success).toBe(true);
    if (parsed.success) expect(parsed.definition.nodes[0].configuration).toEqual(configuration);
    expect(parseCanvasDefinition(definition(71, configuration)).success).toBe(false);
  });

  it('retains incomplete business drafts and rejects unsafe nested structures', () => {
    const draft = createSpatialGroupByProximityConfiguration();
    expect(parseCanvasDefinition(definition(72, draft)).success).toBe(true);
    expect(parseCanvasDefinition(definition(72, {
      ...draft, attributeConditions: 'region',
    })).success).toBe(false);
    expect(parseCanvasDefinition(definition(72, {
      ...draft,
      temporalCondition: { relationship: 'NEAR', startColumnName: 'at', nearDistance: 1.5 },
    })).success).toBe(false);
  });
});
