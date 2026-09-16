import type { SpatialAreaUnit, SpatialDistanceUnit } from '../canvasTypes';


export const spatialDistanceUnitLabels: Record<SpatialDistanceUnit, string> = {
  SOURCE_CRS_UNIT: '来源 CRS 单位', METERS: '米', KILOMETERS: '千米',
  FEET: '国际英尺', YARDS: '国际码', MILES: '国际英里', NAUTICAL_MILES: '国际海里',
  FEET_US: '美国测量英尺', YARDS_US: '美国测量码', MILES_US: '美国测量英里', NAUTICAL_MILES_US: '美制海里（1954 年前）',
};
export const spatialAreaUnitLabels: Record<SpatialAreaUnit, string> = {
  SQUARE_METERS: '平方米', SQUARE_KILOMETERS: '平方千米', HECTARES: '公顷',
  ACRES: '国际英亩', SQUARE_FEET: '平方国际英尺', SQUARE_YARDS: '平方国际码', SQUARE_MILES: '平方国际英里',
  ACRES_US: '美国测量英亩', SQUARE_FEET_US: '平方美国测量英尺', SQUARE_YARDS_US: '平方美国测量码', SQUARE_MILES_US: '平方美国测量英里',
};
export const spatialDistanceUnitOptions = Object.entries(spatialDistanceUnitLabels).map(([value, label]) => ({ value: value as SpatialDistanceUnit, label }));
export const spatialAreaUnitOptions = Object.entries(spatialAreaUnitLabels).map(([value, label]) => ({ value: value as SpatialAreaUnit, label }));
export const spatialDistanceUnits: ReadonlySet<string> = new Set(Object.keys(spatialDistanceUnitLabels));
export const spatialAreaUnits: ReadonlySet<string> = new Set(Object.keys(spatialAreaUnitLabels));
export const spatialUnitHelp = '国际英尺为 0.3048 米，美国测量英尺为 1200/3937 米。国际海里为 1852 米，旧美制海里为 1853.248 米。面积按所选长度制平方换算；来源 CRS 单位取坐标轴，不代表米。切换单位不会自动换算已经填写的数值，请同时核对数值。';
