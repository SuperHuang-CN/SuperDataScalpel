import { Tooltip } from 'antd';

const fallbackLayerColors = [
  '#5E6B89',
  '#6B4BC8',
  '#315FC7',
  '#167D9B',
  '#27805D',
  '#A96018',
  '#B04F70',
] as const;

const colorForCode = (code: string) => {
  const hash = [...code].reduce((value, character) => (
    ((value * 31) + (character.codePointAt(0) ?? 0)) >>> 0
  ), 0);
  return fallbackLayerColors[hash % fallbackLayerColors.length];
};

const normalizeColor = (color: string | null | undefined, code: string) => (
  color?.trim().match(/^#[0-9A-Fa-f]{6}$/)?.[0].toUpperCase() ?? colorForCode(code)
);

const mixWithWhite = (color: string, whiteRatio: number) => {
  const channels = [1, 3, 5].map((start) => Number.parseInt(color.slice(start, start + 2), 16));
  const mixed = channels.map((channel) => Math.round(channel * (1 - whiteRatio) + 255 * whiteRatio));
  return `rgb(${mixed.join(' ')})`;
};

export const ModelWarehouseLayerIcon = ({
  code,
  color,
}: {
  code: string;
  color?: string | null;
}) => {
  const normalizedCode = code.trim().toUpperCase();
  const glyph = normalizedCode.slice(0, 3) || '—';
  const normalizedColor = normalizeColor(color, normalizedCode);

  return (
    <Tooltip title={`分层编码：${normalizedCode || '未配置'}`}>
      <span
        className="model-warehouse-layer-icon"
        style={{
          color: normalizedColor,
          backgroundColor: mixWithWhite(normalizedColor, 0.9),
          borderColor: mixWithWhite(normalizedColor, 0.72),
        }}
        aria-hidden
      >
        {glyph}
      </span>
    </Tooltip>
  );
};
