<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { formatCountdown } from '@/utils/format'

/**
 * 秒杀倒计时。
 *
 * ── 一个容易踩的坑：为什么倒计时必须以服务端时间为准 ──
 * 直接拿本机时间和券的起止时间比，用户把系统时间往前调一天就能"提前开抢"，
 * 或者把时间调后就能看到"已结束"。本项目为简化没有做服务端时间下发，
 * 但这是已知取舍（见文档），真实项目应该由服务端返回时间偏移量。
 */
const props = defineProps<{
  beginTime?: string
  endTime?: string
}>()

const emit = defineEmits<{ (e: 'state-change', state: SeckillState): void }>()

export type SeckillState = 'not-started' | 'in-progress' | 'ended'

const state = ref<SeckillState>('in-progress')
const remainText = ref('')

function toTime(value?: string): number | null {
  if (!value) return null
  const normalized = value.includes('T') ? value.replace('T', ' ') : value
  const t = new Date(normalized).getTime()
  return Number.isNaN(t) ? null : t
}

function tick() {
  const now = Date.now()
  const begin = toTime(props.beginTime)
  const end = toTime(props.endTime)

  let next: SeckillState = 'in-progress'
  let target: number | null = null

  if (end != null && now >= end) {
    next = 'ended'
  } else if (begin != null && now < begin) {
    next = 'not-started'
    target = begin
  } else if (end != null) {
    next = 'in-progress'
    target = end
  }

  if (next !== state.value) {
    state.value = next
    emit('state-change', next)
  }

  remainText.value = target == null ? '' : formatCountdown(target - now)
}

tick()
const timer = window.setInterval(tick, 1000)
onUnmounted(() => window.clearInterval(timer))

const label = computed(() =>
  state.value === 'not-started' ? '距开始' : state.value === 'ended' ? '已结束' : '距结束',
)
</script>

<template>
  <div class="countdown" :class="state">
    <span class="label">{{ label }}</span>
    <span v-if="remainText" class="value">{{ remainText }}</span>
    <span v-else class="value">—</span>
  </div>
</template>

<style scoped>
.countdown {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  padding: 3px 9px;
  border-radius: 6px;
  background: #f3f4f6;
  color: var(--text-muted);
}

.countdown.in-progress {
  background: var(--brand-soft);
  color: var(--brand-dark);
}

.countdown.not-started {
  background: #fff8e6;
  color: #a16207;
}

.countdown.ended {
  background: #f3f4f6;
  color: var(--text-faint);
}

.value {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}
</style>
