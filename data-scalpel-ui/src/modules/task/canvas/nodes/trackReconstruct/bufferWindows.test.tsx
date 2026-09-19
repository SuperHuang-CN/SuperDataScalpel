import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { Modal } from 'antd';
import { createRef } from 'react';
import { afterEach, expect, it, vi } from 'vitest';
import { CANVAS_SCHEMA_MINOR_VERSION, CanvasNodeType, type CanvasColumnSchema, type TrackBufferWindowBinding } from '../../canvasTypes';
import { parseCanvasDefinition } from '../../canvasDefinitionIO';
import { createTrackReconstructConfiguration } from '../nodeDefaults';
import type { CanvasNodeInspectorHandle } from '../nodeSpec';
import { createAreaGeometryOptions } from './areaGeometry';
import { bufferWindowProblems, parseBufferWindows } from './bufferWindows';
import { BufferWindowEditor } from './BufferWindowEditor';
import { trackReconstructCanvasView } from './canvasView';
import Inspector from './inspector';

afterEach(() => { Modal.destroyAll();cleanup(); });
const binding:TrackBufferWindowBinding={name:'history',sourceColumnName:'radius',startOffset:-3,endOffset:-1,statistic:'MEAN'};
const id='11111111-1111-4111-8111-111111111111';const layout={x:0,y:0,width:352,height:216};
const configuration=()=>{
  const c=createTrackReconstructConfiguration();
  c.sourceTableName='events';c.outputTableName='tracks';
  c.reconstruction!.areaGeometry={...createAreaGeometryOptions(false),bufferMode:'EXPRESSION',bufferExpression:'coalesce(history,radius)',windowBindings:[{...binding}]};
  return c;
};
const definition=(c:unknown,minor:number)=>({schemaVersion:4,schemaMinorVersion:minor,nodes:[{id,type:CanvasNodeType.TrackReconstruct,name:'重建',layout,configuration:c}],edges:[]});

it('gates only nonempty bindings at 43 including disabled geometry and field branches',()=>{
  const c=configuration();
  c.reconstruction!.areaGeometry!.enabled=false;c.reconstruction!.areaGeometry!.bufferMode='FIELD';
  expect(parseCanvasDefinition(definition(c,42)).success).toBe(false);
  const parsed=parseCanvasDefinition(definition(c,CANVAS_SCHEMA_MINOR_VERSION));expect(parsed.success).toBe(true);
  if(parsed.success)expect(parsed.definition.nodes[0].configuration).toEqual(c);
  for(const windowBindings of [null,[]]){
    c.reconstruction!.areaGeometry!.windowBindings=windowBindings;
    const old=parseCanvasDefinition(definition(c,42));expect(old.success).toBe(true);
    if(old.success)expect(old.definition.nodes[0].configuration).toMatchObject({reconstruction:{areaGeometry:{windowBindings:[]}}});
  }
});

it('rejects malformed JSON but preserves business-invalid window drafts',()=>{
  for(const raw of [true,[null],[{...binding,startOffset:1.2}],[{...binding,name:3}],[{...binding,statistic:'AUTO'}]]){
    const errors:string[]=[];parseBufferWindows(raw,'windowBindings',errors);expect(errors.length).toBeGreaterThan(0);
  }
  const errors:string[]=[];
  expect(parseBufferWindows([{...binding,startOffset:5,endOffset:-1,statistic:'ANY'}],'windowBindings',errors)).toHaveLength(1);
  expect(errors).toEqual([]);
  const columns=[{name:'radius',fieldType:'DOUBLE'}] as CanvasColumnSchema[];
  const bad=[{...binding,name:'radius'},{...binding,startOffset:5,endOffset:-1},{...binding,name:'HISTORY',sourceColumnName:'history'}];
  const problems=bufferWindowProblems(bad,columns,true);
  expect(problems[0]).toContain('绑定名重复');expect(problems[1].length).toBeGreaterThan(0);
  expect(problems[2]).toContain('来源字段已失效；不能引用其他绑定');
  expect(bufferWindowProblems([binding],[],false)).toEqual([[]]);
});

it('keeps modal history drafts isolated on cancel',async()=>{
  const c=configuration(),ref=createRef<CanvasNodeInspectorHandle>(),apply=vi.fn();
  render(<Inspector node={{id,type:CanvasNodeType.TrackReconstruct,name:'重建',layout,configuration:c}} executionMode="BATCH"
    validation={undefined} validationUnavailableMessage={null} onApply={apply} onDirtyChange={vi.fn()} inspectorRef={ref}/>);
  fireEvent.click(screen.getByRole('button',{name:'设置面轨迹'}));
  const dialog=await screen.findByRole('dialog');
  fireEvent.change(within(dialog).getByRole('textbox',{name:'缓冲窗口 1 名称'}),{target:{value:'discard_me'}});
  fireEvent.click(within(dialog).getByRole('button',{name:/取\s*消/}));
  await act(async()=>{expect(await ref.current?.apply()).toBe(true);});
  expect(apply.mock.calls.at(-1)?.[0].configuration.reconstruction.areaGeometry.windowBindings).toEqual([binding]);
});

it('preserves invalid hidden bindings when changing the buffer source and saving',async()=>{
  const c=configuration(),ref=createRef<CanvasNodeInspectorHandle>(),apply=vi.fn();
  render(<Inspector node={{id,type:CanvasNodeType.TrackReconstruct,name:'重建',layout,configuration:c}} executionMode="BATCH"
    validation={undefined} validationUnavailableMessage={null} onApply={apply} onDirtyChange={vi.fn()} inspectorRef={ref}/>);
  fireEvent.click(screen.getByRole('button',{name:'设置面轨迹'}));
  let dialog=await screen.findByRole('dialog');
  fireEvent.change(within(dialog).getByRole('spinbutton',{name:'缓冲窗口 1 起点'}),{target:{value:'5'}});
  fireEvent.mouseDown(within(dialog).getByRole('combobox',{name:'面轨迹缓冲距离来源'}));
  fireEvent.click(await screen.findByText('数值字段',{selector:'.ant-select-item-option-content'}));
  expect(screen.queryByRole('spinbutton',{name:'缓冲窗口 1 起点'})).toBeNull();
  fireEvent.click(within(dialog).getByRole('button',{name:'保存面轨迹草稿'}));
  await act(async()=>{expect(await ref.current?.apply()).toBe(true);});
  expect(apply.mock.calls.at(-1)?.[0].configuration.reconstruction.areaGeometry).toMatchObject({bufferMode:'FIELD',windowBindings:[{...binding,startOffset:5}]});
  fireEvent.click(screen.getByRole('button',{name:'设置面轨迹'}));
  dialog=await screen.findByRole('dialog');
  fireEvent.mouseDown(within(dialog).getByRole('combobox',{name:'面轨迹缓冲距离来源'}));
  fireEvent.click(await screen.findByText('受控数值表达式',{selector:'.ant-select-item-option-content'}));
  expect(screen.getByRole('spinbutton',{name:'缓冲窗口 1 起点'})).toHaveValue('5');
});

it('sorts complete bindings and confirms removal without editing the expression',async()=>{
  const values=[binding,{...binding,name:'next',startOffset:1,endOffset:3}],change=vi.fn();
  const props={value:values,columns:[],validationAvailable:false,onChange:change};
  render(<BufferWindowEditor {...props}/>);
  fireEvent.click(screen.getByRole('button',{name:'下移缓冲窗口 1'}));
  expect(change.mock.calls.at(-1)?.[0]).toEqual([values[1],values[0]]);
  fireEvent.click(screen.getByRole('button',{name:'删除缓冲窗口 1'}));
  expect(change).toHaveBeenCalledTimes(1);
  const confirmationTitle=await screen.findByText('删除窗口 history？');
  const confirmation=confirmationTitle.closest<HTMLElement>('.ant-popover');
  expect(confirmation).not.toBeNull();
  fireEvent.click(within(confirmation as HTMLElement).getByRole('button',{name:'删除窗口'}));
  await waitFor(()=>expect(change).toHaveBeenCalledTimes(2));
  expect(change.mock.calls.at(-1)?.[0]).toEqual([values[1]]);
});

it('disables adding a buffer window at the 32-window limit',()=>{
  render(<BufferWindowEditor value={Array.from({length:32},(_,i)=>({...binding,name:`history${i}`}))}
    columns={[]} validationAvailable={false} onChange={vi.fn()}/>);
  expect(screen.getByRole('button',{name:'添加缓冲窗口'})).toBeDisabled();
});

it('shows only active window counts on Canvas, never binding expressions or offsets',()=>{
  const c=configuration();c.reconstruction!.areaGeometry!.bufferExpression='coalesce(history,987654)';
  const Body=trackReconstructCanvasView.Body;
  const {container}=render(<Body data={{type:CanvasNodeType.TrackReconstruct,name:'重建',configuration:c}}/>);
  expect(screen.getByText('1 个缓冲窗口')).toBeInTheDocument();
  expect(container).not.toHaveTextContent('987654');expect(container).not.toHaveTextContent('history');
});
