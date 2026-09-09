import type {
  CartographyField, ClassBreaksRenderer, ClassBreakVisualChannel, ClassificationMethod, ColorRamp,
  FieldProfile, LineSymbol, NumericRange, PointSymbol, PolygonSymbol, SpatialGeometryFamily,
  SpatialRenderer, SpatialStyleDocument, SpatialSymbol, StyleRule, UniqueValueRenderer, UniqueValueRule,
} from './model';
import { documentScaleError } from './scale';

export const COLOR_RAMPS: Record<string, string[]> = {
  DATASCALPEL_12: ['#4F6BFF', '#8B5CF6', '#14B8A6', '#F59E0B', '#EF4444', '#06B6D4', '#84CC16', '#EC4899', '#6366F1', '#10B981', '#F97316', '#64748B'],
  BLUE_PURPLE: ['#F3F4FF', '#DDE2FF', '#C3CCFF', '#A5B3FF', '#8599FF', '#667DFF', '#4F63E8', '#3F4FC0', '#303A8F'],
  BLUES: ['#F7FBFF', '#DEEBF7', '#C6DBEF', '#9ECAE1', '#6BAED6', '#4292C6', '#2171B5', '#08519C', '#08306B'],
  GREENS: ['#F7FCF5', '#E5F5E0', '#C7E9C0', '#A1D99B', '#74C476', '#41AB5D', '#238B45', '#006D2C', '#00441B'],
  YELLOW_RED: ['#FFFFCC', '#FFEDA0', '#FED976', '#FEB24C', '#FD8D3C', '#FC4E2A', '#E31A1C', '#BD0026', '#800026'],
};

export const POINT_SIZE_RANGE: NumericRange = { minimum: 6, maximum: 24 };
export const LINE_WIDTH_RANGE: NumericRange = { minimum: 1, maximum: 8 };

export function defaultSymbol(family: 'POINT'): PointSymbol;
export function defaultSymbol(family: 'LINE'): LineSymbol;
export function defaultSymbol(family: 'POLYGON'): PolygonSymbol;
export function defaultSymbol(family: SpatialGeometryFamily): SpatialSymbol;
export function defaultSymbol(family: SpatialGeometryFamily): SpatialSymbol {
  if (family === 'POINT') return { type: 'POINT', shape: 'CIRCLE', size: 10, fillColor: '#4F6BFF', fillOpacity: 0.85, outlineColor: '#FFFFFF', outlineOpacity: 1, outlineWidth: 1 };
  if (family === 'LINE') return { type: 'LINE', color: '#4F6BFF', opacity: 0.9, width: 2.5, pattern: 'SOLID', casing: null };
  if (family === 'POLYGON') return { type: 'POLYGON', fillColor: '#6F7DFF', fillOpacity: 0.35, outlineColor: '#3F51C6', outlineOpacity: 1, outlineWidth: 1.5, outlinePattern: 'SOLID', pattern: null };
  throw new Error('通用 Geometry 不支持可视化制图');
}

export const defaultStyleDocument = (family: SpatialGeometryFamily): SpatialStyleDocument => ({
  schemaVersion: 4,
  renderer: { type: 'SINGLE_SYMBOL', symbol: defaultSymbol(family) },
  labeling: { enabled: false, fieldCode: null, fontSize: 12, bold: false, color: '#26324A', haloColor: '#FFFFFF', haloWidth: 1.5,
    prefix: '', suffix: '', decimalPlaces: null, pointPosition: 'TOP', pointOffset: 6, linePlacement: 'FOLLOW_LINE',
    repeat: true, repeatDistance: 300, polygonFit: true, allowOverlap: false,
    scaleRange: { minScaleDenominator: null, maxScaleDenominator: null } },
  scaleRange: { minScaleDenominator: null, maxScaleDenominator: null },
});

export const recolorSymbol = (symbol: SpatialSymbol, color: string): SpatialSymbol => {
  if (symbol.type === 'LINE') return { ...symbol, color };
  return { ...symbol, fillColor: color };
};

const palette = (ramp: ColorRamp, count: number) => {
  const source = COLOR_RAMPS[ramp.id] ?? COLOR_RAMPS.DATASCALPEL_12;
  const colors = ramp.reversed ? [...source].reverse() : source;
  if (count <= 1) return [colors[Math.floor(colors.length / 2)]];
  return Array.from({ length: count }, (_, index) => colors[Math.round(index * (colors.length - 1) / (count - 1)) % colors.length]);
};

const rule = (label: string, family: SpatialGeometryFamily, color?: string): StyleRule => ({
  id: crypto.randomUUID(),
  label,
  symbol: color ? recolorSymbol(defaultSymbol(family), color) : defaultSymbol(family),
});

export const uniqueRenderer = (
  profile: FieldProfile,
  family: SpatialGeometryFamily,
  existing?: SpatialRenderer,
  ramp: ColorRamp = existing?.type === 'UNIQUE_VALUE' ? existing.colorRamp : { id: 'DATASCALPEL_12', reversed: false },
): UniqueValueRenderer => {
  const colors = palette(ramp, Math.max(1, profile.uniqueValues.length + 1));
  const previous = new Map((existing?.type === 'UNIQUE_VALUE' ? existing.uniqueValueRules : []).map((item) => [item.value, item]));
  const uniqueValueRules: UniqueValueRule[] = profile.uniqueValues.map((item, index) => previous.get(item.value) ?? ({
    id: crypto.randomUUID(), value: item.value, label: item.value,
    symbol: recolorSymbol(defaultSymbol(family), colors[index]),
  }));
  const old = existing?.type === 'UNIQUE_VALUE' ? existing : null;
  return {
    type: 'UNIQUE_VALUE', fieldCode: profile.field.code, valueType: profile.field.valueType,
    colorRamp: ramp, uniqueValueRules, nullHandling: old?.nullHandling ?? 'OTHER',
    nullRule: old?.nullRule ?? null,
    elseRule: old?.elseRule ?? rule('其他', family, colors[colors.length - 1]),
  };
};

const defaultChannel = (family: SpatialGeometryFamily): ClassBreakVisualChannel => {
  if (family === 'GENERIC') throw new Error('通用 Geometry 不支持可视化制图');
  return 'COLOR';
};
const defaultRange = (channel: ClassBreakVisualChannel): NumericRange | null => channel === 'SIZE'
  ? { ...POINT_SIZE_RANGE }
  : channel === 'WIDTH' ? { ...LINE_WIDTH_RANGE } : null;

const colorOf = (symbol: SpatialSymbol): string => symbol.type === 'LINE' ? symbol.color : symbol.fillColor;

const withFixedColor = (symbol: SpatialSymbol, color: string): SpatialSymbol => recolorSymbol(symbol, color);

const withMagnitude = (symbol: SpatialSymbol, channel: ClassBreakVisualChannel, value: number): SpatialSymbol => {
  if (channel === 'SIZE' && symbol.type === 'POINT') return { ...symbol, size: value };
  if (channel === 'WIDTH' && symbol.type === 'LINE') return { ...symbol, width: value };
  return symbol;
};

const interpolate = (range: NumericRange, index: number, count: number) => count <= 1
  ? (range.minimum + range.maximum) / 2
  : range.minimum + (range.maximum - range.minimum) * index / (count - 1);

const materializeClassBreakSymbols = (
  rules: StyleRule[], family: SpatialGeometryFamily, channel: ClassBreakVisualChannel,
  ramp: ColorRamp | null, range: NumericRange | null,
): StyleRule[] => {
  if (channel === 'COLOR') {
    const colors = palette(ramp ?? { id: 'BLUE_PURPLE', reversed: false }, Math.max(1, rules.length));
    return rules.map((item, index) => {
      let symbol = recolorSymbol(item.symbol, colors[index]);
      if (symbol.type === 'POINT') symbol = { ...symbol, size: 10 };
      if (symbol.type === 'LINE') symbol = { ...symbol, width: 2.5 };
      return { ...item, symbol };
    });
  }
  const effectiveRange = range ?? defaultRange(channel)!;
  const fixedColor = rules[0] ? colorOf(rules[0].symbol) : colorOf(defaultSymbol(family));
  return rules.map((item, index) => ({
    ...item,
    symbol: withMagnitude(withFixedColor(item.symbol, fixedColor), channel,
      interpolate(effectiveRange, index, rules.length)),
  }));
};

export const classBreakRenderer = (
  profile: FieldProfile,
  family: SpatialGeometryFamily,
  method: Exclude<ClassificationMethod, 'MANUAL'>,
  existing?: SpatialRenderer,
  ramp?: ColorRamp,
  visualChannel?: ClassBreakVisualChannel,
): ClassBreaksRenderer => {
  const old = existing?.type === 'CLASS_BREAKS' ? existing : null;
  const channel = visualChannel ?? old?.visualChannel ?? defaultChannel(family);
  const effectiveRamp = channel === 'COLOR' ? (ramp ?? old?.colorRamp ?? { id: 'BLUE_PURPLE', reversed: false }) : null;
  const range = channel === 'COLOR' ? null : old?.sizeRange ?? defaultRange(channel);
  const labels = classBreakLabels(profile.breaks);
  const baseRules = labels.map((label, index) => {
    const previous = old?.classBreakRules[index];
    return previous ? { ...previous, label } : rule(label, family);
  });
  return {
    type: 'CLASS_BREAKS', fieldCode: profile.field.code, classificationMethod: method,
    visualChannel: channel, colorRamp: effectiveRamp, sizeRange: range,
    breaks: profile.breaks, classBreakRules: materializeClassBreakSymbols(baseRules, family, channel, effectiveRamp, range),
    nullRule: old?.nullRule ?? null,
  };
};

export const emptyUniqueRenderer = (): UniqueValueRenderer => ({
  type: 'UNIQUE_VALUE', fieldCode: null, valueType: null,
  colorRamp: { id: 'DATASCALPEL_12', reversed: false }, uniqueValueRules: [],
  nullHandling: 'OTHER', nullRule: null, elseRule: null,
});

export const emptyClassBreaksRenderer = (family: SpatialGeometryFamily): ClassBreaksRenderer => ({
  type: 'CLASS_BREAKS', fieldCode: null, classificationMethod: 'EQUAL_INTERVAL',
  visualChannel: defaultChannel(family), colorRamp: { id: 'BLUE_PURPLE', reversed: false }, sizeRange: null,
  breaks: [], classBreakRules: [], nullRule: null,
});

export const changeClassBreakVisualChannel = (
  renderer: ClassBreaksRenderer, family: SpatialGeometryFamily, channel: ClassBreakVisualChannel,
): ClassBreaksRenderer => {
  const colorRamp = channel === 'COLOR' ? (renderer.colorRamp ?? { id: 'BLUE_PURPLE', reversed: false }) : null;
  const sizeRange = channel === 'COLOR' ? null : renderer.sizeRange ?? defaultRange(channel);
  return {
    ...renderer, visualChannel: channel, colorRamp, sizeRange,
    classBreakRules: materializeClassBreakSymbols(renderer.classBreakRules, family, channel, colorRamp, sizeRange),
  };
};

export const changeClassBreakRange = (
  renderer: ClassBreaksRenderer, family: SpatialGeometryFamily, range: NumericRange,
): ClassBreaksRenderer => {
  if ((family !== 'POINT' || renderer.visualChannel !== 'SIZE') && (family !== 'LINE' || renderer.visualChannel !== 'WIDTH')) return renderer;
  return {
    ...renderer, sizeRange: range,
    classBreakRules: renderer.classBreakRules.map((item, index) => ({
      ...item, symbol: withMagnitude(item.symbol, renderer.visualChannel, interpolate(range, index, renderer.classBreakRules.length)),
    })),
  };
};

export const classBreakLabels = (breaks: string[]): string[] => breaks.length === 0
  ? ['全部数值']
  : Array.from({ length: breaks.length + 1 }, (_, index) => {
    if (index === 0) return `≤ ${breaks[0]}`;
    if (index === breaks.length) return `> ${breaks[breaks.length - 1]}`;
    return `${breaks[index - 1]} – ${breaks[index]}`;
  });

export const applyRamp = (renderer: UniqueValueRenderer | ClassBreaksRenderer, ramp: ColorRamp): typeof renderer => {
  if (renderer.type === 'UNIQUE_VALUE') return {
    ...renderer, colorRamp: ramp,
    uniqueValueRules: renderer.uniqueValueRules.map((item, index) => ({
      ...item, symbol: recolorSymbol(item.symbol, palette(ramp, Math.max(1, renderer.uniqueValueRules.length))[index]),
    })),
  } as typeof renderer;
  return {
    ...renderer, colorRamp: ramp,
    classBreakRules: renderer.classBreakRules.map((item, index) => ({
      ...item, symbol: recolorSymbol(item.symbol, palette(ramp, Math.max(1, renderer.classBreakRules.length))[index]),
    })),
  } as typeof renderer;
};

export const fieldOptions = (fields: CartographyField[], capability: keyof Pick<CartographyField, 'uniqueValueSupported' | 'classBreaksSupported' | 'labelSupported'>) => fields
  .filter((field) => field[capability])
  .map((field) => ({ value: field.code, label: `${field.name} (${field.code})` }));

export interface SpatialStylePreviewReadiness { ready: boolean; reason: string | null; }

const finiteDecimal = (value: string) => {
  const normalized = value.trim();
  if (!/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)$/.test(normalized)) return null;
  const parsed = Number(normalized);
  return Number.isFinite(parsed) ? parsed : null;
};

/** Lightweight client-side gate that avoids sending structurally incomplete drafts to GeoServer. */
export const spatialStylePreviewReadiness = (document: SpatialStyleDocument | null): SpatialStylePreviewReadiness => {
  if (!document) return { ready: false, reason: '样式文档尚未就绪' };
  const scaleError = documentScaleError(document);
  if (scaleError) return { ready: false, reason: scaleError };
  const renderer = document.renderer;
  if (renderer.type === 'UNIQUE_VALUE') {
    if (!renderer.fieldCode) return { ready: false, reason: '请先选择分类字段' };
    if (!renderer.valueType || renderer.uniqueValueRules.length === 0) return { ready: false, reason: '正在等待唯一值分类规则' };
    if (renderer.nullHandling === 'SEPARATE' && !renderer.nullRule) return { ready: false, reason: '空值规则尚未配置完整' };
  }
  if (renderer.type === 'CLASS_BREAKS') {
    if (!renderer.fieldCode) return { ready: false, reason: '请先选择数值字段' };
    if (renderer.classBreakRules.length !== renderer.breaks.length + 1) return { ready: false, reason: '正在等待数值分级规则' };
    if (renderer.visualChannel !== 'COLOR' && (!renderer.sizeRange || renderer.sizeRange.minimum >= renderer.sizeRange.maximum)) {
      return { ready: false, reason: '分级尺寸最小值必须小于最大值' };
    }
    const values = renderer.breaks.map(finiteDecimal);
    if (values.some((value) => value == null)) return { ready: false, reason: '请完成分级断点输入' };
    const numericValues = values as number[];
    if (numericValues.some((value, index) => index > 0 && value <= numericValues[index - 1])) return { ready: false, reason: '分级断点必须严格递增' };
  }
  if (document.labeling.enabled && !document.labeling.fieldCode) return { ready: false, reason: '请选择标注字段' };
  return { ready: true, reason: null };
};
