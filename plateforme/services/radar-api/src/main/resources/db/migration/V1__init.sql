-- Schéma initial du radar. Toute évolution passe par une nouvelle migration Flyway (V2__..., V3__...).

CREATE TABLE signals (
    id           VARCHAR(16)              PRIMARY KEY,
    source       VARCHAR(100)             NOT NULL,
    source_type  VARCHAR(20)              NOT NULL,
    title        VARCHAR(1000)            NOT NULL,
    summary      VARCHAR(4000),
    url          VARCHAR(2000)            NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE NOT NULL,
    engagement   DOUBLE PRECISION         NOT NULL DEFAULT 0,
    collected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_source_type CHECK (source_type IN ('academic', 'code', 'community', 'press', 'regulatory'))
);

CREATE INDEX idx_signals_published_at ON signals (published_at);

CREATE TABLE feedback (
    term       VARCHAR(200)             PRIMARY KEY,
    verdict    VARCHAR(20)              NOT NULL,
    analyst    VARCHAR(100),
    comment    VARCHAR(1000),
    decided_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_verdict CHECK (verdict IN ('pertinent', 'bruit'))
);
