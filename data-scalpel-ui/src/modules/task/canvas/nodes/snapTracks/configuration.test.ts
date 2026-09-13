import { describe, expect, it } from 'vitest';
import { parseCanvasDefinition } from '../../canvasDefinitionIO';
import { CANVAS_SCHEMA_MINOR_VERSION, CanvasNodeType } from '../../canvasTypes';
import { createSnapTracksConfiguration } from '../nodeDefaults';

const definition = (schemaMinorVersion: number, configuration: unknown) => ({
  schemaVersion: 4,
  schemaMinorVersion,
  nodes: [{
    id: '11111111-1111-4111-8111-111111111111',
    type: CanvasNodeType.SnapTracks,
    name: '吸附轨迹',
    layout: { x: 10, y: 20, width: 392, height: 232 },
    configuration,
  }],
  edges: [],
});

describe('snap tracks configuration', () => {
  it('round trips at 4.74 and rejects the node below its introduction version', () => {
    const configuration = {
      ...createSnapTracksConfiguration(),
      pointTableName: 'vehicle_observations',
      pointGeometryColumnName: 'shape',
      trackIdColumns: ['vehicle_id'],
      timeColumnName: 'observed_at',
      orderByColumns: ['sequence_no'],
      lineTableName: 'road_network',
      lineGeometryColumnName: 'shape',
      lineIdColumnName: 'road_id',
      fromNodeColumnName: 'from_node',
      toNodeColumnName: 'to_node',
      searchDistance: 50,
      searchDistanceUnit: 'METERS' as const,
      distanceMethod: 'GEODESIC' as const,
      boundaries: {
        maximumTimeGap: 30,
        maximumTimeGapUnit: 'MINUTES' as const,
        maximumDistanceGap: 2,
        maximumDistanceGapUnit: 'KILOMETERS' as const,
        fixedTimeBoundary: null,
      },
      directionMatching: {
        directionColumnName: 'travel_direction',
        forwardValue: 'F',
        backwardValue: 'B',
        bothValue: 'A',
        noneValue: 'N',
      },
      lineFields: [{ sourceColumnName: 'road_class', outputColumnName: 'matched_road_class' }],
      outputMode: 'MATCHED_FEATURES' as const,
      outputTableName: 'snapped_tracks',
    };

    const parsed = parseCanvasDefinition(definition(CANVAS_SCHEMA_MINOR_VERSION, configuration));
    expect(parsed.success).toBe(true);
    if (parsed.success) expect(parsed.definition.nodes[0].configuration).toEqual(configuration);
    expect(parseCanvasDefinition(definition(73, configuration)).success).toBe(false);
  });

  it('retains empty business drafts and rejects unsafe nested structures', () => {
    const draft = createSnapTracksConfiguration();
    expect(parseCanvasDefinition(definition(74, draft)).success).toBe(true);
    expect(parseCanvasDefinition(definition(74, {
      ...draft,
      directionMatching: 'travel_direction',
    })).success).toBe(false);
    expect(parseCanvasDefinition(definition(74, {
      ...draft,
      directionMatching: {
        directionColumnName: 'travel_direction',
        forwardValue: 'F',
        backwardValue: 'B',
        bothValue: 'A',
      },
    })).success).toBe(false);
    expect(parseCanvasDefinition(definition(74, {
      ...draft,
      lineFields: 'road_class',
    })).success).toBe(false);
    expect(parseCanvasDefinition(definition(74, {
      ...draft,
      lineFields: [{ sourceColumnName: 'road_class' }],
    })).success).toBe(false);
  });
});
