#!/usr/bin/env python3
"""Back up / explicitly remove only the six retired assistant tables. No database server is started."""
import sys, os, json, subprocess, pathlib, hashlib, datetime
root = pathlib.Path(__file__).resolve().parents[3]
tables = ['ai_assistant_tool_invocation','ai_assistant_change_set','ai_assistant_message','ai_assistant_run','ai_assistant_session','ai_llm_model_configuration']
config = (root / 'config/application-local.yml').read_bytes()
# Use the existing DSH Python YAML parser; capture configuration only in memory.
parsed = subprocess.check_output(['docker','exec','-i','datascalpel-dsh-dsh-1','python','-c','import sys,yaml,json; print(json.dumps(yaml.safe_load(sys.stdin)["spring"]["datasource"]))'], input=config)
from urllib.parse import urlparse
settings = json.loads(parsed); address = urlparse(settings['url'].removeprefix('jdbc:'))
env = dict(os.environ, PGHOST=address.hostname, PGPORT=str(address.port or 5432), PGDATABASE=address.path.lstrip('/'), PGUSER=settings['username'], PGPASSWORD=settings['password'])
folder = root / '.local/dsh-phase-three/legacy-backup'; folder.mkdir(parents=True, exist_ok=True)
base = ['docker','run','--rm','-i','--network','bridge','--mount',f'type=bind,src={folder},dst=/backup']
for key in ['PGHOST','PGPORT','PGDATABASE','PGUSER','PGPASSWORD']: base += ['-e',key]
base += ['postgres:16.14']
def run(args, sql=None):
    result = subprocess.run(base+args, input=sql, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        print('Database client failed:',result.stderr.decode().replace(settings['password'],'[redacted]'),file=sys.stderr);sys.exit(1)
    return result.stdout
query = "select current_database(), current_schema(), current_setting('server_version');"
identity=run(['psql','-X','-A','-t','-v','ON_ERROR_STOP=1','-c',query]).decode().strip()
print('Database target:', identity)
metadata_path=folder/'metadata.json'; dump=folder/'legacy-assistant.dump'
mode=sys.argv[1] if len(sys.argv)>1 else 'inspect'
if mode=='backup':
    if metadata_path.exists(): raise SystemExit('Backup already exists; will not overwrite.')
    counts={}
    for table in tables: counts[table]=int(run(['psql','-X','-A','-t','-v','ON_ERROR_STOP=1','-c',f'SELECT count(*) FROM public.{table}']).decode())
    args=['pg_dump','--format=custom','--no-owner','--no-acl','--file=/backup/legacy-assistant.dump']
    for table in tables: args += ['--table',f'public.{table}']
    run(args); os.chmod(dump,0o600)
    run(['pg_restore','--file=/dev/null','/backup/legacy-assistant.dump'])
    listing=run(['pg_restore','--list','/backup/legacy-assistant.dump']).decode()
    for table in tables:
        if f'TABLE DATA public {table} ' not in listing: raise SystemExit('Backup does not contain every expected table.')
    metadata={'target':identity,'tables':counts,'sha256':hashlib.sha256(dump.read_bytes()).hexdigest(),'createdAt':datetime.datetime.now(datetime.timezone.utc).isoformat()}
    metadata_path.write_text(json.dumps(metadata,indent=2));os.chmod(metadata_path,0o600)
    print('Verified backup:',metadata['tables'])
elif mode=='drop':
    metadata=json.loads(metadata_path.read_text())
    if metadata['target']!=identity or metadata['sha256']!=hashlib.sha256(dump.read_bytes()).hexdigest(): raise SystemExit('Backup target or checksum mismatch.')
    run(['pg_restore','--list','/backup/legacy-assistant.dump'])
    run(['pg_restore','--file=/dev/null','/backup/legacy-assistant.dump'])
    # RESTRICT is intentional. Any unexpected dependency rolls back the whole transaction.
    sql='BEGIN; SET LOCAL lock_timeout=\'5s\'; DROP TABLE '+', '.join('public.'+t for t in tables)+' RESTRICT; COMMIT;'
    run(['psql','-X','-v','ON_ERROR_STOP=1'],sql.encode()); print('Removed exactly the six retired tables.')
elif mode=='inspect':
    print(run(['psql','-X','-A','-t','-c',"SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename IN ("+','.join("'"+t+"'" for t in tables)+") ORDER BY tablename"]).decode())
else: raise SystemExit('Use inspect, backup or drop')
