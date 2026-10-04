<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { seckillApi } from '@/api'
import type { VoucherOrderVO } from '@/api/types'
import { fen2yuan, fromNow } from '@/utils/format'

const router = useRouter()
const orders = ref<VoucherOrderVO[]>([])
const loading = ref(true)

/** 订单状态码 → 文案与颜色（与后端 tb_voucher_order.status 的注释一致） */
const STATUS_MAP: Record<number, { text: string; type: 'warning' | 'success' | 'info' | 'danger' }> = {
  1: { text: '待支付', type: 'warning' },
  2: { text: '已支付', type: 'success' },
  3: { text: '已核销', type: 'info' },
  4: { text: '已取消', type: 'danger' },
  5: { text: '退款中', type: 'warning' },
  6: { text: '已退款', type: 'info' },
}

function statusOf(status: number) {
  return STATUS_MAP[status] ?? { text: `未知(${status})`, type: 'info' as const }
}

onMounted(async () => {
  try {
    const res = await seckillApi.myOrders()
    orders.value = res.data ?? []
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page-narrow">
    <div class="section-title">
      <h2 class="bar-title">我的订单</h2>
      <router-link to="/shops" class="more">去逛逛 →</router-link>
    </div>

    <el-skeleton v-if="loading" :rows="5" animated />

    <template v-else-if="orders.length">
      <div class="order-list">
        <div v-for="o in orders" :key="o.orderId" class="order card">
          <div class="cover cover-img" :style="{ background: 'var(--brand-soft)' }">
            <span class="cover-icon">券</span>
          </div>
          <div class="main">
            <div class="row1">
              <span class="title">{{ o.voucherTitle }}</span>
              <el-tag :type="statusOf(o.status).type" size="small" effect="light">
                {{ statusOf(o.status).text }}
              </el-tag>
            </div>
            <div class="sub muted">{{ o.voucherSubTitle }}</div>
            <div class="shop faint">
              <router-link :to="`/shops/${o.shopId}`">{{ o.shopName || '未知商铺' }}</router-link>
            </div>
            <div class="meta faint">
              订单号 {{ o.orderId }} · {{ fromNow(o.createTime) }}
            </div>
          </div>
          <div class="right">
            <div class="amount">
              <span class="symbol">¥</span>{{ fen2yuan(o.payValue) }}
            </div>
            <div class="origin faint">面值 ¥{{ fen2yuan(o.actualValue) }}</div>
          </div>
        </div>
      </div>
    </template>

    <div v-else class="empty-block">
      还没有订单
      <div class="empty-action">
        <el-button type="primary" round @click="router.push('/shops')">去找好店</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.more {
  color: var(--brand);
  font-size: 13px;
}

.order-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.order {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
}

.cover-img {
  flex-shrink: 0;
  width: 62px;
  height: 62px;
  border-radius: 10px;
  display: grid;
  place-items: center;
}

.cover-icon {
  color: var(--brand);
  font-weight: 700;
  font-size: 20px;
}

.main {
  flex: 1;
  min-width: 0;
}

.row1 {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 4px;
}

.title {
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sub {
  font-size: 13px;
  margin-bottom: 6px;
}

.shop {
  font-size: 12.5px;
  margin-bottom: 4px;
}

.shop a:hover {
  color: var(--brand);
}

.meta {
  font-size: 12px;
}

.right {
  flex-shrink: 0;
  text-align: right;
}

.amount {
  font-size: 20px;
  font-weight: 700;
  color: var(--brand);
}

.symbol {
  font-size: 13px;
}

.origin {
  font-size: 12px;
  text-decoration: line-through;
}

.empty-action {
  margin-top: 16px;
}
</style>
