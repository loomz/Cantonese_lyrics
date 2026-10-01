const store = require('../../utils/store')
const api = require('../../utils/lyrics-api')

Page({
  data: {
    apiserverUrl: '',
    showTone: false,
    testing: false,
    testResult: ''
  },

  onLoad() {
    const s = store.getSettings()
    this.setData({
      apiserverUrl: s.apiserverUrl,
      showTone: !!s.showTone
    })
  },

  onUrl(e) {
    this.setData({ apiserverUrl: e.detail.value })
  },

  onTone(e) {
    this.setData({ showTone: e.detail.value })
  },

  save() {
    store.setSettings({
      apiserverUrl: this.data.apiserverUrl.trim(),
      showTone: !!this.data.showTone
    })
    wx.showToast({ title: '已保存', icon: 'success' })
  },

  // 用当前输入框里的地址测试（不要求先保存）
  test() {
    if (this.data.testing) return
    this.setData({ testing: true, testResult: '' })
    api
      .healthWith(this.data.apiserverUrl)
      .then(() => {
        this.setData({ testing: false, testResult: '✅ 连接成功' })
      })
      .catch((err) => {
        this.setData({
          testing: false,
          testResult: '❌ ' + (err && err.message ? err.message : '连接失败')
        })
      })
  }
})
