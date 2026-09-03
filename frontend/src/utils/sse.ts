/**
 * SSE 流式响应解析（fetch + ReadableStream）。
 * 后端 SSE 事件：start / message / tool_call / tool_result / done / error
 */

export interface SseFrame {
  event: string
  data: string
}

/** 将一个 SSE 帧（以 \n\n 分隔的文本块）解析为 event/data */
export function parseSseFrame(frame: string): SseFrame | null {
  let event = 'message'
  const dataLines: string[] = []
  for (const rawLine of frame.split('\n')) {
    const line = rawLine.replace(/\r$/, '')
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart())
    }
  }
  if (dataLines.length === 0) {
    return null
  }
  return { event, data: dataLines.join('\n') }
}

/** 按 \n\n 切分缓冲区，返回完整帧与不完整剩余 */
export function splitSseFrames(buffer: string): { frames: string[]; rest: string } {
  const parts = buffer.split('\n\n')
  const rest = parts.pop() ?? ''
  return { frames: parts, rest }
}

export interface StreamChatOptions {
  sessionId?: string | number | null
  agentType?: string | null
  content: string
  onEvent: (type: string, payload: any) => void
}

/** 发起对话流式请求，返回中断函数 */
export async function streamChat(options: StreamChatOptions): Promise<() => void> {
  const controller = new AbortController()
  let terminalEventReceived = false
  const params = new URLSearchParams()
  if (options.sessionId != null) params.set('sessionId', String(options.sessionId))
  if (options.agentType) params.set('agentType', options.agentType)

  // 动态引入避免循环依赖
  const { default: http } = await import('../api/http')
  const token = http.readToken()

  void fetch(`/api/chat/stream?${params.toString()}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify({ content: options.content }),
    signal: controller.signal
  })
    .then(async (resp) => {
      if (!resp.ok || !resp.body) {
        let message = `HTTP ${resp.status}`
        try {
          const j = await resp.json()
          message = j?.message || message
        } catch (e) {
          /* ignore */
        }
        terminalEventReceived = true
        options.onEvent('error', { message })
        return
      }
      const reader = resp.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''
      for (;;) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const { frames, rest } = splitSseFrames(buffer)
        buffer = rest
        for (const frame of frames) {
          const parsed = parseSseFrame(frame)
          if (parsed && parsed.data) {
            terminalEventReceived = emitParsed(options.onEvent, parsed) || terminalEventReceived
          }
        }
      }
      const tail = parseSseFrame(buffer)
      if (tail && tail.data) {
        terminalEventReceived = emitParsed(options.onEvent, tail) || terminalEventReceived
      }
      if (!terminalEventReceived) {
        terminalEventReceived = true
        options.onEvent('error', { message: '连接意外中断，请重试' })
      }
    })
    .catch((err: any) => {
      // 某些代理会在服务端发送 done 后以 TCP reset 结束流。业务已经完成时，
      // 该传输层异常不应再向用户显示 Network Error。
      if (err?.name !== 'AbortError' && !terminalEventReceived) {
        terminalEventReceived = true
        options.onEvent('error', { message: String(err?.message || err) })
      }
    })

  return () => controller.abort()
}

function emitParsed(onEvent: (type: string, payload: any) => void, frame: SseFrame): boolean {
  try {
    const envelope = JSON.parse(frame.data)
    // 后端发送的是 StreamEvent 包装；页面消费的是事件 data。保留外层元数据，
    // 同时把 data 展平，避免 message/tool_call 等实时字段读取不到。
    const payload = envelope && typeof envelope === 'object' && envelope.data && typeof envelope.data === 'object'
      ? {
          ...envelope.data,
          sessionId: envelope.sessionId,
          messageId: envelope.messageId,
          round: envelope.round,
          timestamp: envelope.timestamp
        }
      : envelope
    onEvent(frame.event, payload)
  } catch (e) {
    onEvent(frame.event, { raw: frame.data })
  }
  return frame.event === 'done' || frame.event === 'error'
}
