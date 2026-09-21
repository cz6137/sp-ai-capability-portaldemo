import DOMPurify from 'dompurify'
import html2pdf from 'html2pdf.js'
import mammoth from 'mammoth'
import { marked } from 'marked'
import * as pdfjs from 'pdfjs-dist'
import pdfWorkerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'
import TurndownService from 'turndown'
import { gfm } from 'turndown-plugin-gfm'
import {
  AlignmentType,
  Document,
  ExternalHyperlink,
  HeadingLevel,
  LevelFormat,
  Packer,
  Paragraph,
  Table,
  TableCell,
  TableRow,
  TextRun,
  WidthType,
  type ParagraphChild,
} from 'docx'

pdfjs.GlobalWorkerOptions.workerSrc = pdfWorkerUrl

// Bundle PDF.js resources with the app so Chinese text extraction works offline.
const pdfAssets = import.meta.glob<string>([
  '/node_modules/pdfjs-dist/cmaps/*.bcmap',
  '/node_modules/pdfjs-dist/standard_fonts/*.{pfb,ttf}',
  '/node_modules/pdfjs-dist/wasm/*.wasm',
], { eager: true, query: '?url', import: 'default' })
class LocalPdfDataFactory {
  async fetch({ kind, filename }: { kind: string; filename: string }) {
    const folder = ({ cMapUrl: 'cmaps', standardFontDataUrl: 'standard_fonts', wasmUrl: 'wasm' } as Record<string, string>)[kind]
    const url = folder && pdfAssets[`/node_modules/pdfjs-dist/${folder}/${filename}`]
    if (!url) throw new Error('所需 PDF 字符或字体资源未包含在当前版本中')
    const response = await fetch(url)
    if (!response.ok) throw new Error('本地 PDF 字符资源读取失败，请刷新页面后重试')
    return new Uint8Array(await response.arrayBuffer())
  }
}

export interface ConversionResult {
  markdown: string
  warnings: string[]
}

const headingLevels = [
  HeadingLevel.HEADING_1,
  HeadingLevel.HEADING_2,
  HeadingLevel.HEADING_3,
  HeadingLevel.HEADING_4,
  HeadingLevel.HEADING_5,
  HeadingLevel.HEADING_6,
] as const

export async function renderMarkdown(markdown: string) {
  const rendered = await marked.parse(markdown, { gfm: true, breaks: false })
  // Sanitize before insertion: a local document preview must not fetch remote images.
  const keepLocalImages = (_node: Element, data: { attrName: string; attrValue: string; keepAttr: boolean }) => {
    if (data.attrName === 'src' && !/^data:image\/(png|jpeg|gif|webp);base64,/i.test(data.attrValue)) data.keepAttr = false
  }
  DOMPurify.addHook('uponSanitizeAttribute', keepLocalImages)
  try {
    return DOMPurify.sanitize(rendered, {
      USE_PROFILES: { html: true },
      FORBID_TAGS: ['style', 'audio', 'video', 'source', 'track'],
      FORBID_ATTR: ['style', 'srcset', 'background', 'poster'],
      ADD_ATTR: ['target', 'rel'],
    })
  } finally { DOMPurify.removeHook('uponSanitizeAttribute') }
}

export async function markdownToDocx(markdown: string, title: string) {
  const html = await renderMarkdown(markdown)
  const root = new DOMParser().parseFromString(`<main>${html}</main>`, 'text/html').querySelector('main')!
  const children = domBlocksToDocx(root)
  const document = new Document({
    title,
    creator: '华南大区 AI 赋能交付平台',
    styles: {
      default: {
        document: {
          run: { font: 'Microsoft YaHei', size: 22, color: '172033' },
          paragraph: { spacing: { line: 360, after: 160 } },
        },
      },
      paragraphStyles: [
        { id: 'Title', name: '标题', basedOn: 'Normal', next: 'Normal', quickFormat: true, run: { size: 38, bold: true, color: '172033' }, paragraph: { spacing: { before: 180, after: 260 } } },
        { id: 'Heading1', name: '标题 1', basedOn: 'Normal', next: 'Normal', quickFormat: true, run: { size: 32, bold: true, color: '172033' }, paragraph: { spacing: { before: 300, after: 160 } } },
        { id: 'Heading2', name: '标题 2', basedOn: 'Normal', next: 'Normal', quickFormat: true, run: { size: 28, bold: true, color: 'A32D2D' }, paragraph: { spacing: { before: 260, after: 140 } } },
        { id: 'Heading3', name: '标题 3', basedOn: 'Normal', next: 'Normal', quickFormat: true, run: { size: 25, bold: true, color: '172033' }, paragraph: { spacing: { before: 220, after: 120 } } },
      ],
    },
    numbering: {
      config: [{
        reference: 'ordered-list',
        levels: Array.from({ length: 9 }, (_, level) => ({
          level,
          format: LevelFormat.DECIMAL,
          text: `%${level + 1}.`,
          alignment: AlignmentType.START,
          style: {
            paragraph: {
              indent: { left: 720 + level * 360, hanging: 360 },
            },
          },
        })),
      }],
    },
    sections: [{
      properties: {
        page: {
          margin: { top: 1134, right: 1134, bottom: 1134, left: 1134 },
        },
      },
      children: children.length ? children : [new Paragraph('')],
    }],
  })
  return Packer.toBlob(document)
}

export async function markdownToPdf(element: HTMLElement, fileName: string) {
  const clone = element.cloneNode(true) as HTMLElement
  clone.classList.add('pdf-document')
  clone.style.width = '180mm'
  clone.style.maxWidth = 'none'
  clone.style.padding = '0'
  clone.style.background = '#ffffff'
  await html2pdf().set({
    margin: [14, 15, 16, 15],
    filename: ensureExtension(fileName, 'pdf'),
    image: { type: 'jpeg', quality: 0.98 },
    enableLinks: true,
    html2canvas: { scale: 2, useCORS: true, backgroundColor: '#ffffff', letterRendering: true },
    jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' },
  }).from(clone).save()
}

export async function wordToMarkdown(file: File): Promise<ConversionResult> {
  const arrayBuffer = await file.arrayBuffer()
  const result = await mammoth.convertToHtml({ arrayBuffer }, {
    convertImage: mammoth.images.imgElement(async image => ({
      src: `data:${image.contentType};base64,${await image.readAsBase64String()}`,
    })),
    styleMap: [
      "p[style-name='Title'] => h1:fresh",
      "p[style-name='Subtitle'] => blockquote > p:fresh",
    ],
  })
  const converter = new TurndownService({
    headingStyle: 'atx',
    bulletListMarker: '-',
    codeBlockStyle: 'fenced',
    emDelimiter: '*',
    strongDelimiter: '**',
  })
  converter.use(gfm)
  converter.addRule('removeEmptyLinks', {
    filter: node => node.nodeName === 'A' && !node.textContent?.trim(),
    replacement: () => '',
  })
  const markdown = normalizeMarkdown(converter.turndown(normalizeWordHtml(result.value)))
  const warnings = result.messages.map(item => item.message)
  if (/data:image\//.test(markdown)) warnings.push('Word 中的图片已以内嵌数据保存，Markdown 文件可能较大。')
  return { markdown, warnings }
}

export async function pdfToMarkdown(file: File): Promise<ConversionResult> {
  const task = pdfjs.getDocument({ data: new Uint8Array(await file.arrayBuffer()), BinaryDataFactory: LocalPdfDataFactory, useWorkerFetch: false, cMapPacked: true })
  try {
  const pdf = await task.promise
  const pages: string[] = []
  let extractedCharacters = 0
  let simpleTableCandidate = false

  for (let pageNumber = 1; pageNumber <= pdf.numPages; pageNumber += 1) {
    const page = await pdf.getPage(pageNumber)
    const content = await page.getTextContent()
    const rawItems = content.items
      .filter((item): item is typeof item & { str: string; transform: number[]; width: number; height: number } => 'str' in item && Boolean(item.str.trim()))
      .map(item => ({ text: item.str.trim(), x: item.transform[4], y: item.transform[5], height: Math.abs(item.height || item.transform[3] || 10), width: item.width || 0 }))
    extractedCharacters += rawItems.reduce((sum, item) => sum + item.text.length, 0)
    if (!rawItems.length) continue

    const bodyHeight = median(rawItems.map(item => item.height).filter(value => value > 0)) || 10
    const lines = groupPdfLines(rawItems)
    if (lines.some(line => line.largeGaps >= 2)) simpleTableCandidate = true
    const pageMarkdown = lines.map(line => pdfLineToMarkdown(line, bodyHeight)).filter(Boolean).join('\n\n')
    pages.push(pdf.numPages > 1 ? `<!-- 第 ${pageNumber} 页 -->\n\n${pageMarkdown}` : pageMarkdown)
  }

  if (extractedCharacters === 0) throw new Error('没有检测到可提取文字。该文件可能是扫描版 PDF，首版暂不提供 OCR。')
  const warnings = ['PDF 是版式文件，转换结果不会完整保留原始分页、字体和精确布局。']
  if (simpleTableCandidate) warnings.push('检测到可能的表格或多栏排版，请重点检查列顺序和表格结构。')
  return { markdown: normalizeMarkdown(pages.join('\n\n---\n\n')), warnings }
  } finally { await task.destroy() }
}

function domBlocksToDocx(root: ParentNode): Array<Paragraph | Table> {
  const result: Array<Paragraph | Table> = []
  root.childNodes.forEach(node => {
    if (node.nodeType === Node.TEXT_NODE) {
      const text = node.textContent?.trim()
      if (text) result.push(new Paragraph({ children: [new TextRun(text)] }))
      return
    }
    if (!(node instanceof HTMLElement)) return
    const tag = node.tagName.toLowerCase()
    if (/^h[1-6]$/.test(tag)) {
      result.push(new Paragraph({ heading: headingLevels[Number(tag.slice(1)) - 1], children: inlineChildren(node) }))
    } else if (tag === 'p') {
      result.push(new Paragraph({ children: inlineChildren(node) }))
    } else if (tag === 'ul' || tag === 'ol') {
      appendList(node, result, tag === 'ol', 0)
    } else if (tag === 'blockquote') {
      result.push(new Paragraph({ children: inlineChildren(node), indent: { left: 540 }, border: { left: { color: 'A32D2D', size: 16, space: 12, style: 'single' } } }))
    } else if (tag === 'pre') {
      result.push(new Paragraph({ children: [new TextRun({ text: node.textContent || '', font: 'Consolas', size: 19, color: '263247' })], shading: { fill: 'F3F5F8' }, spacing: { before: 120, after: 160 } }))
    } else if (tag === 'table') {
      result.push(htmlTableToDocx(node as HTMLTableElement))
    } else if (tag === 'hr') {
      result.push(new Paragraph({ text: '────────────────────────', alignment: AlignmentType.CENTER }))
    } else {
      const nested = domBlocksToDocx(node)
      if (nested.length) result.push(...nested)
      else if (node.textContent?.trim()) result.push(new Paragraph({ children: inlineChildren(node) }))
    }
  })
  return result
}

function appendList(list: HTMLElement, target: Array<Paragraph | Table>, ordered: boolean, level: number) {
  const items = Array.from(list.children).filter(child => child.tagName.toLowerCase() === 'li') as HTMLElement[]
  items.forEach(item => {
    const inlineRoot = item.cloneNode(true) as HTMLElement
    inlineRoot.querySelectorAll(':scope > ul, :scope > ol').forEach(child => child.remove())
    const children = inlineChildren(inlineRoot)
    target.push(new Paragraph({
      children,
      bullet: ordered ? undefined : { level: Math.min(level, 8) },
      numbering: ordered ? { reference: 'ordered-list', level: Math.min(level, 8) } : undefined,
    }))
    Array.from(item.children).filter(child => ['ul', 'ol'].includes(child.tagName.toLowerCase())).forEach(child => appendList(child as HTMLElement, target, child.tagName.toLowerCase() === 'ol', level + 1))
  })
}

function normalizeWordHtml(html: string) {
  const document = new DOMParser().parseFromString(`<main>${html}</main>`, 'text/html')
  const root = document.querySelector('main')!

  root.querySelectorAll('table').forEach(table => {
    const rows = Array.from(table.querySelectorAll('tr'))
    let body = table.querySelector('tbody')
    if (rows.length > 1 && !body) {
      body = document.createElement('tbody')
      table.appendChild(body)
    }

    rows.forEach((row, rowIndex) => {
      if (rowIndex > 0 && row.parentElement?.tagName.toLowerCase() === 'thead') body?.appendChild(row)
      Array.from(row.children).forEach(cell => {
        const targetTag = rowIndex === 0 ? 'th' : 'td'
        let normalizedCell = cell
        if (cell.tagName.toLowerCase() !== targetTag) {
          const replacement = document.createElement(targetTag)
          Array.from(cell.attributes).forEach(attribute => replacement.setAttribute(attribute.name, attribute.value))
          while (cell.firstChild) replacement.appendChild(cell.firstChild)
          cell.replaceWith(replacement)
          normalizedCell = replacement
        }

        Array.from(normalizedCell.querySelectorAll('p')).forEach((paragraph, paragraphIndex) => {
          if (paragraphIndex > 0) paragraph.before(document.createElement('br'))
          paragraph.replaceWith(...Array.from(paragraph.childNodes))
        })
      })
    })
  })

  return root.innerHTML
}

function inlineChildren(root: ParentNode, style: { bold?: boolean; italics?: boolean; strike?: boolean; font?: string; color?: string; underline?: boolean } = {}): ParagraphChild[] {
  const children: ParagraphChild[] = []
  root.childNodes.forEach(node => {
    if (node.nodeType === Node.TEXT_NODE) {
      if (node.textContent) children.push(new TextRun({ text: node.textContent, ...style, underline: style.underline ? { type: 'single' } : undefined }))
      return
    }
    if (!(node instanceof HTMLElement)) return
    const tag = node.tagName.toLowerCase()
    if (tag === 'br') { children.push(new TextRun({ break: 1 })); return }
    if (tag === 'img') { children.push(new TextRun({ text: `[图片：${node.getAttribute('alt') || '未命名'}]`, color: '687386', italics: true })); return }
    if (tag === 'a') {
      const link = node.getAttribute('href') || ''
      children.push(new ExternalHyperlink({ link, children: [new TextRun({ text: node.textContent || link, color: '2563EB', underline: { type: 'single' } })] }))
      return
    }
    const next = { ...style }
    if (tag === 'strong' || tag === 'b') next.bold = true
    if (tag === 'em' || tag === 'i') next.italics = true
    if (tag === 's' || tag === 'del') next.strike = true
    if (tag === 'code') { next.font = 'Consolas'; next.color = '9A3412' }
    children.push(...inlineChildren(node, next))
  })
  return children.length ? children : [new TextRun('')]
}

function htmlTableToDocx(table: HTMLTableElement) {
  const rows = Array.from(table.rows).map((row, rowIndex) => new TableRow({
    ...(rowIndex === 0 ? { tableHeader: true } : {}),
    children: Array.from(row.cells).map(cell => new TableCell({
      children: [new Paragraph({ children: inlineChildren(cell) })],
      shading: rowIndex === 0 ? { fill: 'EDEFF3' } : undefined,
    })),
  }))
  return new Table({ rows, width: { size: 100, type: WidthType.PERCENTAGE } })
}

interface PdfTextItem { text: string; x: number; y: number; height: number; width: number }
interface PdfLine { text: string; maxHeight: number; largeGaps: number }

function groupPdfLines(items: PdfTextItem[]): PdfLine[] {
  const sorted = [...items].sort((a, b) => Math.abs(b.y - a.y) > 3 ? b.y - a.y : a.x - b.x)
  const rows: Array<{ y: number; items: PdfTextItem[] }> = []
  sorted.forEach(item => {
    const row = rows.find(value => Math.abs(value.y - item.y) <= Math.max(2.5, item.height * 0.28))
    if (row) row.items.push(item)
    else rows.push({ y: item.y, items: [item] })
  })
  return rows.sort((a, b) => b.y - a.y).map(row => {
    const lineItems = row.items.sort((a, b) => a.x - b.x)
    let largeGaps = 0
    const chunks: string[] = []
    lineItems.forEach((item, index) => {
      const previous = lineItems[index - 1]
      if (previous) {
        const gap = item.x - (previous.x + previous.width)
        if (gap > Math.max(18, previous.height * 2.4)) { chunks.push(' | '); largeGaps += 1 }
        else if (!/[\s\-—/(]$/.test(chunks[chunks.length - 1] || '') && !/^[,.;:!?，。；：！？）】]/.test(item.text)) chunks.push(' ')
      }
      chunks.push(item.text)
    })
    return { text: chunks.join('').replace(/\s+([，。；：！？）】])/g, '$1').trim(), maxHeight: Math.max(...lineItems.map(item => item.height)), largeGaps }
  })
}

function pdfLineToMarkdown(line: PdfLine, bodyHeight: number) {
  let text = line.text
  if (!text) return ''
  if (line.largeGaps >= 2 && text.includes('|')) text = `| ${text.replace(/^\|\s*|\s*\|$/g, '').split('|').map(value => value.trim()).join(' | ')} |`
  if (/^[•●▪◦]\s*/.test(text)) return `- ${text.replace(/^[•●▪◦]\s*/, '')}`
  if (/^\d+[.)、]\s*/.test(text)) return text.replace(/^(\d+)[)）、]\s*/, '$1. ')
  if (line.maxHeight >= bodyHeight * 1.75 && text.length < 80) return `# ${text}`
  if (line.maxHeight >= bodyHeight * 1.4 && text.length < 100) return `## ${text}`
  if (line.maxHeight >= bodyHeight * 1.18 && text.length < 120) return `### ${text}`
  return text
}

function median(values: number[]) {
  if (!values.length) return 0
  const sorted = [...values].sort((a, b) => a - b)
  const middle = Math.floor(sorted.length / 2)
  return sorted.length % 2 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2
}

export function normalizeMarkdown(value: string) {
  return value.replace(/\r\n/g, '\n').replace(/[ \t]+\n/g, '\n').replace(/\n{3,}/g, '\n\n').trim() + '\n'
}

export function fileStem(name: string) {
  return name.replace(/\.[^.]+$/, '').replace(/[\\/:*?"<>|]/g, '-').trim() || '转换文档'
}

export function ensureExtension(name: string, extension: string) {
  const clean = fileStem(name)
  return `${clean}.${extension.replace(/^\./, '')}`
}

export function downloadBlob(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = name
  link.click()
  window.setTimeout(() => URL.revokeObjectURL(url), 1500)
}
