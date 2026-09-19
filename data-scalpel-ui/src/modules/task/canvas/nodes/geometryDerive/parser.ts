import { parseUnaryPolicy } from "../unaryGeometryPolicy";
import { stringValue, validateOptionalUuid } from "../../canvasValueParsers";
import { CANVAS_GEOMETRY_DERIVE_MAX_DERIVATIONS, type GeometryDerivation } from "../../canvasTypes";
import { parseConfiguration, isRecord, type Configuration } from '../configurationParsing';

export const geometryDeriveKinds = new Set([
  'CENTROID',
  'POINT_ON_SURFACE',
  'ENVELOPE',
  'CONVEX_HULL',
  'BOUNDARY',
]);

export const parseGeometryDerivations = (
  value: unknown,
  path: string,
  errors: string[],
): GeometryDerivation[] => {
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  if (value.length > CANVAS_GEOMETRY_DERIVE_MAX_DERIVATIONS) {
    errors.push(`${path} 不能超过 ${CANVAS_GEOMETRY_DERIVE_MAX_DERIVATIONS} 项`);
  }
  return value.slice(0, CANVAS_GEOMETRY_DERIVE_MAX_DERIVATIONS)
    .flatMap((item, index): GeometryDerivation[] => {
      const itemPath = `${path}[${index}]`;
      if (!isRecord(item)) {
        errors.push(`${itemPath} 必须是对象`);
        return [];
      }
      const kind = stringValue(item.kind);
      if (item.kind != null && !geometryDeriveKinds.has(kind)) {
        errors.push(`${itemPath}.kind 不是受支持的 Geometry 派生类型`);
      }
      return [{
        derivationId: validateOptionalUuid(
          stringValue(item.derivationId),
          `${itemPath}.derivationId`,
          errors,
        ),
        kind: geometryDeriveKinds.has(kind)
          ? kind as GeometryDerivation['kind'] : null,
        sourceColumnName: stringValue(item.sourceColumnName),
        outputColumnName: stringValue(item.outputColumnName),
        ...parseUnaryPolicy(item, itemPath, errors),
      }];
    });
};

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'GEOMETRY_DERIVE'>>(
      value,
      path,
      (configuration, errors) => ({
        sourceTableName: stringValue(configuration.sourceTableName),
        outputTableName: stringValue(configuration.outputTableName),
        derivations: parseGeometryDerivations(
          configuration.derivations,
          `${path}.derivations`,
          errors,
        ),
      }),
    )
  );
