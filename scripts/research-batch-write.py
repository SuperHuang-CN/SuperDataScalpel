"""Run bounded write-strategy research on the authorized 102 test containers.

Run locally with --remote to execute this same program over SSH. No credentials
are printed or stored; only containers' existing environment is used. All DDL
is restricted to this run's randomly named research tables/schema.
"""
import argparse
import concurrent.futures
import json
import pathlib
import subprocess
import time
import uuid
import shlex

SSH = None


def command(args, sql=None, timeout=300):
    if SSH is not None:
        stdin, stdout, stderr = SSH.exec_command(' '.join(shlex.quote(a) for a in args), timeout=timeout)
        if sql:
            stdin.write(sql)
        stdin.channel.shutdown_write()
        output = stdout.read().decode('utf-8', errors='replace')
        error = stderr.read().decode('utf-8', errors='replace')
        if stdout.channel.recv_exit_status():
            raise RuntimeError(error[-1500:])
        return output.strip()
    result = subprocess.run(args, input=sql, encoding='utf-8', errors='replace', capture_output=True, timeout=timeout)
    if result.returncode:
        raise RuntimeError(result.stderr[-1500:])
    return result.stdout.strip()


def environment(container):
    info = json.loads(command(['docker', 'inspect', container]))[0]
    return dict(value.split('=', 1) for value in info['Config']['Env'] if '=' in value)


def main():
    global SSH
    parser = argparse.ArgumentParser()
    parser.add_argument('--remote', action='store_true')
    parser.add_argument('--rows', type=int, default=1000000)
    args = parser.parse_args()
    if args.remote:
        import paramiko
        SSH = paramiko.SSHClient()
        SSH.load_system_host_keys()
        SSH.connect('192.168.5.102', username='root', timeout=10)
    prefix = 'ds_write_research_' + uuid.uuid4().hex[:10]
    results = {'namespace': prefix, 'rows': args.rows, 'measurements': []}
    def record(name, action):
        started = time.monotonic()
        output = action()
        result = {'name': name, 'seconds': round(time.monotonic() - started, 3), 'result': output}
        results['measurements'].append(result)
        print(json.dumps(result), flush=True)
        return output

    pg = 'datascalpel-test-postgresql'
    env = environment(pg)
    def psql(sql):
        return command(['docker', 'exec', '-i', pg, 'psql', '-X', '-v', 'ON_ERROR_STOP=1',
                        '-U', env['POSTGRES_USER'], '-d', env.get('POSTGRES_DB', env['POSTGRES_USER']),
                        '-At'], sql)
    record('postgresql.version', lambda: psql('select version();'))
    record('postgresql.source_inventory', lambda: psql("select table_schema,table_name from information_schema.tables where table_schema not in ('pg_catalog','information_schema') and table_type='BASE TABLE' order by 1,2 limit 40;"))
    psql('CREATE SCHEMA ' + prefix)
    try:
        psql(f'CREATE TABLE {prefix}.source (id bigint primary key, region int, payload varchar(160));')
        record('postgresql.generate', lambda: psql(f"INSERT INTO {prefix}.source SELECT i, i%100, repeat(md5(i::text),4) FROM generate_series(1,{args.rows}) i;"))
        record('postgresql.stage_load', lambda: psql(f'CREATE TABLE {prefix}.stage AS SELECT * FROM {prefix}.source;'))
        record('postgresql.target_load', lambda: psql(f'CREATE TABLE {prefix}.target (LIKE {prefix}.source INCLUDING ALL); INSERT INTO {prefix}.target SELECT * FROM {prefix}.source;'))
        record('postgresql.overwrite_commit', lambda: psql(f'BEGIN; DELETE FROM {prefix}.target; INSERT INTO {prefix}.target SELECT * FROM {prefix}.stage; COMMIT;'))
        record('postgresql.conditional_1_percent', lambda: psql(f'BEGIN; DELETE FROM {prefix}.target WHERE region=1; INSERT INTO {prefix}.target SELECT * FROM {prefix}.stage WHERE region=1; COMMIT;'))
        record('postgresql.set_upsert', lambda: psql(f'BEGIN; INSERT INTO {prefix}.target SELECT * FROM {prefix}.stage ON CONFLICT(id) DO UPDATE SET payload=excluded.payload; COMMIT;'))
        try:
            psql(f'BEGIN; DELETE FROM {prefix}.target; INSERT INTO {prefix}.target VALUES(1,1,\'a\'),(1,1,\'b\'); COMMIT;')
            raise AssertionError('Expected constraint failure')
        except RuntimeError:
            record('postgresql.rollback_count', lambda: psql(f'SELECT count(*) FROM {prefix}.target;'))
        with concurrent.futures.ThreadPoolExecutor() as pool:
            pending = pool.submit(psql, f'BEGIN; DELETE FROM {prefix}.target; SELECT pg_sleep(3); ROLLBACK;')
            time.sleep(1)
            record('postgresql.concurrent_read_during_delete', lambda: psql(f'SELECT count(*) FROM {prefix}.target;'))
            pending.result()
        record('postgresql.shadow_load_index', lambda: psql(f'CREATE TABLE {prefix}.shadow (LIKE {prefix}.source INCLUDING ALL); INSERT INTO {prefix}.shadow SELECT * FROM {prefix}.source;'))
        psql(f'CREATE VIEW {prefix}.dependent AS SELECT * FROM {prefix}.target;')
        record('postgresql.shadow_swap', lambda: psql(f'BEGIN; ALTER TABLE {prefix}.target RENAME TO old_target; ALTER TABLE {prefix}.shadow RENAME TO target; COMMIT;'))
        record('postgresql.view_still_old_object', lambda: psql(f"SELECT pg_get_viewdef('{prefix}.dependent'::regclass);"))
    finally:
        # Exact random namespace created above, never a business schema.
        psql('DROP SCHEMA ' + prefix + ' CASCADE;')

    mysql = 'datascalpel-test-mysql'
    menv = environment(mysql)
    def mysqlsql(sql):
        return command(['docker','exec','-i','-e','MYSQL_PWD=' + menv['MYSQL_ROOT_PASSWORD'], mysql,
                        'mysql','-uroot','--batch','--skip-column-names', menv.get('MYSQL_DATABASE','test')], sql)
    record('mysql.version', lambda: mysqlsql('SELECT VERSION();'))
    src,stage,target = [prefix + '_' + suffix for suffix in ['source','stage','target']]
    try:
        mysqlsql(f'CREATE TABLE {src}(id bigint primary key, region int, payload varchar(160)) ENGINE=InnoDB;')
        # Generate with six ten-row cross joins, no recursion-depth changes.
        digits = '(SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9)'
        seq = '+'.join(f'{10**i}*d{i}.n' for i in range(6))
        joins = ' CROSS JOIN '.join(digits + f' d{i}' for i in range(6))
        record('mysql.generate', lambda: mysqlsql(f'INSERT INTO {src} SELECT {seq},d0.n,REPEAT(MD5({seq}),4) FROM {joins} WHERE {seq}<{args.rows};'))
        record('mysql.stage_load', lambda: mysqlsql(f'CREATE TABLE {stage} ENGINE=InnoDB AS SELECT * FROM {src};'))
        record('mysql.target_load', lambda: mysqlsql(f'CREATE TABLE {target} LIKE {src}; INSERT INTO {target} SELECT * FROM {src};'))
        record('mysql.overwrite_commit', lambda: mysqlsql(f'START TRANSACTION; DELETE FROM {target}; INSERT INTO {target} SELECT * FROM {stage}; COMMIT;'))
        record('mysql.set_upsert', lambda: mysqlsql(f'START TRANSACTION; INSERT INTO {target} SELECT * FROM {stage} WHERE true ON DUPLICATE KEY UPDATE payload=VALUES(payload); COMMIT;'))
        try:
            mysqlsql(f'START TRANSACTION; DELETE FROM {target}; INSERT INTO {target} VALUES(1,1,\'a\'),(1,1,\'b\'); COMMIT;')
            raise AssertionError('Expected constraint failure')
        except RuntimeError:
            record('mysql.rollback_count', lambda: mysqlsql(f'SELECT COUNT(*) FROM {target};'))
        with concurrent.futures.ThreadPoolExecutor() as pool:
            pending = pool.submit(mysqlsql, f'START TRANSACTION; DELETE FROM {target}; SELECT SLEEP(3); ROLLBACK;')
            time.sleep(1)
            record('mysql.concurrent_read_during_delete', lambda: mysqlsql(f'SELECT COUNT(*) FROM {target};'))
            pending.result()
    finally:
        mysqlsql('DROP TABLE IF EXISTS ' + ','.join([src,stage,target]))
    print(json.dumps(results), flush=True)


if __name__ == '__main__':
    main()
