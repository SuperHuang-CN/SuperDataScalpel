#!/usr/bin/env python3
"""Real-user SSE, native questions, independent cancellation and disable/re-enable verification."""
import importlib.util,pathlib,json,time,uuid,threading,urllib.request
spec=importlib.util.spec_from_file_location('verify',pathlib.Path(__file__).with_name('verify-admin.py'));v=importlib.util.module_from_spec(spec);spec.loader.exec_module(v)
state=json.loads(v.STATE.read_text());admin=v.admin();clients={n:v.Api.login(u['username'],u['password']) for n,u in state['users'].items()}
# Isolate repeated cancellation scenarios from the durable context-restoration fixtures.
# Otherwise a real model may reasonably refuse a question repeatedly aborted in earlier runs.
lifecycle={name:client.call('/api/v1/dsh/sessions',{'clientSessionId':str(uuid.uuid4())})['sessionId'] for name,client in clients.items()}
state['lastLifecycleSessions']=lifecycle;v.save(state);state['sessions']=lifecycle

class Feed:
    def __init__(self,client,id):
        self.items=[];self.done=threading.Event();self.ready=threading.Event();self.error=None;self.response=None
        def run():
            try:
                self.response=urllib.request.urlopen(urllib.request.Request(v.BASE+'/api/v1/dsh/sessions/'+id+'/events',headers={'Authorization':'Bearer '+client.token}),timeout=25);self.ready.set()
                for line in self.response:
                    if line.startswith(b'data:'):
                        item=json.loads(line[5:]);assert item.get('sessionId',id)==id;self.items.append(item)
            except Exception as e:self.error=type(e).__name__
            finally:self.ready.set();self.done.set()
        threading.Thread(target=run,daemon=True).start();assert self.ready.wait(10)
    def close(self):
        if self.response:self.response.close()
def send(name,text):
    return clients[name].call('/api/v1/dsh/sessions/'+state['sessions'][name]+'/messages',{'clientMessageId':str(uuid.uuid4()),'text':text})
def pending(name):
    for _ in range(70):
        value=clients[name].call('/api/v1/dsh/sessions/'+state['sessions'][name])
        if value['pendingInteractions']:return value['pendingInteractions'][0]
        time.sleep(1)
    raise RuntimeError('Model did not ask the requested question')
def respond(name,question,free_text=False):
    path='/api/v1/dsh/sessions/'+state['sessions'][name]+'/interactions/'+question['interactionId']+'/actions/respond'
    answers=[{'id':q['id'],'selected':[],'custom':'phase-three answer '+name} if free_text else {'id':q['id'],'selected':[q['options'][0]['label']]} for q in question['questions']]
    clients[name].call(path,{'answers':answers});clients[name].call(path,{'answers':answers},expected=(410,))
feeds={n:Feed(c,state['sessions'][n]) for n,c in clients.items()}
for name in clients:send(name,'请必须调用 ask_user_question 工具，问我喜欢蓝色还是绿色，等待我的回答后再继续。不要调用其他工具。')
questions={name:pending(name) for name in clients}
respond('alice',questions['alice'])
bpath='/api/v1/dsh/sessions/'+state['sessions']['bob']
clients['bob'].call(bpath+'/actions/cancel',{})
clients['bob'].call(bpath+'/interactions/'+questions['bob']['interactionId']+'/actions/respond',{'answers':[{'id':questions['bob']['questions'][0]['id'],'selected':[],'custom':'stale'}]},expected=(410,))
assert v.wait_idle(clients['alice'],state['sessions']['alice'])['lastOutcome']=='COMPLETED'
assert v.wait_idle(clients['bob'],state['sessions']['bob'])['lastOutcome']=='CANCELLED'
assert any(e['type']=='assistant.delta' for e in feeds['alice'].items)
print('PASS: concurrent native questions, option reply, duplicate reply rejected, independent cancellation and SSE correlation')
send('alice','请必须使用 ask_user_question 问我如何称呼我，等待自由文本回答后再问好。');respond('alice',pending('alice'),True)
assert v.wait_idle(clients['alice'],state['sessions']['alice'])['lastOutcome']=='COMPLETED'
print('PASS: native question free-text reply')
# Disconnect an observer while keeping the Agent execution alive.
feeds['alice'].close();send('alice','请只回答“断线不取消任务验证通过”。')
assert v.wait_idle(clients['alice'],state['sessions']['alice'])['lastOutcome']=='COMPLETED'
reconnected=Feed(clients['alice'],state['sessions']['alice']);time.sleep(1)
assert any(e['type']=='session.snapshot' for e in reconnected.items);reconnected.close()
print('PASS: disconnected observer does not cancel execution; reconnect returns snapshot')
send('bob','请必须调用 ask_user_question 问我是否继续，并一直等待我的回答。');pending('bob')
user=state['users']['bob'];update={'displayName':'DSH 验证 bob','roleId':state['roles']['bob'],'enabled':False}
try:
    started=time.monotonic();admin.call('/api/v1/system/users/'+user['id']+'/actions/update',update)
    err=clients['bob'].call(bpath,expected=(401,));assert err['code']=='DSH_USER_UNAVAILABLE'
    assert feeds['bob'].done.wait(15),'Disabled user event connection exceeded 15 seconds'
    elapsed=time.monotonic()-started;print('PASS: disabled user rejects commands and event connection closed in',round(elapsed,2),'seconds')
    assert any(e['type']=='connection.failed' for e in feeds['bob'].items),'Expected terminal SSE error'
    # Stream closure and Agent cancellation are separate facts; verify both within the lease deadline.
    bridge_env=dict(line.split('=',1) for line in (v.ROOT/'deploy/dsh/.env.admin').read_text().splitlines() if '=' in line)
    bridge_headers={'Authorization':'Bearer '+bridge_env['DATASCALPEL_DSH_ADMIN_BRIDGE_TOKEN']}
    while time.monotonic()-started<15:
        active=json.load(urllib.request.urlopen(urllib.request.Request('http://127.0.0.1:13080/bridge/v3/active-users',headers=bridge_headers),timeout=3))
        if user['id'] not in active['userIds']:break
        time.sleep(0.2)
    else:raise AssertionError('Disabled user Agent scope exceeded 15 seconds')
    print('PASS: disabled user Agent scope revoked in',round(time.monotonic()-started,2),'seconds')
finally:
    update['enabled']=True;admin.call('/api/v1/system/users/'+user['id']+'/actions/update',update)
# Allow the identity reconciliation cancellation to persist before reinitializing.
time.sleep(2)
value=clients['bob'].call(bpath)
assert value['lastOutcome'] in ('CANCELLED','INTERRUPTED'),value
assert not value['pendingInteractions']
print('PASS: user re-enabled with original binding; revoked Agent cancelled and old question expired')
