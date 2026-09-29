"""Deploy verified local Runner/Dispatcher artifacts to the existing authorized 102 instance.

No database, Topic, target key or Kubernetes image changes. Old release and config remain recoverable.
"""
import hashlib
import pathlib
import shlex
import paramiko
import yaml
import zipfile
import io

root=pathlib.Path(__file__).resolve().parents[1]
release='/data/datascalpel-compute-engine/releases/20260929-atomic-write-r3'
compose_dir='/data/datascalpel-compute-engine/compose'
c=paramiko.SSHClient(); c.load_system_host_keys(); c.connect('192.168.5.102',username='root',timeout=10)
s=c.open_sftp()
def command(value):
    _,out,err=c.exec_command(value)
    result=out.read().decode(); error=err.read().decode()
    if out.channel.recv_exit_status()!=0: raise RuntimeError(error[:1000])
    return result

running=command("docker ps --format '{{.Names}}'").splitlines()
assert not any(n.startswith('ds-') for n in running), 'Active Runner: deployment postponed'
try: s.mkdir(release)
except OSError: pass
for filename,module,artifact in [
    ('runner-local.jar','data-scalpel-task-engine','data-scalpel-task-engine-0.1.0-SNAPSHOT-runner-local.jar'),
    ('runner-cluster.jar','data-scalpel-task-engine','data-scalpel-task-engine-0.1.0-SNAPSHOT-runner-cluster.jar'),
    ('dispatcher.jar','data-scalpel-task-dispatcher','data-scalpel-task-dispatcher-0.1.0-SNAPSHOT.jar')]:
    local=root/module/'target'/artifact
    with zipfile.ZipFile(local) as jar:
        if filename=='dispatcher.jar':
            contract=next(n for n in jar.namelist() if n.startswith('BOOT-INF/lib/data-scalpel-contracts-'))
            with zipfile.ZipFile(io.BytesIO(jar.read(contract))) as contracts:
                assert 'cn/superhuang/data/scalpel/contract/task/BatchWriteOptions.class' in contracts.namelist(), 'Stale Dispatcher contracts'
        else:
            assert 'cn/superhuang/datascalpel/taskengine/runner/BatchJdbcWriter.class' in jar.namelist(), 'Stale Runner: use task-engine-full-package'
            strategy = jar.read('cn/superhuang/data/scalpel/dialect/runtime/JdbcBatchWriteStrategies$Base.class')
            assert b'getExportedKeys' in strategy, 'Stale Runner: missing foreign-key overwrite protection'
    digest=hashlib.file_digest(local.open('rb'),'sha256').hexdigest()
    remote=release+'/'+filename
    try: existing=command('sha256sum '+shlex.quote(remote)).split()[0]
    except RuntimeError: existing=None
    if existing!=digest:
        assert existing is None, 'Refuse to overwrite an existing different release'
        progress=[-1]
        def report(done,total):
            step=int(done*10/total)
            if step!=progress[0]: print(filename,step*10,'%',flush=True); progress[0]=step
        s.put(str(local),remote+'.upload',callback=report)
        assert command('sha256sum '+shlex.quote(remote+'.upload')).split()[0]==digest
        s.rename(remote+'.upload',remote)
    print('VERIFIED_ARTIFACT',filename,digest,flush=True)

config_path=compose_dir+'/application-instance.yml'
compose_path=compose_dir+'/compose.shared.yaml'
with s.open(config_path) as f: config_text=f.read().decode()
with s.open(compose_path) as f: compose_text=f.read().decode()
config=yaml.safe_load(config_text); compose=yaml.safe_load(compose_text)
old_runner=config['data-scalpel']['dispatcher']['targets']['local-docker-102']['local-docker']['runner-jar']
old_dispatcher=compose['services']['datascalpel-compute-engine']['command'][-1]
assert config_text.count(old_runner)==1 and compose_text.count(old_dispatcher)==1
for filename,text in [('application-instance.before.yml',config_text),('compose.before.yaml',compose_text)]:
    backup=release+'/'+filename
    try: s.stat(backup)
    except OSError:
        with s.open(backup,'w') as f: f.write(text)
        s.chmod(backup,0o600)
with s.open(config_path,'w') as f: f.write(config_text.replace(old_runner,release+'/runner-local.jar'))
with s.open(compose_path,'w') as f: f.write(compose_text.replace(old_dispatcher,release+'/dispatcher.jar'))
s.chmod(config_path,0o600); s.chmod(compose_path,0o600)
# Validation only prints a success marker, never the expanded file (contains credentials).
try:
    command('cd '+shlex.quote(compose_dir)+' && docker compose -p datascalpel-compute-engine -f compose.shared.yaml config -q')
    print(command('cd '+shlex.quote(compose_dir)+' && docker compose -p datascalpel-compute-engine -f compose.shared.yaml up -d --no-deps --force-recreate datascalpel-compute-engine'),flush=True)
except Exception:
    with s.open(config_path,'w') as f: f.write(config_text)
    with s.open(compose_path,'w') as f: f.write(compose_text)
    command('cd '+shlex.quote(compose_dir)+' && docker compose -p datascalpel-compute-engine -f compose.shared.yaml up -d --no-deps --force-recreate datascalpel-compute-engine')
    raise
print('DEPLOYMENT_STARTED',release,flush=True)
s.close(); c.close()
