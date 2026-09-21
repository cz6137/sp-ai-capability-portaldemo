CREATE TABLE capability_asset (
  id varchar(36) PRIMARY KEY,
  slug varchar(200) NOT NULL UNIQUE,
  kind varchar(20) NOT NULL CHECK(kind IN ('skill','tool')),
  name varchar(300) NOT NULL,
  status varchar(30) NOT NULL CHECK(status IN ('DRAFT','IN_REVIEW','PUBLISHED','ARCHIVED')),
  current_version_id varchar(36),
  created_by varchar(36) REFERENCES app_user(id),
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL
);

CREATE TABLE capability_version (
  id varchar(36) PRIMARY KEY,
  asset_id varchar(36) NOT NULL REFERENCES capability_asset(id) ON DELETE CASCADE,
  version_name varchar(80) NOT NULL,
  manifest_json text NOT NULL,
  package_file_id varchar(36) REFERENCES file_object(id),
  status varchar(30) NOT NULL CHECK(status IN ('DRAFT','IN_REVIEW','PUBLISHED','ARCHIVED')),
  change_note varchar(1000),
  created_by varchar(36) REFERENCES app_user(id),
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  UNIQUE(asset_id, version_name)
);

ALTER TABLE capability_asset ADD CONSTRAINT fk_capability_current_version FOREIGN KEY(current_version_id) REFERENCES capability_version(id);
CREATE INDEX idx_capability_kind_status ON capability_asset(kind, status, updated_at DESC);
CREATE INDEX idx_capability_version_asset ON capability_version(asset_id, created_at DESC);
