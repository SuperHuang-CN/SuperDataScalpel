import type { SpatialBinAggregateConfiguration } from "../../canvasTypes";

export const createSpatialBinAggregateConfiguration = (): SpatialBinAggregateConfiguration => ({
  binSizeSemantics: 'HEXAGON_FLAT_TO_FLAT',
  sourceTableName: '',
  pointGeometryColumnName: '',
  binShape: 'SQUARE',
  binSize: 1000,
  binSizeUnit: 'METERS',
  includeEmptyBins: false,
  statistics: [{
    statisticId: crypto.randomUUID(),
    kind: 'COUNT',
    sourceColumnName: null,
    outputColumnName: 'point_count',
  }],
  groupSummary: null,
  temporalSlicing: null,
  outputTableName: '',
  binIdColumnName: 'bin_id',
  binGeometryColumnName: 'bin_geometry',
});
