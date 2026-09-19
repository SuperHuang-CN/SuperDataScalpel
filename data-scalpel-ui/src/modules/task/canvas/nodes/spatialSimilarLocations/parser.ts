import { parseFilterCondition, stringValue } from "../../canvasValueParsers";
import { CANVAS_SPATIAL_SIMILAR_LOCATIONS_MAX_ANALYSIS_FIELDS, CANVAS_SPATIAL_SIMILAR_LOCATIONS_MAX_APPEND_FIELDS, type SpatialSimilarLocationsAnalysisField, type SpatialSimilarLocationsAppendField } from "../../canvasTypes";
import { parseConfiguration, isRecord, type Configuration, parseInteger } from '../configurationParsing';

export const parseSpatialSimilarLocationsAnalysisFields = (
  value: unknown,
  path: string,
  errors: string[],
): SpatialSimilarLocationsAnalysisField[] => {
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  if (value.length > CANVAS_SPATIAL_SIMILAR_LOCATIONS_MAX_ANALYSIS_FIELDS) {
    errors.push(`${path} 不能超过 ${CANVAS_SPATIAL_SIMILAR_LOCATIONS_MAX_ANALYSIS_FIELDS} 项`);
  }
  return value.slice(0, CANVAS_SPATIAL_SIMILAR_LOCATIONS_MAX_ANALYSIS_FIELDS)
    .flatMap((item, index): SpatialSimilarLocationsAnalysisField[] => {
      const itemPath = `${path}[${index}]`;
      if (!isRecord(item)) {
        errors.push(`${itemPath} 必须是对象`);
        return [];
      }
      if (typeof item.columnName !== 'string') {
        errors.push(`${itemPath}.columnName 必须是字符串`);
      }
      if (typeof item.outputColumnName !== 'string') {
        errors.push(`${itemPath}.outputColumnName 必须是字符串`);
      }
      return [{
        columnName: stringValue(item.columnName),
        outputColumnName: stringValue(item.outputColumnName),
      }];
    });
};

export const parseSpatialSimilarLocationsAppendFields = (
  value: unknown,
  path: string,
  errors: string[],
): SpatialSimilarLocationsAppendField[] => {
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  if (value.length > CANVAS_SPATIAL_SIMILAR_LOCATIONS_MAX_APPEND_FIELDS) {
    errors.push(`${path} 不能超过 ${CANVAS_SPATIAL_SIMILAR_LOCATIONS_MAX_APPEND_FIELDS} 项`);
  }
  return value.slice(0, CANVAS_SPATIAL_SIMILAR_LOCATIONS_MAX_APPEND_FIELDS)
    .flatMap((item, index): SpatialSimilarLocationsAppendField[] => {
      const itemPath = `${path}[${index}]`;
      if (!isRecord(item)) {
        errors.push(`${itemPath} 必须是对象`);
        return [];
      }
      if (typeof item.sourceColumnName !== 'string') {
        errors.push(`${itemPath}.sourceColumnName 必须是字符串`);
      }
      if (typeof item.outputColumnName !== 'string') {
        errors.push(`${itemPath}.outputColumnName 必须是字符串`);
      }
      return [{
        sourceColumnName: stringValue(item.sourceColumnName),
        outputColumnName: stringValue(item.outputColumnName),
      }];
    });
};

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'SPATIAL_SIMILAR_LOCATIONS'>>(
      value,
      path,
      (configuration, errors) => {
        const matchMethod = configuration.matchMethod == null
          ? null : stringValue(configuration.matchMethod);
        const resultMode = configuration.resultMode == null
          ? null : stringValue(configuration.resultMode);
        if (matchMethod !== null
          && !['ATTRIBUTE_VALUES', 'ATTRIBUTE_PROFILES'].includes(matchMethod)) {
          errors.push(`${path}.matchMethod 不是受支持的匹配方法`);
        }
        if (resultMode !== null
          && !['MOST_SIMILAR', 'LEAST_SIMILAR', 'BOTH'].includes(resultMode)) {
          errors.push(`${path}.resultMode 不是受支持的返回范围`);
        }
        return {
          referenceTableName: stringValue(configuration.referenceTableName),
          referenceIdColumnName: stringValue(configuration.referenceIdColumnName),
          referenceGeometryColumnName: stringValue(configuration.referenceGeometryColumnName),
          referenceFilter: configuration.referenceFilter == null ? null
            : parseFilterCondition(configuration.referenceFilter, `${path}.referenceFilter`, errors),
          candidateTableName: stringValue(configuration.candidateTableName),
          candidateIdColumnName: stringValue(configuration.candidateIdColumnName),
          candidateGeometryColumnName: stringValue(configuration.candidateGeometryColumnName),
          candidateFilter: configuration.candidateFilter == null ? null
            : parseFilterCondition(configuration.candidateFilter, `${path}.candidateFilter`, errors),
          analysisFields: parseSpatialSimilarLocationsAnalysisFields(
            configuration.analysisFields, `${path}.analysisFields`, errors,
          ),
          appendFields: parseSpatialSimilarLocationsAppendFields(
            configuration.appendFields, `${path}.appendFields`, errors,
          ),
          matchMethod: ['ATTRIBUTE_VALUES', 'ATTRIBUTE_PROFILES'].includes(matchMethod ?? '')
            ? matchMethod as Configuration<'SPATIAL_SIMILAR_LOCATIONS'>['matchMethod'] : null,
          resultMode: ['MOST_SIMILAR', 'LEAST_SIMILAR', 'BOTH'].includes(resultMode ?? '')
            ? resultMode as Configuration<'SPATIAL_SIMILAR_LOCATIONS'>['resultMode'] : null,
          numberOfResults: parseInteger(
            configuration.numberOfResults, `${path}.numberOfResults`, errors, 0,
          ),
          outputTableName: stringValue(configuration.outputTableName),
          outputGeometryColumnName: stringValue(configuration.outputGeometryColumnName),
          locationTypeColumnName: stringValue(configuration.locationTypeColumnName),
          similarityRankColumnName: stringValue(configuration.similarityRankColumnName),
          dissimilarityRankColumnName: stringValue(configuration.dissimilarityRankColumnName),
          similarityIndexColumnName: stringValue(configuration.similarityIndexColumnName),
          cosineIndexColumnName: stringValue(configuration.cosineIndexColumnName),
          labelRankColumnName: stringValue(configuration.labelRankColumnName),
          referenceIdOutputColumnName: stringValue(configuration.referenceIdOutputColumnName),
          searchIdOutputColumnName: stringValue(configuration.searchIdOutputColumnName),
        };
      },
    )
  );
