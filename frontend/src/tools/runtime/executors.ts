import type { ToolOperationDefinition } from '../../capabilityManifest'
import {
  downloadBlob,
  ensureExtension,
  markdownToDocx,
  markdownToPdf,
  pdfToMarkdown,
  wordToMarkdown,
} from '../../utils/documentConversion'
import { hasRegisteredToolExecutor } from './registry'

export interface ToolExecutionRequest {
  operation: ToolOperationDefinition
  sourceText: string
  sourceFile?: File
  previewElement?: HTMLElement
  outputName: string
}

export interface ToolExecutionResult {
  markdown?: string
  warnings: string[]
  message: string
}

type ToolExecutor = (request: ToolExecutionRequest) => Promise<ToolExecutionResult>

const executors: Record<string, ToolExecutor> = {
  'markdown-to-docx': async ({ sourceText, outputName }) => {
    requireText(sourceText)
    const blob = await markdownToDocx(sourceText, outputName)
    downloadBlob(blob, ensureExtension(outputName, 'docx'))
    return { warnings: [], message: 'Word 文档已生成' }
  },
  'markdown-to-pdf': async ({ sourceText, previewElement, outputName }) => {
    requireText(sourceText)
    if (!previewElement) throw new Error('预览内容尚未准备完成')
    await markdownToPdf(previewElement, outputName)
    return { warnings: [], message: 'PDF 已生成' }
  },
  'docx-to-markdown': async ({ sourceFile }) => {
    const result = await wordToMarkdown(requireFile(sourceFile))
    return { ...result, message: 'Word 已在浏览器本地转换为 Markdown' }
  },
  'pdf-to-markdown': async ({ sourceFile }) => {
    const result = await pdfToMarkdown(requireFile(sourceFile))
    return { ...result, message: 'PDF 已在浏览器本地转换为 Markdown' }
  },
  'image-to-markdown': async ({ sourceFile }) => {
    const file = requireFile(sourceFile)
    const { createWorker, OEM } = await import('tesseract.js')
    const base = import.meta.env.BASE_URL.replace(/\/$/, '')
    const worker = await createWorker(['chi_sim', 'eng'], OEM.LSTM_ONLY, {
      workerPath: `${base}/ocr/worker.min.js`,
      corePath: `${base}/ocr/core/tesseract-core-lstm.wasm.js`,
      langPath: `${base}/ocr/lang`,
    })
    try {
      const { data } = await worker.recognize(file)
      const text = data.text.replace(/\r\n?/g, '\n').replace(/[ \t]+\n/g, '\n').trim()
      if (!text) throw new Error('没有识别到可用文字，请换用更清晰、方向正确的图片')
      const warnings = data.confidence < 75
        ? [`平均识别置信度约 ${Math.round(data.confidence)}%，请重点复核数字、专有名词和表格。`]
        : ['OCR 会改变原图片的排版；数字、专有名词和表格仍需人工复核。']
      return { markdown: text, warnings, message: '图片文字已在浏览器本地识别' }
    } finally {
      await worker.terminate()
    }
  },
}

export function hasToolExecutor(id: string) {
  return hasRegisteredToolExecutor(id) && Boolean(executors[id])
}

export function executeToolOperation(request: ToolExecutionRequest) {
  const executor = executors[request.operation.executor]
  if (!executor) throw new Error(`平台未注册执行器：${request.operation.executor}`)
  return executor(request)
}

function requireText(value: string) {
  if (!value.trim()) throw new Error('请先输入或载入内容')
}

function requireFile(file?: File) {
  if (!file) throw new Error('请先选择文件')
  return file
}
