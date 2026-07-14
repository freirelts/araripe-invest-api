ALTER TABLE ai_context_analyses
    ADD COLUMN thesis_id UUID REFERENCES position_theses (id),
    ADD COLUMN requested_by_user_id UUID REFERENCES app_users (id),
    ADD COLUMN input_hash VARCHAR(128),
    ADD COLUMN processing_status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED',
    ADD COLUMN started_at TIMESTAMPTZ,
    ADD COLUMN finished_at TIMESTAMPTZ;

UPDATE ai_context_analyses
SET input_hash = prompt_hash
WHERE input_hash IS NULL;

ALTER TABLE ai_context_analyses
    ALTER COLUMN input_hash SET NOT NULL,
    ALTER COLUMN processing_status DROP DEFAULT;

ALTER TABLE ai_context_analyses
    DROP CONSTRAINT IF EXISTS uk_ai_context_asset_date_prompt_model;

ALTER TABLE ai_context_analyses
    DROP CONSTRAINT IF EXISTS ck_ai_context_processing;

ALTER TABLE ai_context_analyses
    ADD CONSTRAINT ck_ai_context_processing
        CHECK (processing_status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'SKIPPED'));

CREATE UNIQUE INDEX uk_ai_context_thesis_date_model_input
    ON ai_context_analyses (thesis_id, reference_date, provider, model, prompt_version, input_hash)
    WHERE thesis_id IS NOT NULL;

ALTER TABLE job_runs
    DROP CONSTRAINT IF EXISTS ck_job_runs_name;

UPDATE job_runs
SET result_summary_json = jsonb_set(
        COALESCE(result_summary_json, '{}'::jsonb),
        '{legacyJobName}',
        '"AI_CONTEXT_ENRICHMENT"'::jsonb,
        true
    )
WHERE job_name = 'AI_CONTEXT_ENRICHMENT';

UPDATE job_runs
SET status = 'SKIPPED',
    completed_at = COALESCE(completed_at, NOW()),
    error_message = LEFT(
        COALESCE(error_message || ' ', '') ||
        'Legacy AI context enrichment job was removed from the operational flow.',
        1000
    )
WHERE job_name = 'AI_CONTEXT_ENRICHMENT'
  AND status = 'RUNNING';

UPDATE job_runs
SET job_name = 'DAILY_OPERATIONAL_FLOW'
WHERE job_name = 'AI_CONTEXT_ENRICHMENT';

ALTER TABLE job_runs
    ADD CONSTRAINT ck_job_runs_name CHECK (job_name IN (
        'DAILY_MARKET_DATA_COLLECTION',
        'FUNDAMENTAL_DATA_COLLECTION',
        'INDICATOR_CALCULATION',
        'FILTERS_AND_THESES',
        'RANKING',
        'PORTFOLIO_SCAN',
        'DAILY_NOTIFICATION_DIGEST',
        'DAILY_OPERATIONAL_FLOW'
    ));
