<template>
  <div class="model-manage">
    <a-card>
      <template #title>
        <div class="card-header">
          <span>模型管理</span>
        </div>
      </template>

      <!-- 当前模型切换（云端模型 glm / dashscope / longcat / 自定义） -->
      <a-form :label-col="{ style: { width: 110 } }" class="model-form">
        <a-form-item label="当前模型">
          <a-tag v-if="activeModel" color="success">{{ activeModel.label }}</a-tag>
          <a-tag v-else>未加载</a-tag>
        </a-form-item>

        <a-form-item label="切换模型">
          <div class="switch-row">
            <a-select v-model:value="selectedModel" @change="onModelSelected" style="width: 320px">
              <a-select-option v-for="m in models" :key="m.name" :value="m.name">
                <span>{{ m.label }}</span>
                <span class="model-meta">{{ m.model }}</span>
              </a-select-option>
            </a-select>
            <a-button
              type="primary"
              :loading="switching"
              :disabled="!selectedModel"
              @click="handleSwitch"
            >切换</a-button>
          </div>
        </a-form-item>

        <!-- 云端模型覆盖：模型 ID / Key / Label（仅 DashScope） -->
        <a-form-item
          v-if="selectedModel === 'dashscope' && selectedModelInfo"
          label="模型覆盖"
        >
          <div class="override-row">
            <div class="override-info">
              <span class="current-label">{{ selectedModelInfo.label }}</span>
              <a-tag>{{ selectedModelInfo.model }}</a-tag>
              <a-tag v-if="selectedModelInfo.preset_model && selectedModelInfo.model !== selectedModelInfo.preset_model" color="orange">已修改</a-tag>
              <a-button size="small" type="link" @click="editing = !editing">
                {{ editing ? '取消' : '修改' }}
              </a-button>
            </div>

            <!-- 编辑模式 -->
            <div v-if="editing" class="override-edit" v-loading="savingOverride">
              <a-form :model="editForm" :label-col="{ style: { width: 80 } }">
                <a-form-item label="模型 ID">
                  <a-input v-model:value="editForm.model" placeholder="如 qwen-turbo" />
                </a-form-item>
                <a-form-item label="Key">
                  <a-input-password v-model:value="editForm.api_key" placeholder="留空保留原值" />
                </a-form-item>
                <a-form-item label="名称">
                  <a-input v-model:value="editForm.label" placeholder="可选" />
                </a-form-item>
                <a-form-item>
                  <a-button type="primary" :loading="savingOverride" @click="handleSaveOverride">保存</a-button>
                </a-form-item>
              </a-form>
            </div>
          </div>
        </a-form-item>

        <a-form-item label="健康检查">
          <div class="switch-row">
            <a-button :loading="checking" @click="handleCheck">检查活动模型</a-button>
            <span
              v-if="health"
              class="health-text"
              :class="health.healthy ? 'ok' : 'err'"
            >
              {{ health.healthy ? '正常' : `异常: ${health.error}` }}
            </span>
          </div>
        </a-form-item>
      </a-form>

      <a-divider />

      <!-- 自定义模型配置 -->
      <a-alert
        type="info"
        :closable="false"
        show-icon
        class="custom-tip"
        message="自定义模型默认指向本地 llama-swap（localhost:8080）+ qwen3.8-27b。"
        description="本地模型不做健康检查（本地编码会话正在使用，避免触发 llama-swap 切换进程），异常时查看服务端日志排查。"
      />

      <a-form :model="customForm" :label-col="{ style: { width: 110 } }" class="custom-form">
        <a-form-item label="名称">
          <a-input v-model:value="customForm.label" style="width: 420px" placeholder="自定义模型" />
        </a-form-item>
        <a-form-item label="地址 (api_url)">
          <a-input v-model:value="customForm.api_url" style="width: 420px" />
        </a-form-item>
        <a-form-item label="模型 ID">
          <a-input v-model:value="customForm.model" style="width: 420px" />
        </a-form-item>
        <a-form-item label="API Key">
          <a-input-password
            v-model:value="customForm.api_key"
            style="width: 420px"
            placeholder="留空保留原值"
          />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" :loading="saving" @click="handleSaveCustom">
            保存自定义模型
          </a-button>
        </a-form-item>
      </a-form>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { modelApi, type ModelInfo, type ModelHealth } from '@/api/model'

const models = ref<ModelInfo[]>([])
const active = ref('')
const selectedModel = ref('')
const switching = ref(false)
const checking = ref(false)
const saving = ref(false)
const savingOverride = ref(false)
const editing = ref(false)
const health = ref<ModelHealth | null>(null)

// 覆盖编辑表单
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
    message.error('获取模型配置失败')
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

const handleSwitch = async () => {
  if (!selectedModel.value) return
  switching.value = true
  try {
    await modelApi.switchModel(selectedModel.value)
    message.success('模型切换成功')
    active.value = selectedModel.value
    fetchConfig()
  } catch {
    message.error('模型切换失败')
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
    message.success('配置已保存')
    editing.value = false
    fetchConfig()
  } catch {
    message.error('保存失败')
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
    message.warning('地址和模型 ID 必填')
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
    message.success('自定义模型配置已保存')
    fetchConfig()
  } catch {
    message.error('保存失败')
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
  color: #52c41a;
}

.health-text.err {
  color: #ff4d4f;
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
  background: #fafafa;
  border-radius: 8px;
  padding: 16px;
}
</style>
