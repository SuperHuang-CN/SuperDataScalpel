import { dataModelFieldTypeLabels, type PlatformDataType } from './dataModel';

export const DATA_MODEL_PREVIEW_COLUMN_MIN_WIDTH = 80;
export const DATA_MODEL_PREVIEW_COLUMN_MAX_AUTO_WIDTH = 320;

const HEADER_HORIZONTAL_SPACE = 34;
const CELL_HORIZONTAL_SPACE = 24;
const DEFAULT_FONT_FAMILY = '-apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif';

export interface DataModelPreviewSizingColumn {
  code: string;
  name: string;
  fieldType: PlatformDataType;
}

export type DataModelPreviewColumnWidths = Record<string, number>;
export type DataModelPreviewTextRole = 'header' | 'type' | 'cell';
export type DataModelPreviewTextMeasurer = (text: string, role: DataModelPreviewTextRole) => number;

export interface DataModelPreviewColumnSizing {
  manualWidths: DataModelPreviewColumnWidths;
  onManualWidthChange: (code: string, width: number) => void;
  onManualWidthReset: (code: string) => void;
}

const fallbackTextWidth = (text: string, role: DataModelPreviewTextRole) => {
  const fontSize = role === 'type' ? 11 : role === 'header' ? 13 : 14;
  return Array.from(text).reduce((width, character) => {
    const codePoint = character.codePointAt(0) ?? 0;
    if (character === ' ') return width + fontSize * 0.35;
    return width + (codePoint > 0xff ? fontSize : fontSize * 0.58);
  }, 0);
};

const createBrowserTextMeasurer = (): DataModelPreviewTextMeasurer => {
  if (typeof document === 'undefined') return fallbackTextWidth;
  let context: CanvasRenderingContext2D | null = null;
  try {
    context = document.createElement('canvas').getContext('2d');
  } catch {
    return fallbackTextWidth;
  }
  if (!context) return fallbackTextWidth;
  const bodyFontFamily = document.body
    ? window.getComputedStyle(document.body).fontFamily || DEFAULT_FONT_FAMILY
    : DEFAULT_FONT_FAMILY;
  return (text, role) => {
    const fontSize = role === 'type' ? 11 : role === 'header' ? 13 : 14;
    const fontWeight = role === 'header' ? 600 : 400;
    context.font = `${fontWeight} ${fontSize}px ${bodyFontFamily}`;
    return context.measureText(text).width;
  };
};

export const dataModelPreviewCellText = (value: unknown): string => {
  if (value === null || value === undefined) return '—';
  if (typeof value === 'string') return value.trimEnd();
  if (typeof value !== 'object') return String(value);
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
};

const clampAutoWidth = (width: number) => Math.min(
  DATA_MODEL_PREVIEW_COLUMN_MAX_AUTO_WIDTH,
  Math.max(DATA_MODEL_PREVIEW_COLUMN_MIN_WIDTH, Math.ceil(width)),
);

export const normalizeDataModelPreviewManualWidth = (width: number) => Math.max(
  DATA_MODEL_PREVIEW_COLUMN_MIN_WIDTH,
  Math.round(width),
);

export const calculateDataModelPreviewColumnWidths = (
  columns: DataModelPreviewSizingColumn[],
  rows: Record<string, unknown>[],
  measureText: DataModelPreviewTextMeasurer = createBrowserTextMeasurer(),
): DataModelPreviewColumnWidths => Object.fromEntries(columns.map((column) => {
  const typeLabel = dataModelFieldTypeLabels[column.fieldType];
  let width = Math.max(
    measureText(column.name, 'header') + HEADER_HORIZONTAL_SPACE,
    measureText(typeLabel, 'type') + HEADER_HORIZONTAL_SPACE,
  );
  if (width < DATA_MODEL_PREVIEW_COLUMN_MAX_AUTO_WIDTH) {
    for (const row of rows) {
      width = Math.max(width, measureText(dataModelPreviewCellText(row[column.code]), 'cell') + CELL_HORIZONTAL_SPACE);
      if (width >= DATA_MODEL_PREVIEW_COLUMN_MAX_AUTO_WIDTH) break;
    }
  }
  return [column.code, clampAutoWidth(width)];
}));

export const effectiveDataModelPreviewColumnWidth = (
  code: string,
  autoWidths: DataModelPreviewColumnWidths,
  manualWidths: DataModelPreviewColumnWidths,
) => manualWidths[code] ?? autoWidths[code] ?? DATA_MODEL_PREVIEW_COLUMN_MIN_WIDTH;

export const totalDataModelPreviewColumnsWidth = (widths: number[]) => widths.reduce(
  (total, width) => total + width,
  0,
);
