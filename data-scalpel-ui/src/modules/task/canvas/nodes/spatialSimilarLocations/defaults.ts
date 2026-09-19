import type { SpatialSimilarLocationsConfiguration } from "../../canvasTypes";

export const createSpatialSimilarLocationsConfiguration = (
): SpatialSimilarLocationsConfiguration => ({
  referenceTableName: '',
  referenceIdColumnName: '',
  referenceGeometryColumnName: '',
  referenceFilter: null,
  candidateTableName: '',
  candidateIdColumnName: '',
  candidateGeometryColumnName: '',
  candidateFilter: null,
  analysisFields: [],
  appendFields: [],
  matchMethod: 'ATTRIBUTE_VALUES',
  resultMode: 'MOST_SIMILAR',
  numberOfResults: 10,
  outputTableName: '',
  outputGeometryColumnName: 'geometry',
  locationTypeColumnName: 'location_type',
  similarityRankColumnName: 'simrank',
  dissimilarityRankColumnName: 'dsimrank',
  similarityIndexColumnName: 'simindex',
  cosineIndexColumnName: 'cosimindex',
  labelRankColumnName: 'labelrank',
  referenceIdOutputColumnName: 'referenceid',
  searchIdOutputColumnName: 'searchid',
});
