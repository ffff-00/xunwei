<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { blogApi, shopApi } from '@/api'
import type { Blog, Shop, ShopType } from '@/api/types'
import BlogCard from '@/components/BlogCard.vue'
import ShopCard from '@/components/ShopCard.vue'
import { locate, type Coordinate } from '@/utils/geo'

const router = useRouter()

const types = ref<ShopType[]>([])
const nearby = ref<Shop[]>([])
const hotBlogs = ref<Blog[]>([])
const loading = ref(true)
const coordinate = ref<Coordinate | null>(null)
/**
 * 附近查询是否落空。
 *
 * 后端按"距我 5 公里以内"查（GEOSEARCH BYRADIUS 5000），而演示数据全在杭州。
 * 实测：浏览器真实定位在安徽（118.41, 31.35），距杭州约 250 公里 → 附近列表为空。
 * 这对任何不在杭州的人来说就是"打开首页一片空"。
 *
 * 处理方式不是假装有附近、也不是把半径调到失真，而是如实说明 + 给一条能用得上的退路：
 * 落空时改为展示该分类的全部商铺，并在标题旁注明原因。
 */
const nearbyFallback = ref(false)

/** 未选分类时用"美食"做默认，首页要有点内容可看 */
const DEFAULT_TYPE_ID = 1

onMounted(async () => {
  try {
    const [typeRes, blogRes, coord] = await Promise.all([
      shopApi.types(),
      blogApi.hot(1),
      locate(),
    ])
    types.value = typeRes.data ?? []
    hotBlogs.value = (blogRes.data ?? []).slice(0, 4)
    coordinate.value = coord

    const typeId = types.value[0]?.id ?? DEFAULT_TYPE_ID

    // 先按定位查：后端走 Redis GEO，带距离并按远近排序
    const nearRes = await shopApi.byType({ typeId, current: 1, x: coord.x, y: coord.y })
    let list = nearRes.data ?? []

    if (list.length === 0) {
      // 附近没有 → 退回"该分类全部商铺"，并标记出来让界面如实说明
      nearbyFallback.value = true
      const allRes = await shopApi.byType({ typeId, current: 1 })
      list = allRes.data ?? []
    }
    nearby.value = list
  } finally {
    loading.value = false
  }
})

function goType(typeId: number) {
  router.push({ name: 'shops', query: { typeId } })
}
</script>

<template>
  <div class="page">
    <!-- 顶部横幅 -->
    <section class="hero">
      <div class="hero-text">
        <h1>今天，去哪儿吃？</h1>
        <p>发现城市里的好味道 · 限时秒杀团购券</p>
        <div class="hero-search">
          <el-input
            placeholder="搜索商铺名称"
            size="large"
            clearable
            @keyup.enter="
              (e: KeyboardEvent) =>
                router.push({ name: 'shops', query: { name: (e.target as HTMLInputElement).value } })
            "
          >
            <template #append>
              <el-button @click="router.push({ name: 'shops' })">搜索</el-button>
            </template>
          </el-input>
        </div>
      </div>
    </section>

    <!-- 分类 -->
    <section class="types card">
      <div
        v-for="t in types"
        :key="t.id"
        class="type-item"
        @click="goType(t.id)"
      >
        <div class="type-icon cover" :style="{ backgroundImage: `url('${t.icon}')` }"></div>
        <span class="type-name">{{ t.name }}</span>
      </div>
      <div v-if="!loading && types.length === 0" class="empty-block">暂无分类数据</div>
    </section>

    <!-- 附近好店 -->
    <div class="section-title">
      <h2 class="bar-title">{{ nearbyFallback ? '这些店都在杭州' : '附近好店' }}</h2>
      <span class="hint">
        <template v-if="nearbyFallback">
          （你的位置周边 5 公里内没有商户，已展示该分类的全部商铺）
        </template>
        <template v-else-if="coordinate?.fallback">（未授权定位，按杭州武林商圈距离排序）</template>
        <router-link to="/shops" class="more">全部商铺 →</router-link>
      </span>
    </div>

    <el-skeleton v-if="loading" :rows="4" animated />
    <div v-else-if="nearby.length" class="grid-cards">
      <ShopCard v-for="s in nearby" :key="s.id" :shop="s" />
    </div>
    <div v-else class="empty-block">附近暂时没有商铺</div>

    <!-- 热门探店 -->
    <div class="section-title">
      <h2 class="bar-title">热门探店</h2>
      <router-link to="/blogs" class="more">更多笔记 →</router-link>
    </div>

    <el-skeleton v-if="loading" :rows="4" animated />
    <div v-else-if="hotBlogs.length" class="grid-cards">
      <BlogCard v-for="b in hotBlogs" :key="b.id" :blog="b" />
    </div>
    <div v-else class="empty-block">还没有探店笔记</div>
  </div>
</template>

<style scoped>
.hero {
  position: relative;
  overflow: hidden;
  border-radius: var(--radius-lg);
  padding: 40px 36px;
  background: linear-gradient(120deg, rgba(244, 98, 42, 0.94) 0%, rgba(217, 79, 26, 0.92) 100%);
  color: #fff;
  margin-bottom: 18px;
}

.hero::after {
  /* 右侧一圈淡光斑，避免整块纯色显得沉闷 */
  content: '';
  position: absolute;
  right: -80px;
  top: -80px;
  width: 320px;
  height: 320px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.12);
}

.hero-text {
  position: relative;
  z-index: 1;
  max-width: 520px;
}

.hero h1 {
  margin: 0 0 8px;
  font-size: 28px;
  letter-spacing: 1px;
}

.hero p {
  margin: 0 0 20px;
  opacity: 0.92;
  font-size: 14px;
}

.hero-search {
  max-width: 420px;
}

.types {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 16px 18px;
}

.type-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  width: 84px;
  padding: 10px 4px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s;
}

.type-item:hover {
  background: var(--brand-soft);
}

.type-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
}

.type-name {
  font-size: 12.5px;
  color: var(--text-muted);
  white-space: nowrap;
}

.more {
  color: var(--brand);
  font-size: 13px;
}
</style>
