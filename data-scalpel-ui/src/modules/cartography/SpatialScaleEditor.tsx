import { Button, InputNumber, Select, Typography } from 'antd';
import type { ScaleRange, SpatialStyleDocument } from './model';
import { documentScaleError } from './scale';

const ScaleRangeEditor = ({ title, value, disabled, currentScale, onChange }: {
  title: string; value: ScaleRange; disabled: boolean; currentScale?: number;
  onChange(value: ScaleRange): void;
}) => <section className="cartography-scale-section">
  <Typography.Text strong>{title}</Typography.Text>
  {(['minScaleDenominator', 'maxScaleDenominator'] as const).map(key => <div className="cartography-scale-row" key={key}>
    <label className="cartography-field"><span>{key === 'minScaleDenominator' ? '最小分母（含）' : '最大分母（不含）'}</span>
      <InputNumber prefix="1:" aria-label={`${title}${key === 'minScaleDenominator' ? '最小分母' : '最大分母'}`}
        value={value[key]} min={0.000001} placeholder="不限" disabled={disabled} onChange={next => onChange({ ...value, [key]: next })} />
    </label>
    <Select aria-label={`${title}常用${key}`} placeholder="常用" allowClear disabled={disabled} value={undefined}
      options={[500, 1000, 5000, 10000, 25000, 50000, 100000, 500000, 1000000].map(number => ({ value: number, label: `1:${number.toLocaleString()}` }))}
      onChange={(next: number | undefined) => onChange({ ...value, [key]: next ?? null })} />
    <Button size="small" disabled={disabled || !currentScale} onClick={() => onChange({ ...value, [key]: currentScale ?? null })}>当前视图</Button>
  </div>)}
</section>;

export const SpatialScaleEditor = ({ document, disabled, currentScale, onChange }: {
  document: SpatialStyleDocument; disabled: boolean; currentScale?: number; onChange(document: SpatialStyleDocument): void;
}) => <div className="cartography-scale-editor">
  <Typography.Text type="secondary">{currentScale ? `当前请求比例尺 1:${Math.round(currentScale).toLocaleString()}` : '地图就绪后可使用当前视图比例尺'}</Typography.Text>
  <ScaleRangeEditor title="整体可见范围" value={document.scaleRange} disabled={disabled} currentScale={currentScale}
    onChange={scaleRange => onChange({ ...document, scaleRange })} />
  <ScaleRangeEditor title="标注可见范围" value={document.labeling.scaleRange} disabled={disabled || !document.labeling.enabled} currentScale={currentScale}
    onChange={scaleRange => onChange({ ...document, labeling: { ...document.labeling, scaleRange } })} />
  {!document.labeling.enabled && <Typography.Text type="secondary">启用标注后可设置标注范围。</Typography.Text>}
  {documentScaleError(document) && <Typography.Text type="danger">{documentScaleError(document)}</Typography.Text>}
</div>;
