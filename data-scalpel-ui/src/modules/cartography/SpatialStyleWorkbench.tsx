import { EditOutlined, FileTextOutlined, UploadOutlined } from '@ant-design/icons';
import {
  Button, ColorPicker, Divider, Input, InputNumber, Modal, Popover, Radio, Segmented, Select, Slider,
  Space, Spin, Switch, Tabs, Tag, Typography, Upload,
} from 'antd';
import { useState } from 'react';
import type {
  CartographyField, ClassBreakVisualChannel, ClassificationMethod, FieldProfileRequest, LineSymbol,
  PointSymbol, PolygonSymbol, SpatialGeometryFamily, SpatialLabeling, SpatialLinePattern,
  SpatialMarkerShape, SpatialRenderer, SpatialStyleDocument, SpatialStyleMode, SpatialSymbol,
  StyleRule, UniqueValueRule,
} from './model';
import {
  applyRamp, changeClassBreakRange, changeClassBreakVisualChannel, classBreakLabels, classBreakRenderer,
  COLOR_RAMPS, defaultStyleDocument, defaultSymbol, emptyClassBreaksRenderer, emptyUniqueRenderer,
  fieldOptions, uniqueRenderer,
} from './style';
import { SpatialStyleLegend, SpatialSymbolSwatch } from './SpatialStyleLegend';
import { SpatialSldSourcePanel } from './SpatialSldSourcePanel';
import { SpatialScaleEditor } from './SpatialScaleEditor';
import './cartography.css';

export interface SpatialStyleWorkbenchProps {
  mode: SpatialStyleMode;
  geometryFamily: SpatialGeometryFamily;
  fields: CartographyField[];
  value: SpatialStyleDocument | null;
  defaultDocument: SpatialStyleDocument | null;
  file: File | null;
  fileName: string | null;
  fileSize: number | null;
  uploadedSldText: string | null;
  styleVersion: number;
  appliedStyleVersion: number | null;
  syncStatus: 'NOT_APPLIED' | 'OUT_OF_SYNC' | 'SYNCING' | 'IN_SYNC' | 'SYNC_FAILED';
  syncError: string | null;
  deployed: boolean;
  dirty: boolean;
  editable: boolean;
  applicable: boolean;
  saving: boolean;
  applying: boolean;
  currentScale?: number;
  onModeChange(mode: SpatialStyleMode): void;
  onChange(value: SpatialStyleDocument): void;
  onFileChange(file: File): void;
  onProfileField(request: FieldProfileRequest): Promise<import('./model').FieldProfile>;
  onQuerySld(document: SpatialStyleDocument, signal: AbortSignal): Promise<string>;
  onRestoreDefault(): void;
  onSave(): void;
  onSaveAndApply(): void;
  onApply(): void;
}

const statusLabels = { NOT_APPLIED: '未应用', OUT_OF_SYNC: '待应用', SYNCING: '应用中', IN_SYNC: '已同步', SYNC_FAILED: '同步失败' } as const;
const statusColors = { NOT_APPLIED: 'default', OUT_OF_SYNC: 'warning', SYNCING: 'processing', IN_SYNC: 'success', SYNC_FAILED: 'error' } as const;
const markerLabels: Record<SpatialMarkerShape, string> = { CIRCLE: '圆形', SQUARE: '方形', TRIANGLE: '三角形', STAR: '星形' };
const lineLabels: Record<SpatialLinePattern, string> = { SOLID: '实线', DASHED: '虚线', DOTTED: '点线' };
const rampLabels: Record<string, string> = { DATASCALPEL_12: '分类色', BLUE_PURPLE: '蓝紫', BLUES: '蓝色', GREENS: '绿色', YELLOW_RED: '黄红' };

const OpacityField = ({ label, value, disabled, onChange }: { label: string; value: number; disabled: boolean; onChange(value: number): void }) => (
  <label className="cartography-field cartography-field-wide"><span>{label}</span><div className="cartography-slider">
    <Slider value={value * 100} min={0} max={100} disabled={disabled} onChange={(next) => onChange(next / 100)} />
    <span>{Math.round(value * 100)}%</span>
  </div></label>
);

const ColorField = ({ label, value, disabled, onChange }: { label: string; value: string; disabled: boolean; onChange(value: string): void }) => (
  <label className="cartography-field"><span>{label}</span><ColorPicker value={value} disabled={disabled} showText
    onChangeComplete={(next) => onChange(next.toHexString().toUpperCase())} /></label>
);

export const PointSymbolEditor = ({ value, disabled, onChange }: { value: PointSymbol; disabled: boolean; onChange(value: PointSymbol): void }) => (
  <div className="cartography-symbol-editor">
    <label className="cartography-field"><span>点形状</span><Select value={value.shape} disabled={disabled}
      options={Object.entries(markerLabels).map(([option, label]) => ({ value: option, label }))}
      onChange={(shape) => onChange({ ...value, shape })} /></label>
    <label className="cartography-field"><span>点大小</span><InputNumber value={value.size} min={2} max={64} disabled={disabled}
      onChange={(size) => onChange({ ...value, size: size ?? 10 })} /></label>
    <ColorField label="填充颜色" value={value.fillColor} disabled={disabled} onChange={(fillColor) => onChange({ ...value, fillColor })} />
    <ColorField label="边框颜色" value={value.outlineColor} disabled={disabled} onChange={(outlineColor) => onChange({ ...value, outlineColor })} />
    <OpacityField label="填充透明度" value={value.fillOpacity} disabled={disabled} onChange={(fillOpacity) => onChange({ ...value, fillOpacity })} />
    <OpacityField label="边框透明度" value={value.outlineOpacity} disabled={disabled} onChange={(outlineOpacity) => onChange({ ...value, outlineOpacity })} />
    <label className="cartography-field"><span>边框宽度</span><InputNumber value={value.outlineWidth} min={0} max={20} step={0.5} disabled={disabled}
      onChange={(outlineWidth) => onChange({ ...value, outlineWidth: outlineWidth ?? 0 })} /></label>
  </div>
);

export const LineSymbolEditor = ({ value, disabled, onChange }: { value: LineSymbol; disabled: boolean; onChange(value: LineSymbol): void }) => (
  <div className="cartography-symbol-editor">
    <ColorField label="线颜色" value={value.color} disabled={disabled} onChange={(color) => onChange({ ...value, color })} />
    <label className="cartography-field"><span>线宽</span><InputNumber value={value.width} min={0.1} max={20} step={0.5} disabled={disabled}
      onChange={(width) => onChange({ ...value, width: width ?? 2.5 })} /></label>
    <OpacityField label="线透明度" value={value.opacity} disabled={disabled} onChange={(opacity) => onChange({ ...value, opacity })} />
    <label className="cartography-field cartography-field-wide"><span>线型</span><Radio.Group value={value.pattern} disabled={disabled}
      optionType="button" buttonStyle="solid" options={Object.entries(lineLabels).map(([option, label]) => ({ value: option, label }))}
      onChange={(event) => onChange({ ...value, pattern: event.target.value })} /></label>
    <label className="cartography-field-wide"><Switch checked={value.casing != null} disabled={disabled}
      onChange={enabled => onChange({ ...value, casing: enabled ? { color: '#FFFFFF', opacity: 1, width: 1 } : null })} /> 外描边</label>
    {value.casing && <>
      <ColorField label="外描边颜色" value={value.casing.color} disabled={disabled} onChange={color => onChange({ ...value, casing: { ...value.casing!, color } })} />
      <label className="cartography-field"><span>单侧扩展宽度</span><InputNumber min={0.1} max={10} step={0.1} disabled={disabled} value={value.casing.width}
        onChange={width => onChange({ ...value, casing: { ...value.casing!, width: width ?? 1 } })} /></label>
      <OpacityField label="外描边透明度" value={value.casing.opacity} disabled={disabled} onChange={opacity => onChange({ ...value, casing: { ...value.casing!, opacity } })} />
    </>}
  </div>
);

export const PolygonSymbolEditor = ({ value, disabled, onChange }: { value: PolygonSymbol; disabled: boolean; onChange(value: PolygonSymbol): void }) => (
  <div className="cartography-symbol-editor">
    <ColorField label="填充颜色" value={value.fillColor} disabled={disabled} onChange={(fillColor) => onChange({ ...value, fillColor })} />
    <ColorField label="边界颜色" value={value.outlineColor} disabled={disabled} onChange={(outlineColor) => onChange({ ...value, outlineColor })} />
    <OpacityField label="填充透明度" value={value.fillOpacity} disabled={disabled} onChange={(fillOpacity) => onChange({ ...value, fillOpacity })} />
    <OpacityField label="边界透明度" value={value.outlineOpacity} disabled={disabled} onChange={(outlineOpacity) => onChange({ ...value, outlineOpacity })} />
    <label className="cartography-field"><span>边界宽度</span><InputNumber value={value.outlineWidth} min={0} max={20} step={0.5} disabled={disabled}
      onChange={(outlineWidth) => onChange({ ...value, outlineWidth: outlineWidth ?? 0 })} /></label>
    <label className="cartography-field"><span>边界线型</span><Select value={value.outlinePattern} disabled={disabled}
      options={Object.entries(lineLabels).map(([option, label]) => ({ value: option, label }))}
      onChange={(outlinePattern) => onChange({ ...value, outlinePattern })} /></label>
    <label className="cartography-field-wide"><Switch checked={value.pattern != null} disabled={disabled}
      onChange={enabled => onChange({ ...value, pattern: enabled ? { type: 'DIAGONAL', color: '#3F51C6', opacity: 0.8, spacing: 12, strokeWidth: 1, dotSize: 2 } : null })} /> 图案叠加</label>
    {value.pattern && <>
      <label className="cartography-field"><span>图案</span><Select value={value.pattern.type} disabled={disabled}
        options={[{ value: 'DIAGONAL', label: '斜线' }, { value: 'CROSS', label: '交叉线' }, { value: 'DOT', label: '点状' }]}
        onChange={type => onChange({ ...value, pattern: { ...value.pattern!, type } })} /></label>
      <ColorField label="图案颜色" value={value.pattern.color} disabled={disabled} onChange={color => onChange({ ...value, pattern: { ...value.pattern!, color } })} />
      <label className="cartography-field"><span>平铺单元</span><InputNumber min={6} max={48} disabled={disabled} value={value.pattern.spacing}
        onChange={spacing => onChange({ ...value, pattern: { ...value.pattern!, spacing: spacing ?? 12 } })} /></label>
      {value.pattern.type === 'DOT' ? <label className="cartography-field"><span>图案点径</span><InputNumber min={1} max={Math.min(8, value.pattern.spacing)} disabled={disabled} value={value.pattern.dotSize}
        onChange={dotSize => onChange({ ...value, pattern: { ...value.pattern!, dotSize: dotSize ?? 2 } })} /></label>
        : <label className="cartography-field"><span>图案线宽</span><InputNumber min={0.5} max={4} step={0.5} disabled={disabled} value={value.pattern.strokeWidth}
          onChange={strokeWidth => onChange({ ...value, pattern: { ...value.pattern!, strokeWidth: strokeWidth ?? 1 } })} /></label>}
      <OpacityField label="图案透明度" value={value.pattern.opacity} disabled={disabled} onChange={opacity => onChange({ ...value, pattern: { ...value.pattern!, opacity } })} />
    </>}
  </div>
);

const SymbolEditor = ({ value, disabled, onChange }: { value: SpatialSymbol; disabled: boolean; onChange(value: SpatialSymbol): void }) => {
  if (value.type === 'POINT') return <PointSymbolEditor value={value} disabled={disabled} onChange={onChange} />;
  if (value.type === 'LINE') return <LineSymbolEditor value={value} disabled={disabled} onChange={onChange} />;
  return <PolygonSymbolEditor value={value} disabled={disabled} onChange={onChange} />;
};

const RuleRow = ({ rule, disabled, value, onChange }: {
  rule: StyleRule | UniqueValueRule; disabled: boolean; value?: string; onChange(rule: StyleRule | UniqueValueRule): void;
}) => <div className="cartography-rule-row">
  <Popover trigger="click" placement="left" content={<SymbolEditor value={rule.symbol} disabled={disabled} onChange={(symbol) => onChange({ ...rule, symbol })} />}>
    <button type="button" className="cartography-swatch-button" disabled={disabled} aria-label="修改符号"><SpatialSymbolSwatch symbol={rule.symbol} /></button>
  </Popover>
  <div className="cartography-rule-copy">{value != null && <Typography.Text ellipsis type="secondary">{value}</Typography.Text>}<Input
    value={rule.label} disabled={disabled} maxLength={100} onChange={(event) => onChange({ ...rule, label: event.target.value })} />
  </div>
</div>;

const ClassBreakChannelControl = ({ renderer, family, disabled, onChange }: {
  renderer: Extract<SpatialRenderer, { type: 'CLASS_BREAKS' }>; family: SpatialGeometryFamily; disabled: boolean;
  onChange(renderer: Extract<SpatialRenderer, { type: 'CLASS_BREAKS' }>): void;
}) => {
  const options = family === 'POINT'
    ? [{ value: 'COLOR', label: '分级颜色' }, { value: 'SIZE', label: '分级大小' }]
    : family === 'LINE'
      ? [{ value: 'COLOR', label: '分级颜色' }, { value: 'WIDTH', label: '分级宽度' }]
      : [{ value: 'COLOR', label: '分级填色' }];
  return <>
    <label className="cartography-field cartography-field-wide"><span>表达方式</span>
      {family === 'POLYGON' ? <Tag color="blue">分级填色</Tag> : <Segmented block value={renderer.visualChannel} disabled={disabled}
        options={options} onChange={(value) => onChange(changeClassBreakVisualChannel(renderer, family, value as ClassBreakVisualChannel))} />}
    </label>
    {renderer.visualChannel !== 'COLOR' && renderer.sizeRange && <div className="cartography-range-row">
      <label className="cartography-field"><span>{renderer.visualChannel === 'SIZE' ? '最小点大小' : '最小线宽'}</span><InputNumber
        value={renderer.sizeRange.minimum} min={renderer.visualChannel === 'SIZE' ? 2 : 0.1} max={renderer.visualChannel === 'SIZE' ? 64 : 20}
        step={renderer.visualChannel === 'SIZE' ? 1 : 0.1} disabled={disabled}
        onChange={(minimum) => onChange(changeClassBreakRange(renderer, family, { ...renderer.sizeRange!, minimum: minimum ?? renderer.sizeRange!.minimum }))} /></label>
      <label className="cartography-field"><span>{renderer.visualChannel === 'SIZE' ? '最大点大小' : '最大线宽'}</span><InputNumber
        value={renderer.sizeRange.maximum} min={renderer.visualChannel === 'SIZE' ? 2 : 0.1} max={renderer.visualChannel === 'SIZE' ? 64 : 20}
        step={renderer.visualChannel === 'SIZE' ? 1 : 0.1} disabled={disabled}
        onChange={(maximum) => onChange(changeClassBreakRange(renderer, family, { ...renderer.sizeRange!, maximum: maximum ?? renderer.sizeRange!.maximum }))} /></label>
    </div>}
  </>;
};

const RendererEditor = ({ document, family, fields, disabled, onChange, onProfile }: {
  document: SpatialStyleDocument; family: SpatialGeometryFamily; fields: CartographyField[]; disabled: boolean;
  onChange(document: SpatialStyleDocument): void; onProfile(request: FieldProfileRequest): Promise<import('./model').FieldProfile>;
}) => {
  const renderer = document.renderer;
  const [uniqueLimit, setUniqueLimit] = useState(12);
  const [classCount, setClassCount] = useState(5);
  const [profiling, setProfiling] = useState(false);
  const [profileError, setProfileError] = useState<string>();
  const [warnings, setWarnings] = useState<string[]>([]);
  const [frequencies, setFrequencies] = useState<Record<string, number>>({});
  const updateRenderer = (next: SpatialRenderer) => onChange({ ...document, renderer: next });
  const selectType = (type: SpatialRenderer['type']) => {
    if (type === 'SINGLE_SYMBOL') updateRenderer(defaultStyleDocument(family).renderer);
    else if (type === 'UNIQUE_VALUE') updateRenderer(emptyUniqueRenderer());
    else updateRenderer(emptyClassBreaksRenderer(family));
  };
  const profile = async (request: FieldProfileRequest) => {
    setProfiling(true); setProfileError(undefined);
    try {
      const result = await onProfile(request);
      setWarnings(result.warnings);
      if (request.profileType === 'UNIQUE_VALUES') {
        setFrequencies(Object.fromEntries(result.uniqueValues.map((item) => [item.value, item.count])));
        updateRenderer(uniqueRenderer(result, family, renderer));
      } else {
        updateRenderer(classBreakRenderer(result, family, request.classificationMethod ?? 'EQUAL_INTERVAL', renderer,
          renderer.type === 'CLASS_BREAKS' ? renderer.colorRamp ?? undefined : undefined,
          renderer.type === 'CLASS_BREAKS' ? renderer.visualChannel : undefined));
      }
    } catch (error) {
      setProfileError(error instanceof Error ? error.message : '字段统计失败');
    } finally { setProfiling(false); }
  };
  const profileReplacingRules = (request: FieldProfileRequest, hasRules: boolean) => {
    if (!hasRules) { void profile(request); return; }
    Modal.confirm({
      title: '重新统计并更新规则？',
      content: '相同分类值会保留已有名称和符号；不再出现的值或手工断点将被替换。',
      okText: '继续统计', cancelText: '取消', onOk: () => profile(request),
    });
  };
  const colorControls = renderer.type !== 'SINGLE_SYMBOL'
    && (renderer.type === 'UNIQUE_VALUE' || renderer.visualChannel === 'COLOR') && <div className="cartography-compact-row">
      <Select value={renderer.colorRamp?.id ?? 'BLUE_PURPLE'} disabled={disabled}
        options={Object.keys(COLOR_RAMPS).map((id) => ({ value: id, label: rampLabels[id] ?? id }))}
        onChange={(id) => updateRenderer(applyRamp(renderer, { id, reversed: renderer.colorRamp?.reversed ?? false }))} />
      <label><Switch checked={renderer.colorRamp?.reversed ?? false} disabled={disabled}
        onChange={(reversed) => updateRenderer(applyRamp(renderer, { id: renderer.colorRamp?.id ?? 'BLUE_PURPLE', reversed }))} /> 反转色带</label>
    </div>;
  return <div className="cartography-renderer-editor">
    <label className="cartography-field cartography-field-wide"><span>符号化方式</span><Select value={renderer.type} disabled={disabled}
      options={[{ value: 'SINGLE_SYMBOL', label: '单一符号' }, { value: 'UNIQUE_VALUE', label: '唯一值' }, { value: 'CLASS_BREAKS', label: '数值分级' }]}
      onChange={selectType} /></label>
    {renderer.type === 'SINGLE_SYMBOL' && <SymbolEditor value={renderer.symbol} disabled={disabled} onChange={(symbol) => updateRenderer({ ...renderer, symbol })} />}
    {renderer.type === 'UNIQUE_VALUE' && <>
      <div className="cartography-compact-row"><Select showSearch value={renderer.fieldCode} placeholder="选择分类字段" disabled={disabled}
        options={fieldOptions(fields, 'uniqueValueSupported')} onChange={(fieldCode) => profileReplacingRules({ fieldCode, profileType: 'UNIQUE_VALUES', limit: uniqueLimit }, renderer.uniqueValueRules.length > 0)} />
        <Select value={uniqueLimit} disabled={disabled} options={[12, 20, 30, 50].map((value) => ({ value, label: `${value} 类` }))} onChange={setUniqueLimit} />
        <Button disabled={disabled || !renderer.fieldCode} loading={profiling} onClick={() => renderer.fieldCode && profileReplacingRules({ fieldCode: renderer.fieldCode, profileType: 'UNIQUE_VALUES', limit: uniqueLimit }, renderer.uniqueValueRules.length > 0)}>重新统计</Button></div>
      {colorControls}
      <div className="cartography-rule-list">{renderer.uniqueValueRules.map((item, index) => <RuleRow key={item.id} rule={item} value={frequencies[item.value] == null ? item.value : `${item.value} · ${frequencies[item.value].toLocaleString()} 条`} disabled={disabled}
        onChange={(next) => updateRenderer({ ...renderer, uniqueValueRules: renderer.uniqueValueRules.map((rule, ruleIndex) => ruleIndex === index ? next as UniqueValueRule : rule) })} />)}</div>
      <div className="cartography-toggle-list"><label><Switch checked={renderer.elseRule != null} disabled={disabled} onChange={(checked) => updateRenderer({ ...renderer, elseRule: checked ? { id: crypto.randomUUID(), label: '其他', symbol: defaultSymbol(family) } : null })} /> 显示其他值</label>
        <label><Switch checked={renderer.nullHandling === 'SEPARATE'} disabled={disabled} onChange={(checked) => updateRenderer({ ...renderer, nullHandling: checked ? 'SEPARATE' : 'OTHER', nullRule: checked ? { id: crypto.randomUUID(), label: '空值', symbol: defaultSymbol(family) } : null })} /> 空值单独显示</label></div>
      {renderer.elseRule && <RuleRow rule={renderer.elseRule} value="未列出的值" disabled={disabled} onChange={(elseRule) => updateRenderer({ ...renderer, elseRule })} />}
      {renderer.nullRule && <RuleRow rule={renderer.nullRule} value="NULL" disabled={disabled} onChange={(nullRule) => updateRenderer({ ...renderer, nullRule })} />}
    </>}
    {renderer.type === 'CLASS_BREAKS' && <>
      <ClassBreakChannelControl renderer={renderer} family={family} disabled={disabled} onChange={updateRenderer} />
      <div className="cartography-compact-row"><Select showSearch value={renderer.fieldCode} placeholder="选择数值字段" disabled={disabled}
        options={fieldOptions(fields, 'classBreaksSupported')} onChange={(fieldCode) => profileReplacingRules({ fieldCode, profileType: 'CLASS_BREAKS', classificationMethod: renderer.classificationMethod === 'QUANTILE' ? 'QUANTILE' : 'EQUAL_INTERVAL', classCount }, renderer.classBreakRules.length > 0)} />
        <Select value={renderer.classificationMethod} disabled={disabled} options={[{ value: 'EQUAL_INTERVAL', label: '等距' }, { value: 'QUANTILE', label: '分位数' }, { value: 'MANUAL', label: '手工' }]}
          onChange={(method: ClassificationMethod) => method === 'MANUAL' ? updateRenderer({ ...renderer, classificationMethod: method }) : renderer.fieldCode && profileReplacingRules({ fieldCode: renderer.fieldCode, profileType: 'CLASS_BREAKS', classificationMethod: method, classCount }, renderer.classBreakRules.length > 0)} />
        <InputNumber value={classCount} min={3} max={9} disabled={disabled} onChange={(value) => setClassCount(value ?? 5)} />
        <Button disabled={disabled || !renderer.fieldCode || renderer.classificationMethod === 'MANUAL'} loading={profiling} onClick={() => renderer.fieldCode && profileReplacingRules({ fieldCode: renderer.fieldCode, profileType: 'CLASS_BREAKS', classificationMethod: renderer.classificationMethod === 'QUANTILE' ? 'QUANTILE' : 'EQUAL_INTERVAL', classCount }, renderer.classBreakRules.length > 0)}>重新统计</Button></div>
      {colorControls}
      <div className="cartography-breaks">{renderer.breaks.map((item, index) => <label key={index}><span>断点 {index + 1}</span><Input value={item} disabled={disabled} onChange={(event) => {
        const nextBreaks = renderer.breaks.map((value, valueIndex) => valueIndex === index ? event.target.value : value);
        const previousLabels = classBreakLabels(renderer.breaks); const nextLabels = classBreakLabels(nextBreaks);
        updateRenderer({ ...renderer, classificationMethod: 'MANUAL', breaks: nextBreaks, classBreakRules: renderer.classBreakRules.map((rule, ruleIndex) => rule.label === previousLabels[ruleIndex] ? { ...rule, label: nextLabels[ruleIndex] } : rule) });
      }} /></label>)}</div>
      <div className="cartography-rule-list">{renderer.classBreakRules.map((item, index) => <RuleRow key={item.id} rule={item} disabled={disabled}
        onChange={(next) => updateRenderer({ ...renderer, classBreakRules: renderer.classBreakRules.map((rule, ruleIndex) => ruleIndex === index ? next : rule) })} />)}</div>
      <label><Switch checked={renderer.nullRule != null} disabled={disabled} onChange={(checked) => updateRenderer({ ...renderer, nullRule: checked ? { id: crypto.randomUUID(), label: '空值', symbol: defaultSymbol(family) } : null })} /> 空值单独显示</label>
      {renderer.nullRule && <RuleRow rule={renderer.nullRule} value="NULL" disabled={disabled} onChange={(nullRule) => updateRenderer({ ...renderer, nullRule })} />}
    </>}
    {profiling && <div className="cartography-inline-state"><Spin size="small" /> 正在分析字段…</div>}
    {profileError && <Typography.Text type="danger">{profileError}</Typography.Text>}
    {warnings.map((warning) => <Typography.Text key={warning} type="warning">{warning}</Typography.Text>)}
  </div>;
};

const LabelEditor = ({ value, fields, family, disabled, onChange }: { value: SpatialLabeling; fields: CartographyField[]; family: SpatialGeometryFamily; disabled: boolean; onChange(value: SpatialLabeling): void }) => {
  const update = <K extends keyof SpatialLabeling>(key: K, next: SpatialLabeling[K]) => onChange({ ...value, [key]: next });
  return <div className="cartography-label-editor">
    <label className="cartography-toggle"><Switch checked={value.enabled} disabled={disabled} onChange={(enabled) => update('enabled', enabled)} /> 启用文字标注</label>
    {value.enabled && <div className="cartography-style-grid">
      <label className="cartography-field cartography-field-wide"><span>标注字段</span><Select showSearch value={value.fieldCode} disabled={disabled} options={fieldOptions(fields, 'labelSupported')}
        onChange={fieldCode => onChange({ ...value, fieldCode, decimalPlaces: fields.find(field => field.code === fieldCode)?.valueType === 'NUMBER' ? value.decimalPlaces : null })} /></label>
      <label className="cartography-field"><span>字号</span><InputNumber value={value.fontSize} min={8} max={48} disabled={disabled} onChange={(next) => update('fontSize', next ?? 12)} /></label>
      <label className="cartography-toggle"><Switch checked={value.bold} disabled={disabled} onChange={(bold) => update('bold', bold)} /> 粗体</label>
      <ColorField label="文字颜色" value={value.color} disabled={disabled} onChange={(color) => update('color', color)} />
      <ColorField label="描边颜色" value={value.haloColor} disabled={disabled} onChange={(haloColor) => update('haloColor', haloColor)} />
      <label className="cartography-field"><span>描边宽度</span><InputNumber value={value.haloWidth} min={0} max={5} step={0.5} disabled={disabled} onChange={(next) => update('haloWidth', next ?? 0)} /></label>
      <label className="cartography-field"><span>前缀</span><Input value={value.prefix} maxLength={50} disabled={disabled} autoComplete="off" onChange={event => update('prefix', event.target.value)} /></label>
      <label className="cartography-field"><span>后缀</span><Input value={value.suffix} maxLength={50} disabled={disabled} autoComplete="off" onChange={event => update('suffix', event.target.value)} /></label>
      {fields.find(field => field.code === value.fieldCode)?.valueType === 'NUMBER' && <label className="cartography-field"><span>小数位（空为原值）</span><InputNumber value={value.decimalPlaces} min={0} max={6} precision={0} disabled={disabled} onChange={next => update('decimalPlaces', next)} /></label>}
      {family === 'POINT' && <>
        <label className="cartography-field"><span>标注位置</span><Select value={value.pointPosition} disabled={disabled}
          options={[{ value: 'TOP', label: '上方' }, { value: 'BOTTOM', label: '下方' }, { value: 'LEFT', label: '左侧' }, { value: 'RIGHT', label: '右侧' }]}
          onChange={next => update('pointPosition', next)} /></label>
        <label className="cartography-field"><span>偏移距离</span><InputNumber value={value.pointOffset} min={0} max={64} disabled={disabled} onChange={next => update('pointOffset', next ?? 6)} /></label>
      </>}
      {family === 'LINE' && <>
        <label className="cartography-field"><span>标注放置</span><Select value={value.linePlacement} disabled={disabled}
          options={[{ value: 'FOLLOW_LINE', label: '沿线' }, { value: 'HORIZONTAL', label: '水平' }]} onChange={next => update('linePlacement', next)} /></label>
        <label className="cartography-toggle"><Switch checked={value.repeat} disabled={disabled} onChange={next => update('repeat', next)} /> 重复标注</label>
        {value.repeat && <label className="cartography-field"><span>重复间距</span><InputNumber value={value.repeatDistance} min={50} max={2000} disabled={disabled} onChange={next => update('repeatDistance', next ?? 300)} /></label>}
      </>}
      {family === 'POLYGON' && <label className="cartography-field-wide"><Switch checked={value.polygonFit} disabled={disabled} onChange={next => update('polygonFit', next)} /> 仅在面内可放下时显示</label>}
      <label className="cartography-field-wide"><Switch checked={value.allowOverlap} disabled={disabled} onChange={next => update('allowOverlap', next)} /> 允许标注重叠</label>
    </div>}
  </div>;
};

export const SpatialStyleWorkbench = (props: SpatialStyleWorkbenchProps) => {
  const document = props.value ?? props.defaultDocument;
  return <div className="cartography-workbench">
    <div className="cartography-heading"><div><strong>在线配图</strong><Typography.Text type="secondary">Renderer 编译为 GeoServer SLD</Typography.Text></div><Tag color={statusColors[props.syncStatus]}>{statusLabels[props.syncStatus]}</Tag></div>
    <div className="cartography-meta"><span>草稿 v{props.styleVersion}</span><span>已应用 {props.appliedStyleVersion == null ? '—' : `v${props.appliedStyleVersion}`}</span></div>
    {props.syncError && <Typography.Text type="danger">{props.syncError}</Typography.Text>}
    <Segmented<SpatialStyleMode> block value={props.mode} disabled={!props.editable} options={[{ value: 'CARTOGRAPHY', label: '在线制图', icon: <EditOutlined /> }, { value: 'UPLOADED_SLD', label: '上传 SLD', icon: <FileTextOutlined /> }]} onChange={props.onModeChange} />
    <div className="cartography-scroll">
      {props.mode === 'CARTOGRAPHY' ? document && props.geometryFamily !== 'GENERIC' ? <>
        <Tabs size="small" items={[
          { key: 'renderer', label: '符号化', children: <RendererEditor document={document} family={props.geometryFamily} fields={props.fields} disabled={!props.editable} onChange={props.onChange} onProfile={props.onProfileField} /> },
          { key: 'label', label: '标注', children: <LabelEditor value={document.labeling} fields={props.fields} family={props.geometryFamily} disabled={!props.editable} onChange={(labeling) => props.onChange({ ...document, labeling })} /> },
          { key: 'scale', label: '比例尺', children: <SpatialScaleEditor document={document} disabled={!props.editable} currentScale={props.currentScale} onChange={props.onChange} /> },
        ]} />
        <Divider plain>图例</Divider><SpatialStyleLegend document={document} />
      </> : <Typography.Text type="secondary">通用 Geometry 请使用上传 SLD。</Typography.Text> : <div className="cartography-upload">
        <Upload.Dragger accept=".sld,.xml" maxCount={1} showUploadList={false} disabled={!props.editable} beforeUpload={(file) => { props.onFileChange(file); return Upload.LIST_IGNORE; }}>
          <p className="ant-upload-drag-icon"><UploadOutlined /></p><p className="ant-upload-text">选择或拖入 SLD 1.0 文件</p><p className="ant-upload-hint">UTF-8，.sld/.xml，最大 512KB</p>
        </Upload.Dragger>
        {(props.file || props.fileName) && <Typography.Text>{props.file?.name ?? props.fileName} · {Math.ceil((props.file?.size ?? props.fileSize ?? 0) / 1024)}KB</Typography.Text>}
      </div>}
      <SpatialSldSourcePanel mode={props.mode} geometryFamily={props.geometryFamily} document={document}
        file={props.file} uploadedSldText={props.uploadedSldText} onQuerySld={props.onQuerySld} />
    </div>
    <div className="cartography-actions"><Space wrap>
      {props.editable && props.mode === 'CARTOGRAPHY' && props.geometryFamily !== 'GENERIC' && <Button onClick={props.onRestoreDefault}>恢复默认</Button>}
      {props.editable && <Button disabled={!props.dirty || props.mode === 'UPLOADED_SLD' && !props.file && !props.fileName} loading={props.saving} onClick={props.onSave}>保存草稿</Button>}
      {props.editable && props.applicable && props.deployed && <Button type="primary" disabled={props.mode === 'UPLOADED_SLD' && props.dirty && !props.file && !props.fileName} loading={props.saving || props.applying} onClick={props.dirty ? props.onSaveAndApply : props.onApply}>{props.dirty ? '保存并应用' : props.syncStatus === 'IN_SYNC' ? '重新应用' : '应用样式'}</Button>}
    </Space>{!props.deployed && <Typography.Text type="secondary">服务启用时自动应用当前样式。</Typography.Text>}</div>
  </div>;
};
