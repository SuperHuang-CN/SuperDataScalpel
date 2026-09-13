import { describe, expect, it } from 'vitest';
import { parseCanvasDefinition } from '../../canvasDefinitionIO';
import { CANVAS_SCHEMA_MINOR_VERSION, CanvasNodeType } from '../../canvasTypes';
import { createTraceProximityEventsConfiguration } from '../nodeDefaults';

const definition = (schemaMinorVersion: number, configuration: unknown) => ({
  schemaVersion: 4,
  schemaMinorVersion,
  nodes: [{
    id: '11111111-1111-4111-8111-111111111111',
    type: CanvasNodeType.TraceProximityEvents,
    name: '追踪邻近事件',
    layout: { x: 10, y: 20, width: 392, height: 232 },
    configuration,
  }],
  edges: [],
});

describe('trace proximity events configuration', () => {
  it('round trips controlled trace options at 4.73 and rejects earlier versions', () => {
    const configuration = {
      ...createTraceProximityEventsConfiguration(),
      sourceTableName: 'observations',
      pointGeometryColumnName: 'shape',
      entityIdColumnName: 'device_id',
      timeColumnName: 'observed_at',
      distanceMethod: 'GEODESIC' as const,
      spatialSearchDistance: 15,
      spatialSearchDistanceUnit: 'METERS' as const,
      temporalSearchDistance: 2,
      temporalSearchDistanceUnit: 'MONTHS' as const,
      entitiesOfInterest: [{ entityId: 'A-01', startEpochMillis: 1_787_572_800_000 }],
      maxTraceDepth: 5,
      attributeMatchColumns: ['building', 'floor'],
      includeTracks: true,
      outputTableName: 'trace_events',
      tracksOutputTableName: 'trace_tracks',
    };

    const parsed = parseCanvasDefinition(definition(CANVAS_SCHEMA_MINOR_VERSION, configuration));
    expect(parsed.success).toBe(true);
    if (parsed.success) expect(parsed.definition.nodes[0].configuration).toEqual(configuration);
    expect(parseCanvasDefinition(definition(72, configuration)).success).toBe(false);
  });

  it('retains incomplete business drafts and rejects unsafe nested structures', () => {
    const draft = createTraceProximityEventsConfiguration();
    expect(parseCanvasDefinition(definition(73, draft)).success).toBe(true);
    expect(parseCanvasDefinition(definition(73, {
      ...draft, entitiesOfInterest: 'A-01',
    })).success).toBe(false);
    expect(parseCanvasDefinition(definition(73, {
      ...draft, entitiesOfInterest: [{ entityId: 'A-01', startEpochMillis: 1.5 }],
    })).success).toBe(false);
    expect(parseCanvasDefinition(definition(73, {
      ...draft, attributeMatchColumns: 'building',
    })).success).toBe(false);
  });
});
