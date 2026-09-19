import type { ConnectionOptionDefinition } from './dataSource';

export interface JdbcConnectionOptionFormRow {
  key?: string;
  value?: string;
}

export interface JdbcConnectionOptionsFormValue {
  options: Record<string, string>;
  customOptions: JdbcConnectionOptionFormRow[];
}

export const MAX_JDBC_CONNECTION_OPTIONS = 20;
export const MAX_JDBC_CONNECTION_OPTION_KEY_LENGTH = 64;
export const MAX_JDBC_CONNECTION_OPTION_VALUE_LENGTH = 512;
export const MAX_ENCODED_JDBC_CONNECTION_OPTIONS_LENGTH = 4000;
export const JDBC_CONNECTION_OPTION_KEY_PATTERN = /^[A-Za-z][A-Za-z0-9._-]{0,63}$/;

const reservedKeys = new Set([
  'user',
  'username',
  'password',
  'host',
  'port',
  'database',
  'databasename',
  'schema',
  'driver',
  'driverclassname',
  'url',
  'jdbcurl',
  'connecttimeout',
  'sockettimeout',
  'applicationname',
  'currentschema',
  'useunicode',
  'characterencoding',
  'logintimeout',
  'connection_timeout',
  'socket_timeout',
  'oracle.net.connect_timeout',
  'oracle.jdbc.readtimeout',
]);

const normalizeKey = (key: string): string => key.toLocaleLowerCase('en-US');

const sensitiveKey = (normalizedKey: string): boolean => (
  normalizedKey.includes('password')
  || normalizedKey.includes('passwd')
  || normalizedKey.includes('secret')
  || normalizedKey.includes('token')
  || normalizedKey.includes('apikey')
  || normalizedKey.includes('api_key')
  || normalizedKey === 'accesskey'
  || normalizedKey === 'access_key'
  || normalizedKey.endsWith('.accesskey')
);

const definitionByKey = (definitions: ConnectionOptionDefinition[]): Map<string, ConnectionOptionDefinition> => (
  new Map(definitions.map((definition) => [normalizeKey(definition.key), definition]))
);

export const defaultJdbcConnectionOptions = (
  definitions: ConnectionOptionDefinition[],
): JdbcConnectionOptionsFormValue => ({
  options: Object.fromEntries(
    definitions
      .filter((definition) => definition.defaultValue !== null)
      .map((definition) => [definition.key, definition.defaultValue as string]),
  ),
  customOptions: [],
});

export const splitJdbcConnectionOptions = (
  savedOptions: Record<string, string>,
  definitions: ConnectionOptionDefinition[],
): JdbcConnectionOptionsFormValue => {
  const definitionsByKey = definitionByKey(definitions);
  const options: Record<string, string> = {};
  const customOptions: JdbcConnectionOptionFormRow[] = [];
  const assignedDefinitions = new Set<string>();

  Object.entries(savedOptions).forEach(([key, value]) => {
    const normalizedKey = normalizeKey(key);
    const definition = definitionsByKey.get(normalizedKey);
    if (definition && !assignedDefinitions.has(normalizedKey)) {
      options[definition.key] = value;
      assignedDefinitions.add(normalizedKey);
      return;
    }
    customOptions.push({ key, value });
  });

  return { options, customOptions };
};

export const jdbcCustomConnectionOptionKeyError = (
  rawKey: string | undefined,
  definitions: ConnectionOptionDefinition[],
): string | undefined => {
  const key = rawKey?.trim();
  if (!key) return '请输入参数名';
  if (!JDBC_CONNECTION_OPTION_KEY_PATTERN.test(key)) {
    return '参数名以字母开头，只能包含字母、数字、点、下划线和短横线';
  }
  const normalizedKey = normalizeKey(key);
  if (definitionByKey(definitions).has(normalizedKey)) {
    return `${key} 已有专用配置项，请使用上方字段`;
  }
  if (reservedKeys.has(normalizedKey)) return `${key} 由系统管理，不能自定义`;
  if (sensitiveKey(normalizedKey)) return `${key} 属于敏感参数，不能保存在普通连接参数中`;
  return undefined;
};

const javaFormEncodeLength = (value: string): number => {
  const bytes = new TextEncoder().encode(value);
  let length = 0;
  bytes.forEach((byte) => {
    const unescaped = (byte >= 0x41 && byte <= 0x5a)
      || (byte >= 0x61 && byte <= 0x7a)
      || (byte >= 0x30 && byte <= 0x39)
      || byte === 0x2e
      || byte === 0x2d
      || byte === 0x2a
      || byte === 0x5f;
    length += unescaped || byte === 0x20 ? 1 : 3;
  });
  return length;
};

export const encodedJdbcConnectionOptionsLength = (options: Record<string, string>): number => {
  const entries = Object.entries(options);
  if (entries.length === 0) return 0;
  return entries.reduce(
    (length, [key, value]) => length + javaFormEncodeLength(key) + 1 + javaFormEncodeLength(value),
    entries.length - 1,
  );
};

export const validateJdbcConnectionOptions = (
  predefinedOptions: Record<string, string> | undefined,
  customOptions: JdbcConnectionOptionFormRow[] | undefined,
  definitions: ConnectionOptionDefinition[],
): string | undefined => {
  const merged: Record<string, string> = {};
  const seenKeys = new Set<string>();

  for (const definition of definitions) {
    const value = predefinedOptions?.[definition.key]?.trim();
    if (!value) continue;
    if (value.length > MAX_JDBC_CONNECTION_OPTION_VALUE_LENGTH) {
      return `${definition.label}不能超过 512 个字符`;
    }
    merged[definition.key] = value;
    seenKeys.add(normalizeKey(definition.key));
  }

  for (const row of customOptions ?? []) {
    const key = row.key?.trim();
    const value = row.value?.trim();
    if (!key && !value) continue;
    const keyError = jdbcCustomConnectionOptionKeyError(key, definitions);
    if (keyError) return keyError;
    if (!value) return `${key} 的参数值不能为空`;
    if (value.length > MAX_JDBC_CONNECTION_OPTION_VALUE_LENGTH) return `${key} 的参数值不能超过 512 个字符`;
    const normalizedKey = normalizeKey(key as string);
    if (seenKeys.has(normalizedKey)) return `参数名不能重复：${key}`;
    seenKeys.add(normalizedKey);
    merged[key as string] = value;
  }

  if (Object.keys(merged).length > MAX_JDBC_CONNECTION_OPTIONS) {
    return `JDBC 连接参数不能超过 ${MAX_JDBC_CONNECTION_OPTIONS} 个`;
  }
  if (encodedJdbcConnectionOptionsLength(merged) > MAX_ENCODED_JDBC_CONNECTION_OPTIONS_LENGTH) {
    return 'JDBC 连接参数编码后不能超过 4000 个字符';
  }
  return undefined;
};

export const mergeJdbcConnectionOptions = (
  predefinedOptions: Record<string, string> | undefined,
  customOptions: JdbcConnectionOptionFormRow[] | undefined,
  definitions: ConnectionOptionDefinition[],
): Record<string, string> => {
  const validationError = validateJdbcConnectionOptions(predefinedOptions, customOptions, definitions);
  if (validationError) throw new Error(validationError);

  const merged: Record<string, string> = {};
  definitions.forEach((definition) => {
    const value = predefinedOptions?.[definition.key]?.trim();
    if (value) merged[definition.key] = value;
  });
  (customOptions ?? []).forEach((row) => {
    const key = row.key?.trim();
    const value = row.value?.trim();
    if (key && value) merged[key] = value;
  });
  return merged;
};
