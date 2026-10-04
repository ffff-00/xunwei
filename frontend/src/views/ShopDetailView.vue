<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { shopApi, voucherApi } from '@/api'
import type { Shop, Voucher } from '@/api/types'
import SeckillCountdown, { type SeckillState } from '@/components/SeckillCountdown.vue'
import { fen2yuan, shopAvgPrice, splitImages } from '@/utils/format'
import { useUserStore } from '@/stores/user'

const props = defineProps<{ id: string }>()
const router = useRouter()
const userStore = useUserStore()

const shop = ref<Shop | null>(null)
const vouchers = ref<Voucher[]>([])
const loading = ref(true)
/** 秒杀券的实时状态（由倒计时组件回传），用来控制按钮可用性 */
const seckillStates = ref<Record<number, SeckillState>>({})

const images = computed(() => splitImages(shop.value?.images))

const normalVouchers = computed(() => vouchers.value.filter((v) => v.type !== 1))
const seckillVouchers = computed(() => vouchers.value.filter((v) => v.type === 1))

onMounted(async () => {
  try {
    const [shopRes, voucherRes] = await Promise.all([
      shopApi.detail(Number(props.id)),
      voucherApi.ofShop(Number(props.id)),
    ])
    shop.value = shopRes.data ?? null
    vouchers.value = voucherRes.data ?? []
  } finally {
    loading.value = false
  }
})

function canSeckill(v: Voucher): boolean {
  const state = seckillStates.value[v.id]
  return state === 'in-progress' && (v.stock ?? 0) > 0
}

function seckillButtonText(v: Voucher): string {
  const state = seckillStates.value[v.id]
  if (state === 'not-started') return '即将开始'
  if (state === 'ended') return '已结束'
  if ((v.stock ?? 0) <= 0) return '已抢完'
  return '立即抢购'
}

function goSeckill(v: Voucher) {
  // 未登录先引导登录，并把目标带过去（守卫也会拦，但这里能给出更明确的提示）
  if (!userStore.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: `/seckill/${v.id}` } })
    return
  }
  router.push(`/seckill/${v.id}`)
}
</script>

<template>
  <div class="page">
    <el-skeleton v-if="loading" :rows="8" animated />

    <template v-else-if="shop">
      <!-- 商铺头部 -->
      <div class="card head">
        <div class="gallery">
          <el-carousel v-if="images.length" height="320px" :autoplay="images.length > 1">
            <el-carousel-item v-for="(img, i) in images" :key="i">
              <div class="slide cover" :style="{ backgroundImage: `url('${img}')` }"></div>
            </el-carousel-item>
          </el-carousel>
          <div v-else class="slide cover placeholder">暂无图片</div>
        </div>

        <div class="info">
          <h1>{{ shop.name }}</h1>
          <div class="tags">
            <span v-if="shop.score" class="tag score">{{ (shop.score / 10).toFixed(1) }} 分</span>
            <span class="tag">{{ shop.area }}</span>
            <span v-if="shop.openHours" class="tag">{{ shop.openHours }}</span>
          </div>
          <p class="address muted">{{ shop.address }}</p>

          <div class="numbers">
            <div class="num">
              <span class="num-value price"><span class="price-symbol">¥</span>{{ shopAvgPrice(shop.avgPrice) }}</span>
              <span class="num-label">人均</span>
            </div>
            <div class="num">
              <span class="num-value">{{ shop.sold ?? 0 }}</span>
              <span class="num-label">已售</span>
            </div>
            <div class="num">
              <span class="num-value">{{ shop.comments ?? 0 }}</span>
              <span class="num-label">评价</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 秒杀券 -->
      <div v-if="seckillVouchers.length" class="section-title">
        <h2 class="bar-title">限时秒杀</h2>
        <span class="hint">数量有限，抢完即止</span>
      </div>
      <div v-if="seckillVouchers.length" class="voucher-list">
        <div v-for="v in seckillVouchers" :key="v.id" class="voucher seckill">
          <div class="v-left">
            <div class="v-price">
              <span class="price-symbol">¥</span>{{ fen2yuan(v.payValue) }}
            </div>
            <div class="v-origin faint">原价 ¥{{ fen2yuan(v.actualValue) }}</div>
          </div>
          <div class="v-mid">
            <div class="v-title">{{ v.title }}</div>
            <div class="v-sub muted">{{ v.subTitle }}</div>
            <div class="v-meta">
              <SeckillCountdown
                :begin-time="v.beginTime"
                :end-time="v.endTime"
                @state-change="(s) => (seckillStates[v.id] = s)"
              />
              <span class="stock" :class="{ low: (v.stock ?? 0) <= 10 }">
                剩余 {{ v.stock ?? 0 }} 张
              </span>
            </div>
          </div>
          <div class="v-right">
            <el-button
              type="primary"
              round
              :disabled="!canSeckill(v)"
              @click="goSeckill(v)"
            >
              {{ seckillButtonText(v) }}
            </el-button>
          </div>
        </div>
      </div>

      <!-- 普通券 -->
      <div v-if="normalVouchers.length" class="section-title">
        <h2 class="bar-title">优惠券</h2>
      </div>
      <div v-if="normalVouchers.length" class="voucher-list">
        <div v-for="v in normalVouchers" :key="v.id" class="voucher">
          <div class="v-left">
            <div class="v-price">
              <span class="price-symbol">¥</span>{{ fen2yuan(v.payValue) }}
            </div>
            <div class="v-origin faint">面值 ¥{{ fen2yuan(v.actualValue) }}</div>
          </div>
          <div class="v-mid">
            <div class="v-title">{{ v.title }}</div>
            <div class="v-sub muted">{{ v.subTitle }}</div>
            <div class="v-rules faint">{{ v.rules }}</div>
          </div>
          <div class="v-right">
            <el-button round disabled>暂不支持购买</el-button>
          </div>
        </div>
      </div>

      <div v-if="!vouchers.length" class="empty-block">这家店暂时没有可用的优惠券</div>
    </template>

    <div v-else class="empty-block">商铺不存在或已下架</div>
  </div>
</template>

<style scoped>
.head {
  overflow: hidden;
  margin-bottom: 4px;
}

.gallery {
  background: #f0f1f3;
}

.slide {
  width: 100%;
  height: 320px;
}

.placeholder {
  display: grid;
  place-items: center;
  color: var(--text-faint);
}

.info {
  padding: 20px 22px 22px;
}

.info h1 {
  margin: 0 0 10px;
  font-size: 22px;
  letter-spacing: 0.3px;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.tag {
  font-size: 12px;
  padding: 2px 9px;
  border-radius: 6px;
  background: #f3f4f6;
  color: var(--text-muted);
}

.tag.score {
  background: var(--brand-soft);
  color: var(--brand-dark);
  font-weight: 600;
}

.address {
  margin: 0 0 16px;
  font-size: 13px;
}

.numbers {
  display: flex;
  gap: 40px;
  padding-top: 14px;
  border-top: 1px dashed var(--border);
}

.num {
  display: flex;
  flex-direction: column;
}

.num-value {
  font-size: 19px;
  font-weight: 600;
}

.num-label {
  font-size: 12px;
  color: var(--text-faint);
}

.voucher-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.voucher {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 16px 20px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}

.voucher.seckill {
  border-color: #ffd9c7;
  background: linear-gradient(90deg, #fff8f5 0%, #ffffff 42%);
}

.v-left {
  flex-shrink: 0;
  width: 108px;
  text-align: center;
}

.v-price {
  font-size: 26px;
  font-weight: 700;
  color: var(--brand);
  line-height: 1.1;
}

.v-origin {
  font-size: 12px;
  text-decoration: line-through;
}

.v-mid {
  flex: 1;
  min-width: 0;
}

.v-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 3px;
}

.v-sub {
  font-size: 13px;
  margin-bottom: 8px;
}

.v-rules {
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.v-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.stock {
  font-size: 12px;
  color: var(--text-muted);
}

.stock.low {
  color: var(--danger);
  font-weight: 600;
}

.v-right {
  flex-shrink: 0;
}
</style>
