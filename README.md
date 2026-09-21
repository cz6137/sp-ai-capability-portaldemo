# AI 能力资产治理平台（公开演示源码）

这是一个用于评估迁移难度和演示业务流程的公开源码仓库。平台集中展示交付基线、Skill、AI 工具和标杆案例，并提供能力清单导入、版本管理、审核、发布、下架与归档流程。

## 公开范围

- 仅保留一个 Skill：[`capabilities/platform-skill-adapter`](capabilities/platform-skill-adapter)，中文名“适应平台 Skill”。
- 保留 3 个通用工具演示：文档格式转换、图片文字识别和会议纪要。
- 不包含其他业务 Skill、案例附件、真实项目目录、IMA 文件、生产数据库、账号、密码、API Key 或服务器配置。
- 交付基线库和标杆案例库使用空白演示数据；需要由接收方导入自己的正式资料。
- 页面默认直接进入 Demo。审核和版本操作写入当前浏览器 `localStorage`，不同电脑不会共享。

公开可见不等于开放使用许可。本仓库未附开源许可证；复制、修改、部署或商用前请先取得仓库维护者授权，详见 [`NOTICE.md`](NOTICE.md)。

## 快速运行 Demo

需要 Node.js 20+ 和 pnpm 9：

```powershell
cd frontend
corepack enable
pnpm install --frozen-lockfile
pnpm build:demo
pnpm start
```

打开 `http://127.0.0.1:8080/sp-ai-portal-web/`。这只启动前端公开演示，不会启动 Java 后端、数据库、IMA 同步或外部 AI 服务。

开发模式：

```powershell
cd frontend
pnpm install --frozen-lockfile
node .\scripts\visual-mock-server.mjs
```

另开一个终端：

```powershell
cd frontend
pnpm dev
```

打开 `http://127.0.0.1:5173/sp-ai-portal-web/`。

## 能力和接口状态

“代码已保留”不代表公开 Demo 已连接正式服务。

| 模块 | 公开 Demo | 正式启用需要 |
| --- | --- | --- |
| 能力目录与详情 | 1 个适配 Skill、3 个工具清单 | 接入正式能力数据和交付包 |
| 提交、审核、发布、下架、归档 | 浏览器本地演示数据 | Java 后端、PostgreSQL、登录和权限 |
| 文件上传、预览、下载 | 静态演示或浏览器本地处理 | MongoDB GridFS、病毒扫描、配额与审计 |
| IMA 知识库同步 | 关闭，不含分享标识和文件 | IMA OpenAPI、知识库权限及摆渡配置 |
| 会议纪要转写和模型整理 | 接口与页面保留，外部处理关闭 | 语音/模型供应商、密钥、额度和合规策略 |
| OCR、文档转换 | 浏览器演示可用 | 服务端化时另接存储、任务和安全审计 |
| RAG | 未实现 | 数据源、切分、向量库、模型、权限和评测方案 |

会议纪要后端接口：

- `GET /api/v1/tools/meeting-minutes/capabilities`
- `POST /api/v1/tools/meeting-minutes/jobs`
- `GET /api/v1/tools/meeting-minutes/jobs/{id}`

没有配置外部服务时，不会上传录音或生成真实结果。

## 版本管理与审批接口

审批工作台前端地址为 `/admin/approvals`。页面在 [`frontend/src/views/CapabilityApprovalView.vue`](frontend/src/views/CapabilityApprovalView.vue)，请求封装在 [`frontend/src/api/portal.ts`](frontend/src/api/portal.ts)，正式管理员入口在 [`CapabilityManagementController.java`](backend/portal-skill/src/main/java/com/spai/portal/skill/controller/CapabilityManagementController.java)。

| 用途 | 方法与路径 |
| --- | --- |
| 审批队列 | `GET /api/v1/admin/capabilities?status=&kind=&submitter=` |
| 导入清单和交付包 | `POST /api/v1/admin/capabilities/import` |
| 版本历史 | `GET /api/v1/admin/capabilities/{assetId}/versions` |
| 单版本审计记录 | `GET /api/v1/admin/capabilities/{assetId}/versions/{versionId}/audits` |
| 提交/审核/退回/发布/下架/归档 | `POST /api/v1/admin/capabilities/{assetId}/versions/{versionId}/actions/{action}` |
| 下载指定版本 | `GET /api/v1/admin/capabilities/{assetId}/versions/{versionId}/download` |
| 全局审计 | `GET /api/v1/admin/audits?page=0&size=20` |

员工侧入口为 `/api/v1/my/capabilities`，支持查询本人提交、导入草稿、查看版本和审计、提交审核及下载交付包。接口需要登录、PostgreSQL 和 MongoDB GridFS；公开 Demo 不调用这些正式接口。

版本状态存储为 `DRAFT`、`IN_REVIEW`、`PUBLISHED`、`ARCHIVED`。页面中的 `APPROVED` 是已审核但尚未发布的视图状态。写操作使用 `expectedUpdatedAt` 做并发校验，并禁止提交人审核自己的版本。

## 完整部署结构

- `frontend/`：Vue 3、TypeScript、Element Plus；构建为 `sp-ai-portal-web.war`
- `backend/`：Java 8、Spring Boot 2.7.18、Maven 多模块；构建为 `sp-ai-portal.war`
- PostgreSQL：业务、权限、版本和审计元数据
- MongoDB GridFS：能力包和资料文件
- `bridge/`：本地 IMA 元数据与文件摆渡工具
- `capabilities/`：统一能力清单事实源；公开仓库中只有适配 Skill 和 3 个工具

后端构建：

```powershell
cd backend
mvn -s .mvn/project-settings.xml -gs .mvn/project-settings.xml package
```

完整迁移还需要接收方提供数据库、文件存储、统一登录、网络策略、外部服务权限和正式业务数据。Tomcat 结构与配置见 [`docs/TOMCAT-DEPLOYMENT.md`](docs/TOMCAT-DEPLOYMENT.md)。

## 文档

- [`docs/PROJECT-STRUCTURE.md`](docs/PROJECT-STRUCTURE.md)：源码、生成物和部署边界
- [`docs/CAPABILITY-ASSET-MODEL.md`](docs/CAPABILITY-ASSET-MODEL.md)：Capability Manifest 2.1 与状态模型
- [`docs/CAPABILITY-INTAKE-GUIDE.md`](docs/CAPABILITY-INTAKE-GUIDE.md)：能力接入、预检和审核流程
- [`docs/CAPABILITY-PACKAGES.md`](docs/CAPABILITY-PACKAGES.md)：公开能力包与重建方式
- [`docs/MEETING-MINUTES-INTEGRATION.md`](docs/MEETING-MINUTES-INTEGRATION.md)：会议纪要服务接入边界
- [`docs/PUBLIC-DEMO-DEPLOY.md`](docs/PUBLIC-DEMO-DEPLOY.md)：公开 Demo 部署
