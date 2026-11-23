-- =====================================================================
-- V2: template-manager schema
-- Three tables for PDF templates: document types, templates and
-- immutable versions. Fields, normalized tags and site assignments
-- are added by later migrations when a feature needs them.
-- Additive: the old public.pdf_templates table is kept until the API
-- is switched over (a later V3 drops it).
-- =====================================================================

CREATE SCHEMA IF NOT EXISTS template;

-- ---------------------------------------------------------------------
-- document_type: kinds of document the business produces
-- ---------------------------------------------------------------------
CREATE TABLE template.document_type (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    code         VARCHAR(50)  NOT NULL,
    name         VARCHAR(100) NOT NULL,
    category     VARCHAR(30)  NOT NULL,
    description  TEXT         NOT NULL DEFAULT '',
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   UUID         NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by   UUID         NOT NULL,
    row_version  INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT pk_document_type PRIMARY KEY (id),
    CONSTRAINT uq_document_type_code UNIQUE (code),
    CONSTRAINT ck_document_type_code CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT ck_document_type_category
        CHECK (category IN ('FINANCE', 'CORRESPONDENCE', 'COMPLIANCE', 'OTHER'))
);

COMMENT ON TABLE  template.document_type      IS 'Kinds of document the business produces. Business flows ask for a document type, not a template.';
COMMENT ON COLUMN template.document_type.code IS 'Stable code used by code and business flows, e.g. SERVICE_CHARGE_DEMAND.';

-- ---------------------------------------------------------------------
-- template: a template's stable identity
-- ---------------------------------------------------------------------
CREATE TABLE template.template (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    document_type_id    UUID         NOT NULL,
    name                VARCHAR(200) NOT NULL,
    description         TEXT         NOT NULL DEFAULT '',
    status              VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    current_version_id  UUID,
    owner_user_id       UUID         NOT NULL,
    tags                TEXT[]       NOT NULL DEFAULT '{}',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          UUID         NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by          UUID         NOT NULL,
    row_version         INTEGER      NOT NULL DEFAULT 0,
    deleted_at          TIMESTAMPTZ,
    deleted_by          UUID,
    CONSTRAINT pk_template PRIMARY KEY (id),
    CONSTRAINT fk_template_document_type FOREIGN KEY (document_type_id)
        REFERENCES template.document_type (id) ON DELETE RESTRICT,
    CONSTRAINT ck_template_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_template_name CHECK (btrim(name) <> ''),
    CONSTRAINT ck_template_deleted CHECK ((deleted_at IS NULL) = (deleted_by IS NULL)),
    -- An ACTIVE template must have a published version to use.
    CONSTRAINT ck_template_active_has_version
        CHECK (status <> 'ACTIVE' OR current_version_id IS NOT NULL)
);

CREATE UNIQUE INDEX uq_template_name_per_type
    ON template.template (document_type_id, lower(name))
    WHERE deleted_at IS NULL;
CREATE INDEX ix_template_document_type ON template.template (document_type_id);
CREATE INDEX ix_template_owner         ON template.template (owner_user_id);
CREATE INDEX ix_template_updated_at    ON template.template (updated_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_template_tags          ON template.template USING gin (tags);

COMMENT ON TABLE  template.template                    IS 'A template''s stable identity. The design itself lives in template_version.';
COMMENT ON COLUMN template.template.current_version_id IS 'The published version flows use now. Must belong to this template.';
COMMENT ON COLUMN template.template.owner_user_id      IS 'User (identity service) responsible for the template.';
COMMENT ON COLUMN template.template.tags               IS 'Labels for filtering. A simple array for now; may become tag tables later.';
COMMENT ON COLUMN template.template.deleted_at         IS 'Soft delete: hidden from lists but kept, because generated documents may reference its versions.';

-- ---------------------------------------------------------------------
-- template_version: one design of a template, immutable once published
-- ---------------------------------------------------------------------
CREATE TABLE template.template_version (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    template_id    UUID         NOT NULL,
    version_no     INTEGER      NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    content        JSONB        NOT NULL,
    sample_inputs  JSONB        NOT NULL DEFAULT '[]',
    pdfme_version  VARCHAR(20),
    content_hash   CHAR(64)     NOT NULL,
    change_note    TEXT         NOT NULL DEFAULT '',
    published_at   TIMESTAMPTZ,
    published_by   UUID,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     UUID         NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by     UUID         NOT NULL,
    row_version    INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT pk_template_version PRIMARY KEY (id),
    CONSTRAINT uq_template_version_no UNIQUE (template_id, version_no),
    -- Target of the composite foreign key from template.current_version_id.
    CONSTRAINT uq_template_version_id_template UNIQUE (id, template_id),
    CONSTRAINT fk_template_version_template FOREIGN KEY (template_id)
        REFERENCES template.template (id) ON DELETE RESTRICT,
    CONSTRAINT ck_template_version_no CHECK (version_no > 0),
    CONSTRAINT ck_template_version_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    CONSTRAINT ck_template_version_content CHECK (
        jsonb_typeof(content) = 'object'
        AND content ? 'basePdf'
        AND jsonb_typeof(content -> 'schemas') = 'array'),
    CONSTRAINT ck_template_version_inputs CHECK (jsonb_typeof(sample_inputs) = 'array'),
    CONSTRAINT ck_template_version_hash CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_template_version_published CHECK (
        status = 'DRAFT' OR (published_at IS NOT NULL AND published_by IS NOT NULL))
);

-- At most one draft per template.
CREATE UNIQUE INDEX uq_template_version_one_draft
    ON template.template_version (template_id)
    WHERE status = 'DRAFT';

COMMENT ON TABLE  template.template_version              IS 'One design of a template. DRAFT is editable; PUBLISHED and RETIRED are locked, so documents can always prove which design they used.';
COMMENT ON COLUMN template.template_version.content      IS 'The pdfme template JSON (basePdf + schemas), stored as-is. Positions and sizes in mm.';
COMMENT ON COLUMN template.template_version.content_hash IS 'SHA-256 (hex) of content, to detect identical or changed designs.';

-- Now that versions exist, link a template to its current version.
-- Composite key: the current version must belong to the same template.
ALTER TABLE template.template
    ADD CONSTRAINT fk_template_current_version
    FOREIGN KEY (current_version_id, id)
    REFERENCES template.template_version (id, template_id)
    ON DELETE RESTRICT
    DEFERRABLE INITIALLY DEFERRED;

-- Published and retired versions cannot be changed or deleted.
-- Only status (PUBLISHED -> RETIRED) and audit columns may change.
CREATE FUNCTION template.protect_published_version() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.status <> 'DRAFT' THEN
            RAISE EXCEPTION 'Version % is % and cannot be deleted.', OLD.id, OLD.status
                USING ERRCODE = 'check_violation';
        END IF;
        RETURN OLD;
    END IF;

    IF OLD.status <> 'DRAFT' AND (
           NEW.content       IS DISTINCT FROM OLD.content
        OR NEW.sample_inputs IS DISTINCT FROM OLD.sample_inputs
        OR NEW.content_hash  IS DISTINCT FROM OLD.content_hash
        OR NEW.template_id   IS DISTINCT FROM OLD.template_id
        OR NEW.version_no    IS DISTINCT FROM OLD.version_no
        OR NEW.published_at  IS DISTINCT FROM OLD.published_at
        OR NEW.published_by  IS DISTINCT FROM OLD.published_by
        OR NEW.status = 'DRAFT') THEN
        RAISE EXCEPTION 'Version % is % and cannot be changed. Create a new version instead.', OLD.id, OLD.status
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_template_version_protect
    BEFORE UPDATE OR DELETE ON template.template_version
    FOR EACH ROW EXECUTE FUNCTION template.protect_published_version();

-- =====================================================================
-- Seed data
-- =====================================================================
INSERT INTO template.document_type (code, name, category, description, created_by, updated_by) VALUES
    ('SERVICE_CHARGE_DEMAND', 'Service charge demand', 'FINANCE',        'Demand for service charges from leaseholders.',  '00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-000000000000'),
    ('PAYMENT_RECEIPT',       'Payment receipt',       'FINANCE',        'Receipt for a payment received.',                '00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-000000000000'),
    ('GENERAL_LETTER',        'General letter',        'CORRESPONDENCE', 'Letters to leaseholders and other contacts.',    '00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-000000000000'),
    ('INSPECTION_REPORT',     'Inspection report',     'COMPLIANCE',     'Property inspection checklists and reports.',    '00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-000000000000'),
    ('OTHER',                 'Other',                 'OTHER',          'Templates not yet linked to a business flow.',   '00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-000000000000');

-- =====================================================================
-- Copy existing data from public.pdf_templates
-- Each old row becomes a template (same id) with one PUBLISHED version 1.
-- =====================================================================
CREATE TEMP TABLE migrated_template ON COMMIT DROP AS
SELECT
    p.id,
    dt.id AS document_type_id,
    -- Names must be unique per type: add (2), (3) ... to duplicates, oldest keeps its name.
    CASE WHEN row_number() OVER w = 1 THEN p.name
         ELSE left(p.name, 190) || ' (' || row_number() OVER w || ')' END AS name,
    p.description,
    COALESCE(ARRAY(SELECT DISTINCT btrim(t) FROM unnest(p.tags) AS t WHERE btrim(t) <> '' ORDER BY 1), '{}') AS tags,
    p.template,
    p.sample_inputs,
    p.created_at,
    p.updated_at,
    gen_random_uuid() AS version_id
FROM public.pdf_templates p
JOIN template.document_type dt ON dt.code = CASE
        WHEN p.name ILIKE '%service charge%' THEN 'SERVICE_CHARGE_DEMAND'
        WHEN p.name ILIKE '%receipt%'        THEN 'PAYMENT_RECEIPT'
        WHEN p.name ILIKE '%inspection%'     THEN 'INSPECTION_REPORT'
        WHEN p.name ILIKE '%letter%'         THEN 'GENERAL_LETTER'
        ELSE 'OTHER' END
WINDOW w AS (PARTITION BY dt.id, lower(p.name) ORDER BY p.created_at, p.id);

INSERT INTO template.template
    (id, document_type_id, name, description, status, current_version_id, owner_user_id, tags,
     created_at, created_by, updated_at, updated_by)
SELECT id, document_type_id, name, description, 'ACTIVE', version_id,
       '00000000-0000-0000-0000-000000000000', tags,
       created_at, '00000000-0000-0000-0000-000000000000',
       updated_at, '00000000-0000-0000-0000-000000000000'
FROM migrated_template;

INSERT INTO template.template_version
    (id, template_id, version_no, status, content, sample_inputs, pdfme_version, content_hash,
     change_note, published_at, published_by, created_at, created_by, updated_at, updated_by)
SELECT version_id, id, 1, 'PUBLISHED', template, sample_inputs, template ->> 'pdfmeVersion',
       encode(sha256(convert_to(template::text, 'UTF8')), 'hex'),
       'Migrated from pdf_templates', updated_at, '00000000-0000-0000-0000-000000000000',
       updated_at, '00000000-0000-0000-0000-000000000000',
       updated_at, '00000000-0000-0000-0000-000000000000'
FROM migrated_template;
