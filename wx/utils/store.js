/**
 * 本地歌词仓库（SQLite via utils/db.js）+ 设置。
 * - 歌曲/歌词行存 SQLite（lyrics.db），设置仍用 wx 存储
 * - 提供 上一首/下一首、搜索、最近列表、增删、主题与歌词服务设置
 * - 旧版 cl_songs 数据由 db.js 在首次启动时自动迁移
 */
const db = require('./db')

const KEY_CURRENT = 'cl_current'
const KEY_THEME = 'cl_theme_index'
const KEY_SETTINGS = 'cl_settings'

let currentId = null
let initPromise = null

// ── 初始化 ──

function init() {
  if (!initPromise) {
    initPromise = db.init().then(() => {
      let cur = null
      try {
        cur = wx.getStorageSync(KEY_CURRENT) || null
      } catch (e) {
        cur = null
      }
      if (!cur || !db.songExists(cur)) {
        const songs = db.allSongs()
        cur = songs.length ? songs[0].id : null
      }
      currentId = cur
      try {
        wx.setStorageSync(KEY_CURRENT, currentId)
      } catch (e) {
        // ignore
      }
    })
  }
  return initPromise
}

/** 页面统一等 DB 就绪后再读数据 */
function whenReady() {
  return init()
}

// ── 歌曲 ──

function getSongs() {
  return db.allSongs()
}

function getCurrent() {
  return currentId ? db.getSong(currentId) : null
}

function open(id) {
  if (db.songExists(id)) {
    currentId = id
    db.touch(id)
    try {
      wx.setStorageSync(KEY_CURRENT, id)
    } catch (e) {
      // ignore
    }
  }
}

function move(delta) {
  const songs = db.allSongs()
  if (!songs.length) return
  const i = songs.findIndex((s) => s.id === currentId)
  const ni = i < 0 ? 0 : ((i + delta) % songs.length + songs.length) % songs.length
  open(songs[ni].id)
}

function next() {
  move(1)
}

function prev() {
  move(-1)
}

/** 本地搜索：按歌名/歌手模糊匹配 */
function search(query) {
  return db.search(query)
}

/** 最近打开的 n 首（列表弹层用） */
function getRecent(n) {
  return db.getRecent(n)
}

/** 去重检查：同 trackId 或同歌名+歌手 → 返回已有 id，否则 null */
function findDuplicate(source, trackId, title, artist) {
  return db.findDuplicate(source, trackId, title, artist)
}

function addSong(song) {
  db.addSong(song)
  open(song.id)
}

function deleteSong(id) {
  db.deleteSong(id)
  if (currentId === id) {
    const songs = db.allSongs()
    currentId = songs.length ? songs[0].id : null
  }
  try {
    wx.setStorageSync(KEY_CURRENT, currentId)
  } catch (e) {
    // ignore
  }
}

/** App onHide 时立即落盘 */
function saveNow() {
  db.saveNow()
}

// ── 主题 ──

function getThemeIndex() {
  let i = 0
  try {
    i = wx.getStorageSync(KEY_THEME) || 0
  } catch (e) {
    i = 0
  }
  return i
}

function setThemeIndex(i) {
  try {
    wx.setStorageSync(KEY_THEME, i)
  } catch (e) {
    // ignore
  }
}

// ── 设置 ──

function getSettings() {
  const def = {
    apiserverUrl: 'http://127.0.0.1:8000',
    showTone: false // 粤拼默认显示不带声调数字
  }
  let s = null
  try {
    s = wx.getStorageSync(KEY_SETTINGS) || null
  } catch (e) {
    s = null
  }
  return Object.assign({}, def, s || {})
}

function setSettings(patch) {
  const next = Object.assign({}, getSettings(), patch || {})
  try {
    wx.setStorageSync(KEY_SETTINGS, next)
  } catch (e) {
    // ignore
  }
  return next
}

module.exports = {
  init,
  whenReady,
  getSongs,
  getCurrent,
  open,
  next,
  prev,
  search,
  getRecent,
  findDuplicate,
  addSong,
  deleteSong,
  saveNow,
  getThemeIndex,
  setThemeIndex,
  getSettings,
  setSettings
}
