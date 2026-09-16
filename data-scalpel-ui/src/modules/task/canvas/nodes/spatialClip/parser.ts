import { stringValue } from "../../canvasValueParsers";
import { parseConfiguration, type Configuration } from '../configurationParsing';

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'SPATIAL_CLIP'>>(
      value,
      path,
      (configuration, errors) => {
        const geometryPolicy = configuration.geometryPolicy;
        if (geometryPolicy != null
          && geometryPolicy !== 'SOURCE_FAMILY_2D'
          && geometryPolicy !== 'LEGACY_ANY_DIMENSION') {
          errors.push(`${path}.geometryPolicy 仅支持 SOURCE_FAMILY_2D 或 LEGACY_ANY_DIMENSION`);
        }
        const maskCombination = configuration.maskCombination;
        if (maskCombination != null
          && maskCombination !== 'DISSOLVE_ALL'
          && maskCombination !== 'PAIRWISE') {
          errors.push(`${path}.maskCombination 仅支持 DISSOLVE_ALL 或 PAIRWISE`);
        }
        return {
          sourceTableName: stringValue(configuration.sourceTableName),
          maskTableName: stringValue(configuration.maskTableName),
          outputTableName: stringValue(configuration.outputTableName),
          sourceGeometryColumnName: stringValue(configuration.sourceGeometryColumnName),
          maskGeometryColumnName: stringValue(configuration.maskGeometryColumnName),
          outputColumnName: stringValue(configuration.outputColumnName),
          geometryPolicy: geometryPolicy === 'SOURCE_FAMILY_2D'
            || geometryPolicy === 'LEGACY_ANY_DIMENSION' ? geometryPolicy : null,
          maskCombination: maskCombination === 'DISSOLVE_ALL'
            || maskCombination === 'PAIRWISE' ? maskCombination : null,
        };
      },
    )
  );
