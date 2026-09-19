import { describe, expect, it } from 'vitest';
import { parseCanvasDefinition } from '../canvasDefinitionIO';
import { CANVAS_SCHEMA_MINOR_VERSION, CanvasNodeType } from '../canvasTypes';
import { createTrackDetectIncidentsConfiguration, createTrackReconstructConfiguration } from './nodeDefaults';
import { parseIncidentLifecycleOptions } from './trackDetectIncidents/readConfiguration';
import { parseTrackFixedTimeBoundary } from './trackTimeBoundary';

const definition = (configuration: unknown, minor: number = CANVAS_SCHEMA_MINOR_VERSION, type: string = CanvasNodeType.TrackDetectIncidents) => ({
  schemaVersion: 4, schemaMinorVersion: minor,
  nodes: [{ id: '11111111-1111-4111-8111-111111111111', type, name: '轨迹分析',
    layout: { x: 80, y: 90, width: 320, height: 200 }, configuration }], edges: [],
});

describe('track optional capabilities', () => {
  it('round-trips lifecycle drafts and requires minor 21 without changing the node introduction', () => {
    const configuration = createTrackDetectIncidentsConfiguration();
    expect(configuration.incidentSemantics).toBe('CONDITION_LIFECYCLE');
    expect(parseCanvasDefinition(definition(configuration)).success).toBe(true);
    expect(parseCanvasDefinition(definition(configuration, 20)).success).toBe(false);
    const legacy = { ...configuration };
    delete legacy.incidentSemantics;
    delete legacy.incidentStatusColumnName;
    delete legacy.orderByColumns;
    const parsed = parseCanvasDefinition(definition(legacy, 17));
    expect(parsed.success).toBe(true);
    if (parsed.success) {
      expect(parsed.definition.schemaMinorVersion).toBe(CANVAS_SCHEMA_MINOR_VERSION);
      expect(parsed.definition.nodes[0].configuration).not.toHaveProperty('incidentSemantics');
    }
  });

  it('validates the optional shapes without rejecting incomplete business drafts', () => {
    const errors: string[] = [];
    expect(parseIncidentLifecycleOptions({}, 'configuration', errors)).toEqual({ conditionWindows: [], conditionScalars: [] });
    parseIncidentLifecycleOptions({ incidentSemantics: 'OTHER', orderByColumns: [1], incidentStatusColumnName: {} }, 'configuration', errors);
    expect(errors).toHaveLength(3);
    const draftErrors: string[] = [];
    expect(parseTrackFixedTimeBoundary({ interval: null, unit: null, timeZone: null, referenceTime: null }, 'boundary', draftErrors))
      .toEqual({ interval: null, unit: null, timeZone: null, referenceTime: null });
    expect(draftErrors).toEqual([]);
    parseTrackFixedTimeBoundary({ interval: 0.5, unit: 'QUARTERS', timeZone: [] }, 'boundary', draftErrors);
    expect(draftErrors).toHaveLength(3);
  });

  it('preserves calendar boundaries and rejects them in an older minor', () => {
    const configuration = createTrackReconstructConfiguration();
    delete configuration.reconstruction;
    configuration.boundaries.fixedTimeBoundary = {
      interval: 1, unit: 'MONTHS', referenceTime: '2024-01-31T12:00:00Z', timeZone: 'UTC',
    };
    expect(parseCanvasDefinition(definition(configuration, 20, CanvasNodeType.TrackReconstruct)).success).toBe(false);
    const parsed = parseCanvasDefinition(definition(configuration, 21, CanvasNodeType.TrackReconstruct));
    expect(parsed.success).toBe(true);
    if (parsed.success) expect(parsed.definition.nodes[0].configuration).toEqual(configuration);
  });
});
