import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import DOMPurify from 'dompurify'
import 'highlight.js/styles/atom-one-dark.css'

const md: MarkdownIt = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
  highlight(code: string, lang: string) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return `<pre><code class="hljs language-${lang}">${hljs.highlight(code, {
          language: lang,
          ignoreIllegals: true
        }).value}</code></pre>`
      } catch (e) {
        /* fallthrough */
      }
    }
    return `<pre><code class="hljs">${md.utils.escapeHtml(code)}</code></pre>`
  }
})

/** 渲染 Markdown（浏览器端经 DOMPurify 消毒，防 XSS） */
export function renderMarkdown(text: string | null | undefined): string {
  if (!text) return ''
  const dirty = md.render(String(text))
  if (typeof window === 'undefined') {
    return dirty
  }
  return DOMPurify.sanitize(dirty, { ADD_ATTR: ['target'] })
}

export default md
