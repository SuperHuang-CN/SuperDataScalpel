\set ON_ERROR_STOP on

SELECT format(
  'CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT',
  'datascalpel_test69', :'admin_password'
)
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'datascalpel_test69')
\gexec
ALTER ROLE datascalpel_test69 PASSWORD :'admin_password';

SELECT format(
  'CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT',
  'datascalpel_engine_test69', :'engine_password'
)
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'datascalpel_engine_test69')
\gexec
ALTER ROLE datascalpel_engine_test69 PASSWORD :'engine_password';

SELECT format(
  'CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT',
  'kong_test69', :'kong_password'
)
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'kong_test69')
\gexec
ALTER ROLE kong_test69 PASSWORD :'kong_password';

SELECT format('CREATE DATABASE %I OWNER %I', 'datascalpel_test69', 'datascalpel_test69')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'datascalpel_test69')
\gexec
SELECT format('CREATE DATABASE %I OWNER %I', 'datascalpel_engine_test69', 'datascalpel_engine_test69')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'datascalpel_engine_test69')
\gexec
SELECT format('CREATE DATABASE %I OWNER %I', 'kong_test69', 'kong_test69')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'kong_test69')
\gexec

REVOKE ALL ON DATABASE datascalpel_test69 FROM PUBLIC;
REVOKE ALL ON DATABASE datascalpel_engine_test69 FROM PUBLIC;
REVOKE ALL ON DATABASE kong_test69 FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE datascalpel_test69 TO datascalpel_test69;
GRANT CONNECT, TEMPORARY ON DATABASE datascalpel_engine_test69 TO datascalpel_engine_test69;
GRANT CONNECT, TEMPORARY ON DATABASE kong_test69 TO kong_test69;
