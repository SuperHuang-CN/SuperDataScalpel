import { createUuid } from '../../../../shared/browser/createUuid';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, expect, it, vi } from 'vitest';
import { parseCanvasDefinition } from '../canvasDefinitionIO';
import { CANVAS_SCHEMA_MINOR_VERSION, CanvasNodeType } from '../canvasTypes';
import * as defaults from './nodeDefaults';
import { unsupportedSpatialDurationPaths } from '../parsing/spatialCompatibility';
import { TrackBoundaryEditor } from './trackShared';
import { spatialDurationUnitOptions } from './spatialAggregationOptions';
import { trackDurationUnitOptions } from './trackOptions';

afterEach(cleanup);
const definition = (type: CanvasNodeType, configuration: unknown, minor = 47) => ({ schemaVersion: 4, schemaMinorVersion: minor,
  nodes: [{ id: '11111111-1111-4111-8111-111111111111', type, configuration, name: '周', layout: { x: 12, y: 34, width: 240, height: 120 } }], edges: [] });

it('preserves every typed fixed-week setting including inactive drafts and gates exact paths', () => {
  const slicing = { timeColumnName: '', interval: 2, intervalUnit: 'WEEKS', repeatInterval: 1, repeatIntervalUnit: 'WEEKS',
    referenceTime: null, timeZone: 'UTC', windowStartColumnName: 'start', windowEndColumnName: 'end',
    calendar: { mode: 'CALENDAR', intervalUnit: 'MONTHS', repeatIntervalUnit: 'MONTHS' } };
  const reconstruct = defaults.createTrackReconstructConfiguration(); reconstruct.boundaries.maximumTimeGapUnit = 'WEEKS';
  const dwell = defaults.createTrackFindDwellConfiguration(); dwell.boundaries.maximumTimeGapUnit = 'WEEKS';
  dwell.minimumDurationUnit = 'WEEKS'; dwell.rangeOptions!.durationUnit = 'WEEKS';
  const incidents = defaults.createTrackDetectIncidentsConfiguration(); incidents.boundaries.maximumTimeGapUnit = 'WEEKS'; incidents.incidentDurationUnit = 'WEEKS';
  const motion = defaults.createTrackMotionStatisticsConfiguration(); motion.boundaries.maximumTimeGapUnit = 'WEEKS';
  motion.motionSemantics = 'LEGACY_LAG'; motion.windowOptions!.durationUnit = 'WEEKS'; motion.windowOptions!.idleTimeThresholdUnit = 'WEEKS';
  motion.metrics = [{ metricId: createUuid(), kind: 'DURATION', outputColumnName: 'elapsed', outputUnit: 'WEEKS' }];
  const cases: Array<[CanvasNodeType, unknown, string[]]> = [
    [CanvasNodeType.SpatialBinAggregate, { ...defaults.createSpatialBinAggregateConfiguration(), temporalSlicing: slicing }, ['temporalSlicing.intervalUnit', 'temporalSlicing.repeatIntervalUnit']],
    [CanvasNodeType.SpatialSummarizeWithin, { ...defaults.createSpatialSummarizeWithinConfiguration(), temporalSlicing: slicing }, ['temporalSlicing.intervalUnit', 'temporalSlicing.repeatIntervalUnit']],
    [CanvasNodeType.SpatialPointCluster, { ...defaults.createSpatialPointClusterConfiguration(),
      dbscan: { mode: 'SPATIAL', timeColumnName: '', searchDuration: null, searchDurationUnit: 'WEEKS' } }, ['dbscan.searchDurationUnit']],
    [CanvasNodeType.TrackReconstruct, reconstruct, ['boundaries.maximumTimeGapUnit']],
    [CanvasNodeType.TrackFindDwell, dwell, ['boundaries.maximumTimeGapUnit', 'minimumDurationUnit', 'rangeOptions.durationUnit']],
    [CanvasNodeType.TrackDetectIncidents, incidents, ['boundaries.maximumTimeGapUnit', 'incidentDurationUnit']],
    [CanvasNodeType.TrackMotionStatistics, motion, ['boundaries.maximumTimeGapUnit', 'windowOptions.durationUnit', 'windowOptions.idleTimeThresholdUnit', 'metrics[0].outputUnit']],
  ];
  for (const [type, c, paths] of cases) {
    const result = parseCanvasDefinition(definition(type, c));
    expect(result.success, result.success ? type : result.errors.join('\n')).toBe(true);
    if (result.success) {
      expect(result.definition.nodes[0].configuration).toEqual(c);
      expect(result.definition.nodes[0].layout).toMatchObject({ x: 12, y: 34 });
      expect(unsupportedSpatialDurationPaths(result.definition.nodes[0], 46)).toEqual(paths.map(p => `configuration.${p}`));
    }
    const old = parseCanvasDefinition(definition(type, c, 46)); expect(old.success).toBe(false);
    if (!old.success) {
      const errors = old.errors.filter(e => e.includes('SPATIAL_DURATION_WEEKS_REQUIRE_SCHEMA_VERSION'));
      expect(errors).toHaveLength(paths.length);
      paths.forEach(path => expect(errors.some(e => e.includes(`configuration.${path}`))).toBe(true));
    }
  }
});

it('does not gate calendar weeks or ordinary names and still rejects fixed months and years', () => {
  const c = defaults.createTrackReconstructConfiguration(); c.sourceTableName = 'WEEKS';
  c.boundaries.fixedTimeBoundary = { interval: 1, unit: 'WEEKS', referenceTime: null, timeZone: 'America/New_York' };
  const result = parseCanvasDefinition(definition(CanvasNodeType.TrackReconstruct, c, 46));
  expect(result.success).toBe(true);
  if (result.success) {
    expect(result.definition.schemaMinorVersion).toBe(CANVAS_SCHEMA_MINOR_VERSION);
    expect(unsupportedSpatialDurationPaths(result.definition.nodes[0], 46)).toEqual([]);
  }
  for (const unit of ['MONTHS', 'YEARS']) expect(parseCanvasDefinition(definition(CanvasNodeType.TrackReconstruct,
    { ...c, boundaries: { ...c.boundaries, maximumTimeGapUnit: unit } })).success).toBe(false);
});

it('labels fixed weeks explicitly and changes only the selected unit, not the threshold', async () => {
  expect(trackDurationUnitOptions).toEqual(spatialDurationUnitOptions);
  const value = { ...defaults.createTrackReconstructConfiguration().boundaries, maximumTimeGap: 2, maximumTimeGapUnit: 'DAYS' as const };
  const change = vi.fn(); render(<TrackBoundaryEditor value={value} onChange={change} />);
  await userEvent.click(screen.getByRole('combobox', { name: '最大时间间隔单位' }));
  await userEvent.click(screen.getByText('周（固定 7 天）'));
  expect(change).toHaveBeenCalledWith({ ...value, maximumTimeGapUnit: 'WEEKS' });
});
