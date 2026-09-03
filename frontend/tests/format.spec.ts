import { describe, expect, it } from 'vitest'
import { formatBytes, formatDateTime } from '../src/utils/format'

describe('format utils', () => {
  it('formatBytes 边界', () => {
    expect(formatBytes(0)).toBe('0 B')
    expect(formatBytes(512)).toBe('512 B')
    expect(formatBytes(2048)).toBe('2.0 KB')
    expect(formatBytes(1024 * 1024)).toBe('1.0 MB')
    expect(formatBytes(null)).toBe('-')
  })

  it('formatDateTime 统一格式', () => {
    expect(formatDateTime('2026-09-02T10:20:30')).toBe('2026-09-02 10:20:30')
    expect(formatDateTime(null)).toBe('-')
  })
})
