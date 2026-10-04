export const number = (value: number) => value.toLocaleString('zh-CN');
export const dateTime = (value: string | number | null | undefined) => value
 ? new Date(value).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }) : '—';

