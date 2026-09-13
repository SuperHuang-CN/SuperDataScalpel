#!/usr/bin/env python3
"""Phase-three real Admin verification. Uses the existing environment; never starts a service.
State/passwords are stored in ignored .local/dsh-phase-three with mode 0600. No tokens are printed.
"""
import concurrent.futures, json, os, pathlib, secrets, sys, time, uuid, urllib.request, urllib.error
ROOT=pathlib.Path(__file__).resolve().parents[3]
STATE=ROOT/'.local/dsh-phase-three/verify-state.json'
BASE=os.getenv('DSH_VERIFY_ADMIN_URL','http://127.0.0.1:18080')
class Api:
    def __init__(self,token=None): self.token=token
    def call(self,path,body=None,expected=(200,201,202,204)):
        headers={'Content-Type':'application/json'}
        if self.token:headers['Authorization']='Bearer '+self.token
        r=urllib.request.Request(BASE+path,data=json.dumps(body).encode() if body is not None else None,headers=headers)
        try: response=urllib.request.urlopen(r,timeout=55)
        except urllib.error.HTTPError as e: response=e
        raw=response.read();data=json.loads(raw) if raw else None
        if response.code not in expected: raise RuntimeError(f'{path}: HTTP {response.code}, code={data.get("code") if isinstance(data,dict) else "unknown"}')
        return data
    @staticmethod
    def login(username,password):return Api(Api().call('/api/v1/auth/login',{'username':username,'password':password})['accessToken'])
def save(state):
    STATE.parent.mkdir(parents=True,exist_ok=True);STATE.write_text(json.dumps(state,indent=2));STATE.chmod(0o600)
def admin():return Api.login(os.getenv('DATASCALPEL_ADMIN_USERNAME','admin'),os.getenv('DATASCALPEL_ADMIN_PASSWORD','admin123456'))
def ready():
    until=time.monotonic()+180
    while time.monotonic()<until:
        try:
            if urllib.request.urlopen(BASE+'/actuator/health',timeout=2).status==200:
                c=admin();configuration=c.call('/api/v1/system-mcp/configuration')
                if configuration['catalogStatus']=='READY' and c.call('/api/v1/dsh/capabilities').get('ready'):
                    print('PASS: Admin healthy, MCP catalog READY and DSH ready');return
        except (OSError,RuntimeError):pass
        time.sleep(2)
    raise RuntimeError('Existing Admin / MCP / DSH environment did not become ready')
def setup():
    a=admin();state=json.loads(STATE.read_text()) if STATE.exists() else {'users':{},'roles':{},'sessions':{}}
    print('capabilities',a.call('/api/v1/dsh/capabilities'))
    doc=a.call('/v3/api-docs');assert not [p for p in doc['paths'] if '/assistant' in p or '/llm-models' in p]
    permissions=a.call('/api/v1/system/permissions?size=500')['content']
    ids=[p['id'] for p in permissions if p['code']=='datasource.view']
    assert len(ids)==1,'datasource.view permission required'
    for owner in ['alice','bob']:
        if owner not in state['roles']:
            role=a.call('/api/v1/system/roles',{'code':'dsh_verify_'+owner,'name':'DSH 验证 '+owner,'description':'第三阶段独立验证角色'});state['roles'][owner]=role['id'];save(state)
        a.call('/api/v1/system/roles/'+state['roles'][owner]+'/actions/update-permissions',{'permissionIds':ids if owner=='alice' else []})
        if owner not in state['users']:
            password=secrets.token_urlsafe(24)
            user=a.call('/api/v1/system/users',{'username':'dsh_verify_'+owner,'displayName':'DSH 验证 '+owner,'password':password,'roleId':state['roles'][owner],'enabled':True})
            state['users'][owner]={'id':user['id'],'username':user['username'],'password':password};save(state)
    clients={name:Api.login(v['username'],v['password']) for name,v in state['users'].items()}
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        jobs=[(name,pool.submit(clients[name].call,'/api/v1/dsh/workspace/actions/ensure',{})) for name in clients for _ in range(3)]
        workspaces={name:set() for name in clients}
        for name,job in jobs:workspaces[name].add(job.result()['workspaceId'])
    assert all(len(v)==1 for v in workspaces.values()) and workspaces['alice']!=workspaces['bob']
    tokens=a.call('/api/v1/system-mcp/access-tokens?size=500')['content']
    for name,user in state['users'].items():
        matches=[t for t in tokens if t['userId']==user['id'] and t['managed']];assert len(matches)==1
        token=matches[0];state['users'][name]['tokenId']=token['id']
        for action in ['enable','disable','rotate','delete','update']:
            body={'name':'forbidden','expiresAt':None} if action=='update' else {}
            err=a.call('/api/v1/system-mcp/access-tokens/'+token['id']+'/actions/'+action,body,expected=(409,));assert err['code']=='SYSTEM_MCP_TOKEN_MANAGED'
    save(state)
    for name,client in clients.items():
        if name not in state['sessions']:
            stable=str(uuid.uuid4());first=client.call('/api/v1/dsh/sessions',{'clientSessionId':stable});second=client.call('/api/v1/dsh/sessions',{'clientSessionId':stable})
            assert first['sessionId']==second['sessionId'];state['sessions'][name]=first['sessionId'];save(state)
    for name,client in clients.items():
        other=state['sessions']['bob' if name=='alice' else 'alice']
        for suffix in ['', '/messages','/events']:
            client.call('/api/v1/dsh/sessions/'+other+suffix,expected=(404,))
        for suffix,body in [('/actions/resume',{}),('/actions/cancel',{}),('/messages',{'clientMessageId':str(uuid.uuid4()),'text':'must reject'})]:
            client.call('/api/v1/dsh/sessions/'+other+suffix,body,expected=(404,))
    print('PASS: old routes removed; concurrent initialization; managed token guards; duplicate sessions; cross-user isolation')
    return state,clients

def wait_idle(client,id,timeout=150):
    until=time.monotonic()+timeout
    while time.monotonic()<until:
        value=client.call('/api/v1/dsh/sessions/'+id)
        if value['runtimeState'] in ['IDLE','UNLOADED'] and value['lastOutcome'] not in ['NONE',None]:return value
        time.sleep(1)
    raise RuntimeError('Conversation did not settle within verification deadline')
def model():
    state=json.loads(STATE.read_text());clients={n:Api.login(v['username'],v['password']) for n,v in state['users'].items()}
    marker='phase-three-'+secrets.token_hex(5);state['marker']=marker;save(state)
    for name,client in clients.items():
        sid=state['sessions'][name];mid=str(uuid.uuid4());body={'clientMessageId':mid,'text':f'请记住标记 {marker}，我是 {name}。必须通过 api_search 检索数据源查询接口，再通过 api_describe 获取 GET /api/v1/data-sources 契约；如当前权限允许则调用该只读接口，仅告诉我 HTTP 状态和数据源数量。若没有权限则明确说明无法访问。不要新增或修改业务数据。'}
        client.call('/api/v1/dsh/sessions/'+sid+'/messages',body);duplicate=client.call('/api/v1/dsh/sessions/'+sid+'/messages',body);assert duplicate['duplicate']
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        futures={n:pool.submit(wait_idle,c,state['sessions'][n]) for n,c in clients.items()}
        for name,result in futures.items(): print(name,'settled',result.result()['lastOutcome'])
    for name,client in clients.items():
        messages=client.call('/api/v1/dsh/sessions/'+state['sessions'][name]+'/messages?limit=200')
        assert marker in json.dumps(messages);print(name,'history messages',len(messages['items']))
    print('PASS: real concurrent model messages, deduplication and durable history; inspect audit for actual MCP results')
def resume():
    state=json.loads(STATE.read_text())
    for name,user in state['users'].items():
        c=Api.login(user['username'],user['password']);sid=state['sessions'][name]
        c.call('/api/v1/dsh/sessions/'+sid+'/actions/resume',{})
        c.call('/api/v1/dsh/sessions/'+sid+'/messages',{'clientMessageId':str(uuid.uuid4()),'text':'请只回答我之前让你记住的 phase-three 开头的完整标记。不要调用工具。'})
        value=wait_idle(c,sid);assert value['lastOutcome']=='COMPLETED',value
        messages=c.call('/api/v1/dsh/sessions/'+sid+'/messages?limit=200')['items']
        assert state['marker'] in json.dumps(messages[-1]);print(name,'PASS restored context')
if __name__=='__main__':
    mode=sys.argv[1] if len(sys.argv)>1 else 'setup'
    {'ready':ready,'setup':setup,'model':model,'resume':resume}[mode]()
