import { type HttpApiRuntimeParameter } from "../canvasTypes";
import { isRecord, stringValue, isSensitiveRuntimeParameterName } from './scalars';

export const parseRuntimeParameters = (
  value: unknown,
  path: string,
  errors: string[],
): HttpApiRuntimeParameter[] => {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  return value.flatMap((item, index): HttpApiRuntimeParameter[] => {
    if (!isRecord(item)) {
      errors.push(`${path}[${index}] 必须是对象`);
      return [];
    }
    const name = stringValue(item.name);
    const parameterValue = stringValue(item.value);
    if (name && !/^[A-Za-z][A-Za-z0-9_.-]{0,127}$/.test(name)) {
      errors.push(`${path}[${index}].name 无效`);
    }
    if (isSensitiveRuntimeParameterName(name)) {
      errors.push(`${path}[${index}].name 不允许用于密码、Token、密钥或签名`);
    }
    return [{ name, value: parameterValue }];
  });
};
