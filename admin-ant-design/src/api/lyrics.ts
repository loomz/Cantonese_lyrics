import request from './index'

export interface LyricsListParams {
  page?: number
  page_size?: number
  provider?: string
  search?: string
}

export interface LyricLine {
  jyutping: string
  jyutpingToneless: string
  mandarin: string
  homophone: string
}

export interface LyricsDoc {
  id: string
  provider: string
  trackId: string
  version: number
  generatedAt: number
  title: string
  artist: string
  language: string
  lines: LyricLine[]
}

export interface LyricsListResponse {
  items: LyricsDoc[]
  total: number
  page: number
  page_size: number
}

export interface SearchResponse {
  provider: string
  results: { trackId: string; title: string; artist: string }[]
}

// 原始歌词（从接口拉取，含时间戳，不触发生成管线）
export interface LyricLineWithTs {
  ts: number | null   // 毫秒级时间戳，无则为 null
  text: string
}

export interface LyricsRichResponse {
  title: string
  artist: string
  lines: LyricLineWithTs[]
  roma_by_index: Record<string, string> // 行下标 → 官方罗马音
}

export const lyricsApi = {
  // 获取歌词列表
  getList(params: LyricsListParams) {
    return request.get('/admin/lyrics/list', { params }) as Promise<LyricsListResponse>
  },

  // 获取歌词详情（查缓存）
  getDetail(provider: string, trackId: string) {
    return request.get(`/admin/lyrics/${provider}/${trackId}`) as Promise<LyricsDoc>
  },

  // 生成歌词（缓存/触发生成管道）
  generate(provider: string, trackId: string) {
    return request.get(`/song/${provider}/${trackId}`) as Promise<LyricsDoc>
  },

  // 刷新歌词（强制重新生成）
  refresh(provider: string, trackId: string) {
    return request.post(`/song/${provider}/${trackId}/refresh`) as Promise<LyricsDoc>
  },

  // 删除歌词
  delete(provider: string, trackId: string) {
    return request.delete(`/admin/lyrics/${provider}/${trackId}`) as Promise<void>
  },

  // 在线搜索歌曲
  search(q: string, limit?: number) {
    return request.get('/lyrics/search', { params: { q, limit } }) as Promise<SearchResponse>
  },

  // 获取原始歌词（含时间戳，不触发生成管线）
  getRawLyrics(provider: string, trackId: string) {
    return request.get(`/lyrics/${provider}/${trackId}/rich`) as Promise<LyricsRichResponse>
  }
}
