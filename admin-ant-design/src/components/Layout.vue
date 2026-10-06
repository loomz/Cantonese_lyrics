<template>
  <a-layout class="layout">
    <a-layout-sider v-model:collapsed="collapsed" collapsible>
      <div class="logo">
        <span v-if="!collapsed">谐音歌词管理</span>
        <span v-else>谐音</span>
      </div>
      <a-menu
        v-model:selectedKeys="selectedKeys"
        theme="dark"
        mode="inline"
        @click="handleMenuClick"
      >
        <a-menu-item key="/lyrics">
          <template #icon>
            <FileTextOutlined />
          </template>
          <span>歌词管理</span>
        </a-menu-item>
        <a-menu-item key="/search">
          <template #icon>
            <SearchOutlined />
          </template>
          <span>在线搜索</span>
        </a-menu-item>
        <a-menu-item key="/model">
          <template #icon>
            <SettingOutlined />
          </template>
          <span>模型管理</span>
        </a-menu-item>
      </a-menu>
    </a-layout-sider>
    <a-layout>
      <a-layout-header class="header">
        <a-breadcrumb>
          <a-breadcrumb-item>首页</a-breadcrumb-item>
          <a-breadcrumb-item>{{ pageTitle }}</a-breadcrumb-item>
        </a-breadcrumb>
      </a-layout-header>
      <a-layout-content class="main">
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { FileTextOutlined, SettingOutlined, SearchOutlined } from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()
const collapsed = ref(false)
const selectedKeys = ref<string[]>(['/lyrics'])

const pageTitle = computed(() => {
  return (route.meta.title as string) || '首页'
})

const handleMenuClick = ({ key }: { key: string }) => {
  router.push(key)
}

// 根据路由更新选中菜单
const updateSelectedKeys = () => {
  const path = route.path
  if (path.startsWith('/lyrics')) {
    selectedKeys.value = ['/lyrics']
  } else if (path.startsWith('/search')) {
    selectedKeys.value = ['/search']
  } else if (path.startsWith('/model')) {
    selectedKeys.value = ['/model']
  }
}

// 监听路由变化
import { watch } from 'vue'
watch(() => route.path, updateSelectedKeys, { immediate: true })
</script>

<style scoped>
.layout {
  height: 100vh;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  white-space: nowrap;
  overflow: hidden;
}

.header {
  background: #fff;
  padding: 0 24px;
  border-bottom: 1px solid #e8e8e8;
  display: flex;
  align-items: center;
}

.main {
  background: #f0f2f5;
  padding: 20px;
  overflow-y: auto;
}
</style>
