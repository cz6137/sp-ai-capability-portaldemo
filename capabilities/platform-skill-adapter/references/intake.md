# 平台接入规则

Manifest 2.1 共用 identity、classification、usage、quality、delivery、governance、references。输入输出要写材料/结果和限制，不能用“文件一个”等占位说明替代业务含义。

Skill 包根目录必须有 SKILL.md；packageItems 使用实际相对文件路径；manual、faq、changelog 如果是本地引用，也必须存在。禁止绝对路径、父目录穿越和链接文件。未知维护人留空并阻止交付预检。

清单的识别不依赖文件名：`adapt.py` 按根目录 JSON 文件里是否存在 schemaVersion 与 kind 判定，叫 `capability.json`、`skill-manifest.json` 或其他名字都可以。目录结构也不做要求，包里没有 `references/`、`assets/` 或 `templates/` 都属正常。入口文件的校验同样不锁定 `SKILL.md`：任意根目录 Markdown 文件只要在 frontmatter 里声明了 name（或 title）与 description，即视为入口，其 name 必须与 identity.slug 一致。

packageItems 的取值规则：目录内文件较多时按顶层目录聚合成一条计数（例如「图片 358 个，其他文件 31 个」），文件较少时逐条列出。每条 detail 只能来自文件自身的标题，或脚本作者写在开头的注释与 docstring，或文件名本身；**抄不到就留空**，并会出现在报告的 needsDetail 里等人工补一句。不得用「随包实际文件」「见中文手册」这类占位话术，也不得替文件编写用途。

报告的 packageWarnings 是**逐字摘录**的明确警告与限制句（含出处文件与行号），从包内说明文档按禁止、否定与风险词筛出，未经改写、未排序、未判轻重。只因“待确认”等弱提示命中的句子进入 warningCandidates，先由人判断是操作要求、风险限制还是示例内容；代码、JSON、表格和 HTML 模板的命中不直接展示为警告。把确认保留的句子写入 `quality.warnings`，不要让平台解析 ADAPTATION-REPORT。每条警告及其他自动填充值都在 `provenance` 记录 JSON 路径和来源状态；存量清单没有来源记录时只标成未确认，不阻断发布。

报告先给出 readingPlan。机器必须先读入口文件，再读入口引用的本地规则、操作手册和补充资料；脚本仅在核对实现时读取。上传者提供原始资料即可，不要求他把 Skill 再讲一遍。机器从材料中归纳“什么任务需要、要交什么、怎么处理、产出什么、何时不能自行定案”，并保留来源。只有维护责任人、审核记录等包内不可能得知的平台登记事实，或无法通过上下文消解且会改变执行方式的矛盾，才进入 questions。

详情页面向 Skill 使用者，不展示 Manifest 字段，也不铺开包内警告。固定回答四件事：什么情况需要这个 Skill、把什么资料交给它、机器会怎么处理、会拿回什么。`quality.warnings` 和 `quality.boundaries` 是机器执行约束及维护审计材料，放在折叠的技术信息中。

工具的 runtime 是声明，不能创建执行算法。document-transform 仅支持平台登记的四个转换执行器；server-job 当前只支持 meeting-minutes-v1；external-link 当前前端尚未承接，检查器会拒绝作为可接入成果。

工具运行地址应为 /tools/{slug}。runtime 与 online 的文件上限、路由保持一致。新接入草稿 online.enabled=false；经过实际接入及审核才由平台开启。Skill 包由用户选择安装位置并按其客户端规则加载；平台不扫描本地磁盘或代为安装。

新交付统一为 capability.json + SKILL.md（Skill）/源码及集成说明（工具）+ manual.md + SOURCES.md + CHANGELOG.md + 实际资源。CHECKSUMS.json 和 ADAPTATION-REPORT.json 为整理命令生成的记录，不是平台操作审计。
