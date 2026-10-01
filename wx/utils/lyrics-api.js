/**
 * apiserver 客户端（FastAPI，见仓库 apiserver/）。
 * 端点：
 *   GET  /health                      健康检查（设置页「测试连接」）
 *   GET  /api/lyrics/search?q=&limit= 在线搜歌（网易云主 / QQ 备）
 *   GET  /api/lyrics/{p}/{id}         取歌词（已清洗）
 *   POST /api/g2p                     粤拼转换（带/不带声调数字两版）
 *
 * 注意：真机/开发者工具访问 http 地址需在开发者工具勾选
 * 「不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书」，
 * 且手机要填电脑的局域网 IP（如 http://192.168.1.10:8000）。
 */
const store = require('./store')

const DEFAULT_URL = 'http://127.0.0.1:8000'

function base() {
  const s = store.getSettings()
  return String(s.apiserverUrl || DEFAULT_URL).trim().replace(/\/+$/, '') || DEFAULT_URL
}

function doRequest(fullUrl, options) {
  options = options || {}
  return new Promise((resolve, reject) => {
    wx.request({
      url: fullUrl,
      method: options.method || 'GET',
      data: options.data,
      header: { 'Content-Type': 'application/json' },
      timeout: options.timeout || 30000,
      success(res) {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(res.data)
        } else {
          let msg = 'HTTP ' + res.statusCode
          try {
            const detail = res.data && (res.data.detail || JSON.stringify(res.data))
            if (detail) msg += '：' + String(detail).slice(0, 150)
          } catch (e) {
            // ignore
          }
          reject(new Error(msg))
        }
      },
      fail(err) {
        reject(
          new Error(
            '无法连接歌词服务 ' + fullUrl + '：' +
              (err && err.errMsg ? err.errMsg : '网络错误') +
              '（手机/真机请填电脑局域网 IP；开发者工具需勾选「不校验合法域名」）'
          )
        )
      }
    })
  })
}

/** 健康检查 */
function health() {
  return doRequest(base() + '/health', { timeout: 5000 })
}

/** 用指定地址做健康检查（设置页未保存时测试当前输入） */
function healthWith(url) {
  const u = String(url || '').trim().replace(/\/+$/, '')
  if (!u) return Promise.reject(new Error('歌词服务地址未填写'))
  return doRequest(u + '/health', { timeout: 5000 })
}

/** 在线搜歌 → {provider, results: [{trackId, title, artist}]} */
function searchSongs(q, limit) {
  return doRequest(
    base() + '/api/lyrics/search?q=' + encodeURIComponent(q) + '&limit=' + (limit || 20),
    { timeout: 15000 }
  )
}

/** 取歌词 → {title, artist, lines: [string]} */
function fetchLyrics(provider, trackId) {
  return doRequest(
    base() + '/api/lyrics/' + encodeURIComponent(provider) + '/' + encodeURIComponent(trackId),
    { timeout: 30000 }
  )
}

/** 粤拼转换 → {lines: [{with_tone, without_tone}]} */
function g2p(lines) {
  return doRequest(base() + '/api/g2p', {
    method: 'POST',
    data: { lines: lines },
    timeout: 60000
  })
}

module.exports = { health, healthWith, searchSongs, fetchLyrics, g2p, DEFAULT_URL }
