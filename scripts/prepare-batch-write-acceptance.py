"""Create isolated acceptance objects through the existing Admin; never edits user tasks/models."""
import os
import uuid
import requests

s = requests.Session()
base = 'http://localhost:8080'
def call(method, path, data=None):
    response = s.request(method, base + path, json=data, timeout=120)
    if not response.ok:
        raise RuntimeError(f'{method} {path}: HTTP {response.status_code}: {response.text[:1500]}')
    return response.json() if response.content else None

s.headers['Authorization'] = 'Bearer ' + call('POST', '/api/v1/auth/login', {
    'username': os.environ.get('DATASCALPEL_ADMIN_USERNAME', 'admin'),
    'password': os.environ.get('DATASCALPEL_ADMIN_PASSWORD', 'admin123456')})['accessToken']
models = call('GET', '/api/v1/models?size=100')['content']
source = call('GET', '/api/v1/models/' + next(m['id'] for m in models if m['code'] == 'meter_readings'))
target_key = os.environ.get('DATASCALPEL_WRITE_ACCEPTANCE_TARGET', 'local-docker-102')
name = ('dswr_k8s_' if target_key == 'k8s-103' else 'dswr_accept_') + uuid.uuid4().hex[:10]
target = call('POST', '/api/v1/models', {
    'code': name, 'name': name, 'storageDataSourceId': source['model']['storageDataSourceId'],
    'physicalTableName': name, 'physicalTableMode': 'MANAGED', 'description': '2026-09-29 atomic write acceptance; isolated target only'})
target_id = target.get('model', target)['id']
print('ACCEPTANCE_MODEL', target_id, name, flush=True)
allowed = call('GET', '/v3/api-docs')['components']['schemas']['DataModelFieldInput']['properties']
fields = [{k:v for k,v in f.items() if k in allowed and k != 'id'} for f in source['fields']]
call('POST', f'/api/v1/models/{target_id}/actions/update-fields', {'fields': fields})
call('POST', f'/api/v1/models/{target_id}/actions/publish')
engines = call('GET', '/api/v1/compute-engines?size=100')['content']
engine = next(e for e in engines if e['targetKey'] == target_key)
task = call('POST', '/api/v1/tasks', {'name': name, 'type': 'SPARK_CANVAS',
    'computeEngineId': engine['id'], 'description': 'Atomic condition overwrite acceptance; source read-only; target dedicated'})
task_id = task['id']
print('ACCEPTANCE_TASK', task_id, flush=True)
input_id, output_id = str(uuid.uuid4()), str(uuid.uuid4())
key = next((f['code'] for f in fields if f.get('primaryKey')), fields[0]['code'])
definition = {'schemaVersion':4, 'schemaMinorVersion':78, 'nodes':[
    {'type':'MODEL_INPUT','id':input_id,'name':'Registered source (read only)',
     'layout':{'x':100,'y':160,'width':240,'height':120},
     'configuration':{'models':[{'modelId':source['model']['id']}]}},
    {'type':'MODEL_OUTPUT','id':output_id,'name':'Atomic conditional overwrite',
     'layout':{'x':500,'y':160,'width':240,'height':120},
     'configuration':{'writes':[{'writeId':str(uuid.uuid4()),'sourceTableName':'meter_readings',
       'targetModelId':target_id,'writeMode':'OVERWRITE',
       'columnMappings':[{'sourceColumnName':f['code'],'targetColumnName':f['code']} for f in fields],
       'batchWrite':{'overwriteCondition':{'kind':'PREDICATE','columnName':key,'operator':'IS_NOT_NULL','values':[]},
                     'allowEmptyOverwrite':False}}]}}
], 'edges':[{'id':str(uuid.uuid4()),'sourceNodeId':input_id,'targetNodeId':output_id}]}
call('POST', f'/api/v1/tasks/{task_id}/actions/update-canvas-definition', {'definition':definition})
loaded = call('GET', f'/api/v1/tasks/{task_id}/canvas-definition')
assert loaded['definition']['nodes'][1]['configuration']['writes'][0]['batchWrite']['overwriteCondition']['columnName'] == key
print('ACCEPTANCE_SAVE_RELOAD_PASS', task_id, flush=True)
