ALTER TABLE quotes ADD COLUMN pinned BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_quotes_pinned_created_at ON quotes (created_at DESC, id DESC)
    WHERE pinned = TRUE;
