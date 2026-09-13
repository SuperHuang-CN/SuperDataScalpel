import { afterEach, describe, expect, it, vi } from 'vitest';
import { requestEventStream, saveAccessToken, clearAccessToken } from './http';
afterEach(() => { vi.unstubAllGlobals(); clearAccessToken(); });
describe('authenticated event stream', () => {
  it('uses an authorization header, parses fragmented UTF-8, and closes on caller cancellation', async () => {
    saveAccessToken('test-only');const controller=new AbortController();const frames:string[]=[];
    const bytes=new TextEncoder().encode('event: assistant.delta\ndata: {"text":"你好"}\n\n');
    let cancelled=false;
    const stream=new ReadableStream<Uint8Array>({start(c){c.enqueue(bytes.slice(0,48));c.enqueue(bytes.slice(48));},cancel(){cancelled=true;}});
    const fetcher=vi.fn().mockResolvedValue(new Response(stream,{headers:{'Content-Type':'text/event-stream'}}));vi.stubGlobal('fetch',fetcher);
    await requestEventStream('/v1/dsh/sessions/test/events',controller.signal,event=>{frames.push(event.data);controller.abort();});
    expect(frames).toEqual(['{"text":"你好"}']);expect(cancelled).toBe(true);
    expect(fetcher).toHaveBeenCalledWith('/api/v1/dsh/sessions/test/events',expect.objectContaining({headers:{Authorization:'Bearer test-only',Accept:'text/event-stream'}}));
  });
  it('retains HTTP problem codes before a stream is established',async()=>{
    vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response(JSON.stringify({detail:'重新登录',code:'DSH_RELOGIN_REQUIRED'}),{status:401})));
    await expect(requestEventStream('/v1/dsh/sessions/test/events',new AbortController().signal,()=>{})).rejects.toMatchObject({status:401,problem:{code:'DSH_RELOGIN_REQUIRED'}});
  });
});
