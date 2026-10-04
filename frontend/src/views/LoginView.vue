<script setup lang="ts">
import { onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi } from '@/api'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const form = reactive({ phone: '', code: '' })
const sending = ref(false)
const submitting = ref(false)
const countdown = ref(0)
let timer: number | undefined

/** 手机号格式校验与后端保持一致（后端才是权威，这里只是提前给反馈） */
const PHONE_PATTERN = /^1[3-9]\d{9}$/

async function sendCode() {
  if (!PHONE_PATTERN.test(form.phone)) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  sending.value = true
  try {
    const res = await userApi.sendCode(form.phone)
    if (res.code === 200) {
      ElMessage.success('验证码已发送（本地开发请看后端控制台日志）')
      startCountdown()
    }
  } finally {
    sending.value = false
  }
}

function startCountdown() {
  countdown.value = 60
  window.clearInterval(timer)
  timer = window.setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) {
      window.clearInterval(timer)
    }
  }, 1000)
}

async function submit() {
  if (!PHONE_PATTERN.test(form.phone)) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  if (!form.code) {
    ElMessage.warning('请输入验证码')
    return
  }
  submitting.value = true
  try {
    await userStore.login(form.phone, form.code)
    ElMessage.success('登录成功')
    // 登录后回到用户本来想去的页面（守卫在拦截时把它放进了 query）
    const redirect = (route.query.redirect as string) || '/'
    router.replace(redirect)
  } catch {
    // 错误提示已由请求层统一处理
  } finally {
    submitting.value = false
  }
}

onUnmounted(() => window.clearInterval(timer))
</script>

<template>
  <div class="login-page">
    <div class="login-card card">
      <div class="brand">
        <div class="brand-mark">寻</div>
        <h1>寻味</h1>
        <p class="muted">发现城市里的好味道</p>
      </div>

      <el-form :model="form" size="large" @submit.prevent>
        <el-form-item>
          <el-input v-model="form.phone" placeholder="手机号" maxlength="11" clearable>
            <template #prefix>
              <span class="prefix">+86</span>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item>
          <div class="code-row">
            <el-input
              v-model="form.code"
              placeholder="验证码"
              maxlength="6"
              @keyup.enter="submit"
            />
            <el-button
              :disabled="countdown > 0"
              :loading="sending"
              class="code-btn"
              @click="sendCode"
            >
              {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
            </el-button>
          </div>
        </el-form-item>

        <el-button
          type="primary"
          size="large"
          class="submit-btn"
          :loading="submitting"
          @click="submit"
        >
          登录 / 注册
        </el-button>
      </el-form>

      <p class="tip muted">
        未注册的手机号将自动创建账号。<br />
        本地开发环境不发真实短信，<strong>验证码在后端控制台日志里</strong>。
      </p>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: calc(100vh - 58px);
  display: grid;
  place-items: center;
  padding: 32px 16px;
  background:
    radial-gradient(900px 420px at 15% -10%, #ffe9dd 0%, transparent 60%),
    radial-gradient(760px 380px at 95% 0%, #e8f1ff 0%, transparent 55%),
    var(--bg);
}

.login-card {
  width: 100%;
  max-width: 380px;
  padding: 34px 30px 26px;
}

.brand {
  text-align: center;
  margin-bottom: 26px;
}

.brand-mark {
  width: 48px;
  height: 48px;
  margin: 0 auto 12px;
  border-radius: 14px;
  background: var(--brand);
  color: #fff;
  font-size: 24px;
  font-weight: 700;
  display: grid;
  place-items: center;
  box-shadow: 0 8px 20px rgba(244, 98, 42, 0.28);
}

.brand h1 {
  margin: 0 0 4px;
  font-size: 22px;
  letter-spacing: 2px;
}

.brand p {
  margin: 0;
  font-size: 13px;
}

.prefix {
  color: var(--text-muted);
  font-size: 13px;
}

.code-row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.code-btn {
  flex-shrink: 0;
  width: 112px;
}

.submit-btn {
  width: 100%;
  margin-top: 4px;
}

.tip {
  margin: 20px 0 0;
  font-size: 12px;
  line-height: 1.8;
  text-align: center;
}
</style>
