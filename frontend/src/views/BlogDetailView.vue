<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { blogApi } from '@/api'
import { BizError } from '@/api/request'
import { BizCode, type Blog, type UserInfo } from '@/api/types'
import { splitImages, fromNow } from '@/utils/format'
import { useUserStore } from '@/stores/user'

const props = defineProps<{ id: string }>()
const router = useRouter()
const userStore = useUserStore()

const blog = ref<Blog | null>(null)
const likes = ref<UserInfo[]>([])
const loading = ref(true)
const liking = ref(false)

const isMine = ref(false)

onMounted(async () => {
  try {
    const res = await blogApi.detail(Number(props.id))
    blog.value = res.data ?? null
    if (blog.value) {
      isMine.value = userStore.info?.id === blog.value.userId
      // 只有登录后才查点赞列表；未登录时接口会 401，没必要打个必败的请求
      if (userStore.isLoggedIn) {
        await loadLikes()
      }
    }
  } catch (e) {
    if (e instanceof BizError && e.code === BizCode.BLOG_NOT_FOUND) {
      blog.value = null
    }
  } finally {
    loading.value = false
  }
})

async function loadLikes() {
  try {
    const res = await blogApi.likes(Number(props.id))
    likes.value = res.data ?? []
  } catch {
    // 点赞列表是次要信息，失败就不展示，不影响正文
  }
}

async function toggleLike() {
  if (!userStore.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: `/blogs/${props.id}` } })
    return
  }
  if (liking.value || !blog.value) return
  liking.value = true
  try {
    // 后端返回的是"操作后的点赞状态"，不是简单翻转 ——
    // 前端自己取反在重复点击/并发下会和服务端状态不一致
    const res = await blogApi.like(Number(props.id))
    const liked = res.data
    blog.value.isLike = liked
    blog.value.liked = (blog.value.liked ?? 0) + (liked ? 1 : -1)
    await loadLikes()
    ElMessage.success(liked ? '已点赞' : '已取消点赞')
  } finally {
    liking.value = false
  }
}
</script>

<template>
  <div class="page-narrow">
    <el-skeleton v-if="loading" :rows="8" animated />

    <article v-else-if="blog" class="card article">
      <h1>{{ blog.title }}</h1>

      <div class="author-row">
        <router-link :to="`/users/${blog.userId}`" class="author">
          <el-avatar :size="36" :src="blog.icon || undefined">
            {{ blog.name?.slice(0, 1) ?? 'U' }}
          </el-avatar>
          <div class="author-meta">
            <span class="nick">{{ blog.name ?? '匿名用户' }}</span>
            <span class="time faint">{{ fromNow(blog.createTime) }}</span>
          </div>
        </router-link>
        <el-button
          v-if="isMine"
          text
          size="small"
          @click="router.push(`/blogs/edit?id=${blog.id}`)"
        >
          编辑
        </el-button>
      </div>

      <div class="content">{{ blog.content }}</div>

      <div v-if="splitImages(blog.images).length" class="images">
        <el-image
          v-for="(img, i) in splitImages(blog.images)"
          :key="i"
          :src="img"
          :preview-src-list="splitImages(blog.images)"
          :initial-index="i"
          fit="cover"
          class="img"
        />
      </div>

      <div class="footer">
        <el-button
          class="like-btn"
          :type="blog.isLike ? 'primary' : 'default'"
          round
          :loading="liking"
          @click="toggleLike"
        >
          ♥ {{ blog.liked ?? 0 }}
        </el-button>
        <span class="faint">{{ likes.length }} 人点过赞</span>
      </div>

      <div v-if="likes.length" class="likers">
        <span class="likers-label faint">点赞的人</span>
        <div class="likers-list">
          <router-link v-for="u in likes" :key="u.id" :to="`/users/${u.id}`" class="liker">
            <el-avatar :size="26" :src="u.icon || undefined">
              {{ u.nickName?.slice(0, 1) ?? 'U' }}
            </el-avatar>
            <span class="liker-name">{{ u.nickName }}</span>
          </router-link>
        </div>
      </div>
    </article>

    <div v-else class="empty-block">
      这篇笔记不存在或已删除
      <div style="margin-top: 14px">
        <el-button round @click="router.push('/blogs')">返回笔记列表</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.article {
  padding: 28px 30px 26px;
  margin-top: 24px;
}

.article h1 {
  margin: 0 0 16px;
  font-size: 22px;
  line-height: 1.5;
}

.author-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border);
  margin-bottom: 20px;
}

.author {
  display: flex;
  align-items: center;
  gap: 10px;
}

.author-meta {
  display: flex;
  flex-direction: column;
}

.nick {
  font-size: 14px;
  font-weight: 600;
}

.time {
  font-size: 12px;
}

.content {
  font-size: 15px;
  line-height: 2;
  /* 笔记正文里的换行要保留：原项目的数据就是纯文本，换行即段落 */
  white-space: pre-wrap;
  word-break: break-word;
  margin-bottom: 20px;
}

.images {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 8px;
  margin-bottom: 22px;
}

.img {
  width: 100%;
  height: 150px;
  border-radius: 8px;
  cursor: zoom-in;
}

.footer {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid var(--border);
}

.like-btn {
  min-width: 84px;
}

.likers {
  margin-top: 18px;
}

.likers-label {
  font-size: 12px;
}

.likers-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 8px;
}

.liker {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  padding: 3px 10px 3px 3px;
  border-radius: 999px;
  background: #f7f8fa;
}

.liker:hover {
  background: var(--brand-soft);
}

.liker-name {
  max-width: 80px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
</style>
