import { describe, expect, it } from 'vitest';
import { parseCanvasDefinition } from '../../canvasDefinitionIO';
import { CANVAS_SCHEMA_MINOR_VERSION, CanvasNodeType } from '../../canvasTypes';
import { createTrackFindDwellConfiguration } from '../nodeDefaults';
import { parseDwellOptions } from './rangeOptions';

const definition = (configuration: unknown, minor: number = CANVAS_SCHEMA_MINOR_VERSION) => ({
  schemaVersion: 4, schemaMinorVersion: minor,
  nodes: [{ id: '11111111-1111-4111-8111-111111111111', type: CanvasNodeType.TrackFindDwell,
    name: '驻留', layout: { x: 12, y: 24, width: 200, height: 100 }, configuration }], edges: [],
});
describe('dwell reference-center contract', () => {
  it('round-trips all four modes including inactive legacy geometry and requires 4.22', () => {
    for (const resultMode of ['MEAN_CENTERS', 'CONVEX_HULLS', 'DWELL_FEATURES', 'ALL_FEATURES'] as const) {
      const configuration = createTrackFindDwellConfiguration();
      configuration.outputGeometryKind = null;
      configuration.rangeOptions = { ...configuration.rangeOptions!, resultMode };
      const parsed = parseCanvasDefinition(definition(configuration));
      expect(parsed.success).toBe(true);
      if (parsed.success) expect(parsed.definition.nodes[0].configuration).toEqual(configuration);
      expect(parseCanvasDefinition(definition(configuration, 21)).success).toBe(false);
    }
  });
  it('preserves absent legacy strategy without turning it into reference-center', () => {
    const old = createTrackFindDwellConfiguration();
    delete old.dwellSemantics;
    delete old.rangeOptions;
    const parsed = parseCanvasDefinition(definition(old, 16));
    expect(parsed.success).toBe(true);
    if (parsed.success) expect(parsed.definition.nodes[0].configuration).not.toHaveProperty('dwellSemantics');
  });
  it('rejects malformed options but keeps incomplete business drafts', () => {
    const errors: string[] = [];
    parseDwellOptions({ dwellSemantics: 'OTHER', rangeOptions: { resultMode: 'OTHER',
      orderByColumns: [1], durationUnit: 'BAD', meanDistanceUnit: 'BAD', dwellFlagColumnName: [] } }, 'c', errors);
    expect(errors).toHaveLength(6);
    expect(parseCanvasDefinition(definition({ ...createTrackFindDwellConfiguration(), distanceMethod: null,
      rangeOptions: { resultMode: null, durationUnit: null, meanDistanceUnit: null, orderByColumns: [],
        meanDistanceColumnName: '', dwellFlagColumnName: '' } })).success).toBe(true);
  });
});
