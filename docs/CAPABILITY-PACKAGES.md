# 公开能力包说明

公开仓库仅保留以下能力事实源：

| 类型 | 标识 | 说明 |
| --- | --- | --- |
| Skill | `platform-skill-adapter` | “适应平台 Skill”，静态检查外部能力资料并生成待审核统一清单 |
| 工具 | `document-converter` | 浏览器文档格式转换演示 |
| 工具 | `image-ocr` | 浏览器图片文字识别演示 |
| 工具 | `meeting-minutes` | 会议纪要页面和服务端任务接口骨架 |

每份清单位于 `capabilities/<slug>/capability.json`。前端生成数据位于 `frontend/src/generated`，由下列命令重建，禁止手工维护：

```powershell
python -B -X utf8 scripts/sync_capability_catalog.py --write
```

适应平台 Skill 的独立内容在 [`capabilities/platform-skill-adapter`](../capabilities/platform-skill-adapter)。其 `scripts/adapt.py` 只做静态检查和文件整理，不执行待接入包中的脚本、不安装依赖、不联网、不发布，也不能替代代码安全审查、资料授权审查和业务验收。

公开仓库不包含其他 Skill、案例附件或历史交付包。接收方需要导入自己的能力资料并重新完成审核。
