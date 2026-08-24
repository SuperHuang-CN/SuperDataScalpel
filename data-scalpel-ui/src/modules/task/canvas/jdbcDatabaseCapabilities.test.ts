import { describe, expect, it } from 'vitest';
import { jdbcWriteModeUnavailableReason } from './jdbcDatabaseCapabilities';

describe('jdbcDatabaseCapabilities', () => {
  it.each(['ORACLE', 'SQL_SERVER', 'CLICKHOUSE', 'DAMENG'] as const)(
    'allows batch OVERWRITE but disables UPSERT for %s',
    (databaseType) => {
      expect(jdbcWriteModeUnavailableReason(databaseType, 'APPEND', 'BATCH')).toBeNull();
      expect(jdbcWriteModeUnavailableReason(databaseType, 'OVERWRITE', 'BATCH')).toBeNull();
      expect(jdbcWriteModeUnavailableReason(databaseType, 'UPSERT', 'BATCH'))
        .toContain('暂不支持 UPSERT');
    },
  );

  it('keeps existing overwrite and upsert capabilities', () => {
    expect(jdbcWriteModeUnavailableReason('POSTGRESQL', 'OVERWRITE', 'BATCH')).toBeNull();
    expect(jdbcWriteModeUnavailableReason('MYSQL', 'UPSERT', 'STREAMING')).toBeNull();
    expect(jdbcWriteModeUnavailableReason('OPENGAUSS', 'OVERWRITE', 'BATCH')).toBeNull();
    expect(jdbcWriteModeUnavailableReason('KINGBASE', 'UPSERT', 'BATCH'))
      .toContain('暂不支持 UPSERT');
  });

  it('keeps imported streaming overwrite visible as an invalid draft value', () => {
    expect(jdbcWriteModeUnavailableReason('POSTGRESQL', 'OVERWRITE', 'STREAMING'))
      .toBe('实时模式不支持');
  });

  it('keeps TDengine unavailable for ordinary JDBC output', () => {
    expect(jdbcWriteModeUnavailableReason('TDENGINE_WEBSOCKET', 'OVERWRITE', 'BATCH'))
      .toContain('不支持普通 JDBC 输出');
  });
});
