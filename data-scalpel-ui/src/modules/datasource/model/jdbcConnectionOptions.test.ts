import { describe, expect, it } from 'vitest';
import type { ConnectionOptionDefinition } from './dataSource';
import {
  defaultJdbcConnectionOptions,
  encodedJdbcConnectionOptionsLength,
  mergeJdbcConnectionOptions,
  splitJdbcConnectionOptions,
  validateJdbcConnectionOptions,
} from './jdbcConnectionOptions';

const definitions: ConnectionOptionDefinition[] = [
  {
    key: 'sslmode',
    label: 'SSL 模式',
    type: 'SELECT',
    defaultValue: 'prefer',
    choices: [{ value: 'disable', label: '禁用' }, { value: 'prefer', label: '优先' }],
  },
];

describe('JDBC connection option form model', () => {
  it('initializes defaults and splits saved predefined and custom options', () => {
    expect(defaultJdbcConnectionOptions(definitions)).toEqual({
      options: { sslmode: 'prefer' },
      customOptions: [],
    });
    expect(splitJdbcConnectionOptions({ SSLMODE: 'disable', tcpKeepAlive: 'true' }, definitions)).toEqual({
      options: { sslmode: 'disable' },
      customOptions: [{ key: 'tcpKeepAlive', value: 'true' }],
    });
  });

  it('trims and merges predefined and custom options while dropping untouched blank rows', () => {
    expect(mergeJdbcConnectionOptions(
      { sslmode: ' prefer ' },
      [{ key: ' tcpKeepAlive ', value: ' true ' }, { key: '', value: '' }],
      definitions,
    )).toEqual({ sslmode: 'prefer', tcpKeepAlive: 'true' });
  });

  it('rejects predefined duplicates, protected keys, sensitive keys and blank values', () => {
    expect(validateJdbcConnectionOptions({}, [{ key: 'SSLMODE', value: 'require' }], definitions))
      .toContain('已有专用配置项');
    expect(validateJdbcConnectionOptions({}, [{ key: 'jdbcUrl', value: 'jdbc:other' }], definitions))
      .toContain('由系统管理');
    expect(validateJdbcConnectionOptions({}, [{ key: 'apiToken', value: 'secret' }], definitions))
      .toContain('敏感参数');
    expect(validateJdbcConnectionOptions({}, [{ key: 'apiKey', value: 'secret' }], definitions))
      .toContain('敏感参数');
    expect(validateJdbcConnectionOptions({}, [{ key: 'tcpKeepAlive', value: ' ' }], definitions))
      .toContain('不能为空');
    expect(validateJdbcConnectionOptions({}, [
      { key: 'tcpKeepAlive', value: 'true' },
      { key: 'TCPKEEPALIVE', value: 'false' },
    ], definitions)).toContain('不能重复');
  });

  it('matches the backend form-encoding length and rejects values beyond the column capacity', () => {
    expect(encodedJdbcConnectionOptionsLength({ name: '数据 手术刀' })).toBe(51);
    const rows = Array.from({ length: 8 }, (_, index) => ({ key: `custom${index}`, value: 'x'.repeat(512) }));
    expect(validateJdbcConnectionOptions({}, rows, [])).toBe('JDBC 连接参数编码后不能超过 4000 个字符');
  });
});
