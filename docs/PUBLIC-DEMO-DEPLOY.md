# 公开 Demo 部署

该部署只用于会议演示，不连接正式数据库，也不展示登录页。访问者直接以管理员 Demo 身份进入首页，并可在页面顶部切换管理员与员工视角。

## 构建与启动

在项目根目录执行：

```bash
cd frontend
corepack enable
pnpm install --frozen-lockfile
pnpm build:demo
PORT=8080 pnpm start
```

如需启用会议录音转写和纪要生成，将私密配置放在服务器独立文件中，并通过
`PORTAL_MEETING_MINUTES_CONFIG` 指定路径。该文件不得进入源码、镜像或 Git：

```bash
PORTAL_MEETING_MINUTES_CONFIG=/run/secrets/meeting-minutes.json PORT=8080 pnpm start
```

启动后的站点地址为：

```text
http://服务器地址:8080/sp-ai-portal-web/
```

健康检查地址为：

```text
http://服务器地址:8080/healthz
```

## 部署边界

- 使用内置演示目录及浏览器端 Demo 状态，不写入 SQL。
- 能力包下载文件会随生产构建一起发布。
- OCR、文档转换等浏览器端工具可以直接使用。
- 会议纪要外部转写与大模型服务默认关闭；只有在服务器安全配置对应环境变量后才启用，禁止上传本机密钥文件。
- 建议由服务器反向代理到 80/443 端口并配置 HTTPS；公开演示结束后应停止服务或限制访问来源。
