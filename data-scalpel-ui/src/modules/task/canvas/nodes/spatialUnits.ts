import { CanvasNodeType, type CanvasNodeDefinition, type SpatialAreaUnit, type SpatialDistanceUnit, type TrackBoundaryConfiguration } from '../canvasTypes';

export const spatialDistanceUnitLabels: Record<SpatialDistanceUnit, string> = {
  SOURCE_CRS_UNIT: '来源 CRS 单位', METERS: '米', KILOMETERS: '千米',
  FEET: '国际英尺', YARDS: '国际码', MILES: '国际英里', NAUTICAL_MILES: '国际海里',
  FEET_US: '美国测量英尺', YARDS_US: '美国测量码', MILES_US: '美国测量英里', NAUTICAL_MILES_US: '美制海里（1954 年前）',
};
export const spatialAreaUnitLabels: Record<SpatialAreaUnit, string> = {
  SQUARE_METERS: '平方米', SQUARE_KILOMETERS: '平方千米', HECTARES: '公顷',
  ACRES: '国际英亩', SQUARE_FEET: '平方国际英尺', SQUARE_YARDS: '平方国际码', SQUARE_MILES: '平方国际英里',
  ACRES_US: '美国测量英亩', SQUARE_FEET_US: '平方美国测量英尺', SQUARE_YARDS_US: '平方美国测量码', SQUARE_MILES_US: '平方美国测量英里',
};
export const spatialDistanceUnitOptions = Object.entries(spatialDistanceUnitLabels).map(([value, label]) => ({ value: value as SpatialDistanceUnit, label }));
export const spatialAreaUnitOptions = Object.entries(spatialAreaUnitLabels).map(([value, label]) => ({ value: value as SpatialAreaUnit, label }));
export const spatialDistanceUnits: ReadonlySet<string> = new Set(Object.keys(spatialDistanceUnitLabels));
export const spatialAreaUnits: ReadonlySet<string> = new Set(Object.keys(spatialAreaUnitLabels));
export const spatialUnitHelp = '国际英尺为 0.3048 米，美国测量英尺为 1200/3937 米。国际海里为 1852 米，旧美制海里为 1853.248 米。面积按所选长度制平方换算；来源 CRS 单位取坐标轴，不代表米。切换单位不会自动换算已经填写的数值，请同时核对数值。';

const newUnits = new Set(['YARDS', 'FEET_US', 'YARDS_US', 'MILES_US', 'NAUTICAL_MILES_US',
  'SQUARE_YARDS', 'SQUARE_FEET_US', 'SQUARE_YARDS_US', 'SQUARE_MILES_US', 'ACRES_US']);

/** Only fixed-duration fields require 4.47; calendar weeks and arbitrary strings do not. */
export function unsupportedSpatialDurationPaths(node: CanvasNodeDefinition, minorVersion: number): string[] {
  if (minorVersion >= 47) return [];
  const paths: string[] = [];
  const add = (path: string, unit: string | null | undefined) => { if (unit === 'WEEKS') paths.push(`configuration.${path}`); };
  const boundary = (value: TrackBoundaryConfiguration) => add('boundaries.maximumTimeGapUnit', value?.maximumTimeGapUnit);
  switch (node.type) {
    case CanvasNodeType.SpatialBinAggregate:
    case CanvasNodeType.SpatialSummarizeWithin:
      add('temporalSlicing.intervalUnit', node.configuration.temporalSlicing?.intervalUnit);
      add('temporalSlicing.repeatIntervalUnit', node.configuration.temporalSlicing?.repeatIntervalUnit);
      break;
    case CanvasNodeType.SpatialPointCluster:
      add('dbscan.searchDurationUnit', node.configuration.dbscan?.searchDurationUnit); break;
    case CanvasNodeType.TrackReconstruct: boundary(node.configuration.boundaries); break;
    case CanvasNodeType.TrackFindDwell:
      boundary(node.configuration.boundaries);
      add('minimumDurationUnit', node.configuration.minimumDurationUnit);
      add('rangeOptions.durationUnit', node.configuration.rangeOptions?.durationUnit); break;
    case CanvasNodeType.TrackDetectIncidents:
      boundary(node.configuration.boundaries);
      add('incidentDurationUnit', node.configuration.incidentDurationUnit); break;
    case CanvasNodeType.TrackMotionStatistics: {
      const c = node.configuration; boundary(c.boundaries);
      add('windowOptions.durationUnit', c.windowOptions?.durationUnit);
      add('windowOptions.idleTimeThresholdUnit', c.windowOptions?.idleTimeThresholdUnit);
      c.metrics.forEach((metric, index) => {
        if (metric.kind === 'DURATION') add(`metrics[${index}].outputUnit`, metric.outputUnit);
      });
      break;
    }
  }
  return paths;
}

/** Inspect unit fields only, including inactive settings; never scan arbitrary configuration strings. */
export function unsupportedSpatialUnitPaths(node: CanvasNodeDefinition, minorVersion: number): string[] {
  if (minorVersion >= 36) return [];
  const paths: string[] = [];
  const add = (path: string, unit: string | null | undefined) => { if (unit && newUnits.has(unit)) paths.push(`configuration.${path}`); };
  const boundary = (value: TrackBoundaryConfiguration) => add('boundaries.maximumDistanceGapUnit', value?.maximumDistanceGapUnit);
  switch (node.type) {
    case CanvasNodeType.GeometrySimplify: add('toleranceUnit', node.configuration.toleranceUnit); break;
    case CanvasNodeType.SpatialNearest: {
      const c = node.configuration;
      add('maximumDistanceUnit', c.maximumDistanceUnit); add('distanceOutputUnit', c.distanceOutputUnit);
      add('matching.connectionLines.maximumGeodesicSegmentLengthUnit', c.matching?.connectionLines?.maximumGeodesicSegmentLengthUnit);
      break;
    }
    case CanvasNodeType.SpatialSummarizeWithin:
      add('lengthUnit', node.configuration.lengthUnit); add('areaUnit', node.configuration.areaUnit); break;
    case CanvasNodeType.SpatialBinAggregate: add('binSizeUnit', node.configuration.binSizeUnit); break;
    case CanvasNodeType.SpatialPointCluster:
      if (node.configuration.parameters.algorithm === 'DBSCAN') add('parameters.searchDistanceUnit', node.configuration.parameters.searchDistanceUnit);
      break;
    case CanvasNodeType.TrackReconstruct:
      boundary(node.configuration.boundaries);
      add('reconstruction.pathGeometry.maximumGeodesicSegmentLengthUnit', node.configuration.reconstruction?.pathGeometry?.maximumGeodesicSegmentLengthUnit);
      break;
    case CanvasNodeType.TrackFindDwell:
      boundary(node.configuration.boundaries); add('distanceThresholdUnit', node.configuration.distanceThresholdUnit);
      add('rangeOptions.meanDistanceUnit', node.configuration.rangeOptions?.meanDistanceUnit); break;
    case CanvasNodeType.TrackDetectIncidents: boundary(node.configuration.boundaries); break;
    case CanvasNodeType.TrackMotionStatistics: {
      const c = node.configuration; boundary(c.boundaries); add('idleDistanceThresholdUnit', c.idleDistanceThresholdUnit);
      add('windowOptions.distanceUnit', c.windowOptions?.distanceUnit);
      add('windowOptions.inputElevationUnit', c.windowOptions?.inputElevationUnit);
      add('windowOptions.elevationUnit', c.windowOptions?.elevationUnit);
      c.metrics.forEach((metric, index) => {
        if (metric.kind === 'DISTANCE' || metric.kind === 'ELEVATION_CHANGE') add(`metrics[${index}].outputUnit`, metric.outputUnit);
      });
      break;
    }
  }
  return paths;
}
