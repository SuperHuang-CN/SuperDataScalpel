import { spatialDistanceUnits } from "../spatialUnits";
import { parseUnaryPolicy } from "../unaryGeometryPolicy";
import { stringValue } from "../../canvasValueParsers";
import { parseConfiguration, type Configuration } from '../configurationParsing';

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'GEOMETRY_SIMPLIFY'>>(
      value,
      path,
      (configuration, errors) => {
        const algorithm = stringValue(configuration.algorithm);
        const toleranceUnit = stringValue(configuration.toleranceUnit);
        const allowedAlgorithms = new Set(['DOUGLAS_PEUCKER', 'TOPOLOGY_PRESERVING']);
        const allowedUnits = spatialDistanceUnits;
        if (configuration.algorithm != null && !allowedAlgorithms.has(algorithm)) {
          errors.push(`${path}.algorithm 不是受支持的简化算法`);
        }
        if (configuration.toleranceUnit != null && !allowedUnits.has(toleranceUnit)) {
          errors.push(`${path}.toleranceUnit 不是受支持的距离单位`);
        }
        if (configuration.tolerance != null && (typeof configuration.tolerance !== 'number'
          || !Number.isFinite(configuration.tolerance))) {
          errors.push(`${path}.tolerance 必须是有限数值或 null`);
        }
        return {
          sourceTableName: stringValue(configuration.sourceTableName),
          geometryColumnName: stringValue(configuration.geometryColumnName),
          outputTableName: stringValue(configuration.outputTableName),
          outputColumnName: stringValue(configuration.outputColumnName),
          algorithm: allowedAlgorithms.has(algorithm)
            ? algorithm as Configuration<'GEOMETRY_SIMPLIFY'>['algorithm'] : null,
          tolerance: typeof configuration.tolerance === 'number'
            ? configuration.tolerance : null,
          toleranceUnit: allowedUnits.has(toleranceUnit)
            ? toleranceUnit as Configuration<'GEOMETRY_SIMPLIFY'>['toleranceUnit']
            : null,
          ...parseUnaryPolicy(configuration, path, errors),
        };
      },
    )
  );
