<template>
  <div class="online-search">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>在线搜索歌曲</span>
        </div>
      </template>

      <!-- 搜索框 -->
      <el-form :inline="true" class="search-form">
        <el-form-item label="搜索关键词">
          <el-input
            v-model="searchQuery"
            placeholder="歌曲名 / 歌手"
            clearable
            style="width: 300px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="searching" @click="handleSearch">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 搜索结果列表 -->
      <el-table
        v-if="results.length > 0"
        :data="results"
        stripe
        size="small"
        class="search-results"
      >
        <el-table-column prop="title" label="歌曲名" min-width="200" show-overflow-tooltip />
        <el-table-column prop="artist" label="歌手" min-width="150" show-overflow-tooltip />
        <el-table-column label="来源" width="100">
          <template #default>
            <el-tag size="small">{{ provider === 'netease' ? '网易云' : 'QQ音乐' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDrawer(row)">查看歌词</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-else-if="searched && !searching" description="未找到相关歌曲" />
    </el-card>

    <!-- 歌词抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="drawerSong ? `${drawerSong.title} - ${drawerSong.artist}` : '歌词'"
      size="500px"
      direction="rtl"
    >
      <div v-if="drawerSong" class="drawer-content">
        <!-- 歌曲信息 -->
        <div class="song-meta">
          <el-tag v-if="doc" size="small" type="info">{{ languageText(doc.language) }}</el-tag>
          <el-tag v-if="doc" size="small">版本 {{ doc.version }}</el-tag>
          <el-tag v-if="rawData" size="small" type="warning">原始歌词（{{ rawData.lines.length }} 行）</el-tag>
        </div>

        <!-- 生成的歌词文档（已生成谐音/粤拼） -->
        <div v-if="doc" class="lyrics-content" v-loading="loadingDoc">
          <div v-for="(line, index) in doc.lines" :key="index" class="lyric-line">
            <div class="line-number">{{ index + 1 }}</div>
            <div class="line-content">
              <div class="homophone">{{ line.homophone || '——' }}</div>
              <div class="original">{{ line.mandarin }}</div>
              <div class="jyutping">{{ line.jyutping }}</div>
            </div>
          </div>
        </div>

        <!-- 仅原始歌词（尚未生成谐音） -->
        <div v-else-if="!loadingDoc && rawData" class="lyrics-content">
          <div v-for="(line, index) in rawData.lines" :key="index" class="lyric-line-raw">
            <div class="line-index">{{ index + 1 }}</div>
            <div class="line-text">{{ line.text }}</div>
            <div v-if="line.ts != null" class="line-ts">
              {{ formatTime(line.ts) }}
            </div>
          </div>
          <div class="roma-info" v-if="Object.keys(rawData.roma_by_index).length > 0">
            （含官方罗马音，可在歌词详情中查看）
          </div>
        </div>

        <!-- 加载中 -->
        <el-empty v-else-if="loadingDoc" description="加载中..." />

        <!-- 错误 -->
        <el-alert v-else :title="'获取失败，该歌曲暂无数据'" :closable="false" type="error" />

        <!-- 操作按钮 -->
        <div class="drawer-actions">
          <el-button
            v-if="!doc"
            type="primary"
            :loading="generating"
            @click="handleGenerate"
          >
            <el-icon><MagicStick /></el-icon>
            生成谐音歌词
          </el-button>
          <el-button
            v-else
            type="warning"
            :loading="regenerating"
            @click="handleRegenerate"
          >
            <el-icon><RefreshRight /></el-icon>
            重新生成新版本
          </el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, MagicStick, RefreshRight } from '@element-plus/icons-vue'
import { lyricsApi, type LyricsDoc, type LyricsRichResponse } from '@/api/lyrics'

// 搜索
const searchQuery = ref('')
const searching = ref(false)
const searched = ref(false)
const results = ref<{ trackId: string; title: string; artist: string }[]>([])
const provider = ref('netease')

// 抽屉
const drawerVisible = ref(false)
const drawerSong = ref<{ trackId: string; title: string; artist: string } | null>(null)
const doc = ref<LyricsDoc | null>(null)
const rawData = ref<LyricsRichResponse | null>(null)
const loadingDoc = ref(false)
const generating = ref(false)
const regenerating = ref(false)

const languageText = (lang: string) => {
  const map: Record<string, string> = {
    yue: '粤语',
    ko: '韩语',
    ja: '日语'
  }
  return map[lang] || lang
}

const formatTime = (ms: number): string => {
  const m = Math.floor(ms / 60000)
  const s = Math.floor((ms % 60000) / 1000)
  const cs = Math.floor((ms % 1000) / 10)
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}.${String(cs).padStart(2, '0')}`
}

const handleSearch = async () => {
  if (!searchQuery.value.trim()) return
  searching.value = true
  searched.value = true
  try {
    const res = await lyricsApi.search(searchQuery.value.trim(), 20)
    results.value = res.results
    provider.value = res.provider
  } catch {
    ElMessage.error('在线搜索失败')
  } finally {
    searching.value = false
  }
}

const openDrawer = (row: { trackId: string; title: string; artist: string }) => {
  drawerSong.value = row
  doc.value = null
  rawData.value = null
  drawerVisible.value = true
  fetchRaw()
}

/** 查看歌词：直接取原始歌词接口（含时间戳），不触发生成管线 */
const fetchRaw = async () => {
  if (!drawerSong.value) return
  loadingDoc.value = true
  try {
    rawData.value = await lyricsApi.getRawLyrics(provider.value, drawerSong.value.trackId)
  } catch {
    rawData.value = null
  } finally {
    loadingDoc.value = false
  }
}

/** 生成谐音歌词（触发完整管线） */
const handleGenerate = async () => {
  if (!drawerSong.value) return
  generating.value = true
  try {
    doc.value = await lyricsApi.generate(provider.value, drawerSong.value.trackId)
    ElMessage.success('歌词生成成功')
  } catch {
    ElMessage.error('歌词生成失败')
  } finally {
    generating.value = false
  }
}

/** 重新生成新版本 */
const handleRegenerate = async () => {
  if (!drawerSong.value) return
  regenerating.value = true
  try {
    doc.value = await lyricsApi.refresh(provider.value, drawerSong.value.trackId)
    ElMessage.success('重新生成成功')
  } catch {
    ElMessage.error('重新生成失败')
  } finally {
    regenerating.value = false
  }
}
</script>

<style scoped>
.online-search {
  padding: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-form {
  margin-bottom: 20px;
}

.search-results {
  margin-top: 10px;
}

.drawer-content {
  padding: 0;
}

.song-meta {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.lyrics-content {
  margin-bottom: 16px;
}

.lyric-line {
  display: flex;
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
}

.lyric-line:last-child {
  border-bottom: none;
}

.line-number {
  width: 36px;
  color: #999;
  font-size: 13px;
  flex-shrink: 0;
}

.line-content {
  flex: 1;
}

.homophone {
  font-size: 17px;
  font-weight: 500;
  color: #333;
  margin-bottom: 4px;
}

.original {
  font-size: 13px;
  color: #666;
  margin-bottom: 3px;
}

.jyutping {
  font-size: 12px;
  color: #999;
  font-style: italic;
}

/* 原始歌词样式（带时间戳） */
.lyric-line-raw {
  display: flex;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px dashed #e8e8e8;
  gap: 12px;
}

.lyric-line-raw:last-child {
  border-bottom: none;
}

.line-index {
  width: 24px;
  color: #bbb;
  font-size: 12px;
  text-align: right;
  flex-shrink: 0;
}

.line-text {
  flex: 1;
  font-size: 15px;
  color: #333;
  white-space: pre-line;
}

.line-ts {
  color: #999;
  font-size: 12px;
  font-family: monospace;
  flex-shrink: 0;
}

.roma-info {
  color: #999;
  font-size: 12px;
  margin-top: 8px;
  text-align: center;
}

.drawer-actions {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}
</style>
