import { parseBinSizeSemantics } from "./binSizeSemantics";
import { parseH3 } from "./h3";
import { parsePlanarGrid } from "./planarGrid";
import { stringValue, validateOptionalUuid } from "../../canvasValueParsers";
import { CANVAS_SPATIAL_BIN_MAX_STATISTICS, type SpatialBinStatistic } from "../../canvasTypes";
import { parseConfiguration, isRecord, type Configuration, trackDistanceUnits, parseFiniteNumber, parseSpatialGroupSummary, parseSpatialTemporalSlicing } from '../configurationParsing';

export const spatialBinStatisticKinds = new Set([
  'COUNT', 'COUNT_FIELD', 'ANY', 'SUM', 'MEAN', 'MIN', 'MAX', 'RANGE', 'STDDEV', 'VARIANCE',
]);

export const parseSpatialBinStatistics = (
  value: unknown,
  path: string,
  errors: string[],
): SpatialBinStatistic[] => {
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  if (value.length > CANVAS_SPATIAL_BIN_MAX_STATISTICS) {
    errors.push(`${path} 不能超过 ${CANVAS_SPATIAL_BIN_MAX_STATISTICS} 项`);
  }
  return value.slice(0, CANVAS_SPATIAL_BIN_MAX_STATISTICS)
    .flatMap((item, index): SpatialBinStatistic[] => {
      const itemPath = `${path}[${index}]`;
      if (!isRecord(item)) {
        errors.push(`${itemPath} 必须是对象`);
        return [];
      }
      const kind = stringValue(item.kind);
      if (!spatialBinStatisticKinds.has(kind)) {
        errors.push(`${itemPath}.kind 不是受支持的格网统计类型`);
      }
      if (item.sourceColumnName !== null && item.sourceColumnName !== undefined
        && typeof item.sourceColumnName !== 'string') {
        errors.push(`${itemPath}.sourceColumnName 必须是字符串或 null`);
      }
      return [{
        statisticId: validateOptionalUuid(
          stringValue(item.statisticId), `${itemPath}.statisticId`, errors,
        ),
        kind: spatialBinStatisticKinds.has(kind)
          ? kind as SpatialBinStatistic['kind'] : 'COUNT',
        sourceColumnName: typeof item.sourceColumnName === 'string'
          ? item.sourceColumnName : null,
        outputColumnName: stringValue(item.outputColumnName),
      }];
    });
};

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'SPATIAL_BIN_AGGREGATE'>>(
      value,
      path,
      (configuration, errors) => {
        const binShape = stringValue(configuration.binShape);
        const binSizeUnit = stringValue(configuration.binSizeUnit);
        if (configuration.binShape != null && !['SQUARE', 'HEXAGON', 'H3'].includes(binShape)) {
          errors.push(`${path}.binShape 仅支持 SQUARE、HEXAGON 或 H3`);
        }
        if (!trackDistanceUnits.has(binSizeUnit)) {
          errors.push(`${path}.binSizeUnit 不是受支持的距离单位`);
        }
        return {
          sourceTableName: stringValue(configuration.sourceTableName),
          pointGeometryColumnName: stringValue(configuration.pointGeometryColumnName),
          binShape: ['SQUARE', 'HEXAGON', 'H3'].includes(binShape)
            ? binShape as Configuration<'SPATIAL_BIN_AGGREGATE'>['binShape'] : null,
          binSize: parseFiniteNumber(configuration.binSize, `${path}.binSize`, errors, 0),
          ...parseH3(configuration, path, errors),
          ...parsePlanarGrid(configuration, path, errors),
          ...(configuration.binSizeSemantics === undefined ? {} : {
            binSizeSemantics: parseBinSizeSemantics(configuration.binSizeSemantics, `${path}.binSizeSemantics`, errors),
          }),
          binSizeUnit: trackDistanceUnits.has(binSizeUnit)
            ? binSizeUnit as Configuration<'SPATIAL_BIN_AGGREGATE'>['binSizeUnit'] : 'METERS',
          includeEmptyBins: configuration.includeEmptyBins === true,
          statistics: parseSpatialBinStatistics(
            configuration.statistics, `${path}.statistics`, errors,
          ),
          groupSummary: parseSpatialGroupSummary(
            configuration.groupSummary, `${path}.groupSummary`, errors,
          ),
          temporalSlicing: parseSpatialTemporalSlicing(
            configuration.temporalSlicing, `${path}.temporalSlicing`, errors,
          ),
          outputTableName: stringValue(configuration.outputTableName),
          binIdColumnName: stringValue(configuration.binIdColumnName),
          binGeometryColumnName: stringValue(configuration.binGeometryColumnName),
        };
      },
    )
  );
