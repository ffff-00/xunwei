<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { blogApi, userApi } from '@/api'
import type { Blog, UserProfile } from '@/api/types'
import BlogCard from '@/components/BlogCard.vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const profile = ref<UserProfile | null>(null)
const myBlogs = ref<Blog[]>([])
const signCount = ref(0)
const signing = ref(false)
const loading = ref(true)

/** 后端 gender 是 Boolean：false=男、true=女（见 tb_user_info 的列注释），null=未填写 */
function genderText(gender?: boolean | null): string {
  if (gender == null) return '保密'
  return gender ? '女' : '男'
}

onMounted(async () => {
  const me = userStore.info
  if (!me) {
    router.replace('/login')
    return
  }
  try {
    const [profileRes, blogRes, signRes] = await Promise.all([
      userApi.getProfile(me.id),
      blogApi.ofMe(1),
      userApi.signCount(),
    ])
    profile.value = profileRes.data ?? null
    myBlogs.value = blogRes.data ?? []
    signCount.value = (signRes.data as number) ?? 0
  } finally {
    loading.value = false
  }
})

async function doSign() {
  if (signing.value) return
  signing.value = true
  try {
    await userApi.sign()
    const res = await userApi.signCount()
    signCount.value = (res.data as number) ?? 0
    ElMessage.success('签到成功')
  } finally {
    signing.value = false
  }
}
</script>

<template>
  <div class="page-narrow">
    <el-skeleton v-if="loading" :rows="6" animated />

    <template v-else>
      <!-- 个人卡片 -->
      <div class="card card-pad me-card">
        <div class="head">
          <el-avatar :size="64" :src="userStore.info?.icon || undefined">
            {{ userStore.info?.nickName?.slice(0, 1) ?? 'U' }}
          </el-avatar>
          <div class="head-info">
            <h2>{{ userStore.info?.nickName ?? '未设置昵称' }}</h2>
            <div class="stats">
              <span><strong>{{ profile?.fans ?? 0 }}</strong> 粉丝</span>
              <span><strong>{{ profile?.followee ?? 0 }}</strong> 关注</span>
              <span><strong>{{ profile?.credits ?? 0 }}</strong> 积分</span>
            </div>
          </div>
          <el-button round @click="router.push('/me/edit')">编辑资料</el-button>
        </div>

        <div class="detail-grid">
          <div class="item">
            <span class="label faint">城市</span>
            <span>{{ profile?.city || '未填写' }}</span>
          </div>
          <div class="item">
            <span class="label faint">性别</span>
            <span>{{ genderText(profile?.gender) }}</span>
          </div>
          <div class="item">
            <span class="label faint">生日</span>
            <span>{{ profile?.birthday || '未填写' }}</span>
          </div>
          <div class="item">
            <span class="label faint">会员等级</span>
            <span>Lv.{{ profile?.level ?? 0 }}</span>
          </div>
        </div>

        <p class="introduce muted">{{ profile?.introduce || '这个人很懒，还没有写介绍' }}</p>
      </div>

      <!-- 签到 -->
      <div class="section-title">
        <h2 class="bar-title">每日签到</h2>
        <span class="hint">用 Redis Bitmap 记录，一个月只占 4 个字节</span>
      </div>
      <div class="card card-pad sign-card">
        <div class="sign-left">
          <div class="sign-num">{{ signCount }}</div>
          <div class="faint">连续签到天数</div>
        </div>
        <el-button type="primary" round :loading="signing" @click="doSign">签到</el-button>
      </div>

      <!-- 我的笔记 -->
      <div class="section-title">
        <h2 class="bar-title">我的探店笔记</h2>
        <el-button text size="small" @click="router.push('/blogs/edit')">写一篇</el-button>
      </div>
      <div v-if="myBlogs.length" class="grid-cards">
        <BlogCard v-for="b in myBlogs" :key="b.id" :blog="b" />
      </div>
      <div v-else class="empty-block">还没有发布过探店笔记</div>
    </template>
  </div>
</template>

<style scoped>
.me-card {
  margin-bottom: 4px;
}

.head {
  display: flex;
  align-items: center;
  gap: 18px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--border);
}

.head-info {
  flex: 1;
}

.head-info h2 {
  margin: 0 0 8px;
  font-size: 19px;
}

.stats {
  display: flex;
  gap: 18px;
  font-size: 13px;
  color: var(--text-muted);
}

.stats strong {
  color: var(--text);
  font-size: 15px;
  margin-right: 2px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 14px;
  padding: 18px 0 4px;
}

.item {
  display: flex;
  flex-direction: column;
  gap: 3px;
  font-size: 13.5px;
}

.label {
  font-size: 12px;
}

.introduce {
  margin: 14px 0 0;
  font-size: 13.5px;
  line-height: 1.8;
}

.sign-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sign-num {
  font-size: 30px;
  font-weight: 700;
  color: var(--brand);
  line-height: 1.1;
}
</style>
