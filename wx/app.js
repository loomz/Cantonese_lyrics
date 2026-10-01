const store = require('./utils/store')

App({
  onLaunch() {
    // 初始化 SQLite 歌词库（首次建表 + 迁移旧数据 + 内置示范歌曲）
    store.init().catch((err) => {
      console.error('数据库初始化失败', err)
      wx.showModal({
        title: '初始化失败',
        content: (err && err.message) || String(err),
        showCancel: false
      })
    })
  },

  onHide() {
    // 切后台时立即把内存中的库落盘
    store.saveNow()
  }
})
