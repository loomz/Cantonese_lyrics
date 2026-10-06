import request from './index'

export interface ModelInfo {
  name: string
  label: string
  api_url: string
  model: string
  api_key_masked: string
  preset_model?: string   // 云端模型原始预设值
  active: boolean
}

export interface CustomModel {
  label: string
  api_url: string
  model: string
  api_key: string
  api_key_masked: string
}

export interface ModelHealth {
  healthy: boolean
  name: string
  model: string
  status_code?: number
  error: string | null
  last_check: number
}

export interface ModelConfigResponse {
  models: ModelInfo[]
  active: string
  custom: CustomModel
}

export const modelApi = {
  // 获取模型配置（所有可选模型 + 活动模型 + 自定义模型配置）
  getConfig() {
    return request.get('/admin/model/config') as Promise<ModelConfigResponse>
  },

  // 切换活动模型（glm / dashscope / longcat / custom）
  switchModel(name: string) {
    return request.post('/admin/model/switch', { name }) as Promise<void>
  },

  // 保存自定义模型配置（api_key 为空则保留原值）
  saveCustom(api_url: string, model: string, api_key?: string, label?: string) {
    return request.post(
      '/admin/model/custom',
      { api_url, model, api_key, label },
    ) as Promise<void>
  },

  // 健康检查（不传 name 检查活动模型）
  getStatus(name?: string) {
    return request.get('/admin/model/status', { params: { name } }) as Promise<ModelHealth>
  },

  // 保存云端模型覆盖配置（model ID / key / label）
  saveOverride(
    name: string,
    model?: string,
    api_key?: string,
    label?: string,
  ) {
    return request.post('/admin/model/override', {
      name, model: model ?? '', api_key, label,
    }) as Promise<void>
  }
}
