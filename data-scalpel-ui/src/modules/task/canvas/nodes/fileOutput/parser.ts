import { parseFileOutputConflictPolicy, parseFileOutputFormatOptions, stringValue, validateOptionalUuid } from "../../canvasValueParsers";
import { normalizeFileOutputPath } from "../../canvasTypes";
import { parseConfiguration, type Configuration, parseOutputWrites } from '../configurationParsing';

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'FILE_OUTPUT'>>(value, path, (configuration, errors) => {
      return {
        dataSourceId: validateOptionalUuid(
          stringValue(configuration.dataSourceId),
          `${path}.dataSourceId`,
          errors,
        ),
        writes: parseOutputWrites(configuration.writes, `${path}.writes`, errors, (write, writePath) => {
          const rawTargetPath = stringValue(write.targetPath);
          const targetPath = normalizeFileOutputPath(rawTargetPath);
          const invalidSegment = targetPath.split('/').some(
            (segment) => !segment || segment === '.' || segment === '..'
              || segment.toLowerCase() === '_temporary',
          );
          if (!targetPath || targetPath.length > 1024 || rawTargetPath.trim().startsWith('/')
            || targetPath.includes('\\') || targetPath.includes('://') || targetPath.includes('?')
            || targetPath.includes('#') || invalidSegment) {
            errors.push(`${writePath}.targetPath 必须是合法的 S3 相对路径`);
          }
          return {
            writeId: validateOptionalUuid(stringValue(write.writeId), `${writePath}.writeId`, errors),
            sourceTableName: stringValue(write.sourceTableName),
            targetPath,
            conflictPolicy: parseFileOutputConflictPolicy(write.conflictPolicy, `${writePath}.conflictPolicy`, errors),
            formatOptions: parseFileOutputFormatOptions(write.formatOptions, `${writePath}.formatOptions`, errors),
          };
        }),
      };
    })
  );
