CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    subscription_status VARCHAR(32) NOT NULL,
    subscription_plan VARCHAR(80),
    subscription_started_at TIMESTAMPTZ,
    subscription_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    last_login_at TIMESTAMPTZ,
    CONSTRAINT uk_app_users_email UNIQUE (email),
    CONSTRAINT ck_app_users_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'BLOCKED')),
    CONSTRAINT ck_app_users_subscription_status CHECK (subscription_status IN ('NONE', 'TRIALING', 'ACTIVE', 'PAST_DUE', 'CANCELLED', 'EXPIRED'))
);

CREATE TABLE user_roles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users (id),
    role VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_user_roles_user_role UNIQUE (user_id, role),
    CONSTRAINT ck_user_roles_role CHECK (role IN ('CUSTOMER', 'ADMIN'))
);

CREATE TABLE assets (
    id UUID PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    name VARCHAR(180) NOT NULL,
    sector VARCHAR(120),
    industry VARCHAR(120),
    market VARCHAR(32) NOT NULL,
    asset_type VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL,
    monitoring_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_assets_symbol UNIQUE (symbol),
    CONSTRAINT ck_assets_market CHECK (market IN ('B3')),
    CONSTRAINT ck_assets_asset_type CHECK (asset_type IN ('STOCK', 'FII', 'ETF', 'BDR'))
);

CREATE TABLE daily_candles (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL REFERENCES assets (id),
    trade_date DATE NOT NULL,
    open_price NUMERIC(19, 6) NOT NULL,
    high_price NUMERIC(19, 6) NOT NULL,
    low_price NUMERIC(19, 6) NOT NULL,
    close_price NUMERIC(19, 6) NOT NULL,
    adjusted_close_price NUMERIC(19, 6),
    volume_quantity NUMERIC(24, 6),
    volume_financial NUMERIC(24, 6),
    source VARCHAR(80) NOT NULL,
    collected_at TIMESTAMPTZ NOT NULL,
    quality_status VARCHAR(32) NOT NULL,
    CONSTRAINT uk_daily_candles_asset_date_source UNIQUE (asset_id, trade_date, source),
    CONSTRAINT ck_daily_candles_prices CHECK (open_price > 0 AND high_price > 0 AND low_price > 0 AND close_price > 0 AND high_price >= low_price),
    CONSTRAINT ck_daily_candles_quality CHECK (quality_status IN ('PENDING', 'VALID', 'INCOMPLETE', 'STALE', 'INCONSISTENT', 'FAILED'))
);

CREATE TABLE technical_indicator_snapshots (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL REFERENCES assets (id),
    trade_date DATE NOT NULL,
    sma_50 NUMERIC(19, 6),
    sma_100 NUMERIC(19, 6),
    sma_200 NUMERIC(19, 6),
    ema_50 NUMERIC(19, 6),
    ema_100 NUMERIC(19, 6),
    ema_200 NUMERIC(19, 6),
    return_6m NUMERIC(10, 6),
    return_12m NUMERIC(10, 6),
    avg_volume_60 NUMERIC(24, 6),
    high_52w NUMERIC(19, 6),
    low_52w NUMERIC(19, 6),
    historical_volatility NUMERIC(10, 6),
    recent_drawdown NUMERIC(10, 6),
    trend_status VARCHAR(32) NOT NULL,
    calculation_version VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_technical_snapshots_asset_date_version UNIQUE (asset_id, trade_date, calculation_version),
    CONSTRAINT ck_technical_snapshots_trend CHECK (trend_status IN ('HEALTHY', 'NEUTRAL', 'DETERIORATING', 'DOWN_TREND', 'INSUFFICIENT_DATA'))
);

CREATE TABLE fundamental_snapshots (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL REFERENCES assets (id),
    reference_date DATE NOT NULL,
    period_type VARCHAR(32) NOT NULL,
    source VARCHAR(80) NOT NULL,
    market_cap NUMERIC(24, 6),
    enterprise_value NUMERIC(24, 6),
    trailing_pe NUMERIC(19, 6),
    price_to_book NUMERIC(19, 6),
    enterprise_to_revenue NUMERIC(19, 6),
    enterprise_to_ebitda NUMERIC(19, 6),
    earnings_per_share NUMERIC(19, 6),
    book_value NUMERIC(19, 6),
    dividend_yield NUMERIC(10, 6),
    profit_margin NUMERIC(10, 6),
    gross_margin NUMERIC(10, 6),
    ebitda_margin NUMERIC(10, 6),
    operating_margin NUMERIC(10, 6),
    roe NUMERIC(10, 6),
    roa NUMERIC(10, 6),
    debt_to_equity NUMERIC(19, 6),
    revenue_growth NUMERIC(10, 6),
    earnings_growth NUMERIC(10, 6),
    free_cashflow NUMERIC(24, 6),
    operating_cashflow NUMERIC(24, 6),
    quality_status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_fundamental_snapshots_asset_period_source UNIQUE (asset_id, reference_date, period_type, source),
    CONSTRAINT ck_fundamental_snapshots_period CHECK (period_type IN ('ANNUAL', 'QUARTERLY', 'TTM')),
    CONSTRAINT ck_fundamental_snapshots_quality CHECK (quality_status IN ('PENDING', 'VALID', 'INCOMPLETE', 'STALE', 'INCONSISTENT', 'FAILED'))
);

CREATE TABLE financial_statement_snapshots (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL REFERENCES assets (id),
    statement_type VARCHAR(32) NOT NULL,
    period_type VARCHAR(32) NOT NULL,
    end_date DATE NOT NULL,
    source VARCHAR(80) NOT NULL,
    payload_json JSONB NOT NULL,
    quality_status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_financial_statements_asset_type_period_source UNIQUE (asset_id, statement_type, period_type, end_date, source),
    CONSTRAINT ck_financial_statements_type CHECK (statement_type IN ('BALANCE_SHEET', 'INCOME_STATEMENT', 'CASH_FLOW')),
    CONSTRAINT ck_financial_statements_period CHECK (period_type IN ('ANNUAL', 'QUARTERLY', 'TTM')),
    CONSTRAINT ck_financial_statements_quality CHECK (quality_status IN ('PENDING', 'VALID', 'INCOMPLETE', 'STALE', 'INCONSISTENT', 'FAILED'))
);

CREATE TABLE dividend_events (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL REFERENCES assets (id),
    event_type VARCHAR(32) NOT NULL,
    payment_date DATE,
    last_date_prior DATE,
    approved_on DATE,
    rate NUMERIC(19, 8),
    factor NUMERIC(19, 8),
    label VARCHAR(120),
    isin_code VARCHAR(40),
    source VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_dividend_events_asset_type_dates_source UNIQUE (asset_id, event_type, last_date_prior, payment_date, source),
    CONSTRAINT ck_dividend_events_type CHECK (event_type IN ('DIVIDEND', 'JCP', 'STOCK_DIVIDEND', 'BONUS', 'SUBSCRIPTION', 'SPLIT'))
);

CREATE TABLE macro_indicator_snapshots (
    id UUID PRIMARY KEY,
    slug VARCHAR(120) NOT NULL,
    name VARCHAR(180) NOT NULL,
    category VARCHAR(120),
    unit VARCHAR(40),
    frequency VARCHAR(40),
    reference_date DATE NOT NULL,
    value NUMERIC(24, 8) NOT NULL,
    source VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_macro_snapshots_slug_date_source UNIQUE (slug, reference_date, source)
);

CREATE TABLE position_theses (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL REFERENCES assets (id),
    reference_date DATE NOT NULL,
    thesis_type VARCHAR(60) NOT NULL,
    status VARCHAR(32) NOT NULL,
    score INTEGER NOT NULL,
    score_breakdown_json JSONB NOT NULL,
    reasons_json JSONB NOT NULL,
    failed_filters_json JSONB NOT NULL,
    price_ceiling NUMERIC(19, 6),
    fair_price_estimate NUMERIC(19, 6),
    safety_margin_percent NUMERIC(10, 6),
    stop_price NUMERIC(19, 6),
    target_price NUMERIC(19, 6),
    review_points_json JSONB NOT NULL,
    rule_version VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_position_theses_asset_date_type_version UNIQUE (asset_id, reference_date, thesis_type, rule_version),
    CONSTRAINT ck_position_theses_score CHECK (score BETWEEN 0 AND 100),
    CONSTRAINT ck_position_theses_type CHECK (thesis_type IN ('QUALITY_REASONABLE_PRICE', 'SUSTAINABLE_DIVIDENDS', 'PROFITABLE_GROWTH_HEALTHY_TREND')),
    CONSTRAINT ck_position_theses_status CHECK (status IN ('IGNORAR', 'MONITORAR', 'OPORTUNIDADE', 'APORTE_PLANEJADO', 'REAVALIAR', 'REDUZIR_EXPOSICAO', 'SAIR_DA_TESE'))
);

CREATE TABLE allocation_plans (
    id UUID PRIMARY KEY,
    thesis_id UUID NOT NULL REFERENCES position_theses (id),
    capital_base NUMERIC(19, 2) NOT NULL,
    target_allocation_percent NUMERIC(10, 6) NOT NULL,
    max_allocation_per_asset_percent NUMERIC(10, 6) NOT NULL,
    max_position_value NUMERIC(19, 2) NOT NULL,
    current_price NUMERIC(19, 6) NOT NULL,
    price_ceiling NUMERIC(19, 6) NOT NULL,
    suggested_quantity INTEGER NOT NULL,
    recommended_action VARCHAR(80) NOT NULL,
    first_tranche_value NUMERIC(19, 2),
    remaining_planned_value NUMERIC(19, 2),
    valid BOOLEAN NOT NULL,
    invalid_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_allocation_plans_thesis UNIQUE (thesis_id),
    CONSTRAINT ck_allocation_plans_values CHECK (capital_base >= 0 AND target_allocation_percent >= 0 AND max_allocation_per_asset_percent >= 0 AND max_position_value >= 0 AND current_price > 0 AND price_ceiling > 0 AND suggested_quantity >= 0)
);

CREATE TABLE customer_positions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users (id),
    asset_id UUID NOT NULL REFERENCES assets (id),
    quantity NUMERIC(24, 8) NOT NULL,
    average_price NUMERIC(19, 6) NOT NULL,
    entry_date DATE NOT NULL,
    stop_price NUMERIC(19, 6),
    target_price NUMERIC(19, 6),
    target_return_percent NUMERIC(10, 6),
    notes VARCHAR(1000),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    CONSTRAINT ck_customer_positions_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT ck_customer_positions_quantity CHECK (quantity > 0),
    CONSTRAINT ck_customer_positions_average_price CHECK (average_price > 0)
);

CREATE TABLE ai_context_analyses (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL REFERENCES assets (id),
    reference_date DATE NOT NULL,
    provider VARCHAR(80) NOT NULL,
    model VARCHAR(120) NOT NULL,
    prompt_version VARCHAR(40) NOT NULL,
    prompt_hash VARCHAR(128) NOT NULL,
    input_summary_json JSONB NOT NULL,
    output_json JSONB,
    sources_json JSONB NOT NULL,
    validation_status VARCHAR(32) NOT NULL,
    latency_ms BIGINT,
    error_message VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_ai_context_asset_date_prompt_model UNIQUE (asset_id, reference_date, provider, model, prompt_version, prompt_hash),
    CONSTRAINT ck_ai_context_validation CHECK (validation_status IN ('PENDING', 'VALID', 'INVALID', 'FAILED', 'TIMEOUT', 'UNAVAILABLE'))
);

CREATE TABLE position_recommendations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users (id),
    position_id UUID NOT NULL REFERENCES customer_positions (id),
    asset_id UUID NOT NULL REFERENCES assets (id),
    reference_date DATE NOT NULL,
    recommendation_type VARCHAR(40) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    current_price NUMERIC(19, 6),
    average_price NUMERIC(19, 6),
    stop_price NUMERIC(19, 6),
    target_price NUMERIC(19, 6),
    score INTEGER,
    deterministic_reason_json JSONB NOT NULL,
    ai_context_analysis_id UUID REFERENCES ai_context_analyses (id),
    final_message VARCHAR(2000) NOT NULL,
    rule_version VARCHAR(40) NOT NULL,
    ai_model VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_position_recommendations_idempotency UNIQUE (user_id, position_id, reference_date, recommendation_type, rule_version),
    CONSTRAINT ck_position_recommendations_type CHECK (recommendation_type IN ('MANTER', 'AUMENTAR_POSICAO', 'REDUZIR_POSICAO', 'REALIZAR_OBJETIVO', 'EXECUTAR_STOP', 'REAVALIAR', 'SAIR_DA_TESE')),
    CONSTRAINT ck_position_recommendations_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_position_recommendations_score CHECK (score IS NULL OR score BETWEEN 0 AND 100)
);

CREATE TABLE notification_events (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users (id),
    position_id UUID NOT NULL REFERENCES customer_positions (id),
    recommendation_id UUID NOT NULL REFERENCES position_recommendations (id),
    asset_id UUID NOT NULL REFERENCES assets (id),
    reference_date DATE NOT NULL,
    channel VARCHAR(32) NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    recommendation_type VARCHAR(40) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    summary VARCHAR(1000) NOT NULL,
    status VARCHAR(32) NOT NULL,
    provider VARCHAR(80),
    provider_message_id VARCHAR(160),
    attempt_count INTEGER NOT NULL,
    last_error VARCHAR(1000),
    rule_version VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    CONSTRAINT uk_notification_events_daily_digest UNIQUE (user_id, reference_date, channel, rule_version, event_type, recommendation_id),
    CONSTRAINT ck_notification_events_channel CHECK (channel IN ('EMAIL_SNS')),
    CONSTRAINT ck_notification_events_type CHECK (event_type IN ('STOP_TRIGGERED', 'TARGET_REACHED', 'REASSESSMENT_REQUIRED', 'REDUCE_EXPOSURE', 'EXIT_THESIS')),
    CONSTRAINT ck_notification_events_recommendation CHECK (recommendation_type IN ('MANTER', 'AUMENTAR_POSICAO', 'REDUZIR_POSICAO', 'REALIZAR_OBJETIVO', 'EXECUTAR_STOP', 'REAVALIAR', 'SAIR_DA_TESE')),
    CONSTRAINT ck_notification_events_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_notification_events_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED')),
    CONSTRAINT ck_notification_events_attempts CHECK (attempt_count >= 0)
);

CREATE TABLE backtest_runs (
    id UUID PRIMARY KEY,
    thesis_type VARCHAR(60) NOT NULL,
    rule_version VARCHAR(40) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    asset_universe VARCHAR(500) NOT NULL,
    benchmark VARCHAR(80),
    data_source VARCHAR(80) NOT NULL,
    parameters_json JSONB NOT NULL,
    metrics_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_backtest_runs_period CHECK (end_date >= start_date),
    CONSTRAINT ck_backtest_runs_thesis CHECK (thesis_type IN ('QUALITY_REASONABLE_PRICE', 'SUSTAINABLE_DIVIDENDS', 'PROFITABLE_GROWTH_HEALTHY_TREND'))
);

CREATE TABLE backtest_positions (
    id UUID PRIMARY KEY,
    backtest_run_id UUID NOT NULL REFERENCES backtest_runs (id),
    asset_id UUID NOT NULL REFERENCES assets (id),
    thesis_date DATE NOT NULL,
    entry_date DATE,
    exit_date DATE,
    entry_price NUMERIC(19, 6),
    average_price NUMERIC(19, 6),
    exit_price NUMERIC(19, 6),
    quantity NUMERIC(24, 8),
    allocated_value NUMERIC(19, 2),
    dividends_received NUMERIC(19, 2),
    exit_reason VARCHAR(120),
    return_percent NUMERIC(10, 6),
    result_amount NUMERIC(19, 2),
    CONSTRAINT ck_backtest_positions_dates CHECK (exit_date IS NULL OR entry_date IS NULL OR exit_date >= entry_date)
);
