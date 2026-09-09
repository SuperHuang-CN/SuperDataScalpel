import { ArrowDownOutlined, ArrowUpOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, Input, InputNumber, Popconfirm, Select, Space, Table, Typography } from 'antd';
import { ContextHelp } from '../../../../../shared/components/ContextualFeedback';
import type { CanvasColumnSchema, TrackBufferWindowBinding } from '../../canvasTypes';
import { spatialColumnOptions } from '../spatialInspectorOptions';
import { numericBufferField } from './areaGeometry';
import { bufferWindowProblems, bufferWindowStatistics } from './bufferWindows';

export function BufferWindowEditor({ value, columns, validationAvailable, onChange }: {
  value:TrackBufferWindowBinding[]; columns:CanvasColumnSchema[]; validationAvailable:boolean;
  onChange:(value:TrackBufferWindowBinding[]) => void;
}) {
  const problems = bufferWindowProblems(value,columns,validationAvailable);
  const update = (index:number,patch:Partial<TrackBufferWindowBinding>) => onChange(value.map((v,i) => i === index ? {...v,...patch} : v));
  const move = (index:number,delta:number) => { const next=[...value]; [next[index],next[index+delta]]=[next[index+delta],next[index]]; onChange(next); };
  return <Space orientation="vertical" size={6} style={{width:'100%'}}>
    <Space size={6}><Typography.Text strong>观测窗口绑定</Typography.Text><Typography.Text type="secondary">{value.length} 项</Typography.Text>
      <ContextHelp ariaLabel="缓冲观测窗口说明" content={<>
        <p>偏移两端均包含：-3～-1 为前 3 次有效观测，0 为当前，正数为后续观测。按轨迹、时间及同时间次序排列；不跨固定周期。</p>
        <p>时间/距离 gap 和表达式拆分之前计算，共享端点保留同一缓冲值。所有绑定读取原始字段，不能引用其他绑定；不是 Arcade 语法。</p>
        <p>普通统计忽略 NULL；首末值保留 NULL；非空数在空窗口为 0，其他空窗口统计为 NULL。可用 coalesce(history_mean, radius) 显式处理不足历史。</p>
        <p>方差/标准差为样本公式，少于两个非空值时为 NULL。窗口计算不新增观测、缓存或 Action；每项最多覆盖 2001 个观测。</p>
      </>} />
      <Button size="small" icon={<PlusOutlined />} disabled={value.length >= 32} aria-label="添加缓冲窗口"
        onClick={() => onChange([...value,{name:'',sourceColumnName:'',startOffset:-3,endOffset:-1,statistic:'MEAN'}])}>添加</Button>
    </Space>
    <Table size="small" pagination={false} rowKey="index" scroll={{y:280}}
      dataSource={value.map((v,index) => ({...v,index}))}
      columns={[
        {title:'绑定 / 字段',render:(_,row) => <Space orientation="vertical" size={3} style={{width:'100%'}}>
          <Input aria-label={`缓冲窗口 ${row.index+1} 名称`} autoComplete="off" value={row.name}
            onChange={e => update(row.index,{name:e.target.value})} />
          <Select aria-label={`缓冲窗口 ${row.index+1} 字段`} style={{width:'100%'}} value={row.sourceColumnName || undefined} showSearch optionFilterProp="label"
            options={spatialColumnOptions(columns,row.sourceColumnName,numericBufferField)} onChange={sourceColumnName => update(row.index,{sourceColumnName})} />
        </Space>},
        {title:'统计',width:125,render:(_,row) => <Select aria-label={`缓冲窗口 ${row.index+1} 统计`} style={{width:'100%'}} value={row.statistic}
          options={bufferWindowStatistics} onChange={statistic => update(row.index,{statistic})} />},
        {title:'起止偏移',width:150,render:(_,row) => <Space.Compact>
          <InputNumber aria-label={`缓冲窗口 ${row.index+1} 起点`} style={{width:73}} value={row.startOffset}
            onChange={startOffset => update(row.index,{startOffset})} />
          <InputNumber aria-label={`缓冲窗口 ${row.index+1} 终点`} style={{width:73}} value={row.endOffset}
            onChange={endOffset => update(row.index,{endOffset})} />
        </Space.Compact>},
        {title:'',width:124,render:(_,row) => <Space size={0}>
          <Button size="small" type="text" aria-label={`上移缓冲窗口 ${row.index+1}`} icon={<ArrowUpOutlined />} disabled={row.index===0} onClick={() => move(row.index,-1)} />
          <Button size="small" type="text" aria-label={`下移缓冲窗口 ${row.index+1}`} icon={<ArrowDownOutlined />} disabled={row.index===value.length-1} onClick={() => move(row.index,1)} />
          <Popconfirm title={`删除窗口 ${row.name || row.index+1}？`} description="表达式中的引用不会自动修改。"
            okText="删除窗口" cancelText="取消" onConfirm={() => onChange(value.filter((_,i) => i!==row.index))}>
            <Button size="small" type="text" danger aria-label={`删除缓冲窗口 ${row.index+1}`} icon={<DeleteOutlined />} />
          </Popconfirm>
          {problems[row.index].length>0 && <ContextHelp ariaLabel={`缓冲窗口 ${row.index+1} 配置问题`} content={problems[row.index].join('；')} />}
        </Space>},
      ]} />
    {value.length>32 && <Typography.Text type="danger">窗口绑定不能超过 32 项；已有配置已保留。</Typography.Text>}
  </Space>;
}
