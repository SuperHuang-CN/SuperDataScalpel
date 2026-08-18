import { describe, expect, it } from 'vitest';
import { jdbcWriteModeUnavailableReason } from './jdbcDatabaseCapabilities';

describe('jdbcDatabaseCapabilities', () => {
  it.each(['ORACLE', 'SQL_SERVER', 'CLICKHOUSE', 'DAMENG'] as const)(
    'allows scalar APPEND but disables special write modes for %s',
    (databaseType) => {
      expect(jdbcWriteModeUnavailableReason(databaseType, 'APPEND', 'BATCH')).toBeNull();
      expect(jdbcWriteModeUnavailableReason(databaseType, 'OVERWRITE', 'BATCH'))
        .toContain('暂不支持 OVERWRITE');
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
});
