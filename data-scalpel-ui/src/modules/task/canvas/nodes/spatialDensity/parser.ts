import { spatialDistanceUnits, spatialAreaUnits } from "../spatialUnits";
import { stringValue, validateOptionalUuid } from "../../canvasValueParsers";
import { CANVAS_SPATIAL_DENSITY_MAX_FIELDS, type SpatialDensityField } from "../../canvasTypes";
import { parseConfiguration, isRecord, type Configuration, parseFiniteNumber, parseSpatialTemporalSlicing } from '../configurationParsing';

export const parseSpatialDensityFields = (
  value: unknown,
  path: string,
  errors: string[],
): SpatialDensityField[] => {
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  if (value.length > CANVAS_SPATIAL_DENSITY_MAX_FIELDS) {
    errors.push(`${path} 不能超过 ${CANVAS_SPATIAL_DENSITY_MAX_FIELDS} 项`);
  }
  return value.slice(0, CANVAS_SPATIAL_DENSITY_MAX_FIELDS)
    .flatMap((item, index): SpatialDensityField[] => {
      const itemPath = `${path}[${index}]`;
      if (!isRecord(item)) {
        errors.push(`${itemPath} 必须是对象`);
        return [];
      }
      return [{
        fieldId: validateOptionalUuid(stringValue(item.fieldId), `${itemPath}.fieldId`, errors),
        sourceColumnName: stringValue(item.sourceColumnName),
        outputColumnName: stringValue(item.outputColumnName),
      }];
    });
};

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'SPATIAL_DENSITY'>>(
      value,
      path,
      (configuration, errors) => {
        const weighting = stringValue(configuration.weighting);
        const binShape = stringValue(configuration.binShape);
        const binSizeUnit = stringValue(configuration.binSizeUnit);
        const radiusUnit = stringValue(configuration.radiusUnit);
        const areaUnit = stringValue(configuration.areaUnit);
        if (configuration.weighting != null && !['UNIFORM', 'KERNEL'].includes(weighting)) {
          errors.push(`${path}.weighting 仅支持 UNIFORM 或 KERNEL`);
        }
        if (configuration.binShape != null && !['SQUARE', 'HEXAGON'].includes(binShape)) {
          errors.push(`${path}.binShape 仅支持 SQUARE 或 HEXAGON`);
        }
        if (!spatialDistanceUnits.has(binSizeUnit)) errors.push(`${path}.binSizeUnit 不是受支持的距离单位`);
        if (!spatialDistanceUnits.has(radiusUnit)) errors.push(`${path}.radiusUnit 不是受支持的距离单位`);
        if (!spatialAreaUnits.has(areaUnit)) errors.push(`${path}.areaUnit 不是受支持的面积单位`);
        return {
          sourceTableName: stringValue(configuration.sourceTableName),
          pointGeometryColumnName: stringValue(configuration.pointGeometryColumnName),
          fields: parseSpatialDensityFields(configuration.fields, `${path}.fields`, errors),
          weighting: ['UNIFORM', 'KERNEL'].includes(weighting)
            ? weighting as Configuration<'SPATIAL_DENSITY'>['weighting'] : null,
          binShape: ['SQUARE', 'HEXAGON'].includes(binShape)
            ? binShape as Configuration<'SPATIAL_DENSITY'>['binShape'] : null,
          binSize: parseFiniteNumber(configuration.binSize, `${path}.binSize`, errors, 0),
          binSizeUnit: spatialDistanceUnits.has(binSizeUnit)
            ? binSizeUnit as Configuration<'SPATIAL_DENSITY'>['binSizeUnit'] : 'METERS',
          radius: parseFiniteNumber(configuration.radius, `${path}.radius`, errors, 0),
          radiusUnit: spatialDistanceUnits.has(radiusUnit)
            ? radiusUnit as Configuration<'SPATIAL_DENSITY'>['radiusUnit'] : 'METERS',
          areaUnit: spatialAreaUnits.has(areaUnit)
            ? areaUnit as Configuration<'SPATIAL_DENSITY'>['areaUnit'] : 'SQUARE_KILOMETERS',
          temporalSlicing: parseSpatialTemporalSlicing(
            configuration.temporalSlicing, `${path}.temporalSlicing`, errors,
          ),
          outputTableName: stringValue(configuration.outputTableName),
          binIdColumnName: stringValue(configuration.binIdColumnName),
          binGeometryColumnName: stringValue(configuration.binGeometryColumnName),
          countDensityColumnName: stringValue(configuration.countDensityColumnName),
        };
      },
    )
  );
