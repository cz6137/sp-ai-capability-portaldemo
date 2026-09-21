# 项目目录与事实源

2026-09-05 更新：新增 `capabilities/`（能力包实物）、`scripts/build_capability_packages.py`（只构建交付 ZIP）、`tests/test_capability_packages.py`（静态检查与文件保护测试）。最新说明见 [能力包开发交接](CAPABILITY-PACKAGES.md)。

## 1. Git 根目录

实际 Git 根目录是 `S:\work\ai平台\sp-ai-portal\sp-ai-portal`。外层同名目录只承载本地包缓存和项目目录，不应在外层执行项目脚本。

## 2. 现行目录

| 目录 | 作用 | 是否是现行事实源 |
| --- | --- | --- |
| `frontend/src` | Vue 3 前端源码 | 是 |
| `frontend/src/generated` | 根据能力目录生成的前端只读清单、Schema 与包内容哈希 | 是；漂移时前端构建失败，禁止手工修改 |
| `capabilities` | 五个 Skill 及两个工具的真实说明、规则、脚本、资源和唯一 capability.json | 是；清单与能力内容一起维护，构建时生成其他副本 |
| `contracts` | 前端、后端和适配器共用的 Capability Manifest Schema | 是；只有这一份权威合同 |
| `frontend/src/tools/runtime` | 已审查的浏览器执行器和服务端处理器注册表 | 是 |
| `backend` | Java 8 / Spring Boot 多模块后端源码 | 是 |
| `docs` | 架构、接入、部署和集成说明 | 是 |
| `bridge` | IMA 本地摆渡程序 | 是 |
| `migration` | 存量能力迁移包和迁移记录 | 迁移用途 |
| `scripts` | 本地配置和联调脚本 | 辅助用途 |

正式发布后，后台 `capability_asset` 和 `capability_version` 是已发布能力资料的运行时事实源；前端内置清单不能替代后台审核和发布记录。

## 3. 兼容与参考内容

- 旧静态平台和开发交接副本未包含在公开仓库中；现行页面只维护 `frontend/src`；
- `frontend/src/skillCatalog.ts`、`skillExperience.ts` 和旧 Skill 详情仍服务于迁移期兼容页面；
- `frontend/src/tools/definitions` 与 `frontend/src/tools/schema` 保留旧 `tool.json 1.0` 的兼容样本；
- 上述内容只有在对应能力完成后台迁移、详情、下载和运行验证后才能删除。

修改新功能时应先判断是否属于兼容路径。除迁移修复外，不要继续向兼容文件追加新的业务事实。

## 4. 本地生成物

以下内容不是源码，已通过 `.gitignore` 排除：

- `frontend/node_modules`、`frontend/.corepack`；
- `frontend/dist`、`frontend/target`；
- `backend/**/target`、`.m2-cache`；
- WAR、日志、`.env` 和根目录 `backend.zip`。

这些文件可能仍用于本地调试或既有部署，不应在未确认发布和回滚用途前删除。制作交付包时应按部署手册从构建结果取件，不要直接压缩整个工作目录。

## 5. 修改入口

- 修改能力资料：优先修改或导入 Capability Manifest 2.1；
- 修改统一详情：`frontend/src/components/CapabilityDetailFrame.vue`；
- 修改能力目录：`frontend/src/components/CapabilityCatalog.vue`；
- 修改文档转换交互：`frontend/src/components/tools/DocumentTransformRenderer.vue`；
- 增加浏览器算法：实现执行器并登记到 `frontend/src/tools/runtime/registry.ts`；
- 增加服务端任务：实现后端接口，并登记到 `frontend/src/tools/runtime/rendererRegistry.ts`；
- 修改能力持久化和发布规则：`backend/portal-skill`。

## 6. 当前整理顺序

1. 将 6 个内置能力逐项迁入后台并验证；
2. 补齐真实交付包、审核记录和服务联调；
3. 确认目录、详情、下载和运行均以后台及 2.1 清单为准；
4. 再移除旧 Skill 数据、旧工具定义和重复文档；
5. 最后整理部署产物，不在迁移中途大规模搬目录。
