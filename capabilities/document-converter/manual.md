# 文档格式转换器使用手册

在统一目录打开能力详情，选择本地转换。支持 Markdown→DOCX、Markdown→PDF、DOCX→Markdown、文本型 PDF→Markdown。选择方向后拖入文件或输入 Markdown，核对预览再下载。上限 20 MB，空文件、损坏文件、格式不匹配应显示错误，不生成替代结果。

Markdown→PDF 需要当前浏览器渲染；DOCX 保留内容结构，复杂表格和浮动图片可能有差异；PDF 不支持 OCR，图片型页面无法提取文字。预览会移除远程图片地址及可加载外部资源的样式，仅允许内嵌的常见位图；文字超链接由用户点击后打开。正式材料不要依赖远程图片。成功生成不表示内容或版式已经验收。

当前交付版本是待审核草稿，公共入口保持关闭；管理员可在统一接入页载入清单，使用“运行页预览”检查浏览器转换。正式开放需完成审核与真实文件回归。

1.1.2 已在 Chrome 使用合成中文 DOCX 和两页中文文本 PDF 验证文字、表格和分页内容；无文字 PDF、损坏文件及空 Markdown 能正确拒绝。PDF 字符映射、字体和辅助资源由现有 pdfjs-dist 随前端构建，不从外部 CDN 获取。上述检查不覆盖所有复杂业务版式，也不代替下载文件的人工版式复核。

本包是现有 Java＋Vue 平台的工具集成资料与源码附件，不是独立安装程序。runtime 使用 document-transform 和四个注册执行器；主实现仍在 frontend/src/utils/documentConversion.ts，共用组件在 frontend/src/components/tools。源码索引见 SOURCES.md。包内不含 node_modules、构建目录、账户或环境配置。

按 examples/regression.md 执行真实文件回归，记录输入、浏览器、输出、人工结论及证据。移除能力由平台真实下架流程完成；本包不会改动服务、数据库或自动卸载依赖。
