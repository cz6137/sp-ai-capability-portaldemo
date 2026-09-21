ALTER TABLE skill
  ADD COLUMN source_type varchar(20) NOT NULL DEFAULT 'INTERNAL',
  ADD COLUMN case_text text,
  ADD COLUMN usage_guide text,
  ADD COLUMN enabled boolean NOT NULL DEFAULT true,
  ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now();

ALTER TABLE skill_version
  ADD CONSTRAINT uk_skill_version_name UNIQUE (skill_id, version_name);

CREATE TABLE case_category (
  id varchar(36) PRIMARY KEY,
  code varchar(80) NOT NULL UNIQUE,
  name varchar(200) NOT NULL,
  description varchar(1000),
  external_folder_id varchar(200),
  sort_order integer NOT NULL DEFAULT 0,
  enabled boolean NOT NULL DEFAULT true
);

ALTER TABLE asset ADD COLUMN case_category_id varchar(36) REFERENCES case_category(id);
CREATE INDEX idx_asset_case_category ON asset(case_category_id, enabled, status);

INSERT INTO case_category(id, code, name, description, sort_order) VALUES
('10000000-0000-0000-0000-000000000001', 'real-estate', '不动产管理', '不动产登记、房产交易、确权登记等项目的完整交付材料。', 1),
('10000000-0000-0000-0000-000000000002', 'spatial-platform', '时空数据与一张图', '时空大数据平台、GIS 平台、城市大脑与一张图项目材料。', 2),
('10000000-0000-0000-0000-000000000003', 'natural-resources', '自然资源一体化', '自然资源治理、用途管制、国土空间规划与政务服务项目材料。', 3),
('10000000-0000-0000-0000-000000000004', 'surveying', '测绘调查与遥感', '测绘、地籍调查、卫星遥感及数据质检类项目材料。', 4),
('10000000-0000-0000-0000-000000000005', 'general', '综合交付案例', '暂未归入专项业务域的优秀项目交付材料。', 99);

UPDATE asset SET case_category_id = CASE
  WHEN name LIKE '%不动产%' OR name LIKE '%房产%' OR name LIKE '%确权登记%' THEN '10000000-0000-0000-0000-000000000001'
  WHEN name LIKE '%时空%' OR name LIKE '%GIS%' OR name LIKE '%一张图%' OR name LIKE '%城市大脑%' THEN '10000000-0000-0000-0000-000000000002'
  WHEN name LIKE '%自然资源%' OR name LIKE '%国土空间%' OR name LIKE '%用途管制%' OR name LIKE '%政务服务%' THEN '10000000-0000-0000-0000-000000000003'
  WHEN name LIKE '%测绘%' OR name LIKE '%地籍%' OR name LIKE '%遥感%' OR name LIKE '%调查%' THEN '10000000-0000-0000-0000-000000000004'
  ELSE '10000000-0000-0000-0000-000000000005'
END WHERE type = 'C';

UPDATE delivery_stage SET name = '项目监控', description = '周报、例会、回款跟踪、贯穿全程' WHERE id = 0;
UPDATE delivery_stage SET name = '预投立项', description = '项目启动、团队组建、计划制定' WHERE id = 1;

INSERT INTO skill(id, slug, name, description, owner_id, status, download_count, created_at, source_type, case_text, usage_guide, enabled, updated_at) VALUES
('20000000-0000-0000-0000-000000000001', 'delivery-weekly-report', '项目周报生成器', '自动汇总项目进度、风险和计划，生成标准项目周报。', NULL, 'PUBLISHED', 356, now(), 'INTERNAL', '已用于交付团队周例会和阶段汇报，减少重复整理工作。', '上传项目进展、风险问题和下周计划，确认统计周期后生成周报并人工复核。', true, now()),
('20000000-0000-0000-0000-000000000002', 'delivery-risk-analysis', '风险预警分析', '识别项目进度、成本和范围偏差，生成风险预警建议。', NULL, 'PUBLISHED', 218, now(), 'INTERNAL', '适用于项目监控阶段的风险台账复核和周报风险摘要。', '导入风险台账和项目计划，选择评估口径，复核风险等级后输出报告。', true, now()),
('20000000-0000-0000-0000-000000000003', 'delivery-requirement-parser', '需求文档解析', '解析需求材料并提取结构化信息，生成需求追踪矩阵。', NULL, 'PUBLISHED', 192, now(), 'INTERNAL', '用于需求调研材料归集和需求条目结构化。', '上传需求文档，选择业务域，确认术语表后导出需求清单。', true, now()),
('20000000-0000-0000-0000-000000000004', 'delivery-test-case-generator', '测试用例生成', '基于需求文档生成覆盖功能、性能和边界场景的测试用例。', NULL, 'PUBLISHED', 165, now(), 'INTERNAL', '用于部署测试阶段的测试设计初稿。', '上传已评审需求，指定用例模板和测试类型，生成后由测试负责人复核。', true, now()),
('20000000-0000-0000-0000-000000000005', 'delivery-script-security-review', '脚本安全审核', '审核 SQL 与 Shell 脚本，识别高风险语句和权限问题。', NULL, 'PUBLISHED', 143, now(), 'INTERNAL', '用于上线前脚本评审和变更检查。', '上传脚本压缩包，选择目标数据库或操作系统，处理阻断项后重新检查。', true, now()),
('20000000-0000-0000-0000-000000000006', 'delivery-operations-report', '运维报告生成', '汇总巡检数据生成运维报告，并标注异常和趋势。', NULL, 'PUBLISHED', 128, now(), 'INTERNAL', '用于月度运维报告和巡检总结。', '导入巡检记录和故障清单，设置报告周期，核对异常项后导出。', true, now()),
('20000000-0000-0000-0000-000000000007', 'delivery-acceptance-organizer', '验收材料整理', '按验收清单归集文档，生成材料目录并检查完整性。', NULL, 'PUBLISHED', 112, now(), 'INTERNAL', '用于项目验收前材料完整性检查。', '上传验收清单和材料目录，执行匹配检查，补齐缺失材料后导出目录。', true, now())
ON CONFLICT (slug) DO NOTHING;

INSERT INTO skill_stage(skill_id, stage_id) VALUES
('20000000-0000-0000-0000-000000000001', 0),
('20000000-0000-0000-0000-000000000002', 0),
('20000000-0000-0000-0000-000000000003', 2),
('20000000-0000-0000-0000-000000000004', 6),
('20000000-0000-0000-0000-000000000005', 4),
('20000000-0000-0000-0000-000000000005', 6),
('20000000-0000-0000-0000-000000000006', 7),
('20000000-0000-0000-0000-000000000007', 8)
ON CONFLICT DO NOTHING;

CREATE INDEX idx_skill_status_download ON skill(status, enabled, download_count DESC);
