import { describe, expect, it } from 'vitest';
import { SseDecoder } from '../../../shared/api/sse';
import { mergeMessages } from './types';
import { toolStatus } from './toolResult';
import { conversationReducer } from '../hooks/useConversation';
describe('DSH transcript and stream boundaries', () => {
  it('reassembles fragmented multiline SSE and ignores heartbeats', () => {
    const decoder = new SseDecoder();
    expect(decoder.push(': heartbeat\r\n\r')).toEqual([]);
    expect(decoder.push('\nevent: assistant.delta\r\ndata: {"text":\r\n')).toEqual([]);
    expect(decoder.push('data: "你好"}\r\n\r\n')).toEqual([{event:'assistant.delta',data:'{"text":\n"你好"}'}]);
    expect(() => decoder.push('x'.repeat(1024*1024+1))).toThrow(/上限/);
  });
  it('deduplicates durable messages across snapshot, reconnect and previous pages', () => {
    const first={id:'a',role:'user',content:[],sourceSeq:1};const next={...first,id:'b',sourceSeq:3};
    expect(mergeMessages([next],[first,next])).toEqual([first,next]);
  });
  it('replaces temporary text on a new attempt and clears it on reset', () => {
    let s=conversationReducer({connection:'',error:'',text:'',tools:{}},{type:'delta',attemptId:'a',text:'hello'});
    s=conversationReducer(s,{type:'delta',attemptId:'b',text:'new'});expect(s.text).toBe('new');
    expect(conversationReducer(s,{type:'reset'}).text).toBe('');
  });
  it('keeps business uncertainty, incomplete responses and tool errors distinct', () => {
    const result=(value:unknown)=>({type:'tool-result',content:[{type:'text',text:JSON.stringify(value)}]});
    expect(toolStatus(result({executionStatus:'UNKNOWN'})).text).toContain('不确定');
    expect(toolStatus(result({executionStatus:'RESPONDED_INCOMPLETE',httpStatus:201})).text).toContain('201');
    expect(toolStatus(result({executionStatus:'RESPONDED',httpStatus:403})).text).toContain('403');
    expect(toolStatus(result({isError:true})).text).toContain('失败');
  });
});
