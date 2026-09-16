import { parseDbscanOptions } from "./dbscanOptions";
import { parseHdbscanOptions } from "./hdbscanOptions";
import { stringValue } from "../../canvasValueParsers";
import { type SpatialPointClusterParameters } from "../../canvasTypes";
import { parseConfiguration, isRecord, type Configuration, trackDistanceUnits, parseFiniteNumber } from '../configurationParsing';

export const parseSpatialPointClusterParameters = (
  value: unknown,
  path: string,
  errors: string[],
): SpatialPointClusterParameters => {
  if (!isRecord(value)) {
    errors.push(`${path} 必须是算法参数对象`);
    return {
      algorithm: 'DBSCAN', searchDistance: 0,
      searchDistanceUnit: 'METERS', minimumFeatures: 0,
    };
  }
  const minimumFeatures = typeof value.minimumFeatures === 'number'
    && Number.isSafeInteger(value.minimumFeatures) ? value.minimumFeatures : 0;
  if (typeof value.minimumFeatures !== 'number' || !Number.isSafeInteger(value.minimumFeatures)) {
    errors.push(`${path}.minimumFeatures 必须是安全整数`);
  }
  if (value.algorithm === 'DBSCAN') {
    const unit = stringValue(value.searchDistanceUnit);
    if (!trackDistanceUnits.has(unit)) errors.push(`${path}.searchDistanceUnit 不是受支持的距离单位`);
    return {
      algorithm: 'DBSCAN',
      searchDistance: parseFiniteNumber(value.searchDistance, `${path}.searchDistance`, errors, 0),
      searchDistanceUnit: trackDistanceUnits.has(unit)
        ? unit as Extract<SpatialPointClusterParameters, { algorithm: 'DBSCAN' }>['searchDistanceUnit']
        : 'METERS',
      minimumFeatures,
    };
  }
  if (value.algorithm === 'HDBSCAN') return { algorithm: 'HDBSCAN', minimumFeatures };
  if (value.algorithm === 'MULTI_SCALE') {
    return {
      algorithm: 'MULTI_SCALE',
      minimumFeatures,
      sensitivity: parseFiniteNumber(value.sensitivity, `${path}.sensitivity`, errors, 0),
    };
  }
  errors.push(`${path}.algorithm 仅支持 DBSCAN、HDBSCAN 或 MULTI_SCALE`);
  return {
    algorithm: 'DBSCAN', searchDistance: 0,
    searchDistanceUnit: 'METERS', minimumFeatures,
  };
};

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'SPATIAL_POINT_CLUSTER'>>(
      value,
      path,
      (configuration, errors) => {
        const distanceMethod = stringValue(configuration.distanceMethod);
        if (!['PLANAR', 'GEODESIC'].includes(distanceMethod)) {
          errors.push(`${path}.distanceMethod 仅支持 PLANAR 或 GEODESIC`);
        }
        return {
          sourceTableName: stringValue(configuration.sourceTableName),
          pointGeometryColumnName: stringValue(configuration.pointGeometryColumnName),
          featureIdColumnName: stringValue(configuration.featureIdColumnName),
          distanceMethod: ['PLANAR', 'GEODESIC'].includes(distanceMethod)
            ? distanceMethod as Configuration<'SPATIAL_POINT_CLUSTER'>['distanceMethod'] : null,
          parameters: parseSpatialPointClusterParameters(
            configuration.parameters, `${path}.parameters`, errors,
          ),
          ...parseDbscanOptions(configuration, path, errors),
          ...parseHdbscanOptions(configuration, path, errors),
          outputTableName: stringValue(configuration.outputTableName),
          clusterIdColumnName: stringValue(configuration.clusterIdColumnName),
          noiseColumnName: stringValue(configuration.noiseColumnName),
        };
      },
    )
  );
