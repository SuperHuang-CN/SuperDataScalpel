"""Run one explicitly identified owned K8s acceptance task, capture image evidence.

Non-idempotent: each invocation submits one REAL run. Source is read-only;
the task must have the dswr_k8s_ acceptance prefix and the existing K8s engine.
"""
import argparse
import json
import os
import shlex
import time
import paramiko
import requests

parser = argparse.ArgumentParser()
parser.add_argument('task_id')
parser.add_argument('--expected', choices=['SUCCESS', 'FAILED'], default='SUCCESS')
args = parser.parse_args()
base = 'http://localhost:8080'
session = requests.Session()

def api(method, path, data=None):
    response = session.request(method, base + path, json=data, timeout=120)
    response.raise_for_status()
    return response.json() if response.content else None

session.headers['Authorization'] = 'Bearer ' + api('POST', '/api/v1/auth/login', {
    'username': os.environ.get('DATASCALPEL_ADMIN_USERNAME', 'admin'),
    'password': os.environ.get('DATASCALPEL_ADMIN_PASSWORD', 'admin123456')})['accessToken']
task = api('GET', '/api/v1/tasks/' + args.task_id)
assert task['name'].startswith('dswr_k8s_')
assert task['computeEngineId'] == '3d477246-78c4-4c9e-a040-da5804c7b72b'
if task['status'] == 'DRAFT':
    api('POST', f'/api/v1/tasks/{args.task_id}/actions/publish')
else:
    assert task['status'] == 'PUBLISHED'
k = paramiko.SSHClient()
k.load_system_host_keys()
k.connect('192.168.5.103', username='root', password=os.environ.get('DATASCALPEL_103_SSH_PASSWORD'), timeout=15)
run = api('POST', f'/api/v1/tasks/{args.task_id}/actions/run')
run_id = run['id']
print('SUBMITTED_RUN', run_id, flush=True)
seen = set()
previous = None
deadline = time.monotonic() + 600
while time.monotonic() < deadline:
    run = api('GET', '/api/v1/task-runs/' + run_id)
    if run['status'] != previous:
        print('STATUS', run['status'], flush=True)
        previous = run['status']
    _, out, err = k.exec_command('kubectl get pods -n datascalpel-test -o json', timeout=15)
    pods = json.loads(out.read())
    for pod in pods['items']:
        labels = pod['metadata'].get('labels', {})
        if labels.get('cn.superhuang.datascalpel/execution-id') != run.get('externalExecutionId'):
            continue
        evidence = {'pod': pod['metadata']['name'], 'role': labels.get('spark-role'),
            'phase': pod['status']['phase'], 'images': [c['image'] for c in pod['spec']['containers']],
            'imageIds': [c.get('imageID') for c in pod['status'].get('containerStatuses', [])]}
        value = json.dumps(evidence)
        if value not in seen:
            print('POD_EVIDENCE', value, flush=True)
            seen.add(value)
    if run['status'] in ['SUCCESS', 'FAILED', 'CANCELLED', 'TIMED_OUT', 'LOST']:
        break
    time.sleep(2)
else:
    raise RuntimeError('Acceptance run still active; inspect before submitting another')
k.close()
print('RUN_RESULT', json.dumps({key: run.get(key) for key in
    ['id', 'status', 'executionMode', 'affectedRows', 'startedAt', 'endedAt', 'errorDetail']}), flush=True)
result = api('GET', f'/api/v1/task-runs/{run_id}/artifacts/result')
print('RESULT_ARTIFACT', json.dumps({key: result.get(key) for key in
    ['state', 'affectedRows', 'durationMs', 'error']}), flush=True)
response = session.get(base + f'/api/v1/task-runs/{run_id}/artifacts/log', timeout=30)
response.raise_for_status()
print('LOG_EVIDENCE', json.dumps({'available': bool(response.content),
    'atomicCommit': 'BATCH_WRITE_COMMITTED' in response.text,
    'sdkAcceptance': 'K8S_SDK_ATOMIC_ACCEPTANCE' in response.text}), flush=True)
source = api('GET', '/api/v1/models/a9b21fb4-8ec8-4d8d-ba94-49dd018b666c')
target = api('GET', '/api/v1/models/b4ef9349-026a-4ace-8d27-3703f02b7a5c')
assert target['model']['physicalTableName'] == 'dswr_k8s_07bff0f173'
assert source['model']['catalogName'] == target['model']['catalogName']
quote = lambda value: '"' + value.replace('"', '""') + '"'
input_table = '.'.join(quote(source['model'][key]) for key in ['schemaName', 'physicalTableName'])
output_table = '.'.join(quote(target['model'][key]) for key in ['schemaName', 'physicalTableName'])
columns = ','.join(quote(field['code']) for field in source['fields'])
c = paramiko.SSHClient()
c.load_system_host_keys()
c.connect('192.168.5.102', username='root', timeout=15)
_, out, _ = c.exec_command('docker inspect datascalpel-test-postgresql')
env = dict(value.split('=', 1) for value in json.loads(out.read())[0]['Config']['Env'] if '=' in value)
cmd = ' '.join(shlex.quote(value) for value in ['docker', 'exec', '-i', 'datascalpel-test-postgresql',
    'psql', '-X', '-v', 'ON_ERROR_STOP=1', '-U', env['POSTGRES_USER'], '-d', source['model']['catalogName'], '-At'])
stdin, out, err = c.exec_command(cmd)
stdin.write(f'SELECT (SELECT count(*) FROM {input_table}), (SELECT count(*) FROM {output_table}), '
    f'(SELECT count(*) FROM ((SELECT {columns} FROM {input_table} EXCEPT ALL SELECT {columns} FROM {output_table}) '
    f'UNION ALL (SELECT {columns} FROM {output_table} EXCEPT ALL SELECT {columns} FROM {input_table})) diff);')
stdin.channel.shutdown_write()
counts = out.read().decode().strip()
assert out.channel.recv_exit_status() == 0, 'Database comparison failed'
c.close()
print('INDEPENDENT_DATABASE_COUNTS source|target|difference', counts, flush=True)
assert counts == '1|1|0', 'Target differs from the read-only registered source'
assert run['status'] == args.expected
assert run['executionMode'] == 'REAL'
assert seen, 'Missing actual Kubernetes Pod/image evidence'
