-- Saved pdfme templates (see the frontend file src/pdfme-demo/api/API_CONTRACT.md).
CREATE TABLE pdf_templates (
    id            UUID         PRIMARY KEY,
    name          VARCHAR(200) NOT NULL,
    description   TEXT         NOT NULL DEFAULT '',
    tags          TEXT[]       NOT NULL DEFAULT '{}',
    template      JSONB        NOT NULL,              -- the pdfme template, stored as-is
    sample_inputs JSONB        NOT NULL DEFAULT '[]', -- pdfme inputs (field name -> value)
    version       INTEGER      NOT NULL,              -- increases on every update (optimistic locking)
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);

-- The list screen shows the most recently updated templates first.
CREATE INDEX idx_pdf_templates_updated_at ON pdf_templates (updated_at DESC);
