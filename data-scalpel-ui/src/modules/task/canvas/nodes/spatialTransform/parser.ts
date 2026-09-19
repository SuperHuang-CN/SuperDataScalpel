import { stringValue } from "../../canvasValueParsers";
import { parseConfiguration, isRecord, type Configuration, parseInteger } from '../configurationParsing';

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'SPATIAL_TRANSFORM'>>(
      value,
      path,
      (configuration, errors) => {
        let targetCrs: Configuration<'SPATIAL_TRANSFORM'>['targetCrs'] = null;
        if (configuration.targetCrs !== null && configuration.targetCrs !== undefined) {
          if (!isRecord(configuration.targetCrs)) {
            errors.push(`${path}.targetCrs 必须是 CRS 对象`);
          } else {
            const authority = stringValue(configuration.targetCrs.authority).toUpperCase();
            const code = parseInteger(
              configuration.targetCrs.code,
              `${path}.targetCrs.code`,
              errors,
              4326,
            );
            if (authority !== 'EPSG') {
              errors.push(`${path}.targetCrs.authority 第一阶段仅支持 EPSG`);
            }
            if (code < 1) errors.push(`${path}.targetCrs.code 必须为正整数`);
            targetCrs = { authority: 'EPSG', code };
          }
        }
        return {
          sourceTableName: stringValue(configuration.sourceTableName),
          outputTableName: stringValue(configuration.outputTableName),
          geometryColumnName: stringValue(configuration.geometryColumnName),
          targetCrs,
        };
      },
    )
  );
