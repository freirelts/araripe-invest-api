CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE asset_watch_items (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users (id),
    asset_id UUID NOT NULL REFERENCES assets (id),
    source_position_id UUID REFERENCES customer_positions (id),
    accompanied_study_model_id UUID REFERENCES customer_position_theses (id),
    status VARCHAR(32) NOT NULL,
    user_lower_price_threshold NUMERIC(19, 6),
    user_upper_price_threshold NUMERIC(19, 6),
    notes VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    archived_at TIMESTAMPTZ,
    CONSTRAINT ck_asset_watch_items_status CHECK (status IN ('ACTIVE', 'PAUSED', 'ARCHIVED')),
    CONSTRAINT ck_asset_watch_items_thresholds CHECK (
        (user_lower_price_threshold IS NULL OR user_lower_price_threshold > 0)
        AND (user_upper_price_threshold IS NULL OR user_upper_price_threshold > 0)
    )
);

CREATE UNIQUE INDEX uk_asset_watch_items_active_user_asset
    ON asset_watch_items (user_id, asset_id)
    WHERE status = 'ACTIVE';

CREATE INDEX ix_asset_watch_items_user_status
    ON asset_watch_items (user_id, status, created_at DESC);

CREATE TABLE informational_alerts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users (id),
    asset_id UUID NOT NULL REFERENCES assets (id),
    watch_item_id UUID REFERENCES asset_watch_items (id),
    source_position_id UUID REFERENCES customer_positions (id),
    study_model_id UUID REFERENCES customer_position_theses (id),
    current_study_model_snapshot_id UUID REFERENCES position_theses (id),
    legacy_recommendation_id UUID REFERENCES position_recommendations (id),
    reference_date DATE NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    title VARCHAR(160) NOT NULL,
    summary VARCHAR(2000) NOT NULL,
    evidence_json JSONB NOT NULL,
    rule_version VARCHAR(40) NOT NULL,
    source VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    read_at TIMESTAMPTZ,
    CONSTRAINT ck_informational_alerts_event_type CHECK (event_type IN (
        'PRICE_THRESHOLD_REACHED',
        'DATA_UPDATED',
        'DATA_STALE',
        'INDICATOR_THRESHOLD_REACHED',
        'STUDY_ASSUMPTION_CHANGED',
        'QUALITY_DATA_BLOCKED'
    )),
    CONSTRAINT ck_informational_alerts_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE UNIQUE INDEX uk_informational_alerts_idempotency
    ON informational_alerts (user_id, asset_id, source_position_id, reference_date, event_type, rule_version, source);

CREATE UNIQUE INDEX uk_informational_alerts_legacy_recommendation
    ON informational_alerts (legacy_recommendation_id)
    WHERE legacy_recommendation_id IS NOT NULL;

CREATE INDEX ix_informational_alerts_user_reference_date
    ON informational_alerts (user_id, reference_date DESC, created_at DESC);

INSERT INTO asset_watch_items (
    id,
    user_id,
    asset_id,
    source_position_id,
    accompanied_study_model_id,
    status,
    user_lower_price_threshold,
    user_upper_price_threshold,
    notes,
    created_at,
    updated_at
)
SELECT DISTINCT ON (position.user_id, position.asset_id)
    gen_random_uuid(),
    position.user_id,
    position.asset_id,
    position.id,
    study_model.id,
    'ACTIVE',
    position.stop_price,
    position.target_price,
    position.notes,
    position.created_at,
    position.updated_at
FROM customer_positions position
LEFT JOIN LATERAL (
    SELECT association.id
    FROM customer_position_theses association
    WHERE association.position_id = position.id
      AND association.status = 'ACTIVE'
    ORDER BY association.created_at DESC
    LIMIT 1
) study_model ON true
WHERE position.status = 'OPEN'
ORDER BY position.user_id, position.asset_id, position.created_at DESC;

INSERT INTO informational_alerts (
    id,
    user_id,
    asset_id,
    watch_item_id,
    source_position_id,
    study_model_id,
    current_study_model_snapshot_id,
    legacy_recommendation_id,
    reference_date,
    event_type,
    severity,
    title,
    summary,
    evidence_json,
    rule_version,
    source,
    created_at
)
SELECT
    gen_random_uuid(),
    recommendation.user_id,
    recommendation.asset_id,
    watch_item.id,
    recommendation.position_id,
    recommendation.customer_position_thesis_id,
    recommendation.current_thesis_id,
    recommendation.id,
    recommendation.reference_date,
    CASE
        WHEN recommendation.recommendation_type IN ('REALIZAR_OBJETIVO', 'EXECUTAR_STOP') THEN 'PRICE_THRESHOLD_REACHED'
        WHEN recommendation.recommendation_type IN ('REAVALIAR', 'SAIR_DA_TESE') THEN 'STUDY_ASSUMPTION_CHANGED'
        WHEN recommendation.recommendation_type IN ('AUMENTAR_POSICAO', 'REDUZIR_POSICAO') THEN 'INDICATOR_THRESHOLD_REACHED'
        ELSE 'DATA_UPDATED'
    END,
    recommendation.severity,
    'Evento informativo derivado de registro legado',
    'Registro legado preservado para auditoria. Este alerta nao indica compra, venda, manutencao, aumento, reducao, alocacao ou encerramento de posicao.',
    jsonb_build_object(
        'legacyRecommendationId', recommendation.id,
        'legacyRecommendationType', recommendation.recommendation_type,
        'legacyFinalMessage', recommendation.final_message,
        'legacyDeterministicReasons', recommendation.deterministic_reason_json
    ),
    recommendation.rule_version,
    'legacy-position-recommendation',
    recommendation.created_at
FROM position_recommendations recommendation
LEFT JOIN asset_watch_items watch_item
    ON watch_item.user_id = recommendation.user_id
   AND watch_item.asset_id = recommendation.asset_id
   AND watch_item.status = 'ACTIVE'
ON CONFLICT DO NOTHING;

COMMENT ON TABLE position_recommendations IS
    'LEGACY/DEPRECATED: preserved for audit only. New neutral domain uses informational_alerts.';

COMMENT ON COLUMN position_recommendations.recommendation_type IS
    'LEGACY/DEPRECATED: operational recommendation type preserved for historical audit only.';
