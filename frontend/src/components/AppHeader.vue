<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const navs = [
  { name: 'home', label: '首页', to: '/' },
  { name: 'shops', label: '找好店', to: '/shops' },
  { name: 'blogs', label: '探店笔记', to: '/blogs' },
]

const activeNav = computed(() => (route.name as string) ?? '')

/**
 * 在登录页隐藏"登录 / 注册"按钮。
 * 不隐藏的话页面上会出现两个同名按钮（顶部一个、表单里一个）——
 * 既让用户困惑，也让自动化测试里的按钮定位不再唯一（这个问题就是端到端验证时暴露出来的）。
 */
const isLoginPage = computed(() => route.name === 'login')

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' })
  } catch {
    return // 用户点了取消
  }
  await userStore.logout()
  router.push('/')
}
</script>

<template>
  <header class="header">
    <div class="header-inner">
      <router-link to="/" class="logo">
        <span class="logo-mark">寻</span>
        <span class="logo-text">寻味</span>
      </router-link>

      <nav class="nav">
        <router-link
          v-for="n in navs"
          :key="n.name"
          :to="n.to"
          class="nav-item"
          :class="{ active: activeNav === n.name }"
        >
          {{ n.label }}
        </router-link>
      </nav>

      <div class="actions">
        <template v-if="userStore.isLoggedIn">
          <router-link to="/orders" class="nav-item">我的订单</router-link>
          <el-dropdown trigger="click">
            <span class="user-chip">
              <el-avatar :size="26" :src="userStore.info?.icon || undefined">
                {{ userStore.info?.nickName?.slice(0, 1) ?? 'U' }}
              </el-avatar>
              <span class="user-name">{{ userStore.info?.nickName ?? '我的' }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="router.push('/me')">个人主页</el-dropdown-item>
                <el-dropdown-item @click="router.push('/blogs/edit')">发布探店</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <el-button
          v-else-if="!isLoginPage"
          type="primary"
          round
          size="small"
          @click="router.push('/login')"
        >
          登录 / 注册
        </el-button>
      </div>
    </div>
  </header>
</template>

<style scoped>
.header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--border);
}

.header-inner {
  max-width: 1180px;
  margin: 0 auto;
  padding: 0 16px;
  height: 58px;
  display: flex;
  align-items: center;
  gap: 28px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.logo-mark {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: var(--brand);
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  display: grid;
  place-items: center;
}

.logo-text {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 1px;
}

.nav {
  display: flex;
  gap: 4px;
  flex: 1;
}

.nav-item {
  padding: 6px 12px;
  border-radius: 8px;
  color: var(--text-muted);
  font-size: 14px;
  transition: all 0.15s;
}

.nav-item:hover {
  color: var(--text);
  background: #f3f4f6;
}

.nav-item.active {
  color: var(--brand);
  background: var(--brand-soft);
  font-weight: 600;
}

.actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 4px 10px 4px 4px;
  border-radius: 999px;
  cursor: pointer;
  outline: none;
  transition: background 0.15s;
}

.user-chip:hover {
  background: #f3f4f6;
}

.user-name {
  font-size: 13px;
  max-width: 90px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
</style>
