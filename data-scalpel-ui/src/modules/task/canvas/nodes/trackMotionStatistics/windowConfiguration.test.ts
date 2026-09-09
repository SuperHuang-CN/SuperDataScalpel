import { describe, expect, it } from 'vitest';
import { CANVAS_SCHEMA_MINOR_VERSION, CanvasNodeType } from '../../canvasTypes';
import { parseCanvasDefinition } from '../../canvasDefinitionIO';
import { createTrackMotionStatisticsConfiguration } from '../nodeDefaults';
import { createMotionGroup, motionStatisticGroups, parseMotionWindowOptions } from './windowOptions';

const definition = (configuration: unknown, minor: number = CANVAS_SCHEMA_MINOR_VERSION) => ({
  schemaVersion: 4, schemaMinorVersion: minor,
  nodes: [{ id: '11111111-1111-4111-8111-111111111111', type: CanvasNodeType.TrackMotionStatistics,
    name: '运动统计', layout: { x: 1, y: 2, width: 320, height: 200 }, configuration }], edges: [],
});
describe('motion observation windows', () => {
  it('round-trips all 31 statistics and gates new semantics at 4.23', () => {
    const configuration = createTrackMotionStatisticsConfiguration();
    configuration.windowOptions!.statistics = Object.keys(motionStatisticGroups)
      .flatMap(group => createMotionGroup(group as keyof typeof motionStatisticGroups));
    expect(configuration.windowOptions!.statistics).toHaveLength(31);
    const parsed = parseCanvasDefinition(definition(configuration));
    expect(parsed.success).toBe(true);
    if (parsed.success) expect(parsed.definition.nodes[0].configuration).toEqual(configuration);
    expect(parseCanvasDefinition(definition(configuration, 22)).success).toBe(false);
  });
  it('does not reinterpret legacy lag configurations', () => {
    const configuration = createTrackMotionStatisticsConfiguration();
    delete configuration.motionSemantics; delete configuration.windowOptions;
    const parsed = parseCanvasDefinition(definition(configuration, 15));
    expect(parsed.success).toBe(true);
    if (parsed.success) expect(parsed.definition.nodes[0].configuration).not.toHaveProperty('windowOptions');
  });
  it('rejects malformed structures but preserves incomplete semantic drafts', () => {
    const errors: string[] = [];
    parseMotionWindowOptions({ motionSemantics: 'BAD', windowOptions: {
      observationCount: 0.2, orderByColumns: [1], statistics: [{ kind: 'BAD' }], distanceUnit: 'BAD',
    } }, 'c', errors);
    expect(errors).toHaveLength(5);
    const config = createTrackMotionStatisticsConfiguration();
    config.windowOptions!.observationCount = null;
    config.windowOptions!.statistics = [];
    expect(parseCanvasDefinition(definition(config)).success).toBe(true);
  });
});
