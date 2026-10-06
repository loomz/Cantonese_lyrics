<template>
  <div class="lyrics-list">
    <a-card>
      <template #title>
        <div class="card-header">
          <span>歌词管理</span>
          <a-button @click="handleRefresh">
            <template #icon>
              <ReloadOutlined />
            </template>
            刷新列表
          </a-button>
        </div>
      </template>

      <!-- 搜索筛选 -->
      <a-form layout="inline" class="search-form">
        <a-form-item>
          <a-input
            v-model:value="queryParams.search"
            placeholder="歌曲名 / 歌手"
            allow-clear
            style="width: 200px"
            @pressEnter="handleSearch"
          />
        </a-form-item>
        <a-form-item>
          <a-select
            v-model:value="queryParams.provider"
            placeholder="全部"
            allow-clear
            style="width: 120px"
          >
            <a-select-option value="netease">网易云</a-select-option>
            <a-select-option value="qq">QQ音乐</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="handleSearch">搜索</a-button>
        </a-form-item>
      </a-form>

      <!-- 数据表格 -->
      <a-table
        :data-source="tableData"
        :loading="loading"
        :pagination="pagination"
        :columns="columns"
        row-key="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'provider'">
            <a-tag :color="record.provider === 'netease' ? 'green' : 'orange'">
              {{ record.provider === 'netease' ? '网易云' : 'QQ音乐' }}
            </a-tag>
          </template>
          <template v-if="column.key === 'language'">
            <a-tag>{{ languageText(record.language) }}</a-tag>
          </template>
          <template v-if="column.key === 'generatedAt'">
            {{ formatTime(record.generatedAt) }}
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a @click="handleView(record)">查看</a>
              <a @click="handleRegenerate(record)">重新生成</a>
              <a-popconfirm
                title="确定要删除这首歌的缓存吗？"
                @confirm="handleDelete(record)"
              >
                <a style="color: #ff4d4f">删除</a>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { lyricsApi, type LyricsDoc } from '@/api/lyrics'

const router = useRouter()
const loading = ref(false)
const tableData = ref<LyricsDoc[]>([])
const total = ref(0)

const queryParams = reactive({
  page: 1,
  page_size: 20,
  provider: undefined as string | undefined,
  search: ''
})

const columns = [
  { title: '歌曲名', dataIndex: 'title', key: 'title', ellipsis: true },
  { title: '歌手', dataIndex: 'artist', key: 'artist', ellipsis: true },
  { title: '来源', key: 'provider', width: 100 },
  { title: '版本', dataIndex: 'version', key: 'version', width: 80 },
  { title: '语言', key: 'language', width: 100 },
  { title: '生成时间', key: 'generatedAt', width: 180 },
  { title: '操作', key: 'action', width: 220 }
]

const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
  showSizeChanger: true,
  showQuickJumper: true,
  pageSizeOptions: ['10', '20', '50', '100'],
  showTotal: (total: number) => `共 ${total} 条`
})

const languageText = (lang: string) => {
  const map: Record<string, string> = {
    yue: '粤语',
    ko: '韩语',
    ja: '日语'
  }
  return map[lang] || lang
}

const formatTime = (timestamp: number) => {
  if (!timestamp) return '-'
  return new Date(timestamp).toLocaleString('zh-CN')
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await lyricsApi.getList(queryParams)
    tableData.value = res.items
    total.value = res.total
    pagination.total = res.total
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  queryParams.page = 1
  pagination.current = 1
  fetchData()
}

const handleRefresh = () => {
  fetchData()
}

const handleView = (row: LyricsDoc) => {
  router.push(`/lyrics/${row.provider}/${row.trackId}`)
}

const handleRegenerate = (row: LyricsDoc) => {
  Modal.confirm({
    title: '确定要重新生成这首歌的歌词吗？',
    onOk: async () => {
      try {
        await lyricsApi.refresh(row.provider, row.trackId)
        message.success('重新生成成功')
        fetchData()
      } catch (error) {
        message.error('重新生成失败')
      }
    }
  })
}

const handleDelete = async (row: LyricsDoc) => {
  try {
    await lyricsApi.delete(row.provider, row.trackId)
    message.success('删除成功')
    fetchData()
  } catch (error) {
    message.error('删除失败')
  }
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.lyrics-list {
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
</style>
