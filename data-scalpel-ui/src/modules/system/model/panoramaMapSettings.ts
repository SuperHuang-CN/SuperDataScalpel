export interface MapSettings { url: string; attribution: string; maxZoom: number }
export const parsePanoramaMapSettings = (value: string): MapSettings => {
  try {
    const parsed: unknown = JSON.parse(value);
    if (typeof parsed === 'object' && parsed !== null && 'url' in parsed && typeof parsed.url === 'string'
      && 'attribution' in parsed && typeof parsed.attribution === 'string' && 'maxZoom' in parsed && typeof parsed.maxZoom === 'number') return { url: parsed.url, attribution: parsed.attribution, maxZoom: parsed.maxZoom };
  } catch { /* Invalid saved values can be repaired with this form. */ }
  return { url: '', attribution: '', maxZoom: 18 };
};
export const panoramaMapSettingsSummary = (value: string) => { const config = parsePanoramaMapSettings(value); return config.url ? `XYZ 底图已配置 · 最大缩放 ${config.maxZoom}` : '未配置底图'; };
