export type SpatialGeometryFamily = 'POINT' | 'LINE' | 'POLYGON' | 'GENERIC';
export type RendererType = 'SINGLE_SYMBOL' | 'UNIQUE_VALUE' | 'CLASS_BREAKS';
export type StyleValueType = 'STRING' | 'NUMBER' | 'BOOLEAN';
export type ClassificationMethod = 'EQUAL_INTERVAL' | 'QUANTILE' | 'MANUAL';
export type ClassBreakVisualChannel = 'COLOR' | 'SIZE' | 'WIDTH';
export type NullHandling = 'OTHER' | 'SEPARATE';
export type SpatialMarkerShape = 'CIRCLE' | 'SQUARE' | 'TRIANGLE' | 'STAR';
export type SpatialLinePattern = 'SOLID' | 'DASHED' | 'DOTTED';
export type SpatialStyleMode = 'CARTOGRAPHY' | 'UPLOADED_SLD';
export interface ScaleRange { minScaleDenominator: number | null; maxScaleDenominator: number | null; }
export interface LineCasing { color: string; opacity: number; width: number; }
export interface PolygonPattern {
  type: 'DIAGONAL' | 'CROSS' | 'DOT'; color: string; opacity: number;
  spacing: number; strokeWidth: number; dotSize: number;
}

export interface PointSymbol {
  type: 'POINT';
  shape: SpatialMarkerShape;
  size: number;
  fillColor: string;
  fillOpacity: number;
  outlineColor: string;
  outlineOpacity: number;
  outlineWidth: number;
}

export interface LineSymbol {
  type: 'LINE';
  color: string;
  opacity: number;
  width: number;
  pattern: SpatialLinePattern;
  casing: LineCasing | null;
}

export interface PolygonSymbol {
  type: 'POLYGON';
  fillColor: string;
  fillOpacity: number;
  outlineColor: string;
  outlineOpacity: number;
  outlineWidth: number;
  outlinePattern: SpatialLinePattern;
  pattern: PolygonPattern | null;
}

export type SpatialSymbol = PointSymbol | LineSymbol | PolygonSymbol;

export interface ColorRamp {
  id: string;
  reversed: boolean;
}

export interface NumericRange {
  minimum: number;
  maximum: number;
}

export interface UniqueValueRule {
  id: string;
  value: string;
  label: string;
  symbol: SpatialSymbol;
}

export interface StyleRule {
  id: string;
  label: string;
  symbol: SpatialSymbol;
}

export interface SingleSymbolRenderer {
  type: 'SINGLE_SYMBOL';
  symbol: SpatialSymbol;
}

export interface UniqueValueRenderer {
  type: 'UNIQUE_VALUE';
  fieldCode: string | null;
  valueType: StyleValueType | null;
  colorRamp: ColorRamp;
  uniqueValueRules: UniqueValueRule[];
  nullHandling: NullHandling;
  nullRule: StyleRule | null;
  elseRule: StyleRule | null;
}

export interface ClassBreaksRenderer {
  type: 'CLASS_BREAKS';
  fieldCode: string | null;
  classificationMethod: ClassificationMethod;
  visualChannel: ClassBreakVisualChannel;
  colorRamp: ColorRamp | null;
  sizeRange: NumericRange | null;
  breaks: string[];
  classBreakRules: StyleRule[];
  nullRule: StyleRule | null;
}

export type SpatialRenderer = SingleSymbolRenderer | UniqueValueRenderer | ClassBreaksRenderer;

export interface SpatialLabeling {
  enabled: boolean;
  fieldCode: string | null;
  fontSize: number;
  bold: boolean;
  color: string;
  haloColor: string;
  haloWidth: number;
  prefix: string;
  suffix: string;
  decimalPlaces: number | null;
  pointPosition: 'TOP' | 'BOTTOM' | 'LEFT' | 'RIGHT';
  pointOffset: number;
  linePlacement: 'FOLLOW_LINE' | 'HORIZONTAL';
  repeat: boolean;
  repeatDistance: number;
  polygonFit: boolean;
  allowOverlap: boolean;
  scaleRange: ScaleRange;
}

export interface SpatialStyleDocument {
  schemaVersion: 4;
  renderer: SpatialRenderer;
  labeling: SpatialLabeling;
  scaleRange: ScaleRange;
}

export interface CartographyField {
  code: string;
  name: string;
  dataType: string;
  valueType: StyleValueType | null;
  uniqueValueSupported: boolean;
  classBreaksSupported: boolean;
  labelSupported: boolean;
}

export interface FieldProfileRequest {
  fieldCode: string;
  profileType: 'UNIQUE_VALUES' | 'CLASS_BREAKS';
  limit?: number;
  classificationMethod?: 'EQUAL_INTERVAL' | 'QUANTILE';
  classCount?: number;
}

export interface FieldProfile {
  field: CartographyField;
  totalRowCount: number;
  nonNullCount: number;
  nullCount: number;
  uniqueValues: Array<{ valueType: StyleValueType; value: string; count: number }>;
  truncated: boolean;
  minimum: string | null;
  maximum: string | null;
  breaks: string[];
  actualClassCount: number;
  warnings: string[];
}

export interface SpatialStyleDraft {
  mode: SpatialStyleMode;
  document: SpatialStyleDocument | null;
  file: File | null;
}
