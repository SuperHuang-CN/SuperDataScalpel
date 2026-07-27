-- SPARK_CANVAS Dispatcher database initialization for PostgreSQL.
--
-- Run this script against the Dispatcher database, not the Admin database.
-- The script creates only Dispatcher-owned tables and can be executed repeatedly.

BEGIN;

CREATE SCHEMA IF NOT EXISTS dispatcher;
SET LOCAL search_path TO dispatcher;
SET LOCAL lock_timeout TO '10s';
SET LOCAL statement_timeout TO '5min';

CREATE TABLE IF NOT EXISTS dispatcher_identity (
    identity_key varchar(32) NOT NULL,
    instance_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT dispatcher_identity_pkey PRIMARY KEY (identity_key)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_dispatcher_identity_instance
    ON dispatcher_identity (instance_id);

CREATE TABLE IF NOT EXISTS dispatcher_registration (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    engine_id uuid NOT NULL,
    dispatcher_instance_id uuid NOT NULL,
    protocol_version integer NOT NULL,
    config_revision bigint NOT NULL,
    backend_type varchar(32) NOT NULL,
    state varchar(32) NOT NULL,
    command_topic varchar(249) NOT NULL,
    runner_event_topic varchar(249) NOT NULL,
    admin_event_topic varchar(249) NOT NULL,
    max_queued_executions integer NOT NULL,
    max_concurrent_submissions integer NOT NULL,
    max_in_flight_applications integer NOT NULL,
    registered_at timestamptz,
    last_error varchar(1000),
    CONSTRAINT dispatcher_registration_pkey PRIMARY KEY (id),
    CONSTRAINT dispatcher_registration_backend_check
        CHECK (backend_type IN ('LOCAL_DOCKER', 'YARN', 'KUBERNETES')),
    CONSTRAINT dispatcher_registration_state_check
        CHECK (state IN ('INACTIVE', 'ACTIVE', 'DRAINING', 'ERROR'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_dispatcher_registration_engine
    ON dispatcher_registration (engine_id);

CREATE TABLE IF NOT EXISTS dispatcher_task_execution (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    engine_id uuid NOT NULL,
    execution_id uuid NOT NULL,
    run_id uuid NOT NULL,
    task_id uuid NOT NULL,
    attempt integer NOT NULL,
    task_type varchar(32) NOT NULL,
    definition_version integer NOT NULL,
    request_fingerprint varchar(64) NOT NULL,
    backend_type varchar(32) NOT NULL,
    state varchar(32) NOT NULL,
    external_execution_id varchar(300),
    tracking_url varchar(1000),
    manifest_key varchar(500) NOT NULL,
    manifest_sha256 varchar(64) NOT NULL,
    result_key varchar(500) NOT NULL,
    log_key varchar(500) NOT NULL,
    deadline_at timestamptz NOT NULL,
    cancel_requested boolean NOT NULL,
    event_sequence bigint NOT NULL,
    safe_error_code varchar(100),
    safe_error_message varchar(1000),
    affected_rows bigint,
    queued_at timestamptz NOT NULL,
    submission_started_at timestamptz,
    submitted_at timestamptz,
    started_at timestamptz,
    ended_at timestamptz,
    last_observed_at timestamptz,
    observation_failure_since timestamptz,
    result_awaiting_since timestamptz,
    log_artifact_stored boolean NOT NULL,
    external_cleanup_completed boolean NOT NULL,
    CONSTRAINT dispatcher_task_execution_pkey PRIMARY KEY (id),
    CONSTRAINT dispatcher_task_execution_task_type_check
        CHECK (task_type IN ('SPARK_CANVAS')),
    CONSTRAINT dispatcher_task_execution_backend_check
        CHECK (backend_type IN ('LOCAL_DOCKER', 'YARN', 'KUBERNETES')),
    CONSTRAINT dispatcher_task_execution_state_check
        CHECK (state IN (
            'QUEUED', 'SUBMITTING', 'SUBMITTED', 'RUNNING',
            'CANCEL_REQUESTED', 'SUCCESS', 'FAILED', 'TIMED_OUT',
            'CANCELLED', 'LOST'
        ))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_dispatcher_execution_attempt
    ON dispatcher_task_execution (execution_id, attempt);

CREATE INDEX IF NOT EXISTS idx_dispatcher_execution_queue
    ON dispatcher_task_execution (state, queued_at);

CREATE INDEX IF NOT EXISTS idx_dispatcher_execution_run
    ON dispatcher_task_execution (run_id);

CREATE TABLE IF NOT EXISTS dispatcher_message_inbox (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    message_id uuid NOT NULL,
    message_type varchar(64) NOT NULL,
    topic varchar(249) NOT NULL,
    topic_partition integer NOT NULL,
    topic_offset bigint NOT NULL,
    execution_id uuid NOT NULL,
    received_at timestamptz NOT NULL,
    processed_at timestamptz,
    state varchar(32) NOT NULL,
    safe_error varchar(1000),
    CONSTRAINT dispatcher_message_inbox_pkey PRIMARY KEY (id),
    CONSTRAINT dispatcher_message_inbox_state_check
        CHECK (state IN ('RECEIVED', 'PROCESSED', 'REJECTED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_dispatcher_inbox_message
    ON dispatcher_message_inbox (message_id);

CREATE INDEX IF NOT EXISTS idx_dispatcher_inbox_execution
    ON dispatcher_message_inbox (execution_id, received_at);

CREATE TABLE IF NOT EXISTS dispatcher_event_outbox (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    message_id uuid NOT NULL,
    execution_id uuid NOT NULL,
    engine_id uuid NOT NULL,
    sequence bigint NOT NULL,
    topic varchar(249) NOT NULL,
    message_type varchar(64) NOT NULL,
    payload text NOT NULL,
    state varchar(32) NOT NULL,
    attempts integer NOT NULL,
    next_attempt_at timestamptz NOT NULL,
    published_at timestamptz,
    claimed_at timestamptz,
    last_error varchar(1000),
    CONSTRAINT dispatcher_event_outbox_pkey PRIMARY KEY (id),
    CONSTRAINT dispatcher_event_outbox_state_check
        CHECK (state IN ('PENDING', 'PUBLISHING', 'PUBLISHED', 'FAILED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_dispatcher_outbox_message
    ON dispatcher_event_outbox (message_id);

CREATE INDEX IF NOT EXISTS idx_dispatcher_outbox_due
    ON dispatcher_event_outbox (state, next_attempt_at);

COMMIT;

SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'dispatcher'
  AND table_name IN (
      'dispatcher_identity', 'dispatcher_registration',
      'dispatcher_task_execution', 'dispatcher_message_inbox',
      'dispatcher_event_outbox'
  )
ORDER BY table_name;
