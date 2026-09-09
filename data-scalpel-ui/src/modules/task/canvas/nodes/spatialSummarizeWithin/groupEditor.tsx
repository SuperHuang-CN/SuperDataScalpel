import { Checkbox, Form, Input, Select, Space, Typography } from 'antd';
import { ContextHelp } from '../../../../../shared/components/ContextualFeedback';
import type { CanvasColumnSchema, SpatialGroupSummary, SpatialWithinGroupResult } from '../../canvasTypes';
import { spatialColumnOptions } from '../spatialInspectorOptions';

export const WithinLinkedGroupEditor = ({ group, result, areaColumns, summaryColumns, mainTableName,
  inputTableNames, onGroupChange, onResultChange, generatedAreaKey = false }: {
  generatedAreaKey?: boolean;
  group: SpatialGroupSummary;
  result: SpatialWithinGroupResult;
  areaColumns: CanvasColumnSchema[];
  summaryColumns: CanvasColumnSchema[];
  mainTableName: string;
  inputTableNames: string[];
  onGroupChange: (value: SpatialGroupSummary) => void;
  onResultChange: (value: SpatialWithinGroupResult) => void;
}) => {
  const output = (field: keyof SpatialWithinGroupResult, label: string) => <Form.Item label={label} style={{ marginBottom: 8 }}>
    <Input autoComplete="off" aria-label={label} value={result[field] ?? ''}
      status={!result[field]?.trim() ? 'error' : undefined}
      onChange={(event) => onResultChange({ ...result, [field]: event.target.value })} />
  </Form.Item>;
  return <Space orientation="vertical" size={8} style={{ width: '100%' }}>
    <div className="canvas-spatial-pair-grid">
      <Form.Item label={<Space size={2}>区域唯一键<ContextHelp ariaLabel="区域唯一键说明"
        content={generatedAreaKey ? '由格网形状、原点、CRS、大小及索引生成稳定 ID。原区域表的键配置保留但不在此模式使用。'
          : '选择来源中的非空、唯一标量字段。两张结果表保存同一个键；重复或空键在真实执行时报错。不会用区域名称或临时行号自动代替。'} /></Space>} style={{ marginBottom: 8 }}>
        {generatedAreaKey ? <Typography.Text>自动生成的稳定格网 ID</Typography.Text> : <Select aria-label="区域唯一键" showSearch optionFilterProp="label" value={result.areaKeyColumnName}
          status={!result.areaKeyColumnName || !areaColumns.some(c => c.name === result.areaKeyColumnName) ? 'error' : undefined}
          options={spatialColumnOptions(areaColumns, result.areaKeyColumnName, c => c.fieldType !== 'GEOMETRY')}
          onChange={areaKeyColumnName => onResultChange({ ...result, areaKeyColumnName })} />}
      </Form.Item>
      {output('areaKeyOutputColumnName', '关联键输出字段')}
      <Form.Item label="分组字段" style={{ marginBottom: 8 }}>
        <Select aria-label="关联结果分组字段" showSearch optionFilterProp="label" value={group.groupByColumnName}
          status={!group.groupByColumnName || !summaryColumns.some(c => c.name === group.groupByColumnName) ? 'error' : undefined}
          options={spatialColumnOptions(summaryColumns, group.groupByColumnName, c => c.fieldType !== 'GEOMETRY')}
          onChange={groupByColumnName => onGroupChange({ ...group, groupByColumnName })} />
      </Form.Item>
      {output('groupValueColumnName', '组值输出字段')}
    </div>
    <Form.Item label="关联组表名" style={{ marginBottom: 8, width: '100%' }}>
      <Input aria-label="关联组表名" autoComplete="off" value={result.outputTableName}
        status={!result.outputTableName.trim() || result.outputTableName === mainTableName
          || inputTableNames.includes(result.outputTableName) ? 'error' : undefined}
        onChange={event => onResultChange({ ...result, outputTableName: event.target.value })} />
    </Form.Item>
    <Space size={4}><Checkbox checked={group.includeMinorityMajority}
      onChange={event => onGroupChange({ ...group, includeMinorityMajority: event.target.checked })}>主表输出少数/多数组值</Checkbox>
      <ContextHelp ariaLabel="少数多数及形状占比说明" content={
        '点按区域内点数、线按相交长度、面按相交面积比较；不是按要素行数。只在正形状量的组中选少数/多数，并列时按组值升序选一个（NULL 最后，平台规则）。'
      } /></Space>
    {group.includeMinorityMajority && <div className="canvas-spatial-pair-grid">
      {output('minorityValueColumnName', '少数组值字段')}{output('majorityValueColumnName', '多数组值字段')}
    </div>}
    <Checkbox checked={group.includeGroupPercentage}
      onChange={event => onGroupChange({ ...group, includeGroupPercentage: event.target.checked })}>输出形状百分比</Checkbox>
    {group.includeGroupPercentage && <>
      <Form.Item label="组表百分比字段" style={{ marginBottom: 8, width: '100%' }}>
        <Input aria-label="组表百分比字段" autoComplete="off" value={group.groupPercentageColumnName ?? ''}
          status={!group.groupPercentageColumnName?.trim() ? 'error' : undefined}
          onChange={event => onGroupChange({ ...group, groupPercentageColumnName: event.target.value })} />
      </Form.Item>
      {group.includeMinorityMajority && <div className="canvas-spatial-pair-grid">
        {output('minorityPercentageColumnName', '主表少数组百分比字段')}
        {output('majorityPercentageColumnName', '主表多数组百分比字段')}
      </div>}
    </>}
  </Space>;
};
