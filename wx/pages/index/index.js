const store = require('../../utils/store')
const themes = require('../../utils/themes')

// 方案 A：粤拼单独一行居中；普通话 + 中文谐音两行逐字对齐。
// 粤拼按设置显示带/不带声调数字：
//   showTone 开 → jyutping（为空回退 jyutpingToneless）
//   showTone 关 → jyutpingToneless（为空回退 jyutping）

Page({
  data: {
    song: null,
    lines: [],
    themes: themes.list,
    themeIndex: 0,
    theme: themes.list[0],
    statusBarHeight: 20,
    showThemePicker: false,
    showRecent: false,
    recent: []
  },

  onShow() {
    const sys = wx.getSystemInfoSync()
    this.setData({ statusBarHeight: sys.statusBarHeight || 20 })
    store.whenReady().then(() => this.refresh())
  },

  refresh() {
    const song = store.getCurrent()
    const settings = store.getSettings()
    const showTone = !!settings.showTone
    const lines = (song && song.lines ? song.lines : []).map((l) => {
      const jp = showTone
        ? l.jyutping || l.jyutpingToneless
        : l.jyutpingToneless || l.jyutping
      return { jyutping: jp, mandarin: l.mandarin, homophone: l.homophone }
    })
    const themeIndex = store.getThemeIndex()
    this.setData({
      song,
      lines,
      themeIndex,
      theme: themes.list[themeIndex % themes.list.length]
    })
  },

  onPrev() {
    store.prev()
    this.refresh()
  },

  onNext() {
    store.next()
    this.refresh()
  },

  onSearch() {
    wx.navigateTo({ url: '/pages/search/search' })
  },

  onSettings() {
    wx.navigateTo({ url: '/pages/settings/settings' })
  },

  onTheme() {
    this.setData({ showThemePicker: true })
  },

  closeTheme() {
    this.setData({ showThemePicker: false })
  },

  pickTheme(e) {
    const i = Number(e.currentTarget.dataset.index)
    store.setThemeIndex(i)
    this.setData({
      themeIndex: i,
      theme: themes.list[i],
      showThemePicker: false
    })
  },

  // ── 最近歌词列表 ──

  openRecent() {
    this.setData({ showRecent: true, recent: store.getRecent(20) })
  },

  closeRecent() {
    this.setData({ showRecent: false })
  },

  pickRecent(e) {
    const id = e.currentTarget.dataset.id
    store.open(id)
    this.setData({ showRecent: false })
    this.refresh()
  },

  noop() {}
})
