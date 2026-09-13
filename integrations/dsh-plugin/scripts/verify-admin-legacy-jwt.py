#!/usr/bin/env python3
"""Local-only compatibility check. Reads deployment signing key in memory; never prints tokens."""
import importlib.util,pathlib,json,subprocess,base64,hmac,hashlib,uuid,os,re
spec=importlib.util.spec_from_file_location('verify',pathlib.Path(__file__).with_name('verify-admin.py'));v=importlib.util.module_from_spec(spec);spec.loader.exec_module(v)
a=v.admin();state=json.loads(v.STATE.read_text());user=state['users']['alice'];alice=v.Api.login(user['username'],user['password'])
config=(v.ROOT/'config/application-local.yml').read_bytes()
raw=subprocess.check_output(['docker','exec','-i','datascalpel-dsh-dsh-1','python','-c','import sys,yaml,json; print(json.dumps(yaml.safe_load(sys.stdin).get("data-scalpel",{}).get("security",{}).get("jwt",{}).get("secret")))'],input=config)
local_secret=json.loads(raw)
if not local_secret:
    source=(v.ROOT/'data-scalpel-admin/src/main/resources/application.yml').read_text()
    default=re.search(r'DATASCALPEL_JWT_SECRET:([^}]+)',source).group(1)
    local_secret=os.getenv('DATASCALPEL_JWT_SECRET',default)
key=local_secret.encode();head,payload,_=a.token.split('.')
claims=json.loads(base64.urlsafe_b64decode(payload+'='*(-len(payload)%4)));claims.pop('userId',None)
encode=lambda b:base64.urlsafe_b64encode(b).rstrip(b'=').decode()
body=head+'.'+encode(json.dumps(claims,separators=(',',':')).encode());old=v.Api(body+'.'+encode(hmac.new(key,body.encode(),hashlib.sha256).digest()))
old.call('/api/v1/data-sources?size=1')
assert old.call('/api/v1/dsh/capabilities',expected=(401,))['code']=='DSH_RELOGIN_REQUIRED'
print('PASS: signed legacy JWT remains valid for original API, requires re-login for DSH')
alice.call('/api/v1/dsh/sessions/'+state['sessions']['alice']+'/messages',{'clientMessageId':str(uuid.uuid4()),'text':'x'*1100000},expected=(413,))
print('PASS: Admin rejects oversized request before model command dispatch')
