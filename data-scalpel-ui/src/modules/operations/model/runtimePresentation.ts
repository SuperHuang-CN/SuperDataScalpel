export const durationLabel = (start: string | null, end: string | null) => {
  if (!start) return '—'; const seconds = Math.max(0, Math.floor(((end ? Date.parse(end) : Date.now()) - Date.parse(start)) / 1000));
  return seconds < 60 ? `${seconds} 秒` : seconds < 3600 ? `${Math.floor(seconds / 60)} 分 ${seconds % 60} 秒` : `${Math.floor(seconds / 3600)} 时 ${Math.floor(seconds % 3600 / 60)} 分`;
};
