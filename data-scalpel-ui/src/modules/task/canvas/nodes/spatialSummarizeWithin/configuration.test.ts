import { createUuid } from '../../../../../shared/browser/createUuid';
import { describe, expect, it } from 'vitest';
import { CANVAS_SCHEMA_MINOR_VERSION, CanvasNodeType } from '../../canvasTypes';
import { parseCanvasDefinition } from '../../canvasDefinitionIO';
import { createSpatialSummarizeWithinConfiguration } from '../nodeDefaults';
import { withinStatisticProblems } from './statisticOptions';
import { usesLinkedWithinGroups } from './groupResult';

describe('weighted within dispersion', () => {
  it.each(['VARIANCE', 'STDDEV'] as const)('requires 4.37 for weighted %s and retains all draft fields', kind => {
    const c = createSpatialSummarizeWithinConfiguration();
    c.statistics = [{ statisticId: '11111111-1111-4111-8111-111111111111', kind, sourceColumnName: 'amount', outputColumnName: 'result', weighting: 'INTERSECTION_FRACTION' }];
    const parsed = parseCanvasDefinition(definition(c));
    expect(parsed.success).toBe(true);
    if (parsed.success) expect(parsed.definition.nodes[0].configuration).toEqual(c);
    const old = parseCanvasDefinition(definition(c, 36)); expect(old.success).toBe(false);
    if (!old.success) expect(old.errors.some(e => e.includes('SPATIAL_WITHIN_WEIGHTED_DISPERSION_REQUIRE_SCHEMA_VERSION') && e.includes('statistics[0].weighting'))).toBe(true);
    expect(withinStatisticProblems(c.statistics[0], true)).toEqual([]);
    expect(withinStatisticProblems(c.statistics[0], false)).toContain('形状分摊和加权要求线或面');
    c.statistics[0].valueTreatment = 'APPORTION_TOTAL';
    expect(withinStatisticProblems(c.statistics[0], true)).toContain('暂不支持分摊后再次加权');
    expect(parseCanvasDefinition(definition(c)).success).toBe(true);
    delete c.statistics[0].weighting; delete c.statistics[0].valueTreatment;
    expect(parseCanvasDefinition(definition(c, 36)).success).toBe(true);
  });
});

const definition = (configuration: unknown, minor: number = CANVAS_SCHEMA_MINOR_VERSION) => ({
  schemaVersion: 4, schemaMinorVersion: minor,
  nodes: [{ id: '11111111-1111-4111-8111-111111111111', type: CanvasNodeType.SpatialSummarizeWithin,
    name: '区域汇总', layout: { x: 1, y: 2, width: 320, height: 200 }, configuration }], edges: [],
});

describe('within explicit statistic contract', () => {
  it('round-trips separate treatment and weight and requires 4.24', () => {
    const configuration = createSpatialSummarizeWithinConfiguration();
    delete configuration.groupResult;
    configuration.statistics = [{ statisticId: createUuid(), kind: 'MEAN', sourceColumnName: 'rate',
      outputColumnName: 'weighted', valueTreatment: 'ORIGINAL_VALUE', weighting: 'INTERSECTION_FRACTION' }];
    const parsed = parseCanvasDefinition(definition(configuration));
    expect(parsed.success).toBe(true);
    if (parsed.success) expect(parsed.definition.nodes[0].configuration).toEqual(configuration);
    expect(parseCanvasDefinition(definition(configuration, 23)).success).toBe(false);
  });
  it('does not add strategies or reinterpret legacy COUNT', () => {
    const configuration = createSpatialSummarizeWithinConfiguration();
    delete configuration.groupResult;
    const parsed = parseCanvasDefinition(definition(configuration, 12));
    expect(parsed.success).toBe(true);
    if (parsed.success) expect(parsed.definition.nodes[0].configuration).toEqual(configuration);
    for (const kind of ['COUNT_FIELD', 'ANY'] as const) {
      configuration.statistics = [{ statisticId: createUuid(), kind, sourceColumnName: 'name', outputColumnName: 'n' }];
      expect(parseCanvasDefinition(definition(configuration, 23)).success).toBe(false);
      expect(parseCanvasDefinition(definition(configuration)).success).toBe(true);
    }
  });
  it('rejects unknown structures but keeps invalid combinations editable', () => {
    const configuration = createSpatialSummarizeWithinConfiguration();
    const statistic = { statisticId: createUuid(), kind: 'MEAN' as const, sourceColumnName: '',
      outputColumnName: '', valueTreatment: 'APPORTION_TOTAL' as const, weighting: 'INTERSECTION_FRACTION' as const };
    configuration.statistics = [statistic];
    expect(parseCanvasDefinition(definition(configuration)).success).toBe(true);
    expect(withinStatisticProblems(statistic, false)).toHaveLength(4);
    expect(parseCanvasDefinition(definition({ ...configuration,
      statistics: [{ ...statistic, weighting: 'GUESS' }] })).success).toBe(false);
    expect(parseCanvasDefinition(definition({ ...configuration,
      statistics: [{ ...statistic, valueTreatment: {} }] })).success).toBe(false);
  });
  it('gates linked results at 4.25 and retains inactive settings in legacy mode', () => {
    const configuration = createSpatialSummarizeWithinConfiguration();
    configuration.groupSummary = { groupByColumnName: 'kind', includeMinorityMajority: true,
      includeGroupPercentage: true, minorityFlagColumnName: 'old_min', majorityFlagColumnName: 'old_max', groupPercentageColumnName: 'pct' };
    configuration.groupResult!.outputTableName = 'groups';
    expect(usesLinkedWithinGroups(configuration)).toBe(true);
    expect(parseCanvasDefinition(definition(configuration, 24)).success).toBe(false);
    const parsed = parseCanvasDefinition(definition(configuration));
    expect(parsed.success).toBe(true);
    if (parsed.success) expect(parsed.definition.nodes[0].configuration).toEqual(configuration);
    configuration.groupResult!.mode = 'LEGACY_FLAT';
    expect(usesLinkedWithinGroups(configuration)).toBe(false);
    expect(parseCanvasDefinition(definition(configuration)).success).toBe(true);
    for (const groupResult of [[], { mode: 'UNKNOWN' }, { areaKeyColumnName: 4 }]) {
      expect(parseCanvasDefinition(definition({ ...configuration, groupResult })).success).toBe(false);
    }
  });
});
