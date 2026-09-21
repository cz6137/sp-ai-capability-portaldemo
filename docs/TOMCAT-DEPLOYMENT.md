# Tomcat 8.5 部署手册

## 1. 前置条件

- 构建机：JDK 8、Maven 3.8+、Node.js 18、pnpm 9
- 服务器：JDK 8 最新安全补丁、Tomcat 8.5 最新补丁
- 已有 PostgreSQL 和 MongoDB；为应用分别建立数据库与最小权限账号
- PostgreSQL 账号需对应用 schema 有建表权限，首次迁移还需创建 `pg_trgm` 扩展的权限

## 2. 构建

```powershell
cd frontend
pnpm install --frozen-lockfile
pnpm build
mvn -s ..\backend\.mvn\project-settings.xml -gs ..\backend\.mvn\project-settings.xml clean package
cd ..\backend
mvn -s .mvn/project-settings.xml -gs .mvn/project-settings.xml clean package
```

最终生成两个独立部署包：

- `backend/portal-web/target/sp-ai-portal.war`：后端 API，上下文为 `/sp-ai-portal`
- `frontend/target/sp-ai-portal-web.war`：前端页面，上下文为 `/sp-ai-portal-web`

前端通过同源地址 `/sp-ai-portal/api/v1` 调用后端，不需要配置 CORS。前端 WAR 使用 Tomcat Rewrite Valve，并以 404 error-page 作为兼容回退，支持直接刷新 Vue History 路由。

外置 Tomcat 部署时，后端没有独立监听端口。访问端口由 `${catalina.base}/conf/server.xml` 中的 HTTP Connector 决定，Tomcat 默认端口为 `8080`。

部署完成后的主要入口为：

- 前端：`/sp-ai-portal-web/`
- 后端健康检查：`/sp-ai-portal/actuator/health`
- 管理员能力资产页面：`/sp-ai-portal-web/admin/capabilities`

数据库脚本位于 `docs/sql/` 和后端 Flyway 目录。推荐让应用启动时由 Flyway 依次执行 V1、V2、V3、V4；其中 V4 `V4__unified_capability_manifest.sql` 创建统一能力资产及版本表。

若必须由 DBA 手工初始化空库，应按顺序执行 `docs/sql/01_create_sp_ai_portal.sql`、`docs/sql/02_meeting_0818_upgrade.sql`、`docs/sql/03_ima_local_bridge.sql`，再执行 `backend/portal-web/src/main/resources/db/migration/V4__unified_capability_manifest.sql`。已运行到 V3 的平台库只需补充 V4。项目已启用 `baseline-on-migrate`，不要手工修改 Flyway 历史表，也不要在同一个 schema 中混用手工升级与 Flyway 版本升级。

## 3. 外部配置

可沿用现有部署参数，也可在 `${catalina.base}/conf/sp-ai-portal/application-prod.yml` 中通过环境变量外置覆盖。生产环境推荐由部署系统或密钥管理服务注入连接信息、JWT 密钥和管理员初始化参数，不在文档、WAR 或代码仓库中保存具体凭据：

```yaml
spring:
  datasource:
    url: ${PORTAL_PG_URL}
    username: ${PORTAL_PG_USERNAME}
    password: ${PORTAL_PG_PASSWORD}
  data:
    mongodb:
      uri: ${PORTAL_MONGODB_URI}
portal:
  jwt:
    private-key: ${PORTAL_JWT_PRIVATE_KEY}
    public-key: ${PORTAL_JWT_PUBLIC_KEY}
  bootstrap:
    admin-username: ${PORTAL_ADMIN_USERNAME}
    admin-password: ${PORTAL_ADMIN_PASSWORD}
  integration:
    remote-enabled: false
```

Tomcat 启动参数加入：

```text
-Dspring.config.additional-location=file:${catalina.base}/conf/sp-ai-portal/
-Dspring.profiles.active=prod
```

JWT 私钥使用 PKCS#8、公开密钥使用 X.509 Base64/PEM。未配置密钥时应用会生成临时密钥，仅允许本地开发使用，重启后旧令牌失效。

会议纪要真实处理默认关闭。需要启用时，除保证 Spring Multipart 上限不低于录音文件上限外，还应外置配置以下环境变量；未完成配置时保持关闭，前端只展示服务未就绪状态：

| 环境变量 | 用途 |
| --- | --- |
| `PORTAL_MEETING_MINUTES_ENABLED` | 是否启用真实会议纪要处理 |
| `PORTAL_XFYUN_APP_ID`、`PORTAL_XFYUN_SECRET_KEY` | 录音文件转写服务凭据 |
| `PORTAL_AI_BASE_URL`、`PORTAL_AI_KEY` | OpenAI 兼容模型服务地址和凭据 |
| `PORTAL_AI_MODEL` | 模型名称，可覆盖当前默认值 |
| `PORTAL_MEETING_MINUTES_MAX_FILE_SIZE_MB` | 工具单文件上限，可覆盖当前默认值 |

完整处理链路、安全边界和上线测试见 [会议纪要生成器接入说明](MEETING-MINUTES-INTEGRATION.md)。

当前网络条件下 Tomcat 不能访问 IMA，因此生产保持 `remote-enabled: false`。不要在后端配置 IMA Client ID 或 API Key，使用第 4 节的本地摆渡方式同步元数据和文件内容。摆渡电脑从 IMA 下载文档，再上传到平台 GridFS；Tomcat 只接收已认证的文件上传，不直接连接 IMA。

标杆案例在 IMA 中按以下目录约定维护；平台会按名称发现根目录，递归读取业务分类和项目子目录，新文件在下次同步时自动进入对应业务分类：

```text
标杆案例归纳/
├─ 不动产管理/
│  ├─ 广州市不动产交付/
│  └─ 湛江市不动产交付/
├─ 时空数据与一张图/
├─ 自然资源一体化/
└─ 测绘调查与遥感/
```

新增案例时优先放入既有业务分类；确需新增分类时，在“标杆案例归纳”下新建一级目录。平台会为新的一级目录创建可维护的业务分类记录。不要在根目录直接放案例文件，因为根目录下的一级目录被视为业务分类。

Tomcat Connector 同时设置 `maxPostSize="330301440"`、`maxSwallowSize="330301440"`，与应用 300 MB 单文件上限匹配（315 MB 预留 multipart 边界和表单字段开销）。应用的默认请求上限为 305 MB；若通过环境变量覆盖应用限制，Connector 上限必须相应提高。

## 4. IMA 本地摆渡

在能同时访问 IMA 和内网应用的本地电脑上使用 `bridge/`：

```powershell
cd bridge
Copy-Item .env.example .env
# 编辑 .env 后先检查读取结果
npm run dry-run
# 九个环节和数量确认无误后正式同步
npm run sync
```

工具通过 IMA OpenAPI 递归读取元数据，并在本地下载每个文件流；再以管理员 JWT 调用以下后端接口：

- `POST /sp-ai-portal/api/v1/admin/bridge/ima/runs`
- `POST /sp-ai-portal/api/v1/admin/bridge/ima/runs/{runId}/assets`
- `POST /sp-ai-portal/api/v1/admin/bridge/ima/runs/{runId}/complete`
- `POST /sp-ai-portal/api/v1/admin/bridge/ima/runs/{runId}/fail`
- `POST /sp-ai-portal/api/v1/files/upload`

每个 `media_id` 在 PostgreSQL 中保持唯一。每个文件上传后都获得 GridFS 对象和 `fileId`，随后与资产绑定；相同 SHA-256 的文件自动复用。完整批次完成后才会停用 IMA 中已经不存在的摆渡条目；失败批次不改变既有条目的启用状态。同步后“在线预览”和“下载”均读取平台 GridFS；资产标题及“在 IMA 客户端中定位”按钮使用 `shareId + mediaId` 打开 IMA 自动定位链接。用户电脑须已安装 IMA 桌面客户端，并允许浏览器打开外部应用，平台不能强制启动未安装或被系统拦截的客户端。

IMA 凭据只保存在本地 `bridge/.env`，该文件已被 `.gitignore` 排除。详细配置见 `bridge/README.md`。

## 5. 管理员能力资产验收

管理员统一通过前端 `/sp-ai-portal-web/admin/capabilities` 维护 Skill 和工具。对应接口为：

- `GET /sp-ai-portal/api/v1/admin/capabilities`：查看全部能力资产；
- `POST /sp-ai-portal/api/v1/admin/capabilities/import`：以 `multipart/form-data` 上传 `manifest`，需要交付包时同时上传 `package`；
- `GET /sp-ai-portal/api/v1/capabilities/{slug}`：读取已发布能力；
- `GET /sp-ai-portal/api/v1/capabilities/{slug}/download`：下载已发布能力的交付包。

上线验收至少检查：

1. 管理员可以进入能力资产页面，普通用户访问管理接口返回无权限。
2. Capability Manifest 的 `schemaVersion` 为 `2.1`，身份、分类、使用方法、质量边界、交付方式和治理状态均能通过校验；开放在线使用的工具还必须具有可校验的 `runtime`。
3. Skill 及 `package`、`hybrid` 交付方式上传了能力包；工具填写了 `delivery.online` 的处理方式和数据边界。
4. `PUBLISHED` 状态包含审核人和审核时间；未发布能力不能从公共详情接口读取。
5. 已发布版本不能被同版本覆盖；升级时使用新版本号，并核对变更说明和下载文件。
6. 导入后 PostgreSQL 中存在能力与版本记录，需要交付包时 MongoDB GridFS 中存在对应文件；前端详情、下载和权限行为一致。

统一字段含义和接入边界见 [统一能力资产模型](CAPABILITY-ASSET-MODEL.md)。

## 6. 发布与回滚

1. 停止 Tomcat，确认进程退出。
2. 将现有 `webapps/sp-ai-portal.war` 和 `webapps/sp-ai-portal-web.war` 移入带时间戳的备份目录。
3. 删除 Tomcat 自动展开的 `webapps/sp-ai-portal/` 和 `webapps/sp-ai-portal-web/` 目录。
4. 放入两个新 WAR 并启动 Tomcat。
5. 验证 `/sp-ai-portal/actuator/health`、`/sp-ai-portal-web/`、前端路由刷新、登录、任务更新、上传、预览、下载、能力资产验收项和 IMA 跳转。
6. 回滚时停止 Tomcat，替换两个备份 WAR，清理对应展开目录并重新启动。

数据库变更由 Flyway 前向执行。若版本包含不可逆迁移，应在发布前完成 PostgreSQL 和 MongoDB 备份，应用回滚不能替代数据库恢复。

## 7. 首次登录与安全

首次启动会导入 9 个阶段、现有资产、Skill 展示数据和 112 项任务，并按现有初始化参数创建管理员。生产环境推荐在首次启动前外置覆盖管理员账号和密码，登录后再建立团队负责人和成员账号；具体凭据不得写入文档或提交代码仓库。远程 IMA/腾讯文档同步默认关闭；当前部署应保持关闭，IMA 凭据只配置在本地摆渡电脑。
