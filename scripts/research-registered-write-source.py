"""Read-only inventory of registered sources; never prints connection credentials or data values."""
import importlib.util
import json
import pathlib
import paramiko
import yaml

root = pathlib.Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('research', root/'scripts/research-batch-write.py')
r = importlib.util.module_from_spec(spec)
spec.loader.exec_module(r)
config = yaml.safe_load((root/'data-scalpel-admin/src/main/resources/config/application-local.yml').read_text(encoding='utf-8'))
r.SSH = paramiko.SSHClient()
r.SSH.load_system_host_keys()
r.SSH.connect('192.168.5.102', username='root', timeout=10)
d = config['spring']['datasource']
def management(sql):
    return r.command(['docker','exec','-i','-e','PGPASSWORD='+d['password'],
        'datascalpel-test-postgresql','psql','-X','-v','ON_ERROR_STOP=1','-h','192.168.5.1',
        '-p','5432','-U',d['username'],'-d',d['url'].rsplit('/',1)[1],'-At'], sql)

if __name__ == '__main__':
    try:
        print(management("SELECT table_schema,table_name FROM information_schema.tables WHERE table_name IN ('ds_data_source','ds_data_model');"))
        print(management("SELECT m.code,m.physical_table_name,m.schema_name,d.database_type,d.host,d.port,d.database_name FROM public.ds_data_model m JOIN public.ds_data_source d ON m.storage_data_source_id=d.id ORDER BY m.code;"))
    finally:
        r.SSH.close()
