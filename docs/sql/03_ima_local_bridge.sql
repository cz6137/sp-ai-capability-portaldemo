-- Apply after 01_create_sp_ai_portal.sql and 02_meeting_0818_upgrade.sql.
-- Existing installations can apply only this script when Flyway is not used.

ALTER TABLE asset
  ADD COLUMN source_type varchar(30) NOT NULL DEFAULT 'INTERNAL',
  ADD COLUMN source_metadata text,
  ADD COLUMN last_sync_run_id varchar(36),
  ADD COLUMN last_synced_at timestamptz;

UPDATE asset SET source_type = 'BOOTSTRAP_IMA' WHERE media_id IS NOT NULL;

CREATE INDEX idx_asset_source_sync ON asset(source_type, last_sync_run_id, enabled);

ALTER TABLE sync_job
  ADD COLUMN source_reference varchar(500),
  ADD COLUMN source_share_id varchar(500),
  ADD COLUMN started_by varchar(36) REFERENCES app_user(id);

CREATE UNIQUE INDEX uk_sync_job_running_ima_bridge ON sync_job(source)
  WHERE source = 'IMA_BRIDGE' AND status = 'RUNNING';

CREATE TABLE ima_bridge_run_item (
  id varchar(36) PRIMARY KEY,
  run_id varchar(36) NOT NULL REFERENCES sync_job(id) ON DELETE CASCADE,
  media_id varchar(500) NOT NULL,
  asset_id varchar(36) NOT NULL REFERENCES asset(id),
  created_at timestamptz NOT NULL,
  UNIQUE(run_id, media_id)
);

CREATE INDEX idx_ima_bridge_item_asset ON ima_bridge_run_item(asset_id);
