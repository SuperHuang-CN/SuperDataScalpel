"""One-off 2026-09-29 K8s acceptance deployment; existing 102/103 only.

Requires SSH trust/credentials supplied by the operator. Preserves the previous
image/configuration; never changes target keys, Topics or user workloads.
"""
import os
import shlex
import paramiko
import yaml

release = '/data/datascalpel-compute-engine/releases/20260929-atomic-write-r3'
tag = 'docker.io/library/datascalpel-spark-runner:k8s-20260929-atomic-r3'
archive = release + '/k8s-20260929-atomic-r3.tar.gz'
remote_archive = '/var/tmp/datascalpel-k8s-20260929-atomic-r3.tar.gz'
expected = 'cc3ebcd007c6d85fdcb42b91f55d4e31e1ee290180dd23de78ac3dc1deb8a3b0'

def connect(host):
    client = paramiko.SSHClient()
    client.load_system_host_keys()
    # Optional explicit trust file supplied after first-connection verification.
    if os.environ.get('DATASCALPEL_DEPLOY_KNOWN_HOSTS'):
        client.load_host_keys(os.environ['DATASCALPEL_DEPLOY_KNOWN_HOSTS'])
    client.connect(host, username='root', password=os.environ.get('DATASCALPEL_103_SSH_PASSWORD') if host.endswith('103') else None, timeout=15)
    return client

def command(client, value, input_text=None):
    stdin, out, err = client.exec_command(value)
    if input_text is not None:
        stdin.write(input_text)
    stdin.channel.shutdown_write()
    result = out.read().decode()
    error = err.read().decode()
    if out.channel.recv_exit_status():
        raise RuntimeError(error[:1500] or result[-1500:])
    return result

c = connect('192.168.5.102')
k = connect('192.168.5.103')
assert command(c, 'sha256sum ' + release + '/runner-cluster.jar').split()[0] == expected
assert not command(k, 'kubectl get pods -n datascalpel-test -o name').strip(), 'Cluster has active resources'
assert not any(n.startswith('ds-') for n in command(c, "docker ps --format '{{.Names}}'").splitlines()), 'Local Runner active'
base = 'datascalpel-spark-runner:k8s-20260927-r3'
assert command(c, "docker image inspect " + base + " --format '{{.Id}}'").strip() == 'sha256:4381fe46b279f4d21782206248316314b23875dc24e118d456a0e896365911c8'
resume = os.environ.get('DATASCALPEL_DEPLOY_RESUME_TRANSFER') == 'true'
if not resume:
    print('BUILD_START', flush=True)
    dockerfile = f'FROM {base}\nCOPY --chown=185:0 runner-cluster.jar /opt/datascalpel/task-runner-cluster.jar\nUSER 185\n'
    command(c, f'docker build -t {tag} -f - {release}', dockerfile)
actual = command(c, f'docker run --rm --entrypoint sha256sum {tag} /opt/datascalpel/task-runner-cluster.jar').split()[0]
assert actual == expected
print('IMAGE_JAR_VERIFIED', actual, flush=True)
if not resume:
    command(c, 'bash -o pipefail -c ' + shlex.quote(f'docker save {tag} | gzip -1 > {archive}'))
source = c.open_sftp()
dest = k.open_sftp()
total = source.stat(archive).st_size
expected_archive_hash = command(c, 'sha256sum ' + archive).split()[0]
offset = dest.stat(remote_archive).st_size if resume else 0
assert offset <= total
with source.open(archive, 'rb') as src, dest.open(remote_archive, 'ab' if resume else 'wb') as dst:
    dst.set_pipelined(True)
    src.seek(offset)
    src.prefetch(total, max_concurrent_requests=32)
    done = offset
    step = -1
    while chunk := src.read(4 * 1024 * 1024):
        dst.write(chunk)
        done += len(chunk)
        current = done * 10 // total
        if current != step:
            print('TRANSFER', current * 10, '%', flush=True)
            step = current
assert command(k, 'sha256sum ' + remote_archive).split()[0] == expected_archive_hash
print('ARCHIVE_VERIFIED', expected_archive_hash, flush=True)
command(k, 'bash -o pipefail -c ' + shlex.quote('gzip -dc ' + remote_archive + ' | ctr -n k8s.io images import -'))
line = next(line for line in command(k, 'ctr -n k8s.io images list').splitlines() if line.split()[0] == tag)
manifest = line.split()[2]
assert manifest.startswith('sha256:') and len(manifest) == 71
immutable = tag.split(':')[0] + '@' + manifest
command(k, f'ctr -n k8s.io images tag {tag} {immutable}')
print('IMPORTED_IMAGE', immutable, flush=True)
config_path = '/data/datascalpel-compute-engine/compose/application-instance.yml'
with source.open(config_path) as f:
    original = f.read().decode()
config = yaml.safe_load(original)
old = config['data-scalpel']['dispatcher']['targets']['k8s-103']['kubernetes']['image']
assert original.count(old) == 1
backup = release + '/application-instance.before-k8s.yml'
try:
    source.stat(backup)
    raise RuntimeError('Backup already exists; inspect deployment before rerun')
except FileNotFoundError:
    pass
with source.open(backup, 'w') as f:
    f.write(original)
source.chmod(backup, 0o600)
with source.open(config_path, 'w') as f:
    f.write(original.replace(old, immutable))
source.chmod(config_path, 0o600)
compose = 'cd /data/datascalpel-compute-engine/compose && docker compose -p datascalpel-compute-engine -f compose.shared.yaml '
try:
    command(c, compose + 'config -q')
    command(c, compose + 'up -d --no-deps --force-recreate datascalpel-compute-engine')
except Exception:
    with source.open(config_path, 'w') as f:
        f.write(original)
    command(c, compose + 'up -d --no-deps --force-recreate datascalpel-compute-engine')
    raise
print('DEPLOYED', immutable, flush=True)
source.close()
dest.close()
c.close()
k.close()
