<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { shopApi } from '@/api'
import type { Shop, ShopType } from '@/api/types'
import ShopCard from '@/components/ShopCard.vue'
import { locate } from '@/utils/geo'

const route = useRoute()
const router = useRouter()

const types = ref<ShopType[]>([])
const shops = ref<Shop[]>([])
const loading = ref(false)
const current = ref(1)
/**
 * 后端 /shop/of/type 与 /of/name 都是"返回固定页大小、不给总数"的老式分页
 * （页大小由后端 SystemConstants.MAX_PAGE_SIZE 决定，当前是 10）。
 * 所以这里只能按"本页是否满页"推断有没有下一页 —— 权宜之计，
 * 正确做法是后端返回 total（已记在改造文档的技术债里）。
 */
const PAGE_SIZE = 10
const typeId = ref<number>(1)
const name = ref('')

const hasNext = computed(() => shops.value.length >= PAGE_SIZE)
const showPager = computed(() => !name.value)

onMounted(async () => {
  const res = await shopApi.types()
  types.value = res.data ?? []
  if (types.value.length && !types.value.some((t) => t.id === typeId.value)) {
    typeId.value = types.value[0].id
  }
  await load()
})

async function load() {
  loading.value = true
  try {
    if (name.value.trim()) {
      const res = await shopApi.byName({ name: name.value.trim(), current: current.value })
      shops.value = res.data ?? []
    } else {
      const coord = await locate()
      const res = await shopApi.byType({
        typeId: typeId.value,
        current: current.value,
        x: coord.x,
        y: coord.y,
      })
      shops.value = res.data ?? []
    }
  } finally {
    loading.value = false
  }
}

function selectType(id: number) {
  typeId.value = id
  name.value = ''
  current.value = 1
  router.replace({ name: 'shops', query: { typeId: id } })
  load()
}

function search() {
  current.value = 1
  load()
}

function changePage(page: number) {
  current.value = page
  load()
}

watch(
  () => route.query,
  (query) => {
    // 支持从首页带参数跳进来（/shops?typeId=2 或 ?name=火锅）
    if (query.typeId && Number(query.typeId) !== typeId.value) {
      typeId.value = Number(query.typeId)
      current.value = 1
      load()
    }
    if (typeof query.name === 'string' && query.name !== name.value) {
      name.value = query.name
      current.value = 1
      load()
    }
  },
)
</script>

<template>
  <div class="page">
    <div class="card card-pad toolbar">
      <div class="search-row">
        <el-input
          v-model="name"
          placeholder="搜索商铺名称，如：茶餐厅"
          clearable
          size="large"
          @keyup.enter="search"
          @clear="search"
        />
        <el-button type="primary" size="large" @click="search">搜索</el-button>
      </div>

      <div v-if="!name" class="type-row">
        <span
          v-for="t in types"
          :key="t.id"
          class="chip"
          :class="{ active: t.id === typeId }"
          @click="selectType(t.id)"
        >
          {{ t.name }}
        </span>
      </div>
    </div>

    <div class="section-title">
      <h2 class="bar-title">{{ name ? `“${name}” 的搜索结果` : '商铺列表' }}</h2>
      <span class="hint" v-if="shops.length">共 {{ shops.length }} 家</span>
    </div>

    <el-skeleton v-if="loading" :rows="6" animated />
    <template v-else>
      <div v-if="shops.length" class="grid-cards">
        <ShopCard v-for="s in shops" :key="s.id" :shop="s" />
      </div>
      <div v-else class="empty-block">没有找到符合条件的商铺</div>

      <div v-if="showPager && (current > 1 || hasNext)" class="pager">
        <el-button :disabled="current <= 1" @click="changePage(current - 1)">上一页</el-button>
        <span class="page-no">第 {{ current }} 页</span>
        <el-button :disabled="!hasNext" @click="changePage(current + 1)">下一页</el-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.search-row {
  display: flex;
  gap: 10px;
  max-width: 620px;
}

.type-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chip {
  padding: 5px 14px;
  border-radius: 999px;
  font-size: 13px;
  color: var(--text-muted);
  background: #f3f4f6;
  cursor: pointer;
  transition: all 0.15s;
}

.chip:hover {
  color: var(--text);
}

.chip.active {
  background: var(--brand);
  color: #fff;
  font-weight: 600;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  margin-top: 28px;
}

.page-no {
  font-size: 13px;
  color: var(--text-muted);
}
</style>
