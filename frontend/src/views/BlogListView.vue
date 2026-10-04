<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { blogApi } from '@/api'
import type { Blog } from '@/api/types'
import BlogCard from '@/components/BlogCard.vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const tab = ref<'hot' | 'follow'>('hot')
const blogs = ref<Blog[]>([])
const loading = ref(false)
const page = ref(1)
const hasMore = ref(true)

/** 关注流用的滚动游标：minTime 是本批最后一条的时间戳，offset 是同时间戳的重复条数 */
const followCursor = ref<{ lastId: number; offset: number }>({
  lastId: Date.now(),
  offset: 0,
})

async function loadHot(reset = false) {
  if (loading.value) return
  loading.value = true
  try {
    if (reset) {
      page.value = 1
      blogs.value = []
    }
    const res = await blogApi.hot(page.value)
    const list = res.data ?? []
    blogs.value = reset ? list : [...blogs.value, ...list]
    // 后端不给 total，只能按"本页满不满"猜还有没有下一页
    hasMore.value = list.length >= 5
    if (hasMore.value) page.value += 1
  } finally {
    loading.value = false
  }
}

async function loadFollow(reset = false) {
  if (loading.value) return
  loading.value = true
  try {
    if (reset) {
      followCursor.value = { lastId: Date.now(), offset: 0 }
      blogs.value = []
    }
    const res = await blogApi.ofFollow({
      lastId: followCursor.value.lastId,
      offset: followCursor.value.offset,
    })
    const scroll = res.data
    const list = (scroll?.list ?? []) as Blog[]
    blogs.value = reset ? list : [...blogs.value, ...list]
    followCursor.value = {
      lastId: scroll?.minTime ?? 0,
      offset: scroll?.offset ?? 0,
    }
    hasMore.value = list.length > 0
  } finally {
    loading.value = false
  }
}

async function switchTab(next: 'hot' | 'follow') {
  hasMore.value = true
  if (next === 'hot') {
    await loadHot(true)
  } else {
    await loadFollow(true)
  }
}

// 直接监听 tab 变化，而不是绑 el-tabs 的 tab-change 事件：
// v-model 已经把值写进 tab 了，再处理事件等于同一件事做两遍，
// 而且事件参数的类型是 string | number，还得手动收窄。
watch(tab, (next) => switchTab(next))

function loadMore() {
  if (tab.value === 'hot') {
    loadHot()
  } else {
    loadFollow()
  }
}

onMounted(() => loadHot(true))
</script>

<template>
  <div class="page">
    <div class="section-title">
      <h2 class="bar-title">探店笔记</h2>
      <el-button v-if="userStore.isLoggedIn" type="primary" round size="small" @click="$router.push('/blogs/edit')">
        写笔记
      </el-button>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane label="热门" name="hot" />
      <el-tab-pane label="我关注的" name="follow" />
    </el-tabs>

    <el-skeleton v-if="loading && blogs.length === 0" :rows="6" animated />

    <template v-else-if="blogs.length">
      <div class="grid-cards">
        <BlogCard v-for="b in blogs" :key="b.id" :blog="b" />
      </div>
      <div class="load-more">
        <el-button v-if="hasMore" :loading="loading" @click="loadMore">加载更多</el-button>
        <span v-else class="faint">没有更多了</span>
      </div>
    </template>

    <div v-else class="empty-block">
      <template v-if="tab === 'follow'">
        还没有关注的人，去<router-link to="/shops" class="link">找好店</router-link>里发现更多吧
      </template>
      <template v-else>还没有探店笔记</template>
    </div>
  </div>
</template>

<style scoped>
.load-more {
  display: flex;
  justify-content: center;
  margin-top: 28px;
}

.link {
  color: var(--brand);
}
</style>
