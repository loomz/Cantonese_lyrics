<template>
  <div class="lyrics-detail">
    <a-page-header
      title="歌词详情"
      @back="goBack"
      class="page-header"
    />

    <a-card v-if="doc" :loading="loading" class="detail-card">
      <template #title>
        <div class="card-header">
          <div class="song-info">
            <h2>{{ doc.title }}</h2>
            <p>{{ doc.artist }}</p>
          </div>
          <div class="actions">
            <a-tag>版本 {{ doc.version }}</a-tag>
            <a-tag color="blue">{{ languageText(doc.language) }}</a-tag>
            <a-button type="primary" @click="handleRegenerate">
              <template #icon>
                <ReloadOutlined />
              </template>
              重新生成
            </a-button>
          </div>
        </div>
      </template>

      <!-- 歌词内容 -->
      <div class="lyrics-content">
        <div v-for="(line, index) in doc.lines" :key="index" class="lyric-line">
          <div class="line-number">{{ index + 1 }}</div>
          <div class="line-content">
            <div class="homophone">{{ line.homophone }}</div>
            <div class="original">{{ line.mandarin }}</div>
            <div class="jyutping">{{ line.jyutping }}</div>
          </div>
        </div>
      </div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { lyricsApi, type LyricsDoc } from '@/api/lyrics'

const route = useRoute()
const router = useRouter()
const doc = ref<LyricsDoc | null>(null)
const loading = ref(false)

const provider = route.params.provider as string
const trackId = route.params.trackId as string

const languageText = (lang: string) => {
  const map: Record<string, string> = {
    yue: '粤语',
    ko: '韩语',
    ja: '日语'
  }
  return map[lang] || lang
}

const fetchDetail = async () => {
  loading.value = true
  try {
    // 用 generate 而不是 getDetail：未缓存的歌会自动触发生成管道
    doc.value = await lyricsApi.generate(provider, trackId)
  } catch {
    // generate 失败时回退到 getDetail（可能已缓存）
    try {
      doc.value = await lyricsApi.getDetail(provider, trackId)
    } catch {
      message.error('歌词获取失败')
    }
  } finally {
    loading.value = false
  }
}

const goBack = () => {
  router.back()
}

const handleRegenerate = () => {
  Modal.confirm({
    title: '确定要重新生成这首歌的歌词吗？',
    onOk: async () => {
      try {
        await lyricsApi.refresh(provider, trackId)
        message.success('重新生成成功')
        fetchDetail()
      } catch (error) {
        message.error('重新生成失败')
      }
    }
  })
}

onMounted(() => {
  fetchDetail()
})
</script>

<style scoped>
.lyrics-detail {
  padding: 0;
}

.page-header {
  margin-bottom: 20px;
}

.detail-card {
  margin-top: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.song-info h2 {
  margin: 0 0 8px 0;
  font-size: 20px;
}

.song-info p {
  margin: 0;
  color: #666;
}

.actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.lyrics-content {
  padding: 20px 0;
}

.lyric-line {
  display: flex;
  padding: 16px 0;
  border-bottom: 1px solid #f0f0f0;
}

.lyric-line:last-child {
  border-bottom: none;
}

.line-number {
  width: 40px;
  color: #999;
  font-size: 14px;
  flex-shrink: 0;
}

.line-content {
  flex: 1;
}

.homophone {
  font-size: 18px;
  font-weight: 500;
  color: #333;
  margin-bottom: 6px;
}

.original {
  font-size: 14px;
  color: #666;
  margin-bottom: 4px;
}

.jyutping {
  font-size: 13px;
  color: #999;
  font-style: italic;
}
</style>
