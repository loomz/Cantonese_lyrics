<template>
  <div class="model-manage">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>模型管理</span>
        </div>
      </template>

      <!-- 当前模型切换（云端模型 glm / dashscope / longcat / 自定义） -->
      <el-form label-width="110px" class="model-form">
        <el-form-item label="当前模型">
          <el-tag v-if="activeModel" type="success">{{ activeModel.label }}</el-tag>
          <el-tag v-else type="info">未加载</el-tag>
        </el-form-item>

        <el-form-item label="切换模型">
          <div class="switch-row">
            <el-select v-model="selectedModel" @change="onModelSelected" style="width: 320px">
              <el-option
                v-for="m in models"
                :key="m.name"
                :label="m.label"
                :value="m.name"
              >
                <span>{{ m.label }}</span>
                <span class="model-meta">{{ m.model }}</span>
              </el-option>
            </el-select>
            <el-button
              type="primary"
              :loading="switching"
              :disabled="!selectedModel"
              @click="handleSwitch"
            >切换</el-button>
          </div>
        </el-form-item>

        <!-- 云端模型覆盖：模型 ID / Key / Label（仅 DashScope） -->
        <el-form-item
          v-if="selectedModel === 'dashscope' && selectedModelInfo"
          label="模型覆盖"
        >
          <div class="override-row">
            <div class="override-info">
              <span class="current-label">{{ selectedModelInfo.label }}</span>
              <el-tag size="small" type="info">{{ selectedModelInfo.model }}</el-tag>
              <el-tag v-if="selectedModelInfo.preset_model && selectedModelInfo.model !== selectedModelInfo.preset_model" size="small" type="warning">已修改</el-tag>
              <el-button link type="primary" size="small" @click="startEdit">
                {{ editing ? '取消编辑' : '修改' }}
              </el-button>
            </div>

            <!-- 编辑模式 -->
            <div v-if="editing" class="override-edit" v-loading="savingOverride">
              <el-form :model="editForm" label-width="80px" inline size="small">
                <el-form-item label="模型 ID">
                  <el-input v-model="editForm.model" placeholder="如 qwen-turbo" />
                </el-form-item>
                <el-form-item label="Key">
                  <el-input
                    v-model="editForm.api_key"
                    show-password
                    placeholder="留空保留原值"
                  />
                </el-form-item>
                <el-form-item label="名称">
                  <el-input v-model="editForm.label" placeholder="可选" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" @click="handleSaveOverride">保存</el-button>
                </el-form-item>
              </el-form>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="健康检查">
          <div class="switch-row">
            <el-button :loading="checking" @click="handleCheck">检查活动模型</el-button>
            <span
              v-if="health"
              class="health-text"
              :class="health.healthy ? 'ok' : 'err'"
            >
              {{ health.healthy ? '正常' : `异常: ${health.error}` }}
            </span>
          </div>
        </el-form-item>
      </el-form>

      <el-divider />

      <!-- 自定义模型配置 -->
      <el-alert
        type="info"
        :closable="false"
        class="custom-tip"
        show-icon
      >
        自定义模型默认指向本地 llama-swap（localhost:8080）+ qwen3.8-27b。
        本地模型不做健康检查（本地编码会话正在使用，避免触发 llama-swap 切换进程），
        异常时查看服务端日志排查。
      </el-alert>

      <el-form :model="customForm" label-width="110px" class="custom-form">
        <el-form-item label="名称">
          <el-input v-model="customForm.label" style="width: 420px" placeholder="自定义模型" />
        </el-form-item>
        <el-form-item label="地址 (api_url)">
          <el-input v-model="customForm.api_url" style="width: 420px" />
        </el-form-item>
        <el-form-item label="模型 ID">
          <el-input v-model="customForm.model" style="width: 420px" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input
            v-model="customForm.api_key"
            style="width: 420px"
            placeholder="留空保留原值"
            show-password
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSaveCustom">
            保存自定义模型
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { modelApi, type ModelInfo, type ModelHealth } from '@/api/model'

const models = ref<ModelInfo[]>([])
const active = ref('')
const selectedModel = ref('')
const switching = ref(false)
const checking = ref(false)
const saving = ref(false)
const savingOverride = ref(false)
const health = ref<ModelHealth | null>(null)

// 覆盖编辑状态
const editing = ref(false)
const editForm = ref({ model: '', api_key: '', label: '' })

// 自定义模型表单（api_key 留空表示保留原值）
const customForm = ref({ label: '', api_url: '', model: '', api_key: '' })

const activeModel = computed(() => models.value.find(m => m.name === active.value))

// 当前选中模型（用于覆盖编辑区，跟随下拉框选择）
const selectedModelInfo = computed(() => models.value.find(m => m.name === selectedModel.value))

const isCloud = computed(() => {
  return selectedModel.value === 'dashscope'
})

const fetchConfig = async () => {
  try {
    const res = await modelApi.getConfig()
    models.value = res.models
    active.value = res.active
    selectedModel.value = res.active
    customForm.value = {
      label: res.custom.label,
      api_url: res.custom.api_url,
      model: res.custom.model,
      api_key: '',
    }
    // 重置覆盖编辑表单
    if (isCloud.value && selectedModelInfo.value) {
      editForm.value.model = selectedModelInfo.value.model || ''
      editForm.value.api_key = ''
      editForm.value.label = selectedModelInfo.value.label || ''
      editing.value = false
    }
  } catch {
    ElMessage.error('获取模型配置失败')
  }
}

const onModelSelected = () => {
  // 选中变化后同步编辑表单初始值
  if (isCloud.value && selectedModelInfo.value) {
    editForm.value.model = selectedModelInfo.value.model || ''
    editForm.value.api_key = ''
    editForm.value.label = selectedModelInfo.value.label || ''
    editing.value = false
  }
}

const startEdit = () => {
  editing.value = !editing.value
  if (!editing.value) {
    // 取消时恢复初始值
    if (selectedModelInfo.value) {
      editForm.value.model = selectedModelInfo.value.model || ''
      editForm.value.label = selectedModelInfo.value.label || ''
    }
  }
}

const handleSwitch = async () => {
  if (!selectedModel.value) return
  switching.value = true
  try {
    await modelApi.switchModel(selectedModel.value)
    ElMessage.success('模型切换成功')
    active.value = selectedModel.value
    fetchConfig()
  } catch {
    ElMessage.error('模型切换失败')
  } finally {
    switching.value = false
  }
}

const handleSaveOverride = async () => {
  if (!selectedModel.value) return
  savingOverride.value = true
  try {
    await modelApi.saveOverride(
      selectedModel.value,
      editForm.value.model || undefined,
      editForm.value.api_key || undefined,
      editForm.value.label || undefined,
    )
    ElMessage.success('配置已保存')
    editing.value = false
    fetchConfig()
  } catch {
    ElMessage.error('保存失败')
  } finally {
    savingOverride.value = false
  }
}

const handleCheck = async () => {
  checking.value = true
  try {
    health.value = await modelApi.getStatus()
  } catch {
    health.value = {
      healthy: false, name: active.value, model: '', error: '请求失败', last_check: 0
    }
  } finally {
    checking.value = false
  }
}

const handleSaveCustom = async () => {
  if (!customForm.value.api_url || !customForm.value.model) {
    ElMessage.warning('地址和模型 ID 必填')
    return
  }
  saving.value = true
  try {
    await modelApi.saveCustom(
      customForm.value.api_url,
      customForm.value.model,
      customForm.value.api_key || undefined,
      customForm.value.label || undefined,
    )
    customForm.value.api_key = ''
    ElMessage.success('自定义模型配置已保存')
    fetchConfig()
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(fetchConfig)
</script>

<style scoped>
.model-manage {
  padding: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.model-form {
  padding-top: 8px;
}

.custom-form {
  padding-top: 8px;
}

.custom-tip {
  margin-bottom: 20px;
  line-height: 1.6;
}

.switch-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.model-meta {
  float: right;
  color: #999;
  font-size: 12px;
  margin-left: 16px;
}

.health-text {
  font-size: 13px;
}

.health-text.ok {
  color: #67c23a;
}

.health-text.err {
  color: #f56c6c;
}

/* 模型覆盖样式 */
.override-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.override-info {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #666;
}

.current-label {
  font-weight: 500;
  color: #333;
}

.override-edit {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 12px;
}

.override-edit .el-form {
  flex-wrap: wrap;
}
</style>
