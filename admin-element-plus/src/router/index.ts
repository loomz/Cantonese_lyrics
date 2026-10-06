import { createRouter, createWebHistory } from 'vue-router'
import Layout from '@/components/Layout.vue'

const routes = [
  {
    path: '/',
    component: Layout,
    redirect: '/lyrics',
    children: [
      {
        path: 'lyrics',
        name: 'LyricsList',
        component: () => import('@/views/lyrics/List.vue'),
        meta: { title: '歌词管理' }
      },
      {
        path: 'lyrics/:provider/:trackId',
        name: 'LyricsDetail',
        component: () => import('@/views/lyrics/Detail.vue'),
        meta: { title: '歌词详情' }
      },
      {
        path: 'search',
        name: 'OnlineSearch',
        component: () => import('@/views/search/OnlineSearch.vue'),
        meta: { title: '在线搜索' }
      },
      {
        path: 'model',
        name: 'ModelManage',
        component: () => import('@/views/model/ModelManage.vue'),
        meta: { title: '模型管理' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
