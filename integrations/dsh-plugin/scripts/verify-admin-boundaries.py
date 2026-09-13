#!/usr/bin/env python3
"""Destructive-user and access-change checks on dedicated verification identities only."""
import importlib.util,pathlib,json,secrets,time,uuid,urllib.parse
spec=importlib.util.spec_from_file_location('verify',pathlib.Path(__file__).with_name('verify-admin.py'));v=importlib.util.module_from_spec(spec);spec.loader.exec_module(v)
a=v.admin();state=json.loads(v.STATE.read_text());alice=state['users']['alice'];client=v.Api.login(alice['username'],alice['password']);sid=state['sessions']['alice'];role=state['roles']['alice']
# Changing these only affects the dedicated verification role.
original=a.call('/api/v1/system/roles/'+role)['permissionIds']
def command(text):
    client.call('/api/v1/dsh/sessions/'+sid+'/messages',{'clientMessageId':str(uuid.uuid4()),'text':text});assert v.wait_idle(client,sid)['lastOutcome']=='COMPLETED'
def latest_call(tool):
    rows=a.call('/api/v1/system-mcp/audits?size=100&sort=-createdAt')['content']
    return next(r for r in rows if r.get('tokenId')==alice['tokenId'] and r.get('toolName')==tool)
try:
    a.call('/api/v1/system/roles/'+role+'/actions/update-permissions',{'permissionIds':[]})
    command('权限已经调整。请务必直接调用 api_describe，operationIds 只包含 GET /api/v1/data-sources。不要检索，不要调用其他接口。报告真实错误，不要猜测。')
    assert latest_call('api_describe')['status']=='ERROR'
    print('PASS: existing scoped MCP client observes role change without JWT login or credential replacement')
finally:a.call('/api/v1/system/roles/'+role+'/actions/update-permissions',{'permissionIds':original})
rows=a.call('/api/v1/system-mcp/apis?size=500&search='+urllib.parse.quote('operationId:"GET /api/v1/data-sources"'))['content']
api=next(r for r in rows if r['operationId']=='GET /api/v1/data-sources');assert api['enabled'],'Never enable a previously closed API during verification'
try:
    a.call('/api/v1/system-mcp/actions/update-configuration',{'changes':[{'id':api['id'],'enabled':False}]})
    command('该数据源接口已关闭。请务必直接调用 api_invoke，operationId=GET /api/v1/data-sources，queryParams={"page":0,"size":1}。不要检索或调用别的接口。只报告真实错误，不能重试。')
    result=latest_call('api_invoke');assert result['status']=='ERROR';assert 'NOT_DISPATCHED' in result['changesJson']
    print('PASS: closing previously described API blocks next invocation before dispatch')
finally:a.call('/api/v1/system-mcp/actions/update-configuration',{'changes':[{'id':api['id'],'enabled':True}]})
# A separate disposable username proves UUID ownership survives delete/recreate.
name='dsh_recreate_'+secrets.token_hex(4);password=secrets.token_urlsafe(24);current=None
try:
    def create():return a.call('/api/v1/system/users',{'username':name,'displayName':'DSH 同名重建验证','password':password,'roleId':state['roles']['bob'],'enabled':True})
    current=create();old=current;first=v.Api.login(name,password);w1=first.call('/api/v1/dsh/workspace/actions/ensure',{});session=first.call('/api/v1/dsh/sessions',{'clientSessionId':str(uuid.uuid4())})
    a.call('/api/v1/system/users/'+old['id']+'/actions/delete',{});current=None
    first.call('/api/v1/dsh/capabilities',expected=(401,))
    current=create();second=v.Api.login(name,password);w2=second.call('/api/v1/dsh/workspace/actions/ensure',{})
    assert current['id']!=old['id'] and w1['workspaceId']!=w2['workspaceId']
    second.call('/api/v1/dsh/sessions/'+session['sessionId'],expected=(404,))
    print('PASS: deleted user JWT rejected; same-name replacement receives new UUID/workspace and cannot access old session')
finally:
    if current:a.call('/api/v1/system/users/'+current['id']+'/actions/delete',{})
# Ordinary manually managed tokens keep their normal administration behavior.
issued=a.call('/api/v1/system-mcp/access-tokens',{'name':'DSH phase-three manual regression','userId':alice['id'],'expiresAt':None});tid=issued['token']['id']
try:
    assert not issued['token']['managed']
    a.call('/api/v1/system-mcp/access-tokens/'+tid+'/actions/update',{'name':'manual regression updated','expiresAt':None})
    for action in ['disable','enable','rotate']:a.call('/api/v1/system-mcp/access-tokens/'+tid+'/actions/'+action,{})
finally:a.call('/api/v1/system-mcp/access-tokens/'+tid+'/actions/delete',{})
print('PASS: manual token update/disable/enable/rotate/delete remain available')
