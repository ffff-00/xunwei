<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { blogApi, followApi, userApi } from '@/api'
import type { Blog, UserInfo, UserProfile } from '@/api/types'
import BlogCard from '@/components/BlogCard.vue'
import { useUserStore } from '@/stores/user'

const props = defineProps<{ id: string }>()
const router = useRouter()
const userStore = useUserStore()

const user = ref<UserInfo | null>(null)
const profile = ref<UserProfile | null>(null)
const blogs = ref<Blog[]>([])
const isFollow = ref(false)
const commons = ref<UserInfo[]>([])
const loading = ref(true)
const following = ref(false)

const userId = computed(() => Number(props.id))
const isSelf = computed(() => userStore.info?.id === userId.value)

function genderText(gender?: boolean | null): string {
  if (gender == null) return '保密'
  return gender ? '女' : '男'
}

async function load() {
  loading.value = true
  try {
    const [userRes, profileRes, blogRes] = await Promise.all([
      userApi.getUser(userId.value),
      userApi.getProfile(userId.value),
      blogApi.ofUser(userId.value, 1),
    ])
    user.value = userRes.data ?? null
    profile.value = profileRes.data ?? null
    blogs.value = blogRes.data ?? []

    // 关注状态与共同关注只在登录后查（未登录查会 401）
    if (userStore.isLoggedIn && !isSelf.value) {
      const [followRes, commonRes] = await Promise.all([
        followApi.isFollow(userId.value),
        followApi.commons(userId.value),
      ])
      isFollow.value = !!followRes.data
      commons.value = commonRes.data ?? []
    }
  } finally {
    loading.value = false
  }
}

async function toggleFollow() {
  if (!userStore.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: `/users/${props.id}` } })
    return
  }
  if (following.value) return
  following.value = true
  try {
    const next = !isFollow.value
    await followApi.follow(userId.value, next)
    isFollow.value = next
    if (profile.value) {
      // 粉丝数是对方的数字，本地乐观更新一下，不用重查整个页面
      profile.value.fans = (profile.value.fans ?? 0) + (next ? 1 : -1)
    }
    ElMessage.success(next ? '已关注' : '已取消关注')
  } finally {
    following.value = false
  }
}

onMounted(load)
watch(() => props.id, load)
</script>

<template>
  <div class="page-narrow">
    <el-skeleton v-if="loading" :rows="6" animated />

    <template v-else-if="user">
      <div class="card card-pad">
        <div class="head">
          <el-avatar :size="64" :src="user.icon || undefined">
            {{ user.nickName?.slice(0, 1) ?? 'U' }}
          </el-avatar>
          <div class="head-info">
            <h2>{{ user.nickName }}</h2>
            <div class="stats">
              <span><strong>{{ profile?.fans ?? 0 }}</strong> 粉丝</span>
              <span><strong>{{ profile?.followee ?? 0 }}</strong> 关注</span>
              <span v-if="profile?.city" class="faint">{{ profile.city }}</span>
              <span class="faint">{{ genderText(profile?.gender) }}</span>
            </div>
          </div>
          <el-button
            v-if="!isSelf"
            :type="isFollow ? 'default' : 'primary'"
            round
            :loading="following"
            @click="toggleFollow"
          >
            {{ isFollow ? '已关注' : '关注' }}
          </el-button>
          <el-button v-else round @click="router.push('/me/edit')">编辑资料</el-button>
        </div>

        <p class="introduce muted">{{ profile?.introduce || '这个人很懒，还没有写介绍' }}</p>

        <div v-if="commons.length" class="commons">
          <span class="faint">共同关注</span>
          <router-link v-for="u in commons" :key="u.id" :to="`/users/${u.id}`" class="common-user">
            <el-avatar :size="24" :src="u.icon || undefined">
              {{ u.nickName?.slice(0, 1) ?? 'U' }}
            </el-avatar>
            <span>{{ u.nickName }}</span>
          </router-link>
        </div>
      </div>

      <div class="section-title">
        <h2 class="bar-title">TA 的探店笔记</h2>
      </div>
      <div v-if="blogs.length" class="grid-cards">
        <BlogCard v-for="b in blogs" :key="b.id" :blog="b" />
      </div>
      <div v-else class="empty-block">还没有发布过探店笔记</div>
    </template>

    <div v-else class="empty-block">用户不存在</div>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  gap: 18px;
  padding-bottom: 16px;
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
  align-items: center;
  gap: 16px;
  font-size: 13px;
  color: var(--text-muted);
}

.stats strong {
  color: var(--text);
  font-size: 15px;
  margin-right: 2px;
}

.introduce {
  margin: 16px 0 0;
  font-size: 13.5px;
  line-height: 1.8;
}

.commons {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px dashed var(--border);
  font-size: 12px;
}

.common-user {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  padding: 3px 10px 3px 3px;
  border-radius: 999px;
  background: #f7f8fa;
}

.common-user:hover {
  background: var(--brand-soft);
}
</style>
