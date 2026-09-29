"""Run real Spark/JDBC tests against authorized 102 containers; secrets remain in process environment."""
import importlib.util
import json
import os
import pathlib
import subprocess
import paramiko
import requests

root=pathlib.Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('research',root/'scripts/research-batch-write.py')
r=importlib.util.module_from_spec(spec)
spec.loader.exec_module(r)
r.SSH=paramiko.SSHClient()
r.SSH.load_system_host_keys()
r.SSH.connect('192.168.5.102',username='root',timeout=10)
pg=r.environment('datascalpel-test-postgresql')
my=r.environment('datascalpel-test-mysql')
ora=r.environment('datascalpel-test-oracle')
ms=r.environment('datascalpel-test-sqlserver')
og=r.environment('datascalpel-test-opengauss')
databases=[
    dict(type='POSTGRESQL',driver='org.postgresql.Driver',url='jdbc:postgresql://192.168.5.102:15432/'+pg.get('POSTGRES_DB',pg['POSTGRES_USER']),user=pg['POSTGRES_USER'],password=pg['POSTGRES_PASSWORD'],schema='public'),
    dict(type='MYSQL',driver='com.mysql.cj.jdbc.Driver',url='jdbc:mysql://192.168.5.102:13306/'+my.get('MYSQL_DATABASE','test')+'?useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true',user='root',password=my['MYSQL_ROOT_PASSWORD'],catalog=my.get('MYSQL_DATABASE','test')),
    dict(type='ORACLE',driver='oracle.jdbc.OracleDriver',url='jdbc:oracle:thin:@//192.168.5.102:11521/FREEPDB1',user='system',password=ora.get('ORACLE_PWD',''),schema='SYSTEM'),
    dict(type='SQL_SERVER',driver='com.microsoft.sqlserver.jdbc.SQLServerDriver',url='jdbc:sqlserver://192.168.5.102:11433;databaseName=master;encrypt=false',user='sa',password=ms.get('MSSQL_SA_PASSWORD',ms.get('SA_PASSWORD','')),schema='dbo'),
    dict(type='OPENGAUSS',driver='org.opengauss.Driver',url='jdbc:opengauss://192.168.5.102:15435/postgres',user='gaussdb',password=og.get('GS_PASSWORD',''),schema='public')]
r.SSH.close()
env=os.environ.copy()
env['JAVA_HOME']=r'F:\language\java\jdk\jdk-21.0.2'
env['DATASCALPEL_WRITE_IT_DATABASES']=json.dumps(databases)
tests=os.environ.get('DATASCALPEL_WRITE_IT_TESTS','BatchJdbcWriterIntegrationTest')
if 'RegisteredDataWriteIntegrationTest' in tests:
    session=requests.Session()
    login=session.post('http://localhost:8080/api/v1/auth/login',json={'username':os.environ.get('DATASCALPEL_ADMIN_USERNAME','admin'),
        'password':os.environ.get('DATASCALPEL_ADMIN_PASSWORD','admin123456')},timeout=10)
    login.raise_for_status()
    session.headers['Authorization']='Bearer '+login.json()['accessToken']
    models=session.get('http://localhost:8080/api/v1/models?size=100',timeout=20).json()['content']
    selected=next(m for m in models if m['code']==os.environ.get('DATASCALPEL_WRITE_IT_MODEL','meter_readings'))
    response=session.get('http://localhost:8080/api/v1/models/'+selected['id'],timeout=20)
    response.raise_for_status()
    env['DATASCALPEL_WRITE_IT_REGISTERED_MODEL']=json.dumps(response.json())
raise SystemExit(subprocess.call([str(root/'mvnw.cmd'),'-pl','data-scalpel-task-engine','-am','test','-Dtest='+tests,'-Dsurefire.failIfNoSpecifiedTests=false','-q'],cwd=root,env=env))
