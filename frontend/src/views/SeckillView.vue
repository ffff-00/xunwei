<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { voucherApi, seckillApi } from '@/api'
import { BizError } from '@/api/request'
import { BizCode, type Voucher, type VoucherOrder } from '@/api/types'
import SeckillCountdown, { type SeckillState } from '@/components/SeckillCountdown.vue'
import { fen2yuan } from '@/utils/format'

/**
 * 秒杀页 —— 本项目最值得看的前端页面，因为它要正确处理"异步下单"。
 *
 * ── 关键难点：下单接口返回成功 ≠ 订单已创建 ──
 * 后端为了抗住瞬时流量，把下单做成了异步：
 *   抢购接口只在 Redis 里做资格预检（原子判断库存 + 一人一单）并把消息投进 Stream，
 *   真正的落库由后台消费线程完成。所以接口返回的 orderId 只是"排队凭据"。
 *
 * 因此这里必须做两件事：
 *   ① 拿到 orderId 后轮询 GET /voucher-order/{orderId} 直到订单真的出现
 *   ② 把"处理中"（后端业务码 44005）和"真失败"区分开 —— 前者继续等，后者立刻停
 * 原项目没有结果查询接口，前端只能乐观地提示"下单成功"，
 * 订单万一落库失败（库存对不上、DB 抖动），用户就永远等不到那张券。
 */
const props = defineProps<{ voucherId: string }>()
const router = useRouter()

const voucher = ref<Voucher | null>(null)
const loading = ref(true)
const seckillState = ref<SeckillState>('in-progress')

type GrabState = 'idle' | 'submitting' | 'polling' | 'success' | 'failed' | 'timeout'
const grabState = ref<GrabState>('idle')
const orderId = ref<string | null>(null)
const order = ref<VoucherOrder | null>(null)
const failMessage = ref('')

/** 轮询上限：每次间隔 500ms，共 20 次 = 最多等 10 秒 */
const MAX_POLL = 20
const POLL_INTERVAL = 500

const canGrab = computed(
  () => seckillState.value === 'in-progress' && (voucher.value?.stock ?? 0) > 0 && grabState.value === 'idle',
)

const buttonText = computed(() => {
  switch (grabState.value) {
    case 'submitting':
      return '提交中…'
    case 'polling':
      return '确认订单中…'
    case 'success':
      return '已抢到'
    default:
      if (seckillState.value === 'not-started') return '即将开始'
      if (seckillState.value === 'ended') return '已结束'
      if ((voucher.value?.stock ?? 0) <= 0) return '已抢完'
      return '立即抢购'
  }
})

onMounted(async () => {
  try {
    const res = await voucherApi.detail(Number(props.voucherId))
    voucher.value = res.data ?? null
  } finally {
    loading.value = false
  }
})

async function grab() {
  if (!canGrab.value) return
  grabState.value = 'submitting'
  failMessage.value = ''

  try {
    const res = await seckillApi.seckill(Number(props.voucherId))
    orderId.value = res.data
    grabState.value = 'polling'
    await pollOrder(res.data)
  } catch (e) {
    grabState.value = 'failed'
    failMessage.value = describeError(e)
  }
}

/**
 * 轮询等待订单落库。
 * 只有"处理中"才继续轮询，其它错误立刻放弃 —— 否则会对着一个永远不会出现的订单等满超时。
 */
async function pollOrder(id: string) {
  for (let i = 0; i < MAX_POLL; i++) {
    await sleep(POLL_INTERVAL)
    try {
      const res = await seckillApi.queryOrder(id)
      order.value = res.data
      grabState.value = 'success'
      return
    } catch (e) {
      if (e instanceof BizError && e.code === BizCode.SECKILL_ORDER_PROCESSING) {
        continue // 还没落库，继续等
      }
      grabState.value = 'failed'
      failMessage.value = describeError(e)
      return
    }
  }
  // 等满了还没落库：既不说成功也不说失败，如实告诉用户去订单页确认
  grabState.value = 'timeout'
}

function describeError(e: unknown): string {
  if (e instanceof BizError) {
    switch (e.code) {
      case BizCode.SECKILL_STOCK_EMPTY:
        return '手慢了，这张券已经被抢完'
      case BizCode.SECKILL_REPEAT:
        return '每人限购一张，您已经抢到过了'
      case BizCode.SECKILL_NOT_START:
        return '秒杀还没开始，请稍后再来'
      case BizCode.SECKILL_ENDED:
        return '这场秒杀已经结束了'
      case BizCode.UNAUTHORIZED:
        return '请先登录'
      default:
        return e.message
    }
  }
  return '下单失败，请稍后重试'
}

function sleep(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms))
}
</script>

<template>
  <div class="page-narrow">
    <el-skeleton v-if="loading" :rows="6" animated />

    <template v-else-if="voucher">
      <div class="card seckill-card">
        <div class="ribbon">限时秒杀</div>

        <h1>{{ voucher.title }}</h1>
        <p class="sub muted">{{ voucher.subTitle }}</p>

        <div class="price-row">
          <div class="now">
            <span class="symbol">¥</span>{{ fen2yuan(voucher.payValue) }}
          </div>
          <div class="origin faint">原价 ¥{{ fen2yuan(voucher.actualValue) }}</div>
        </div>

        <div class="meta-row">
          <SeckillCountdown
            :begin-time="voucher.beginTime"
            :end-time="voucher.endTime"
            @state-change="(s) => (seckillState = s)"
          />
          <span class="stock">剩余 {{ voucher.stock ?? 0 }} 张</span>
        </div>

        <el-divider />

        <div class="rules">
          <div class="rules-title">使用规则</div>
          <p class="muted">{{ voucher.rules }}</p>
        </div>

        <el-button
          type="primary"
          size="large"
          class="grab-btn"
          :loading="grabState === 'submitting' || grabState === 'polling'"
          :disabled="!canGrab && grabState !== 'success'"
          @click="grab"
        >
          {{ buttonText }}
        </el-button>

        <!-- 抢购结果 -->
        <el-alert
          v-if="grabState === 'success'"
          class="result"
          type="success"
          :closable="false"
          show-icon
          title="抢到啦！"
          :description="`订单号 ${order?.id}，可在「我的订单」查看`"
        />
        <el-alert
          v-else-if="grabState === 'failed'"
          class="result"
          type="error"
          :closable="false"
          show-icon
          :title="failMessage"
        />
        <el-alert
          v-else-if="grabState === 'timeout'"
          class="result"
          type="warning"
          :closable="false"
          show-icon
          title="订单还在处理中"
          :description="`订单号 ${orderId}，稍后到「我的订单」确认即可（并发下异步落库需要一点时间）`"
        />
        <el-alert
          v-else-if="grabState === 'polling'"
          class="result"
          type="info"
          :closable="false"
          show-icon
          title="已抢到资格，正在确认订单…"
          description="下单是异步处理的，这里在轮询最终结果"
        />

        <div class="actions">
          <el-button text @click="router.push('/orders')">查看我的订单</el-button>
          <el-button text @click="router.back()">返回</el-button>
        </div>
      </div>
    </template>

    <div v-else class="empty-block">优惠券不存在</div>
  </div>
</template>

<style scoped>
.seckill-card {
  position: relative;
  overflow: hidden;
  padding: 30px 30px 24px;
  margin-top: 24px;
}

.ribbon {
  position: absolute;
  top: 18px;
  right: -34px;
  transform: rotate(38deg);
  background: var(--brand);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  padding: 4px 40px;
  letter-spacing: 1px;
}

.seckill-card h1 {
  margin: 0 0 6px;
  font-size: 22px;
  padding-right: 40px;
}

.sub {
  margin: 0 0 20px;
  font-size: 13px;
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 14px;
}

.now {
  color: var(--brand);
  font-size: 38px;
  font-weight: 700;
  line-height: 1;
}

.symbol {
  font-size: 20px;
  margin-right: 2px;
}

.origin {
  font-size: 14px;
  text-decoration: line-through;
}

.meta-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.stock {
  font-size: 13px;
  color: var(--text-muted);
}

.rules-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 6px;
}

.rules p {
  margin: 0;
  font-size: 13px;
  line-height: 1.9;
  white-space: pre-wrap;
}

.grab-btn {
  width: 100%;
  height: 48px;
  font-size: 16px;
  letter-spacing: 2px;
  margin-top: 10px;
}

.result {
  margin-top: 16px;
}

.actions {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-top: 14px;
}
</style>
