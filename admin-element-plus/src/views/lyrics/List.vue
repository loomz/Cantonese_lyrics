<template>
  <div class="lyrics-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>歌词管理</span>
          <el-button @click="handleRefresh">
            <el-icon><Refresh /></el-icon>
            刷新列表
          </el-button>
        </div>
      </template>

      <!-- 搜索筛选 -->
      <el-form :inline="true" :model="queryParams" class="search-form">
        <el-form-item label="搜索">
          <el-input
            v-model="queryParams.search"
            placeholder="歌曲名 / 歌手"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="queryParams.provider" placeholder="全部" clearable style="width: 120px">
            <el-option label="网易云" value="netease" />
            <el-option label="QQ音乐" value="qq" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
        </el-form-item>
      </el-form>

      <!-- 数据表格 -->
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="title" label="歌曲名" min-width="150" show-overflow-tooltip />
        <el-table-column prop="artist" label="歌手" min-width="120" show-overflow-tooltip />
        <el-table-column prop="provider" label="来源" width="100">
          <template #default="{ row }">
            <el-tag :type="row.provider === 'netease' ? 'success' : 'warning'" size="small">
              {{ row.provider === 'netease' ? '网易云' : 'QQ音乐' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="80" align="center" />
        <el-table-column prop="language" label="语言" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ languageText(row.language) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="generatedAt" label="生成时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.generatedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">查看</el-button>
            <el-button link type="warning" @click="handleRegenerate(row)">重新生成</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="queryParams.page"
        v-model:page-size="queryParams.page_size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
        class="pagination"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
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
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  queryParams.page = 1
  fetchData()
}

const handleRefresh = () => {
  fetchData()
}

const handleSizeChange = (val: number) => {
  queryParams.page_size = val
  fetchData()
}

const handleCurrentChange = (val: number) => {
  queryParams.page = val
  fetchData()
}

const handleView = (row: LyricsDoc) => {
  router.push(`/lyrics/${row.provider}/${row.trackId}`)
}

const handleRegenerate = async (row: LyricsDoc) => {
  try {
    await ElMessageBox.confirm('确定要重新生成这首歌的歌词吗？', '提示', {
      type: 'warning'
    })
    await lyricsApi.refresh(row.provider, row.trackId)
    ElMessage.success('重新生成成功')
    fetchData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('重新生成失败')
    }
  }
}

const handleDelete = async (row: LyricsDoc) => {
  try {
    await ElMessageBox.confirm('确定要删除这首歌的缓存吗？', '提示', {
      type: 'warning'
    })
    await lyricsApi.delete(row.provider, row.trackId)
    ElMessage.success('删除成功')
    fetchData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
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

.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}
</style>
