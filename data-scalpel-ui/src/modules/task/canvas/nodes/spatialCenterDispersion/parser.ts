import { parseCenterFeatureColumns, parseCenterOutputTable, parseCenterResultMode } from "./resultMode";
import { parseStringArray, stringValue, validateOptionalUuid } from "../../canvasValueParsers";
import { CANVAS_SPATIAL_CENTER_MAX_ANALYSES, CANVAS_SPATIAL_CENTER_MAX_GROUP_COLUMNS, type SpatialCenterDispersionAnalysis } from "../../canvasTypes";
import { parseConfiguration, isRecord, type Configuration } from '../configurationParsing';

export const spatialCenterKinds = new Set([
  'MEAN_CENTER', 'MEDIAN_CENTER', 'CENTRAL_FEATURE',
  'STANDARD_DISTANCE', 'DIRECTIONAL_ELLIPSE',
]);

export const parseSpatialCenterAnalyses = (
  value: unknown,
  path: string,
  errors: string[],
): SpatialCenterDispersionAnalysis[] => {
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  if (value.length > CANVAS_SPATIAL_CENTER_MAX_ANALYSES) {
    errors.push(`${path} 不能超过 ${CANVAS_SPATIAL_CENTER_MAX_ANALYSES} 项`);
  }
  return value.slice(0, CANVAS_SPATIAL_CENTER_MAX_ANALYSES)
    .flatMap((item, index): SpatialCenterDispersionAnalysis[] => {
      const itemPath = `${path}[${index}]`;
      if (!isRecord(item)) {
        errors.push(`${itemPath} 必须是对象`);
        return [];
      }
      const kind = stringValue(item.kind);
      if (!spatialCenterKinds.has(kind)) errors.push(`${itemPath}.kind 不是受支持的分析类型`);
      if (item.standardDeviations !== null && item.standardDeviations !== undefined
        && (typeof item.standardDeviations !== 'number'
          || !Number.isSafeInteger(item.standardDeviations))) {
        errors.push(`${itemPath}.standardDeviations 必须是安全整数或 null`);
      }
      return [{
        analysisId: validateOptionalUuid(
          stringValue(item.analysisId), `${itemPath}.analysisId`, errors,
        ),
        kind: spatialCenterKinds.has(kind)
          ? kind as SpatialCenterDispersionAnalysis['kind'] : 'MEAN_CENTER',
        outputColumnName: stringValue(item.outputColumnName),
        standardDeviations: typeof item.standardDeviations === 'number'
          ? item.standardDeviations : null,
        ...parseCenterOutputTable(item, itemPath, errors),
        ...parseCenterFeatureColumns(item, itemPath, errors),
      }];
    });
};

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'SPATIAL_CENTER_DISPERSION'>>(
      value,
      path,
      (configuration, errors) => {
        if (configuration.featureIdColumnName !== null
          && configuration.featureIdColumnName !== undefined
          && typeof configuration.featureIdColumnName !== 'string') {
          errors.push(`${path}.featureIdColumnName 必须是字符串或 null`);
        }
        if (configuration.weightColumnName !== null
          && configuration.weightColumnName !== undefined
          && typeof configuration.weightColumnName !== 'string') {
          errors.push(`${path}.weightColumnName 必须是字符串或 null`);
        }
        const groups = parseStringArray(
          configuration.groupByColumns, `${path}.groupByColumns`, errors,
        );
        if (groups.length > CANVAS_SPATIAL_CENTER_MAX_GROUP_COLUMNS) {
          errors.push(`${path}.groupByColumns 不能超过 ${CANVAS_SPATIAL_CENTER_MAX_GROUP_COLUMNS} 项`);
        }
        return {
          sourceTableName: stringValue(configuration.sourceTableName),
          pointGeometryColumnName: stringValue(configuration.pointGeometryColumnName),
          featureIdColumnName: typeof configuration.featureIdColumnName === 'string'
            ? configuration.featureIdColumnName : null,
          groupByColumns: groups,
          weightColumnName: typeof configuration.weightColumnName === 'string'
            ? configuration.weightColumnName : null,
          analyses: parseSpatialCenterAnalyses(
            configuration.analyses, `${path}.analyses`, errors,
          ),
          outputTableName: stringValue(configuration.outputTableName),
          ...parseCenterResultMode(configuration, path, errors),
        };
      },
    )
  );
