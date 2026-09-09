import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { parseCanvasDefinition } from '../canvasDefinitionIO';
import { CANVAS_SCHEMA_MINOR_VERSION, CanvasNodeType } from '../canvasTypes';
import * as defaults from './nodeDefaults';
import { spatialAreaUnitOptions, spatialDistanceUnitOptions, unsupportedSpatialUnitPaths } from './spatialUnits';
import { createNearestMatching } from './spatialNearest/matching';
import { ConnectionLinesModal } from './spatialNearest/ConnectionLinesModal';

afterEach(cleanup);
const definition = (type: CanvasNodeType, configuration: unknown, minor = 36) => ({ schemaVersion: 4, schemaMinorVersion: minor,
  nodes: [{ id: '11111111-1111-4111-8111-111111111111', type, configuration, name: '单位', layout: { x: 12, y: 34, width: 240, height: 120 } }], edges: [] });

describe('extended spatial units', () => {
  it('round-trips every distance and area option and rejects undocumented aliases', () => {
    for (const { value } of spatialDistanceUnitOptions) {
      const c = { ...defaults.createGeometrySimplifyConfiguration(), tolerance: 2, toleranceUnit: value };
      const result = parseCanvasDefinition(definition(CanvasNodeType.GeometrySimplify, c));
      expect(result.success).toBe(true);
      if (result.success) { expect(result.definition.nodes[0].configuration).toEqual(c); expect(result.definition.nodes[0].layout).toMatchObject({ x: 12, y: 34 }); }
    }
    for (const { value } of spatialAreaUnitOptions) {
      const c = { ...defaults.createSpatialSummarizeWithinConfiguration(), areaUnit: value };
      expect(parseCanvasDefinition(definition(CanvasNodeType.SpatialSummarizeWithin, c)).success).toBe(true);
    }
    expect(parseCanvasDefinition(definition(CanvasNodeType.GeometrySimplify,
      { ...defaults.createGeometrySimplifyConfiguration(), toleranceUnit: 'FeetInt' })).success).toBe(false);
  });

  it('gates every affected node, preserves inactive unit settings and ignores ordinary strings', () => {
    const nearest = defaults.createSpatialNearestConfiguration();
    nearest.maximumDistanceUnit = 'FEET_US'; nearest.distanceOutputUnit = 'YARDS'; nearest.matching = createNearestMatching();
    nearest.matching.connectionLines!.maximumGeodesicSegmentLengthUnit = 'NAUTICAL_MILES_US';
    const reconstruct = defaults.createTrackReconstructConfiguration(); reconstruct.boundaries.maximumDistanceGapUnit = 'YARDS';
    reconstruct.reconstruction!.pathGeometry!.maximumGeodesicSegmentLengthUnit = 'MILES_US';
    const motion = defaults.createTrackMotionStatisticsConfiguration(); motion.boundaries.maximumDistanceGapUnit = 'FEET_US';
    motion.idleDistanceThresholdUnit = 'YARDS'; motion.windowOptions!.distanceUnit = 'YARDS_US';
    motion.windowOptions!.inputElevationUnit = 'FEET_US'; motion.windowOptions!.elevationUnit = 'MILES_US';
    motion.metrics = [{ metricId: '11111111-1111-4111-8111-111111111111', kind: 'DISTANCE', outputColumnName: 'distance', outputUnit: 'NAUTICAL_MILES_US' },
      { metricId: '22222222-2222-4222-8222-222222222222', kind: 'ELEVATION_CHANGE', outputColumnName: 'elevation', outputUnit: 'YARDS_US' }];
    const dwell = defaults.createTrackFindDwellConfiguration(); dwell.boundaries.maximumDistanceGapUnit = 'YARDS_US';
    dwell.distanceThresholdUnit = 'FEET_US'; dwell.rangeOptions!.meanDistanceUnit = 'YARDS'; dwell.rangeOptions!.resultMode = 'ALL_FEATURES';
    const incident = defaults.createTrackDetectIncidentsConfiguration(); incident.boundaries.maximumDistanceGapUnit = 'YARDS';
    const cases: Array<[CanvasNodeType, unknown, number]> = [
      [CanvasNodeType.GeometrySimplify, { ...defaults.createGeometrySimplifyConfiguration(), toleranceUnit: 'YARDS' }, 1],
      [CanvasNodeType.SpatialNearest, nearest, 3],
      [CanvasNodeType.SpatialSummarizeWithin, { ...defaults.createSpatialSummarizeWithinConfiguration(), lengthUnit: 'YARDS', areaUnit: 'ACRES_US' }, 2],
      [CanvasNodeType.SpatialBinAggregate, { ...defaults.createSpatialBinAggregateConfiguration(), binSizeUnit: 'YARDS' }, 1],
      [CanvasNodeType.SpatialPointCluster, { ...defaults.createSpatialPointClusterConfiguration(), parameters: { algorithm: 'DBSCAN', searchDistance: 5, searchDistanceUnit: 'YARDS', minimumFeatures: 3 } }, 1],
      [CanvasNodeType.TrackReconstruct, reconstruct, 2], [CanvasNodeType.TrackMotionStatistics, motion, 7],
      [CanvasNodeType.TrackFindDwell, dwell, 3], [CanvasNodeType.TrackDetectIncidents, incident, 1],
    ];
    for (const [type, c, count] of cases) {
      const result = parseCanvasDefinition(definition(type, c)); expect(result.success, result.success ? type : result.errors.join('\n')).toBe(true);
      if (result.success) {
        expect(result.definition.nodes[0].configuration).toEqual(c);
        expect(unsupportedSpatialUnitPaths(result.definition.nodes[0], 35)).toHaveLength(count);
      }
      const old = parseCanvasDefinition(definition(type, c, 35)); expect(old.success).toBe(false);
      if (!old.success) expect(old.errors.filter(e => e.includes('SPATIAL_EXTENDED_UNITS_REQUIRE_SCHEMA_VERSION'))).toHaveLength(count);
    }
    const legacy = parseCanvasDefinition(definition(CanvasNodeType.GeometrySimplify,
      { ...defaults.createGeometrySimplifyConfiguration(), sourceTableName: 'FEET_US', toleranceUnit: 'FEET' }, 35));
    expect(legacy.success).toBe(true);
    if (legacy.success) expect(legacy.definition.schemaMinorVersion).toBe(CANVAS_SCHEMA_MINOR_VERSION);
  });

  it('keeps the numeric value when switching units and retains disabled connection settings', async () => {
    const value = { ...createNearestMatching().connectionLines!, enabled: true, maximumGeodesicSegmentLength: 10 };
    const save = vi.fn();
    const view = render(<ConnectionLinesModal value={value} geodesic onSave={save} onCancel={vi.fn()} />);
    await userEvent.click(screen.getByRole('combobox', { name: '段长单位' }));
    await userEvent.click(screen.getByText('美国测量码'));
    fireEvent.click(screen.getByRole('button', { name: '保存草稿' }));
    await waitFor(() => expect(save).toHaveBeenCalledWith(expect.objectContaining({ maximumGeodesicSegmentLength: 10, maximumGeodesicSegmentLengthUnit: 'YARDS_US' })));
    view.unmount(); save.mockClear();
    render(<ConnectionLinesModal value={{ ...value, enabled: false, maximumGeodesicSegmentLengthUnit: 'NAUTICAL_MILES_US' }} geodesic onSave={save} onCancel={vi.fn()} />);
    fireEvent.click(screen.getByRole('button', { name: '保存草稿' }));
    expect(save).toHaveBeenCalledWith(expect.objectContaining({ enabled: false, maximumGeodesicSegmentLength: 10, maximumGeodesicSegmentLengthUnit: 'NAUTICAL_MILES_US' }));
  });
});
