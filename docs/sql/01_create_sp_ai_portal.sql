-- SP AI Portal PostgreSQL initialization script.
-- Target database: guangzhshtshuzhidb
-- Run as a user that can create schemas and the pg_trgm extension.

BEGIN;

CREATE SCHEMA IF NOT EXISTS sp_ai_portal AUTHORIZATION CURRENT_USER;
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;
SET LOCAL search_path TO sp_ai_portal, public;

CREATE TABLE IF NOT EXISTS team (
    id varchar(36) PRIMARY KEY,
    code varchar(50) NOT NULL UNIQUE,
    name varchar(200) NOT NULL,
    enabled boolean NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS app_user (
    id varchar(36) PRIMARY KEY,
    username varchar(100) NOT NULL UNIQUE,
    password_hash varchar(100) NOT NULL,
    display_name varchar(200) NOT NULL,
    team_id varchar(36) REFERENCES team(id),
    enabled boolean NOT NULL DEFAULT true,
    failed_attempts integer NOT NULL DEFAULT 0,
    locked_until timestamptz
);

CREATE TABLE IF NOT EXISTS user_role (
    user_id varchar(36) NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role_code varchar(30) NOT NULL CHECK (role_code IN ('ADMIN', 'TEAM_LEAD', 'MEMBER')),
    PRIMARY KEY (user_id, role_code)
);

CREATE TABLE IF NOT EXISTS refresh_token (
    id varchar(36) PRIMARY KEY,
    user_id varchar(36) NOT NULL REFERENCES app_user(id),
    token_hash varchar(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz
);

CREATE TABLE IF NOT EXISTS delivery_stage (
    id integer PRIMARY KEY,
    name varchar(100) NOT NULL,
    description varchar(500),
    sort_order integer NOT NULL
);

CREATE TABLE IF NOT EXISTS asset_category (
    id varchar(36) PRIMARY KEY,
    stage_id integer NOT NULL REFERENCES delivery_stage(id),
    number varchar(20) NOT NULL,
    name varchar(300) NOT NULL,
    external_folder_id varchar(200),
    sort_order integer NOT NULL
);

CREATE TABLE IF NOT EXISTS file_object (
    id varchar(36) PRIMARY KEY,
    gridfs_id varchar(50) NOT NULL UNIQUE,
    file_name varchar(500) NOT NULL,
    content_type varchar(200),
    file_size bigint NOT NULL,
    sha256 varchar(64) NOT NULL,
    status varchar(30) NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_file_sha ON file_object (sha256, status);

CREATE TABLE IF NOT EXISTS asset (
    id varchar(36) PRIMARY KEY,
    name varchar(500) NOT NULL,
    type varchar(1) NOT NULL CHECK (type IN ('T', 'C', 'A')),
    stage_id integer NOT NULL REFERENCES delivery_stage(id),
    category_id varchar(36) NOT NULL REFERENCES asset_category(id),
    media_id varchar(500) UNIQUE,
    source_url varchar(2048),
    current_file_id varchar(36) REFERENCES file_object(id),
    status varchar(30) NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    search_text text,
    created_at timestamptz NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_asset_search ON asset USING gin (search_text public.gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_asset_filter ON asset (stage_id, type, status, enabled);

CREATE TABLE IF NOT EXISTS asset_version (
    id varchar(36) PRIMARY KEY,
    asset_id varchar(36) NOT NULL REFERENCES asset(id),
    version_no integer NOT NULL,
    file_id varchar(36) REFERENCES file_object(id),
    status varchar(30) NOT NULL,
    change_note varchar(1000),
    review_note varchar(1000),
    created_by varchar(36) REFERENCES app_user(id),
    created_at timestamptz NOT NULL,
    UNIQUE (asset_id, version_no)
);

CREATE TABLE IF NOT EXISTS skill (
    id varchar(36) PRIMARY KEY,
    slug varchar(200) NOT NULL UNIQUE,
    name varchar(300) NOT NULL,
    description varchar(2000),
    owner_id varchar(36) REFERENCES app_user(id),
    status varchar(30) NOT NULL,
    download_count bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL
);

CREATE TABLE IF NOT EXISTS skill_version (
    id varchar(36) PRIMARY KEY,
    skill_id varchar(36) NOT NULL REFERENCES skill(id),
    version_name varchar(50) NOT NULL,
    file_id varchar(36) REFERENCES file_object(id),
    status varchar(30) NOT NULL,
    change_note varchar(1000),
    review_note varchar(1000),
    created_by varchar(36) REFERENCES app_user(id),
    created_at timestamptz NOT NULL
);

CREATE TABLE IF NOT EXISTS skill_stage (
    skill_id varchar(36) NOT NULL REFERENCES skill(id),
    stage_id integer NOT NULL REFERENCES delivery_stage(id),
    PRIMARY KEY (skill_id, stage_id)
);

CREATE TABLE IF NOT EXISTS delivery_task (
    id varchar(36) PRIMARY KEY,
    external_id varchar(100) NOT NULL UNIQUE,
    stage varchar(100) NOT NULL,
    scene varchar(500) NOT NULL,
    ai varchar(1000) NOT NULL,
    description varchar(2000) NOT NULL,
    input_text varchar(2000),
    output_text varchar(1000),
    carrier_type varchar(30) NOT NULL,
    skill_name varchar(300) NOT NULL,
    priority varchar(10) NOT NULL,
    difficulty varchar(10) NOT NULL,
    enabled boolean NOT NULL DEFAULT true
);

CREATE INDEX IF NOT EXISTS idx_task_filter
    ON delivery_task (stage, priority, difficulty, carrier_type, enabled);

CREATE TABLE IF NOT EXISTS task_progress (
    id varchar(36) PRIMARY KEY,
    task_id varchar(36) NOT NULL REFERENCES delivery_task(id),
    team_id varchar(36) NOT NULL REFERENCES team(id),
    status varchar(20) NOT NULL CHECK (status IN ('待创建', '进行中', '已完成')),
    lock_version bigint NOT NULL DEFAULT 0,
    updated_by varchar(36) REFERENCES app_user(id),
    updated_at timestamptz NOT NULL,
    UNIQUE (task_id, team_id)
);

CREATE TABLE IF NOT EXISTS task_progress_history (
    id varchar(36) PRIMARY KEY,
    task_id varchar(36) NOT NULL REFERENCES delivery_task(id),
    team_id varchar(36) NOT NULL REFERENCES team(id),
    old_status varchar(20),
    new_status varchar(20) NOT NULL,
    changed_by varchar(36) REFERENCES app_user(id),
    changed_at timestamptz NOT NULL
);

CREATE TABLE IF NOT EXISTS sync_job (
    id varchar(36) PRIMARY KEY,
    source varchar(50) NOT NULL,
    status varchar(30) NOT NULL,
    total_count integer NOT NULL DEFAULT 0,
    success_count integer NOT NULL DEFAULT 0,
    error_count integer NOT NULL DEFAULT 0,
    message varchar(2000),
    started_at timestamptz NOT NULL,
    finished_at timestamptz
);

CREATE TABLE IF NOT EXISTS sync_job_error (
    id varchar(36) PRIMARY KEY,
    job_id varchar(36) NOT NULL REFERENCES sync_job(id),
    row_key varchar(200),
    message varchar(2000) NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE TABLE IF NOT EXISTS audit_log (
    id varchar(36) PRIMARY KEY,
    actor_id varchar(36) REFERENCES app_user(id),
    action varchar(100) NOT NULL,
    target_type varchar(100) NOT NULL,
    target_id varchar(100) NOT NULL,
    before_data text,
    after_data text,
    ip varchar(100),
    created_at timestamptz NOT NULL
);

INSERT INTO team (id, code, name) VALUES
    ('00000000-0000-0000-0000-000000000001', 'DEMO_A', '演示团队一'),
    ('00000000-0000-0000-0000-000000000002', 'DEMO_B', '演示团队二'),
    ('00000000-0000-0000-0000-000000000003', 'DEMO_C', '演示团队三')
ON CONFLICT (id) DO NOTHING;

INSERT INTO delivery_stage (id, name, description, sort_order) VALUES
    (0, '项目管理', '周报、例会、回款与风险贯穿全程', 0),
    (1, '项目启动', '组队、计划、启动会与项目立项', 1),
    (2, '需求分析', '需求调研、确认、评审与实施方案', 2),
    (3, '系统设计', '技术选型、概要设计、接口与评审', 3),
    (4, '编码实现', '编码规范、脚本安全与质量审核', 4),
    (5, '数据处理', '数据收集、治理、迁移与验证', 5),
    (6, '部署测试', '测试计划、部署方案与性能验证', 6),
    (7, '运行维护', '上线、培训、巡检与运维保障', 7),
    (8, '总结验收', '验收材料、专家评审与项目复盘', 8)
ON CONFLICT (id) DO NOTHING;

COMMIT;
