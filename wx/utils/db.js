/**
 * SQLite 本地数据库（sql.js WASM）。
 * - 库文件 lyrics.db 存于 wx.env.USER_DATA_PATH，启动时整体载入内存
 * - 写操作只改内存，防抖 1 秒落盘；App onHide 时立即落盘
 * - 首次启动自动建表，并迁移旧版 cl_songs 存储 + 内置示范歌曲
 *
 * 注意：sql.js 需要先在微信开发者工具里「工具 → 构建 npm」，
 * wasm 文件随包放在 utils/sql-wasm.wasm。
 */
const initSqlJs = require('sql.js')
const SEED = require('../data/seed_lyrics.json')

const DB_NAME = 'lyrics.db'
const KEY_MIGRATED = 'cl_db_migrated'
const KEY_LEGACY_SONGS = 'cl_songs'

const SCHEMA = [
  'CREATE TABLE IF NOT EXISTS songs (',
  '  id TEXT PRIMARY KEY,',
  '  title TEXT NOT NULL,',
  '  artist TEXT NOT NULL DEFAULT \'\',',
  '  source TEXT NOT NULL,',
  '  trackId TEXT,',
  '  createdAt INTEGER NOT NULL,',
  '  lastOpenedAt INTEGER NOT NULL',
  ')',
  'CREATE INDEX IF NOT EXISTS idx_songs_title ON songs(title)',
  'CREATE INDEX IF NOT EXISTS idx_songs_artist ON songs(artist)',
  'CREATE INDEX IF NOT EXISTS idx_songs_recent ON songs(lastOpenedAt)',
  'CREATE TABLE IF NOT EXISTS lines (',
  '  id INTEGER PRIMARY KEY AUTOINCREMENT,',
  '  songId TEXT NOT NULL REFERENCES songs(id) ON DELETE CASCADE,',
  '  seq INTEGER NOT NULL,',
  '  jyutping TEXT NOT NULL DEFAULT \'\',',
  '  jyutpingToneless TEXT NOT NULL DEFAULT \'\',',
  '  mandarin TEXT NOT NULL DEFAULT \'\',',
  '  homophone TEXT NOT NULL DEFAULT \'\',',
  '  UNIQUE(songId, seq)',
  ')',
  'CREATE INDEX IF NOT EXISTS idx_lines_song ON lines(songId)'
].join('; ')

let SQL = null
let db = null
let initPromise = null
let saveTimer = null

function dbPath() {
  return wx.env.USER_DATA_PATH + '/' + DB_NAME
}

/** 去掉粤拼每个音节尾部的声调数字 1-6（"nei5 si6" -> "nei si"） */
function stripTones(s) {
  return String(s || '').replace(/([a-z]+)([1-6])(?=\s|$)/g, '$1')
}

function init() {
  if (!initPromise) {
    initPromise = new Promise((resolve, reject) => {
      const fs = wx.getFileSystemManager()
      let wasmBinary = null
      try {
        // 不传 encoding 时 readFileSync 返回 ArrayBuffer
        wasmBinary = fs.readFileSync('/utils/sql-wasm.wasm')
      } catch (e) {
        reject(new Error('sql-wasm.wasm 加载失败：' + (e.errMsg || e.message)))
        return
      }
      initSqlJs({ wasmBinary: wasmBinary })
        .then((SQLJS) => {
          SQL = SQLJS
          let buf = null
          try {
            buf = fs.readFileSync(dbPath())
          } catch (e) {
            buf = null // 首次启动，建空库
          }
          db = buf ? new SQL.Database(buf) : new SQL.Database()
          db.exec('PRAGMA foreign_keys = ON')
          db.exec(SCHEMA)
          migrate()
          scheduleSave()
          resolve()
        })
        .catch((e) => reject(new Error('sql.js 初始化失败：' + (e && e.message ? e.message : e))))
    })
  }
  return initPromise
}

function isReady() {
  return !!db
}

// ── 迁移 ──

function migrate() {
  let migrated = false
  try {
    migrated = !!wx.getStorageSync(KEY_MIGRATED)
  } catch (e) {
    migrated = false
  }
  if (!migrated) {
    importLegacy()
    try {
      wx.setStorageSync(KEY_MIGRATED, Date.now())
    } catch (e) {
      // ignore
    }
  }
  importSeed()
}

/** 旧版 cl_songs（JSON 数组）导入 DB，成功后删除旧键 */
function importLegacy() {
  let legacy = null
  try {
    legacy = wx.getStorageSync(KEY_LEGACY_SONGS) || []
  } catch (e) {
    legacy = []
  }
  if (!Array.isArray(legacy) || !legacy.length) return
  for (const s of legacy) {
    if (!s || !s.id || songExists(s.id)) continue
    addSong({
      id: s.id,
      title: s.title || '未命名',
      artist: s.artist || '',
      source: s.source || 'local',
      trackId: s.trackId || null,
      createdAt: s.createdAt || Date.now(),
      lastOpenedAt: s.lastOpenedAt || s.createdAt || Date.now(),
      lines: (s.lines || []).map(normLine)
    })
  }
  try {
    wx.removeStorageSync(KEY_LEGACY_SONGS)
  } catch (e) {
    // ignore
  }
}

/** 内置示范歌曲（幂等：已存在则跳过） */
function importSeed() {
  const list = Array.isArray(SEED) ? SEED : [SEED]
  for (const s of list) {
    if (!s || !s.id || songExists(s.id)) continue
    addSong({
      id: s.id,
      title: s.title || '未命名',
      artist: s.artist || '',
      source: s.source || 'builtin',
      trackId: s.trackId || null,
      createdAt: s.createdAt || Date.now(),
      lastOpenedAt: s.createdAt || Date.now(),
      lines: (s.lines || []).map(normLine)
    })
  }
}

/** 旧行 {jyutping, mandarin, homophone} → 新行（补 jyutpingToneless） */
function normLine(l) {
  l = l || {}
  return {
    jyutping: l.jyutping || '',
    jyutpingToneless: l.jyutpingToneless || stripTones(l.jyutping),
    mandarin: l.mandarin || '',
    homophone: l.homophone || ''
  }
}

// ── 查询 ──

function mapSong(row) {
  return {
    id: row.id,
    title: row.title,
    artist: row.artist,
    source: row.source,
    trackId: row.trackId || null,
    createdAt: row.createdAt,
    lastOpenedAt: row.lastOpenedAt
  }
}

const SONG_COLS = 'id,title,artist,source,trackId,createdAt,lastOpenedAt'

function allSongs() {
  const stmt = db.prepare(
    'SELECT ' + SONG_COLS + ' FROM songs ORDER BY createdAt ASC, title ASC'
  )
  const out = []
  while (stmt.step()) out.push(mapSong(stmt.getAsObject()))
  stmt.free()
  return out
}

function songExists(id) {
  const stmt = db.prepare('SELECT 1 FROM songs WHERE id = ?')
  stmt.bind([id])
  const ok = stmt.step()
  stmt.free()
  return !!ok
}

function linesOf(songId) {
  const stmt = db.prepare(
    'SELECT seq,jyutping,jyutpingToneless,mandarin,homophone FROM lines WHERE songId = ? ORDER BY seq ASC'
  )
  stmt.bind([songId])
  const out = []
  while (stmt.step()) {
    const r = stmt.getAsObject()
    out.push({
      seq: r.seq,
      jyutping: r.jyutping,
      jyutpingToneless: r.jyutpingToneless,
      mandarin: r.mandarin,
      homophone: r.homophone
    })
  }
  stmt.free()
  return out
}

function getSong(id) {
  const stmt = db.prepare('SELECT ' + SONG_COLS + ' FROM songs WHERE id = ?')
  stmt.bind([id])
  if (!stmt.step()) {
    stmt.free()
    return null
  }
  const song = mapSong(stmt.getAsObject())
  stmt.free()
  song.lines = linesOf(id)
  return song
}

/** 本地搜索：歌名/歌手子串匹配（忽略大小写），最近打开的在前 */
function search(q) {
  const kw = '%' + String(q || '').trim().toLowerCase() + '%'
  const stmt = db.prepare(
    'SELECT ' + SONG_COLS + ' FROM songs WHERE lower(title) LIKE ? OR lower(artist) LIKE ? ORDER BY lastOpenedAt DESC'
  )
  stmt.bind([kw, kw])
  const out = []
  while (stmt.step()) out.push(mapSong(stmt.getAsObject()))
  stmt.free()
  return out
}

/** 最近打开的 n 首 */
function getRecent(n) {
  const stmt = db.prepare(
    'SELECT ' + SONG_COLS + ' FROM songs ORDER BY lastOpenedAt DESC, createdAt DESC LIMIT ?'
  )
  stmt.bind([n || 20])
  const out = []
  while (stmt.step()) out.push(mapSong(stmt.getAsObject()))
  stmt.free()
  return out
}

/** 去重：同平台同 trackId，或同歌名+歌手 */
function findDuplicate(source, trackId, title, artist) {
  if (trackId) {
    const stmt = db.prepare('SELECT id FROM songs WHERE source = ? AND trackId = ?')
    stmt.bind([source, trackId])
    if (stmt.step()) {
      const id = stmt.getAsObject().id
      stmt.free()
      return id
    }
    stmt.free()
  }
  if (title) {
    const stmt = db.prepare(
      'SELECT id FROM songs WHERE lower(title) = lower(?) AND lower(artist) = lower(?) LIMIT 1'
    )
    stmt.bind([title, artist || ''])
    if (stmt.step()) {
      const id = stmt.getAsObject().id
      stmt.free()
      return id
    }
    stmt.free()
  }
  return null
}

// ── 写入 ──

function addSong(song) {
  const now = Date.now()
  const createdAt = song.createdAt || now
  db.run('BEGIN')
  try {
    db.run(
      'INSERT INTO songs (id,title,artist,source,trackId,createdAt,lastOpenedAt) VALUES (?,?,?,?,?,?,?)',
      [song.id, song.title, song.artist || '', song.source, song.trackId || null, createdAt, now]
    )
    const ins = db.prepare(
      'INSERT OR REPLACE INTO lines (songId,seq,jyutping,jyutpingToneless,mandarin,homophone) VALUES (?,?,?,?,?,?)'
    )
    ;(song.lines || []).forEach((l, i) => {
      const n = normLine(l)
      ins.run([song.id, i, n.jyutping, n.jyutpingToneless, n.mandarin, n.homophone])
    })
    ins.free()
    db.run('COMMIT')
  } catch (e) {
    db.run('ROLLBACK')
    throw e
  }
  scheduleSave()
}

function deleteSong(id) {
  db.run('DELETE FROM songs WHERE id = ?') // lines 由 ON DELETE CASCADE 清理
  scheduleSave()
}

function touch(id) {
  db.run('UPDATE songs SET lastOpenedAt = ? WHERE id = ?', [Date.now(), id])
  scheduleSave()
}

// ── 落盘 ──

function scheduleSave() {
  if (saveTimer) return
  saveTimer = setTimeout(() => {
    saveTimer = null
    saveNow()
  }, 1000)
}

function saveNow() {
  if (!db) return
  try {
    const u8 = db.export()
    const ab = new ArrayBuffer(u8.byteLength)
    new Uint8Array(ab).set(u8)
    wx.getFileSystemManager().writeFileSync(dbPath(), ab, 'binary')
  } catch (e) {
    console.error('保存数据库失败', e)
  }
}

module.exports = {
  init,
  isReady,
  allSongs,
  songExists,
  getSong,
  search,
  getRecent,
  findDuplicate,
  addSong,
  deleteSong,
  touch,
  saveNow
}
