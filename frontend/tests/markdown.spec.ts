import { describe, expect, it } from 'vitest'
import { renderMarkdown } from '../src/utils/markdown'

describe('markdown renderer', () => {
  it('渲染标题与粗体', () => {
    const html = renderMarkdown('# 标题\n**加粗**')
    expect(html).toContain('<h1>')
    expect(html).toContain('<strong>加粗</strong>')
  })

  it('html 关闭时脚本被转义（防 XSS）', () => {
    const html = renderMarkdown('<script>alert(1)</script>')
    expect(html).not.toContain('<script>')
  })

  it('渲染表格', () => {
    const html = renderMarkdown('| a | b |\n|---|---|\n| 1 | 2 |')
    expect(html).toContain('<table>')
    expect(html).toContain('<td>2</td>')
  })

  it('渲染代码块', () => {
    const html = renderMarkdown('```\nSELECT 1;\n```')
    expect(html).toContain('<pre><code')
  })

  it('空输入返回空串', () => {
    expect(renderMarkdown('')).toBe('')
    expect(renderMarkdown(null)).toBe('')
  })
})
