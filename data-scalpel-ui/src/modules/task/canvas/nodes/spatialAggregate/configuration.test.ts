import { describe, expect, it } from 'vitest';
import {
  CANVAS_SCHEMA_MINOR_VERSION,
  CanvasNodeType,
} from '../../canvasTypes';
import { parseCanvasDefinition } from '../../canvasDefinitionIO';
import { createSpatialAggregateConfiguration } from '../nodeDefaults';

const definition = (configuration: unknown, minor: number = CANVAS_SCHEMA_MINOR_VERSION) => ({
  schemaVersion: 4,
  schemaMinorVersion: minor,
  nodes: [{
    id: '11111111-1111-4111-8111-111111111111',
    type: CanvasNodeType.SpatialAggregate,
    name: '空间聚合',
    layout: { x: 0, y: 0, width: 352, height: 224 },
    configuration,
  }],
  edges: [],
});

const aggregate = () => ({
  sourceTableName: 'parcels',
  outputTableName: 'districts',
  groupByColumns: ['district'],
  aggregations: [{
    kind: 'UNION',
    geometryColumnName: 'shape',
    outputColumnName: 'district_shape',
  }],
});

describe('spatial aggregate dissolve options', () => {
  it('gates any explicit dissolve draft at Canvas 4.53', () => {
    const inactiveDraft = {
      enabled: false,
      multipart: true,
      countOutputColumnName: 'saved_count',
      summaryStatistics: [{
        statisticId: 'saved-draft-id',
        kind: 'SUM',
        sourceColumnName: 'saved_value',
        outputColumnName: 'saved_sum',
      }],
    };
    expect(parseCanvasDefinition(definition({
      ...aggregate(),
      dissolve: inactiveDraft,
    }, 52)).success).toBe(false);

    const parsed = parseCanvasDefinition(definition({
      ...aggregate(),
      dissolve: inactiveDraft,
    }, 53));
    expect(parsed.success).toBe(true);
    if (parsed.success) {
      expect(parsed.definition.nodes[0].configuration).toMatchObject({
        dissolve: inactiveDraft,
      });
    }
  });

  it('keeps missing dissolve options compatible with 4.52', () => {
    const configuration = createSpatialAggregateConfiguration();
    delete configuration.dissolve;
    const parsed = parseCanvasDefinition(definition(configuration, 52));
    expect(parsed.success).toBe(true);
    if (parsed.success) {
      expect(parsed.definition.nodes[0].configuration).toMatchObject({ dissolve: null });
    }
  });

  it('parses enabled statistics and rejects malformed structures', () => {
    const dissolve = {
      enabled: true,
      multipart: false,
      countOutputColumnName: 'feature_count',
      summaryStatistics: [{
        statisticId: '11111111-1111-4111-8111-111111111111',
        kind: 'MEAN',
        sourceColumnName: 'population',
        outputColumnName: 'population_mean',
      }],
    };
    expect(parseCanvasDefinition(definition({ ...aggregate(), dissolve })).success).toBe(true);

    for (const invalid of [
      { ...dissolve, enabled: 'yes' },
      { ...dissolve, multipart: 1 },
      { ...dissolve, summaryStatistics: {} },
      {
        ...dissolve,
        summaryStatistics: [{ ...dissolve.summaryStatistics[0], kind: 'MEDIAN' }],
      },
    ]) {
      expect(parseCanvasDefinition(definition({
        ...aggregate(),
        dissolve: invalid,
      })).success).toBe(false);
    }
  });

  it('gates connected grouping at Canvas 4.61 and keeps missing mode compatible', () => {
    const legacyDissolve = {
      enabled: true,
      multipart: true,
      countOutputColumnName: 'feature_count',
      summaryStatistics: [],
    };
    const explicitDissolve = {
      ...legacyDissolve,
      groupingMode: 'CONNECTED_COMPONENTS',
    };

    expect(parseCanvasDefinition(definition({
      ...aggregate(),
      groupByColumns: [],
      dissolve: explicitDissolve,
    }, 60)).success).toBe(false);
    const parsed = parseCanvasDefinition(definition({
      ...aggregate(),
      groupByColumns: [],
      dissolve: explicitDissolve,
    }, 61));
    expect(parsed.success).toBe(true);
    if (parsed.success) {
      expect(parsed.definition.nodes[0].configuration).toMatchObject({
        dissolve: explicitDissolve,
      });
    }

    const legacy = parseCanvasDefinition(definition({
      ...aggregate(),
      groupByColumns: [],
      dissolve: legacyDissolve,
    }, 60));
    expect(legacy.success).toBe(true);
    if (legacy.success) {
      expect(legacy.definition.nodes[0].configuration).toMatchObject({
        dissolve: { groupingMode: null },
      });
    }

    expect(parseCanvasDefinition(definition({
      ...aggregate(),
      dissolve: { ...legacyDissolve, groupingMode: 'PROXIMITY' },
    }, 61)).success).toBe(false);
  });
});
