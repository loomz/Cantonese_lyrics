/**
 * 在线歌曲导入管道（三端逻辑一致）：
 *   去重 → 获取原词(apiserver) → 生成粤拼(apiserver g2p) → GLM 谐音标注 → 合并入库
 *
 * 任一步失败不写半截数据，可重试。
 * onStage(stage) 回调进度：'fetch' | 'g2p' | 'glm' | 'done'
 * 返回 Promise<songId>。
 */
const api = require('./lyrics-api')
const glm = require('./glm')
const store = require('./store')

function importSong(provider, trackId, title, artist, onStage) {
  onStage = onStage || function () {}
  title = String(title || '').trim()
  artist = String(artist || '').trim()

  // 1. 去重：已有同 trackId（或同歌名+歌手）直接打开
  const dup = store.findDuplicate(provider, trackId, title, artist)
  if (dup) {
    onStage('done')
    return Promise.resolve(dup)
  }

  // 2. 获取原词
  onStage('fetch')
  return api.fetchLyrics(provider, trackId).then((d) => {
    const lines = (d && d.lines) || []
    if (!lines.length) throw new Error('该歌曲没有歌词')
    const t = String((d && d.title) || title || '').trim() || title || '未命名'
    const a = String((d && d.artist) || artist || '').trim() || artist || ''

    // 3. 生成粤拼
    onStage('g2p')
    return api.g2p(lines).then((g) => {
      const jp = (g && g.lines) || []

      // 4. GLM 谐音标注
      onStage('glm')
      return glm.annotate(lines).then((hps) => {
        // 5. 合并入库（all-or-nothing：走到这里才写库）
        const songLines = lines.map((md, i) => ({
          jyutping: (jp[i] && jp[i].with_tone) || '',
          jyutpingToneless: (jp[i] && jp[i].without_tone) || '',
          mandarin: md,
          homophone: hps[i] || ''
        }))
        const id = provider + '_' + trackId
        store.addSong({
          id: id,
          title: t,
          artist: a,
          source: provider,
          trackId: trackId,
          lines: songLines
        })
        onStage('done')
        return id
      })
    })
  })
}

module.exports = { importSong }
