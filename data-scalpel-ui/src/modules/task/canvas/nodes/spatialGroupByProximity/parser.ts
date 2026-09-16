import { spatialDistanceUnits } from "../spatialUnits";
import { stringValue } from "../../canvasValueParsers";
import { type SpatialGroupByProximityAttributeCondition, type SpatialGroupByProximityAttributeRelationship, type SpatialGroupByProximitySpatialRelationship, type SpatialGroupByProximityTemporalCondition, type SpatialGroupByProximityTemporalRelationship, type SpatialGroupByProximityTemporalUnit, type SpatialDistanceUnit } from "../../canvasTypes";
import { parseConfiguration, isRecord, type Configuration, groupByProximityTemporalUnits } from '../configurationParsing';

export const groupByProximitySpatialRelationships = new Set([
  'INTERSECTS', 'TOUCHES', 'NEAR_PLANAR', 'NEAR_GEODESIC',
]);

export const groupByProximityTemporalRelationships = new Set(['INTERSECTS', 'NEAR']);

export const groupByProximityAttributeRelationships = new Set([
  'EQUALS', 'ABSOLUTE_DIFFERENCE_AT_MOST',
]);

export const parseGroupByProximityTemporalCondition = (
  value: unknown,
  path: string,
  errors: string[],
): SpatialGroupByProximityTemporalCondition | null => {
  if (value == null) return null;
  if (!isRecord(value)) {
    errors.push(`${path} 必须是对象或 null`);
    return null;
  }
  const relationship = value.relationship == null ? null : stringValue(value.relationship);
  if (relationship !== null && !groupByProximityTemporalRelationships.has(relationship)) {
    errors.push(`${path}.relationship 不是受支持的时间关系`);
  }
  const unit = value.nearDistanceUnit == null ? null : stringValue(value.nearDistanceUnit);
  if (unit !== null && !groupByProximityTemporalUnits.has(unit)) {
    errors.push(`${path}.nearDistanceUnit 不是受支持的时间单位`);
  }
  if (value.endColumnName != null && typeof value.endColumnName !== 'string') {
    errors.push(`${path}.endColumnName 必须是字符串或 null`);
  }
  if (value.nearDistance != null
    && (typeof value.nearDistance !== 'number' || !Number.isInteger(value.nearDistance))) {
    errors.push(`${path}.nearDistance 必须是整数或 null`);
  }
  return {
    relationship: relationship !== null && groupByProximityTemporalRelationships.has(relationship)
      ? relationship as SpatialGroupByProximityTemporalRelationship : null,
    startColumnName: stringValue(value.startColumnName),
    endColumnName: typeof value.endColumnName === 'string' ? value.endColumnName : null,
    nearDistance: typeof value.nearDistance === 'number' && Number.isInteger(value.nearDistance)
      ? value.nearDistance : null,
    nearDistanceUnit: unit !== null && groupByProximityTemporalUnits.has(unit)
      ? unit as SpatialGroupByProximityTemporalUnit : null,
  };
};

export const parseGroupByProximityAttributeConditions = (
  value: unknown,
  path: string,
  errors: string[],
): SpatialGroupByProximityAttributeCondition[] => {
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  return value.flatMap((item, index): SpatialGroupByProximityAttributeCondition[] => {
    const itemPath = `${path}[${index}]`;
    if (!isRecord(item)) {
      errors.push(`${itemPath} 必须是对象`);
      return [];
    }
    const relationship = item.relationship == null ? null : stringValue(item.relationship);
    if (relationship !== null && !groupByProximityAttributeRelationships.has(relationship)) {
      errors.push(`${itemPath}.relationship 不是受支持的属性关系`);
    }
    if (item.maximumDifference != null
      && (typeof item.maximumDifference !== 'number'
        || !Number.isFinite(item.maximumDifference))) {
      errors.push(`${itemPath}.maximumDifference 必须是有限数值或 null`);
    }
    return [{
      columnName: stringValue(item.columnName),
      relationship: relationship !== null
        && groupByProximityAttributeRelationships.has(relationship)
        ? relationship as SpatialGroupByProximityAttributeRelationship : null,
      maximumDifference: typeof item.maximumDifference === 'number'
        && Number.isFinite(item.maximumDifference) ? item.maximumDifference : null,
    }];
  });
};

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'SPATIAL_GROUP_BY_PROXIMITY'>>(
      value,
      path,
      (configuration, errors) => {
        const spatialRelationship = configuration.spatialRelationship == null
          ? null : stringValue(configuration.spatialRelationship);
        const distanceUnit = configuration.spatialNearDistanceUnit == null
          ? null : stringValue(configuration.spatialNearDistanceUnit);
        if (spatialRelationship !== null
          && !groupByProximitySpatialRelationships.has(spatialRelationship)) {
          errors.push(`${path}.spatialRelationship 不是受支持的空间关系`);
        }
        if (distanceUnit !== null && !spatialDistanceUnits.has(distanceUnit)) {
          errors.push(`${path}.spatialNearDistanceUnit 不是受支持的距离单位`);
        }
        if (configuration.spatialNearDistance != null
          && (typeof configuration.spatialNearDistance !== 'number'
            || !Number.isFinite(configuration.spatialNearDistance))) {
          errors.push(`${path}.spatialNearDistance 必须是有限数值或 null`);
        }
        return {
          sourceTableName: stringValue(configuration.sourceTableName),
          geometryColumnName: stringValue(configuration.geometryColumnName),
          spatialRelationship: spatialRelationship !== null
            && groupByProximitySpatialRelationships.has(spatialRelationship)
            ? spatialRelationship as SpatialGroupByProximitySpatialRelationship : null,
          spatialNearDistance: typeof configuration.spatialNearDistance === 'number'
            && Number.isFinite(configuration.spatialNearDistance)
            ? configuration.spatialNearDistance : null,
          spatialNearDistanceUnit: distanceUnit !== null && spatialDistanceUnits.has(distanceUnit)
            ? distanceUnit as SpatialDistanceUnit : null,
          temporalCondition: parseGroupByProximityTemporalCondition(
            configuration.temporalCondition, `${path}.temporalCondition`, errors,
          ),
          attributeConditions: parseGroupByProximityAttributeConditions(
            configuration.attributeConditions, `${path}.attributeConditions`, errors,
          ),
          groupIdColumnName: stringValue(configuration.groupIdColumnName),
          outputTableName: stringValue(configuration.outputTableName),
        };
      },
    )
  );
