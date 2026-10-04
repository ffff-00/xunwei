<script setup lang="ts">
import { computed } from 'vue'
import type { Shop } from '@/api/types'
import { firstImage, formatDistance, shopAvgPrice } from '@/utils/format'

const props = defineProps<{ shop: Shop }>()

const cover = computed(() => firstImage(props.shop.images))
const coverStyle = computed(() =>
  cover.value ? { backgroundImage: `url("${cover.value}")` } : undefined,
)
</script>

<template>
  <router-link :to="`/shops/${shop.id}`" class="shop-card card">
    <div class="cover" :style="coverStyle">
      <span v-if="!cover" class="cover-empty">暂无图片</span>
      <span v-if="shop.distance != null" class="distance">{{ formatDistance(shop.distance) }}</span>
    </div>
    <div class="body">
      <h3 class="name">{{ shop.name }}</h3>
      <div class="meta">
        <span class="score" v-if="shop.score">{{ (shop.score / 10).toFixed(1) }} 分</span>
        <span class="muted">{{ shop.area }}</span>
      </div>
      <div class="flex-between bottom">
        <span class="price">
          <span class="price-symbol">¥</span>{{ shopAvgPrice(shop.avgPrice) }}
          <span class="per muted">/人</span>
        </span>
        <span class="faint sold">已售 {{ shop.sold }}</span>
      </div>
    </div>
  </router-link>
</template>

<style scoped>
.shop-card {
  display: block;
  overflow: hidden;
  transition: box-shadow 0.18s, transform 0.18s;
}

.shop-card:hover {
  box-shadow: var(--shadow-hover);
  transform: translateY(-2px);
}

.cover {
  position: relative;
  height: 150px;
  display: grid;
  place-items: center;
}

.cover-empty {
  color: var(--text-faint);
  font-size: 13px;
}

.distance {
  position: absolute;
  right: 8px;
  bottom: 8px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 12px;
  padding: 2px 7px;
  border-radius: 6px;
}

.body {
  padding: 12px 14px 14px;
}

.name {
  margin: 0 0 6px;
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  margin-bottom: 10px;
}

.score {
  color: var(--warning);
  font-weight: 600;
}

.bottom {
  font-size: 13px;
}

.per {
  font-size: 10px;
  font-weight: 400;
}

.sold {
  font-size: 12px;
}
</style>
