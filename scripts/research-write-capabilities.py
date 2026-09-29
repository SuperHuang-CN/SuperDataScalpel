"""Native SQL capability probes; only per-run owned tables are changed."""
import importlib.util
import json
import pathlib
import uuid
import paramiko

spec = importlib.util.spec_from_file_location('research', pathlib.Path(__file__).with_name('research-batch-write.py'))
r = importlib.util.module_from_spec(spec)
spec.loader.exec_module(r)
r.SSH = paramiko.SSHClient()
r.SSH.load_system_host_keys()
r.SSH.connect('192.168.5.102', username='root', timeout=10)
name = 'dswr_' + uuid.uuid4().hex[:10]

def report(db, action):
    try:
        print(json.dumps({'db':db,'result':action()}), flush=True)
    except Exception as error:
        print(json.dumps({'db':db,'error':str(error)}), flush=True)

def oracle():
    sql = f'''whenever sqlerror exit failure rollback
set heading off feedback on
alter session set container=FREEPDB1;
select banner from v$version;
create table {name}(id number primary key, payload varchar2(40));
insert into {name} values(1,'old');
commit;
create table {name}_s as select id,payload,cast(null as varchar2(36)) ds_attempt from {name} where 1=0;
insert into {name}_s values(1,'new','winner');
insert into {name}_s values(2,'new','winner');
commit;
merge into {name} t using (select id,payload from {name}_s) s on (t.id=s.id) when matched then update set t.payload=s.payload when not matched then insert(id,payload) values(s.id,s.payload);
rollback;
select count(*),min(payload) from {name};
delete from {name};
insert into {name}(id,payload) select id,payload from {name}_s;
rollback;
select count(*),min(payload) from {name};
drop table {name}_s purge;
drop table {name} purge;
exit;
'''
    return r.command(['docker','exec','-i','-u','oracle','datascalpel-test-oracle','sqlplus','-s','/ as sysdba'],sql)

def sqlserver():
    c='datascalpel-test-sqlserver'
    e=r.environment(c)
    sql=f'''SET NOCOUNT ON;
SELECT @@VERSION;
CREATE TABLE dbo.{name}(id bigint primary key,payload varchar(40));
INSERT INTO dbo.{name} VALUES(1,'old');
SELECT id,payload,CAST(NULL AS varchar(36)) ds_attempt INTO dbo.{name}_s FROM dbo.{name} WHERE 1=0;
INSERT INTO dbo.{name}_s VALUES(1,'new','winner'),(2,'new','winner');
BEGIN TRANSACTION;
MERGE INTO dbo.{name} WITH(HOLDLOCK) t USING (SELECT id,payload FROM dbo.{name}_s) s ON(t.id=s.id) WHEN MATCHED THEN UPDATE SET payload=s.payload WHEN NOT MATCHED THEN INSERT(id,payload) VALUES(s.id,s.payload);
ROLLBACK;
SELECT count(*),min(payload) FROM dbo.{name};
BEGIN TRANSACTION;
DELETE FROM dbo.{name};
INSERT INTO dbo.{name}(id,payload) SELECT id,payload FROM dbo.{name}_s;
ROLLBACK;
SELECT count(*),min(payload) FROM dbo.{name};
DROP TABLE dbo.{name}_s;
DROP TABLE dbo.{name};
GO
'''
    return r.command(['docker','exec','-i','-e','SQLCMDPASSWORD='+e.get('MSSQL_SA_PASSWORD',e.get('SA_PASSWORD','')),c,'/opt/mssql-tools18/bin/sqlcmd','-S','localhost','-U','sa','-C','-b'],sql)

def gauss():
    c='datascalpel-test-opengauss'
    sql=f'''select version();
CREATE TABLE {name}(id bigint primary key,payload varchar(40));
INSERT INTO {name} VALUES(1,'old');
CREATE TABLE {name}_s AS SELECT id,payload,CAST(NULL AS varchar(36)) ds_attempt FROM {name} WHERE 1=0;
INSERT INTO {name}_s VALUES(1,'new','winner'),(2,'new','winner');
BEGIN;
MERGE INTO {name} t USING (SELECT id,payload FROM {name}_s) s ON(t.id=s.id) WHEN MATCHED THEN UPDATE SET payload=s.payload WHEN NOT MATCHED THEN INSERT(id,payload) VALUES(s.id,s.payload);
ROLLBACK;
SELECT count(*),min(payload) FROM {name};
BEGIN;
DELETE FROM {name};
INSERT INTO {name}(id,payload) SELECT id,payload FROM {name}_s;
ROLLBACK;
SELECT count(*),min(payload) FROM {name};
DROP TABLE {name}_s;
DROP TABLE {name};
'''
    return r.command(['docker','exec','-i','-u','omm','-e','LD_LIBRARY_PATH=/usr/local/opengauss/lib',c,'/usr/local/opengauss/bin/gsql','-d','postgres','-v','ON_ERROR_STOP=1','-At'],sql)

def clickhouse():
    c='datascalpel-test-clickhouse'
    e=r.environment(c)
    sql=f'''SELECT version();
CREATE DATABASE {name} ENGINE=Atomic;
CREATE TABLE {name}.target(id UInt64,payload String) ENGINE=MergeTree ORDER BY id;
INSERT INTO {name}.target VALUES(1,'old');
CREATE TABLE {name}.shadow AS {name}.target;
INSERT INTO {name}.shadow VALUES(2,'new');
EXCHANGE TABLES {name}.target AND {name}.shadow;
SELECT * FROM {name}.target;
DROP TABLE {name}.target;
DROP TABLE {name}.shadow;
DROP DATABASE {name};
'''
    cli=['docker','exec','-i',c,'clickhouse-client','--user',e.get('CLICKHOUSE_USER','default'),'--password',e.get('CLICKHOUSE_PASSWORD',''),'--multiquery']
    try:
        return r.command(cli,sql)
    finally:
        r.command(cli,f'DROP DATABASE IF EXISTS {name};')

for db, action in [('oracle',oracle),('sqlserver',sqlserver),('opengauss',gauss),('clickhouse',clickhouse)]:
    report(db,action)
r.SSH.close()
