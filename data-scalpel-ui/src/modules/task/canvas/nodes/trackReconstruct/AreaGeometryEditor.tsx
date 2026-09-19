import { Form, Input, InputNumber, Select, Space, Typography } from 'antd';
import { ContextHelp } from '../../../../../shared/components/ContextualFeedback';
import type { CanvasColumnSchema, SpatialDistanceMethod, TrackAreaGeometryOptions } from '../../canvasTypes';
import { spatialColumnOptions } from '../spatialInspectorOptions';
import { trackDistanceUnitOptions } from '../trackOptions';
import { areaGeometryProblems, numericBufferField } from './areaGeometry';
import { BufferWindowEditor } from './BufferWindowEditor';

export function AreaGeometryEditor({ value, columns, geometry, validationAvailable, distanceMethod, onChange }: {
  value: TrackAreaGeometryOptions;
  columns: CanvasColumnSchema[];
  geometry: CanvasColumnSchema | undefined;
  validationAvailable: boolean;
  distanceMethod?: SpatialDistanceMethod | null;
  onChange: (value: TrackAreaGeometryOptions) => void;
}) {
  const update = (patch: Partial<TrackAreaGeometryOptions>) => onChange({ ...value, ...patch });
  const problems = areaGeometryProblems(value, geometry, columns, validationAvailable, distanceMethod);
  return <Space orientation="vertical" size={8} style={{ width: '100%' }}>
    <Form.Item label={<span className="canvas-inspector-field-label">缓冲距离来源<ContextHelp ariaLabel="面轨迹缓冲说明" content={<>
      <p>先计算每个观测的缓冲面，再连接时间相邻的观测。单个面观测也保留；插值不增加观测数或统计样本。</p>
      <p>平面 XY 使用相邻凸包，圆弧每象限 16 段；测地面使用 EPSG:4326 XY 的 WGS84 缓冲与相邻连接。均不是整条轨迹的全局凸包。尚未声明与 ArcGIS 数值等价。</p>
      <p>点缓冲距离须大于零；面缓冲允许零。不接受 NULL、负数、NaN 或无穷值。地理 CRS 的平面距离只能使用来源单位（度），测地使用线性单位。距离拆分始终比较原始观测，不比较缓冲面。</p>
    </>} /></span>}>
      <Select<NonNullable<TrackAreaGeometryOptions['bufferMode']>> aria-label="面轨迹缓冲距离来源" value={value.bufferMode}
        options={[{ value: 'NONE', label: '不缓冲 · 直接连接面观测', disabled: geometry?.geometry?.kind === 'POINT' },
          { value: 'FIELD', label: '数值字段' }, { value: 'EXPRESSION', label: '受控数值表达式' }]}
        onChange={bufferMode => update({ bufferMode })} />
    </Form.Item>
    {value.bufferMode === 'FIELD' && <Form.Item label="距离字段">
      <Select aria-label="面轨迹距离字段" value={value.bufferField} showSearch optionFilterProp="label" allowClear
        options={spatialColumnOptions(columns, value.bufferField ?? '', numericBufferField)}
        onChange={bufferField => update({ bufferField: bufferField ?? null })} />
    </Form.Item>}
    {value.bufferMode === 'EXPRESSION' && <Form.Item label={<span className="canvas-inspector-field-label">距离表达式<ContextHelp ariaLabel="轨迹缓冲表达式说明"
      content="单个确定性的 Spark SQL 数值表达式，可引用当前观测字段和下方窗口绑定，例如 coalesce(history_mean, radius)。不支持 Arcade、直接书写 OVER/聚合、随机函数、展开或完整 SQL；窗口统计通过绑定配置，最终以 Compiler 校验为准。" /></span>}>
      <Input.TextArea aria-label="轨迹缓冲距离表达式" autoComplete="off" autoSize={{ minRows: 3, maxRows: 8 }}
        value={value.bufferExpression ?? ''} onChange={event => update({ bufferExpression: event.target.value })} />
    </Form.Item>}
    {value.bufferMode === 'EXPRESSION' && <BufferWindowEditor value={value.windowBindings ?? []} columns={columns}
      validationAvailable={validationAvailable} onChange={windowBindings => update({windowBindings})} />}
    {value.bufferMode != null && value.bufferMode !== 'NONE' && <Form.Item label="距离单位">
      <Select aria-label="面轨迹缓冲距离单位" value={value.bufferUnit} options={trackDistanceUnitOptions} allowClear
        onChange={bufferUnit => update({ bufferUnit: bufferUnit ?? null })} />
    </Form.Item>}
    {distanceMethod === 'GEODESIC' && <Form.Item label={<span className="canvas-inspector-field-label">边界采样最大段长<ContextHelp
      ariaLabel="测地面边界采样说明" content={<>
        <p>控制圆周、偏移带和连接边的离散粒度，不是最大位置误差，也不改变原始观测间的距离拆分。与线轨迹采样独立，切换模式保留配置。</p>
        <p>当前须能在局部参考域内验证几何，全球域/半球尚未完成；无法确认拓扑或数值阈值时明确失败，不回退质心或平面算法。单片段一百万顶点保护，较小段长会增加计算量。</p>
      </>} /></span>}>
      <Space.Compact block>
        <InputNumber aria-label="面边界采样最大段长" value={value.geodesicBoundary?.maximumSegmentLength}
          onChange={maximumSegmentLength => update({ geodesicBoundary: { maximumSegmentLength,
            maximumSegmentLengthUnit: value.geodesicBoundary?.maximumSegmentLengthUnit ?? 'METERS' } })} />
        <Select aria-label="面边界采样单位" value={value.geodesicBoundary?.maximumSegmentLengthUnit ?? null} allowClear
          options={trackDistanceUnitOptions.map(option => ({ ...option, disabled: option.value === 'SOURCE_CRS_UNIT' }))}
          onChange={unit => update({ geodesicBoundary: { maximumSegmentLength: value.geodesicBoundary?.maximumSegmentLength ?? null,
            maximumSegmentLengthUnit: unit ?? null } })} />
      </Space.Compact>
    </Form.Item>}
    {!validationAvailable && <Typography.Text type="secondary">字段等待解析，已保存值保持不变。</Typography.Text>}
    {problems.length > 0 && <Space size={4}><Typography.Text type="danger">{problems.length} 个配置问题</Typography.Text>
      <ContextHelp ariaLabel="面轨迹配置问题" content={problems.join('；')} /></Space>}
  </Space>;
}
