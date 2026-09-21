# 工具运行接入约定

工具体验是核心入口：目录和详情首屏必须保留“进入体验模式”，不能只剩说明页。`runtime/availability.ts` 将工作台入口与服务端处理授权分开判断。本地资料模式可以运行已登记的浏览器转换；会议工作台在未接通时也可进入，但必须传入 `preview`，禁止网络提交与模拟结果。生产下架、未发布和后台读取失败不得通过内置资料绕过。

工具的业务资料、治理信息和运行声明统一保存在 `capabilities/<slug>/capability.json` 的 `Capability Manifest 2.1` 中。前端使用 `frontend/src/generated` 下的构建产物；本目录只维护平台已经实现的运行代码、注册表和旧格式兼容材料。

## 目录职责

- `runtime/registry.ts`：已经注册的浏览器执行器编号；
- `runtime/executors.ts`：浏览器本地执行器实现及调用入口；
- `runtime/rendererRegistry.ts`：已经登记的服务端任务处理器和对应页面；
- `frontend/src/components/tools/*Renderer.vue`：可复用的工具交互渲染器；
- `frontend/src/toolCatalog.ts`：从 2.1 内置清单派生运行组件使用的兼容对象；
- `definitions/*/tool.json`、`schema/tool.schema.json`：旧 `tool.json 1.0` 的迁移兼容材料，不是新增工具的事实源。

## 重要边界

内置清单通过 Vite 的 `import.meta.glob` 在开发启动或构建时发现，并编入前端产物。这不等于生产环境动态安装：

- 管理员上传 JSON 不会把文件写入源码目录；
- 上传不能新增 Vue 渲染器或注入 JavaScript 执行器；
- 上传不能部署后端接口、模型服务或第三方依赖；
- 新增或修改内置运行能力后必须重新构建和部署前端；
- JSON 只能引用注册表中已经审查过的执行器或处理器。

## 接入文档转换类工具

适用条件：输入是 Markdown 或单个文件，结果是文件或 Markdown，交互可以表达为“选择操作—提供输入—处理—预览—下载”。

1. 在 `capabilities/<slug>/capability.json` 新建或更新唯一的 `Capability Manifest 2.1`，再运行 `pnpm run sync:capabilities` 更新前端生成物。
2. 将 `runtime.renderer` 设为 `document-transform`，`runtime.executionMode` 设为 `browser-local`。
3. 在 `runtime.operations` 配置输入、输出和执行器编号。
4. 确认执行器已在 `runtime/registry.ts` 注册，且 `runtime/executors.ts` 有实现。
5. 重新启动或构建前端，让内置清单进入构建产物。
6. 使用真实文件验证格式限制、预览、下载和错误提示。
7. 从管理员接入页导入同一份 2.1 清单，补齐交付和审核资料并保存到后台。

如果执行器已经存在，不需要为每个工具新建 Vue 页面。如果执行器不存在，只修改 JSON 无法让工具运行。

## 服务端任务

录音转写、异步处理、多阶段人工确认等工具使用 `runtime.renderer: server-job`，并通过 `runtime.handler` 引用 `rendererRegistry.ts` 中已登记的处理器。

服务端任务必须有真实后端接口或外部服务、权限控制、任务状态、失败恢复、数据清理和真实文件验证。它不是“上传 JSON 后自动生成算法”的扩展点。

## 数据边界

- `browser-local` 必须如实声明文件不上传；
- `server-job` 必须明确文件上限、处理位置、外部服务、留存和人工复核；
- `runtime.fileLimitMB` 应与后端限制一致；
- `securityStatement` 必须说明数据是否离开浏览器、是否调用第三方及是否留存；
- 输出必须保留人工确认要求；
- Schema 版本、执行器编号和运行模式只放在技术信息或管理页面。

## 构建前检查

- 2.1 清单可解析并通过前后端校验；
- slug、路由和 operation id 不与现有能力冲突；
- `document-transform` 的所有执行器均已注册；
- `server-job` 的处理器和后端接口均已登记；
- 输入扩展名、大小限制与实际能力一致；
- 本地处理工具没有网络上传；
- 服务端工具的权限、超时、清理和失败提示已经部署；
- 类型检查、生产构建和真实文件回归均通过。

管理员接入和审核流程见 `../../../docs/CAPABILITY-INTAKE-GUIDE.md`。
