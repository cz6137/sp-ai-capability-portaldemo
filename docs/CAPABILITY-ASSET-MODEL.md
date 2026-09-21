# Skill 与工具统一能力资产模型

## 1. 当前结论

平台以“能力资产（Capability Asset）”统一管理 Skill 和工具，现行数据合同为 `Capability Manifest 2.1`。

一份 2.1 清单同时回答两类问题：

1. 这是什么能力、适用于什么场景、需要什么输入、产生什么输出、由谁维护和审核；
2. 如果它是在线工具，平台应使用哪个已登记的渲染器、执行方式、路由和处理器。

目录、统一详情、管理员预检和工具运行入口均读取同一结构。旧 `Capability Manifest 2.0` 和运行型 `tool.json 1.0` 只作为迁移期兼容输入，不再是新增能力的推荐格式。

上传清单只完成登记、校验和治理，不能把新的 Vue 组件、JavaScript 算法、Java 服务或第三方依赖动态注入已经部署的平台。

## 2. Capability Manifest 2.1

正式 JSON Schema 只有一份，位于 `contracts/capability-manifest.schema.json`。前端构建时生成只读副本并按它校验，Java 后端启动时读取同一份 Schema；平台自身的执行器、路由和文件大小对应关系仍作为语义校验处理。

### 2.1 作用范围

- 管理员录入和导入 Skill、工具的共同业务信息；
- 后端按能力和版本保存完整 JSON；
- 前台用同一详情组件展示定位、输入、输出、流程、质量、边界和交付说明；
- 管理草稿、待审核、已发布、已归档状态；
- 关联能力包或部署包，并向已发布能力提供下载；
- 为已实现的工具声明统一运行入口；
- 为迁移、检索、审核和审计提供稳定数据合同。

Manifest 不包含下载量、虚构任务数、无证据的“已验证”或固定演示结果。

### 2.2 字段分区

| 分区 | 内容 | Skill / 工具 |
| --- | --- | --- |
| `identity` | slug、名称、说明、版本、维护人、更新时间 | 共用 |
| `classification` | 阶段、标签、适用对象 | 共用 |
| `usage` | 场景、输入、输出、流程、快速上手 | 共用 |
| `quality` | 质量标准、证据、人工确认、使用边界 | 共用 |
| `delivery.package` | 环境、安装说明、包内文件 | Skill 必填；工具按交付方式填写 |
| `delivery.online` | 在线状态、路由、文件限制、处理位置、外部服务、留存与数据告知 | 工具必填；混合交付必填 |
| `governance` | 状态、审核人、审核时间、变更说明 | 共用 |
| `references` | 手册、FAQ、变更日志引用 | 共用 |
| `runtime` | 渲染器、执行方式、路由、处理器、操作和安全声明 | 已开放在线工具必填 |

`delivery.mode` 支持 `package`、`online` 和 `hybrid`。Skill 至少提供 `delivery.package`，工具至少提供 `delivery.online`，`hybrid` 必须同时提供两部分。

### 2.3 工具运行声明

`runtime` 只允许引用平台已经实现并登记的能力：

- `document-transform`：单次文本或文件转换，共用选择操作、输入、处理、预览和下载界面；
- `server-job`：后端任务型工具，必须声明平台已登记的 `handler`；
- `external-link`：跳转至经过审核的外部服务入口；
- `browser-local`：数据在浏览器内处理；
- `server-job`：由平台后端或已接入服务处理；
- `external`：由明确告知的外部系统处理。

当前浏览器执行器为 `markdown-to-docx`、`markdown-to-pdf`、`docx-to-markdown` 和 `pdf-to-markdown`；当前服务端处理器为 `meeting-minutes-v1`。

JSON 不允许携带或执行任意脚本。引用未登记执行器或处理器时，必须先开发、测试并重新部署平台。

## 3. 持久化与发布

- `capability_asset` 保存稳定的 slug、类型、名称、当前状态和当前版本指针；
- `capability_version` 保存版本号、完整 Manifest JSON、交付包引用、状态和变更说明；
- 相同 slug 的能力不能在 `skill` 和 `tool` 之间切换；
- 已发布版本不能覆盖，修改后必须提升 `identity.version`；
- 草稿或待审核版本当前允许在同一版本号下继续修订；
- 发布时必须填写 `governance.reviewer` 和 `governance.reviewedAt`；
- 公共接口只返回状态为 `PUBLISHED` 的能力；
- Skill、`package` 和 `hybrid` 首次导入时必须有交付包，后续修订可以沿用同版本原有文件。

管理员入口为 `/admin/capabilities`，管理接口要求 `ADMIN` 角色。公共详情入口为 `/capabilities/{slug}`。

## 4. 管理员导入时发生什么

### 4.1 上传 Capability Manifest 2.1

页面校验共同字段、交付规则和运行声明。通过后可以使用正式统一详情或运行渲染器预览，并向 `/api/v1/admin/capabilities/import` 提交 Manifest 和可选交付包。

### 4.2 兼容旧格式

管理员页面仍可读取 2.0 清单和旧运行型 `tool.json 1.0`。旧工具定义会先转换成 2.1 清单并进入统一预览，管理员需要补齐分类、质量、审核和交付信息。

兼容转换不等于安装工具代码。旧格式只用于迁移存量，不应用于新建能力；迁移完成后应逐步移除旧定义和对应文档。

## 5. 当前迁移状态

- `capabilities/<slug>/capability.json` 是每个能力唯一的 2.1 源码清单；
- `scripts/sync_capability_catalog.py` 根据这些清单和实际包内容生成 `frontend/src/generated`，并记录包内容哈希；任何漂移都会让前端构建失败；
- `frontend/src/migratedCapabilities.ts` 只发现、解包和校验生成的数据；
- `frontend/src/toolCatalog.ts` 从 2.1 清单派生现有运行组件需要的兼容对象，不再直接读取旧 `tools/definitions`；
- `frontend/src/skillCatalog.ts`、`skillExperience.ts`、旧 Skill 详情和旧工具定义仍为迁移期兼容内容；
- 后端已具备清单导入、版本存储、公共详情和交付包下载；源码不会把 6 个内置能力自动写入数据库，需在目标环境逐项导入并验收。

迁移应先验证后台目录、统一详情、下载和真实运行，再删除兼容内容。不能仅因新页面可预览就提前移除旧路径。

## 6. 维护边界

| 变更 | 是否只需管理员导入 | 是否需要开发和重新部署 |
| --- | --- | --- |
| 修改名称、场景、输入输出、质量边界、维护人 | 是 | 否 |
| 上传新版本 Skill 包 | 是 | 否 |
| 登记已有在线工具的治理信息 | 是 | 否 |
| 为已实现工具更新统一详情资料 | 是 | 否 |
| 增加引用现有渲染器和执行器的源码内置工具 | 否 | 是，需要加入清单并构建 |
| 增加新的浏览器处理算法 | 否 | 是，需要注册执行器并构建 |
| 增加新的交互模型 | 否 | 是，需要开发渲染器并构建 |
| 增加服务端任务或外部服务 | 否 | 是，需要后端或服务接入和部署 |

具体接入步骤见 [《管理员能力接入手册》](CAPABILITY-INTAKE-GUIDE.md)，目录边界见 [《项目目录与事实源》](PROJECT-STRUCTURE.md)。
