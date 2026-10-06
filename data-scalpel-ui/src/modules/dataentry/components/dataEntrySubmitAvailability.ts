import type { DataEntryFormDetail } from '../model/dataEntry';

export const dataEntrySubmitBlockedReason = (detail: DataEntryFormDetail): string | null => {
  if (!detail.health.canSubmit) {
    const issues = [...new Set(detail.health.issues
      .filter((issue) => issue.affectedOperations.includes('SUBMIT'))
      .map((issue) => issue.message))];
    return issues.join('；') || (detail.form.status === 'PUBLISHED'
      ? '当前填报表单不可提交数据'
      : '表单尚未发布，暂不能提交数据');
  }
  if (detail.fields.some((field) => field.fieldType === 'BINARY' || field.fieldType === 'GEOMETRY')) {
    return '当前模型包含二进制或空间字段，暂不支持填报和批量导入';
  }
  return null;
};
