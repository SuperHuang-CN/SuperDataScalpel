const QUARTZ_FIELD_PATTERN = /^[0-9A-Za-z*?,/\-#LW]+$/;

export const quartzCronValidationMessage = (value: string): string | null => {
  const fields = value.trim().split(/\s+/).filter(Boolean);
  if (fields.length !== 6 && fields.length !== 7) {
    return 'Quartz Cron 必须包含 6 或 7 个字段';
  }
  if (fields.some((field) => !QUARTZ_FIELD_PATTERN.test(field))) {
    return 'Quartz Cron 包含不支持的字符';
  }
  return null;
};

export const isValidIanaZoneId = (value: string): boolean => {
  if (!value.trim()) return false;
  try {
    new Intl.DateTimeFormat('zh-CN', { timeZone: value.trim() }).format();
    return true;
  } catch {
    return false;
  }
};

