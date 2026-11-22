CREATE TABLE IF NOT EXISTS plans (
    id UUID PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    price_cents INT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    billing_interval VARCHAR(20) NOT NULL,
    max_active_rooms INT NOT NULL,
    max_participants_per_room INT NOT NULL,
    ai_daily_limit INT NOT NULL,
    ai_tier VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS subscriptions (
    id UUID PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    plan_id UUID NOT NULL REFERENCES plans(id),
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    current_period_start TIMESTAMP WITH TIME ZONE NOT NULL,
    current_period_end TIMESTAMP WITH TIME ZONE NOT NULL,
    canceled_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_subscriptions_user_active
    ON subscriptions (user_id) WHERE status = 'ACTIVE';

CREATE TABLE IF NOT EXISTS billing_records (
    id UUID PRIMARY KEY,
    invoice_number VARCHAR(50) NOT NULL UNIQUE,
    user_id VARCHAR(255) NOT NULL,
    subscription_id UUID REFERENCES subscriptions(id),
    event_type VARCHAR(20) NOT NULL,
    plan_code VARCHAR(32) NOT NULL,
    plan_name VARCHAR(100) NOT NULL,
    amount_cents INT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    payment_method_summary VARCHAR(100) NOT NULL,
    external_checkout_id VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

ALTER TABLE billing_records ADD COLUMN IF NOT EXISTS external_checkout_id VARCHAR(128);
CREATE UNIQUE INDEX IF NOT EXISTS uq_billing_records_external_checkout_id ON billing_records (external_checkout_id);

