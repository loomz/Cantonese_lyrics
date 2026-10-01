const store = require('../../utils/store')
const api = require('../../utils/lyrics-api')
const pipeline = require('../../utils/pipeline')

const STAGE_TEXT = {
  fetch: '正在获取原词…',
  g2p: '正在生成粤拼…',
  glm: '正在生成谐音（可能需要 1-2 分钟）…',
  done: '完成'
}

function withSourceText(songs) {
  return songs.map((s) =>
    Object.assign({}, s, {
      sourceText:
        s.source === 'builtin' ? '内置' :
        s.source === 'llm' ? 'AI 生成' :
        s.source === 'netease' ? '网易云' :
        s.source === 'qq' ? 'QQ 音乐' : '本地'
    })
  )
}

Page({
  data: {
    query: '',
    searched: false,
    results: [],
    online: [],
    onlineLoading: false,
    onlineError: '',
    onlineProviderText: '',
    pipeline: null,
    stageText: '',
    error: '',
    library: []
  },

  onShow() {
    store.whenReady().then(() => {
      this.setData({ library: withSourceText(store.getSongs()) })
      if (this.data.searched && this.data.query.trim()) {
        this.setData({ results: withSourceText(store.search(this.data.query)) })
      }
    })
  },

  onInput(e) {
    this.setData({ query: e.detail.value })
  },

  doSearch() {
    const q = this.data.query.trim()
    if (!q) return
    this.setData({
      error: '',
      searched: true,
      results: withSourceText(store.search(q)),
      online: [],
      onlineError: '',
      onlineLoading: true,
      onlineProviderText: ''
    })
    api
      .searchSongs(q, 20)
      .then((d) => {
        this.setData({
          onlineLoading: false,
          online: (d.results || []).map((r) =>
            Object.assign({}, r, { provider: d.provider })
          ),
          onlineProviderText: d.provider === 'netease' ? '网易云' : 'QQ 音乐'
        })
      })
      .catch((err) => {
        this.setData({
          onlineLoading: false,
          onlineError: (err && err.message) || '在线搜索失败'
        })
      })
  },

  // ── 在线歌曲导入管道 ──

  startPipeline(e) {
    const idx = Number(e.currentTarget.dataset.index)
    const item = this.data.online[idx]
    if (!item || this.data.pipeline) return
    this.runPipeline(item)
  },

  runPipeline(item) {
    this.setData({
      error: '',
      pipeline: {
        stage: 'fetch',
        title: item.title,
        artist: item.artist,
        provider: item.provider,
        trackId: item.trackId
      },
      stageText: STAGE_TEXT.fetch
    })
    pipeline
      .importSong(item.provider, item.trackId, item.title, item.artist, (stage) => {
        if (stage === 'done') return
        this.setData({
          'pipeline.stage': stage,
          stageText: STAGE_TEXT[stage] || stage
        })
      })
      .then((id) => {
        store.open(id)
        this.setData({ pipeline: null })
        wx.navigateBack() // 导入成功，回主页展示
      })
      .catch((err) => {
        this.setData({
          'pipeline.stage': 'error',
          'pipeline.error': (err && err.message) || '导入失败',
          stageText: ''
        })
      })
  },

  retryPipeline() {
    const p = this.data.pipeline
    if (!p || !p.trackId) return
    this.runPipeline({
      provider: p.provider,
      trackId: p.trackId,
      title: p.title,
      artist: p.artist
    })
  },

  openSong(e) {
    const id = e.currentTarget.dataset.id
    store.open(id)
    wx.navigateBack()
  },

  deleteSong(e) {
    const id = e.currentTarget.dataset.id
    const that = this
    wx.showModal({
      title: '删除歌曲',
      content: '确定删除这首歌词吗？',
      success(res) {
        if (res.confirm) {
          store.deleteSong(id)
          that.setData({
            library: withSourceText(store.getSongs()),
            results: that.data.searched
              ? withSourceText(store.search(that.data.query))
              : []
          })
        }
      }
    })
  }
})
