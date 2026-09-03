import { describe, expect, it, vi } from 'vitest'
import { parseSseFrame, splitSseFrames, streamChat } from '../src/utils/sse'

vi.mock('../src/api/http', () => ({
  default: { readToken: () => '' }
}))

describe('SSE 解析器', () => {
  it('解析 event + data 帧', () => {
    const frame = parseSseFrame('event: message\ndata: {"delta":"你好"}')
    expect(frame).toEqual({ event: 'message', data: '{"delta":"你好"}' })
  })

  it('缺少 event 行时默认 message', () => {
    const frame = parseSseFrame('data: plain')
    expect(frame?.event).toBe('message')
    expect(frame?.data).toBe('plain')
  })

  it('多行 data 合并', () => {
    const frame = parseSseFrame('data: line1\ndata: line2')
    expect(frame?.data).toBe('line1\nline2')
  })

  it('仅注释的帧返回 null', () => {
    expect(parseSseFrame(': ping')).toBeNull()
  })

  it('splitSseFrames 保留不完整尾部', () => {
    const { frames, rest } = splitSseFrames(
      'event: a\ndata: 1\n\nevent: b\ndata: {"x"'
    )
    expect(frames).toHaveLength(1)
    expect(rest).toContain('event: b')
  })

  it('完整两个帧切分', () => {
    const { frames, rest } = splitSseFrames('data: 1\n\ndata: 2\n\n')
    expect(frames).toHaveLength(2)
    expect(rest).toBe('')
  })

  it('展平后端 StreamEvent 包装，页面可直接读取实时数据', async () => {
    const bytes = new TextEncoder().encode(
      'event: message\ndata: {"type":"message","data":{"delta":"你好"},"sessionId":"42","round":0}\n\n' +
      'event: done\ndata: {"type":"done","data":{"rounds":1},"sessionId":"42","round":1}\n\n'
    )
    let reads = 0
    vi.stubGlobal('fetch', vi.fn(async () => ({
      ok: true,
      body: {
        getReader: () => ({
          read: vi.fn(async () => reads++ === 0
            ? { done: false, value: bytes }
            : { done: true, value: undefined })
        })
      }
    })))
    const events: Array<{ type: string; payload: any }> = []

    await streamChat({ content: '测试', onEvent: (type, payload) => events.push({ type, payload }) })
    await vi.waitFor(() => expect(events).toHaveLength(2))

    expect(events[0]).toEqual({ type: 'message', payload: expect.objectContaining({ delta: '你好', sessionId: '42' }) })
    expect(events[1]).toEqual({ type: 'done', payload: expect.objectContaining({ rounds: 1, sessionId: '42' }) })
    vi.unstubAllGlobals()
  })

  it('收到 done 后连接复位不再误报 Network Error', async () => {
    const bytes = new TextEncoder().encode(
      'event: done\ndata: {"type":"done","data":{"rounds":1},"sessionId":"42"}\n\n'
    )
    let reads = 0
    const read = vi.fn(async () => {
      if (reads++ === 0) return { done: false, value: bytes }
      throw new Error('network error')
    })
    vi.stubGlobal('fetch', vi.fn(async () => ({
      ok: true,
      body: { getReader: () => ({ read }) }
    })))
    const eventTypes: string[] = []

    await streamChat({ content: '测试', onEvent: (type) => eventTypes.push(type) })
    await vi.waitFor(() => expect(read).toHaveBeenCalledTimes(2))

    expect(eventTypes).toEqual(['done'])
    vi.unstubAllGlobals()
  })
})
