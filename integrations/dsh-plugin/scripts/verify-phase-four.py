#!/usr/bin/env python3
"""Phase-four checks against the existing Admin/DSH. No services, credentials or business data created here.
Uses phase-three verification accounts; creates isolated conversation fixtures only.
"""
import importlib.util, pathlib, json, uuid, time, sys, threading, urllib.request
spec=importlib.util.spec_from_file_location('verify',pathlib.Path(__file__).with_name('verify-admin.py'))
v=importlib.util.module_from_spec(spec);spec.loader.exec_module(v)
STATE=v.ROOT/'.local/dsh-phase-four/verify-state.json'
def save(s):
    STATE.parent.mkdir(parents=True,exist_ok=True);STATE.write_text(json.dumps(s,indent=2));STATE.chmod(0o600)
def clients():
    users=json.loads(v.STATE.read_text())['users']
    return {n:v.Api.login(u['username'],u['password']) for n,u in users.items()}
class Feed:
    def __init__(self,c,sid):
        self.items=[];self.response=None;self.ready=threading.Event()
        def receive():
            try:
                self.response=urllib.request.urlopen(urllib.request.Request(v.BASE+'/api/v1/dsh/sessions/'+sid+'/events',headers={'Authorization':'Bearer '+c.token}),timeout=25)
                self.ready.set()
                for line in self.response:
                    if line.startswith(b'data:'):self.items.append(json.loads(line[5:]))
            except (OSError,ValueError,AttributeError):pass
            finally:self.ready.set()
        threading.Thread(target=receive,daemon=True).start();assert self.ready.wait(10)
    def close(self):
        if self.response:self.response.close()
def pending(c,path):
    for _ in range(100):
        state=c.call(path)
        if state['pendingInteractions']:return state['pendingInteractions'][0]
        if state['lastOutcome']=='FAILED':raise AssertionError('model failed')
        time.sleep(1)
    raise AssertionError('question not received')
def initial():
    cs=clients();state={'sessions':{},'marker':'phase-four-'+uuid.uuid4().hex[:12]}
    for n,c in cs.items():
        assert c.call('/api/v1/dsh/capabilities')['pluginVersion']=='0.4.0'
        cid=str(uuid.uuid4());a=c.call('/api/v1/dsh/sessions',{'clientSessionId':cid});b=c.call('/api/v1/dsh/sessions',{'clientSessionId':cid});assert a['sessionId']==b['sessionId']
        state['sessions'][n]=a['sessionId'];save(state)
        title='第四阶段 '+n
        r=c.call('/api/v1/dsh/sessions/'+a['sessionId']+'/actions/update',{'title':title});assert r['title']==title
    for n,c in cs.items():
        own='/api/v1/dsh/sessions/'+state['sessions'][n];other='/api/v1/dsh/sessions/'+state['sessions']['bob' if n=='alice' else 'alice']
        for action,body in [('update',{'title':'forbidden'}),('archive',{}),('restore',{})]:c.call(other+'/actions/'+action,body,expected=(404,))
        c.call(own+'/messages?mode=cursor&offset=0',expected=(400,))
        c.call(own+'/actions/update',{'title':'  '},expected=(400,))
    print('PASS session creation dedupe, metadata, ownership and validation',flush=True)
    feeds={n:Feed(c,state['sessions'][n]) for n,c in cs.items()}
    for n,c in cs.items():
        path='/api/v1/dsh/sessions/'+state['sessions'][n]
        body={'clientMessageId':str(uuid.uuid4()),'text':f'请记住标记 {state["marker"]}。请必须调用 ask_user_question，问我选择蓝色还是绿色，并等待回答。回答后用一个简短 Markdown 表格和 Python 代码块表示我的选择。不要操作业务数据。'}
        c.call(path+'/messages',body);assert c.call(path+'/messages',body)['duplicate']
    questions={n:pending(c,'/api/v1/dsh/sessions/'+state['sessions'][n]) for n,c in cs.items()}
    for n,c in cs.items():
        path='/api/v1/dsh/sessions/'+state['sessions'][n];question=questions[n]
        assert c.call(path+'/actions/archive',{},expected=(409,))['code']=='BRIDGE_SESSION_BUSY'
        answers=[{'id':q['id'],'selected':[q['options'][0]['label']]} if n=='alice' and q.get('options') else {'id':q['id'],'selected':[],'custom':'绿色'} for q in question['questions']]
        c.call(path+'/interactions/'+question['interactionId']+'/actions/respond',{'answers':answers})
        c.call(path+'/interactions/'+question['interactionId']+'/actions/respond',{'answers':answers},expected=(410,))
    for n,c in cs.items():
        sid=state['sessions'][n];assert v.wait_idle(c,sid)['lastOutcome']=='COMPLETED'
        assert any(e['type']=='assistant.delta' for e in feeds[n].items)
        path='/api/v1/dsh/sessions/'+sid
        all_messages=c.call(path+'/messages?limit=200')['items'];seen=[];cursor=''
        while True:
            page=c.call(path+'/messages?mode=cursor&limit=2'+cursor);seen=page['items']+seen
            if not page['hasMore']:break
            cursor='&beforeSeq='+str(page['nextBeforeSeq'])
        assert [m['id'] for m in seen]==[m['id'] for m in all_messages]
        feeds[n].close()
    print('PASS real concurrent questions, option/free text, busy archive rejection, SSE and cursor history',flush=True)
    # A disconnected observer does not cancel a model run; also validate current MCP identity.
    for n,c in cs.items():
        path='/api/v1/dsh/sessions/'+state['sessions'][n]
        c.call(path+'/messages',{'clientMessageId':str(uuid.uuid4()),'text':'请调用 api_search 搜索数据源查询接口，再获取 GET /api/v1/data-sources 的契约；有权限则调用它，报告实际 HTTP 状态，无权限则如实说明。不要新增或修改业务数据。'})
    for n,c in cs.items():
        sid=state['sessions'][n];assert v.wait_idle(c,sid)['lastOutcome']=='COMPLETED'
        feed=Feed(c,sid);time.sleep(.5);assert any(e['type']=='session.snapshot' for e in feed.items);feed.close()
    print('PASS observer disconnection/reconnection and real read-only MCP conversations',flush=True)
    # Cancel one pending question without disturbing the other user's completed session.
    c=cs['bob'];sid=state['sessions']['bob'];path='/api/v1/dsh/sessions/'+sid
    c.call(path+'/messages',{'clientMessageId':str(uuid.uuid4()),'text':'这是停止执行的验证。请必须调用 ask_user_question 问我是否继续，等待回答。'})
    pending(c,path);c.call(path+'/actions/cancel',{});assert v.wait_idle(c,sid)['lastOutcome']=='CANCELLED'
    for n,c in cs.items():
        sid=state['sessions'][n];path='/api/v1/dsh/sessions/'+sid
        previous=c.call(path);r=c.call(path+'/actions/archive',{});assert r['archived'] and r['runtimeState']=='UNLOADED'
        assert c.call(path+'/actions/archive',{})['archived']
        assert r['lastActivityAt']==previous['lastActivityAt']
        for suffix,body in [('/actions/resume',{}),('/messages',{'clientMessageId':str(uuid.uuid4()),'text':'reject'})]:
            assert c.call(path+suffix,body,expected=(409,))['code']=='BRIDGE_SESSION_ARCHIVED'
        listed=c.call('/api/v1/dsh/sessions?archived=true&query='+n)['items'];assert any(s['sessionId']==sid for s in listed)
        assert not any(s['sessionId']==sid for s in c.call('/api/v1/dsh/sessions?query='+n)['items'])
        assert c.call(path+'/messages?mode=cursor')['items']
        if n=='alice':assert not c.call(path+'/actions/restore',{})['archived']
    print('PASS independent cancellation, archive/release, archived read-only history and cold restore',flush=True)
def resume():
    state=json.loads(STATE.read_text())
    for n,c in clients().items():
        sid=state['sessions'][n];path='/api/v1/dsh/sessions/'+sid
        before=c.call(path);assert before['title']=='第四阶段 '+n;assert before['archived']==(n=='bob')
        if before['archived']:c.call(path+'/actions/restore',{})
        c.call(path+'/messages',{'clientMessageId':str(uuid.uuid4()),'text':'请只回答我先前让你记住的 phase-four 开头的完整标记，不要调用工具。'})
        assert v.wait_idle(c,sid)['lastOutcome']=='COMPLETED'
        recent=c.call(path+'/messages?mode=cursor')['items'];assert state['marker'] in json.dumps(recent[-1])
    print('PASS restart persisted title/archive/history and restored conversation context',flush=True)
if __name__=='__main__': {'initial':initial,'resume':resume}[sys.argv[1] if len(sys.argv)>1 else 'initial']()
