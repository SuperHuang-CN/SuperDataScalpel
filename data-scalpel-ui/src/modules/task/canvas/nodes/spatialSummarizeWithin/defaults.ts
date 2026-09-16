import { createWithinGroupResult } from "./groupResult";
import type { SpatialSummarizeWithinConfiguration } from "../../canvasTypes";

export const createSpatialSummarizeWithinConfiguration = (): SpatialSummarizeWithinConfiguration => ({
  areaTableName: '',
  areaGeometryColumnName: '',
  summaryTableName: '',
  summaryGeometryColumnName: '',
  includeEmptyAreas: true,
  distanceMethod: 'PLANAR',
  lengthUnit: 'SOURCE_CRS_UNIT',
  areaUnit: 'SQUARE_METERS',
  areaOutputColumns: [],
  statistics: [],
  groupSummary: null,
  groupResult: createWithinGroupResult(),
  temporalSlicing: null,
  outputTableName: '',
});
