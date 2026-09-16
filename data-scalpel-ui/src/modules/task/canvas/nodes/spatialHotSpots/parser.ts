import { spatialDistanceUnits } from "../spatialUnits";
import { stringValue } from "../../canvasValueParsers";
import { parseConfiguration, type Configuration, parseFiniteNumber, parseSpatialTemporalSlicing } from '../configurationParsing';

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'SPATIAL_HOT_SPOTS'>>(
      value,
      path,
      (configuration, errors) => {
        const analysisSource = stringValue(configuration.analysisSource);
        const multipleTesting = stringValue(configuration.multipleTesting);
        const binSizeUnit = stringValue(configuration.binSizeUnit);
        const neighborhoodDistanceUnit = stringValue(configuration.neighborhoodDistanceUnit);
        if (configuration.analysisSource != null
          && !['POINT_COUNT', 'FIELD_SUM'].includes(analysisSource)) {
          errors.push(`${path}.analysisSource 仅支持 POINT_COUNT 或 FIELD_SUM`);
        }
        if (configuration.analysisColumnName !== null
          && configuration.analysisColumnName !== undefined
          && typeof configuration.analysisColumnName !== 'string') {
          errors.push(`${path}.analysisColumnName 必须是字符串或 null`);
        }
        if (configuration.multipleTesting != null
          && !['NONE', 'FDR_BH'].includes(multipleTesting)) {
          errors.push(`${path}.multipleTesting 仅支持 NONE 或 FDR_BH`);
        }
        if (!spatialDistanceUnits.has(binSizeUnit)) {
          errors.push(`${path}.binSizeUnit 不是受支持的距离单位`);
        }
        if (!spatialDistanceUnits.has(neighborhoodDistanceUnit)) {
          errors.push(`${path}.neighborhoodDistanceUnit 不是受支持的距离单位`);
        }
        return {
          sourceTableName: stringValue(configuration.sourceTableName),
          pointGeometryColumnName: stringValue(configuration.pointGeometryColumnName),
          analysisSource: ['POINT_COUNT', 'FIELD_SUM'].includes(analysisSource)
            ? analysisSource as Configuration<'SPATIAL_HOT_SPOTS'>['analysisSource'] : null,
          analysisColumnName: typeof configuration.analysisColumnName === 'string'
            ? configuration.analysisColumnName : null,
          binSize: parseFiniteNumber(configuration.binSize, `${path}.binSize`, errors, 0),
          binSizeUnit: spatialDistanceUnits.has(binSizeUnit)
            ? binSizeUnit as Configuration<'SPATIAL_HOT_SPOTS'>['binSizeUnit'] : 'METERS',
          neighborhoodDistance: parseFiniteNumber(
            configuration.neighborhoodDistance, `${path}.neighborhoodDistance`, errors, 0,
          ),
          neighborhoodDistanceUnit: spatialDistanceUnits.has(neighborhoodDistanceUnit)
            ? neighborhoodDistanceUnit as Configuration<'SPATIAL_HOT_SPOTS'>['neighborhoodDistanceUnit'] : 'METERS',
          temporalSlicing: parseSpatialTemporalSlicing(
            configuration.temporalSlicing, `${path}.temporalSlicing`, errors,
          ),
          multipleTesting: ['NONE', 'FDR_BH'].includes(multipleTesting)
            ? multipleTesting as Configuration<'SPATIAL_HOT_SPOTS'>['multipleTesting'] : null,
          outputTableName: stringValue(configuration.outputTableName),
          binIdColumnName: stringValue(configuration.binIdColumnName),
          binGeometryColumnName: stringValue(configuration.binGeometryColumnName),
          pointCountColumnName: stringValue(configuration.pointCountColumnName),
          analysisValueColumnName: stringValue(configuration.analysisValueColumnName),
          zScoreColumnName: stringValue(configuration.zScoreColumnName),
          pValueColumnName: stringValue(configuration.pValueColumnName),
          adjustedPValueColumnName: stringValue(configuration.adjustedPValueColumnName),
          confidenceBinColumnName: stringValue(configuration.confidenceBinColumnName),
        };
      },
    )
  );
