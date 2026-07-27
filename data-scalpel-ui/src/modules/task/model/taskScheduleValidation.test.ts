import { describe, expect, it } from 'vitest';
import { isValidIanaZoneId, quartzCronValidationMessage } from './taskScheduleValidation';

describe('task schedule validation', () => {
  it('accepts six and seven field Quartz Cron expressions', () => {
    expect(quartzCronValidationMessage('0 0 2 * * ?')).toBeNull();
    expect(quartzCronValidationMessage('0 0 2 * * ? 2027')).toBeNull();
  });

  it('rejects non-Quartz field counts and unsupported characters', () => {
    expect(quartzCronValidationMessage('0 2 * * *')).toContain('6 或 7');
    expect(quartzCronValidationMessage('0 0 2 * * @')).toContain('不支持');
  });

  it('validates IANA time-zone identifiers', () => {
    expect(isValidIanaZoneId('Asia/Shanghai')).toBe(true);
    expect(isValidIanaZoneId('America/New_York')).toBe(true);
    expect(isValidIanaZoneId('Mars/Olympus')).toBe(false);
  });
});

