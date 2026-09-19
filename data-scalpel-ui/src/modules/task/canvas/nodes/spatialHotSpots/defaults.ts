import type { SpatialHotSpotsConfiguration } from "../../canvasTypes";

export const createSpatialHotSpotsConfiguration = (): SpatialHotSpotsConfiguration => ({
  sourceTableName: '',
  pointGeometryColumnName: '',
  analysisSource: 'POINT_COUNT',
  analysisColumnName: null,
  binSize: 1000,
  binSizeUnit: 'METERS',
  neighborhoodDistance: 2000,
  neighborhoodDistanceUnit: 'METERS',
  temporalSlicing: null,
  multipleTesting: 'FDR_BH',
  outputTableName: '',
  binIdColumnName: 'bin_id',
  binGeometryColumnName: 'bin_geometry',
  pointCountColumnName: 'point_count',
  analysisValueColumnName: 'analysis_value',
  zScoreColumnName: 'gi_z_score',
  pValueColumnName: 'gi_p_value',
  adjustedPValueColumnName: 'gi_adjusted_p_value',
  confidenceBinColumnName: 'gi_bin',
});
