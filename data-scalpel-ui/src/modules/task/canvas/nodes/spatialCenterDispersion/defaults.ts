import { createUuid } from '../../../../../shared/browser/createUuid';
import type { SpatialCenterDispersionConfiguration } from "../../canvasTypes";

export const createSpatialCenterDispersionConfiguration = (
): SpatialCenterDispersionConfiguration => ({
  sourceTableName: '',
  pointGeometryColumnName: '',
  featureIdColumnName: null,
  groupByColumns: [],
  weightColumnName: null,
  analyses: [{ analysisId: createUuid(), kind: 'MEAN_CENTER', outputColumnName: 'mean_center', standardDeviations: null, outputTableName: '' }],
  outputTableName: '',
  resultMode: 'ANALYSIS_TABLES',
});
