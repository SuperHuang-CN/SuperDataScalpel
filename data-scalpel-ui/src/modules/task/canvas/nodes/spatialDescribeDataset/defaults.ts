import type { SpatialDescribeDatasetConfiguration } from "../../canvasTypes";

export const createSpatialDescribeDatasetConfiguration = (
): SpatialDescribeDatasetConfiguration => ({
  sourceTableName: '',
  geometryColumnName: '',
  statisticsTableName: '',
  descriptionTableName: '',
  sampleSize: 0,
  sampleTableName: '',
  extentOutput: false,
  extentTableName: '',
});
