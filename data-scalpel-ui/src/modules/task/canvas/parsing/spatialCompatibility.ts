import { CanvasNodeType, type CanvasNodeDefinition, type TrackBoundaryConfiguration } from '../canvasTypes';

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
    case CanvasNodeType.SpatialDensity:
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
    case CanvasNodeType.SpatialDensity:
      add('binSizeUnit', node.configuration.binSizeUnit);
      add('radiusUnit', node.configuration.radiusUnit);
      add('areaUnit', node.configuration.areaUnit);
      break;
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
