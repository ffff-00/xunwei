<script setup lang="ts">
import { computed } from 'vue'
import type { Blog } from '@/api/types'
import { firstImage, fromNow } from '@/utils/format'

const props = defineProps<{ blog: Blog }>()

const cover = computed(() => firstImage(props.blog.images))
const coverStyle = computed(() =>
  cover.value ? { backgroundImage: `url("${cover.value}")` } : undefined,
)
</script>

<template>
  <router-link :to="`/blogs/${blog.id}`" class="blog-card card">
    <div class="cover" :style="coverStyle">
      <span v-if="!cover" class="cover-empty">无图</span>
    </div>
    <div class="body">
      <h3 class="title">{{ blog.title }}</h3>
      <div class="author">
        <el-avatar :size="22" :src="blog.icon || undefined">
          {{ blog.name?.slice(0, 1) ?? 'U' }}
        </el-avatar>
        <span class="nick muted">{{ blog.name ?? '匿名用户' }}</span>
        <span class="faint time">{{ fromNow(blog.createTime) }}</span>
      </div>
      <div class="stats">
        <span class="stat" :class="{ liked: blog.isLike }">
          <span class="dot"></span>{{ blog.liked ?? 0 }} 赞
        </span>
        <span class="stat faint">{{ blog.comments ?? 0 }} 评论</span>
      </div>
    </div>
  </router-link>
</template>

<style scoped>
.blog-card {
  display: block;
  overflow: hidden;
  transition: box-shadow 0.18s, transform 0.18s;
}

.blog-card:hover {
  box-shadow: var(--shadow-hover);
  transform: translateY(-2px);
}

.cover {
  height: 160px;
  display: grid;
  place-items: center;
}

.cover-empty {
  color: var(--text-faint);
  font-size: 13px;
}

.body {
  padding: 12px 14px 14px;
}

.title {
  margin: 0 0 10px;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.45;
  /* 标题最多两行，避免长短不一把卡片撑得高低不平 */
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.author {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 10px;
}

.nick {
  font-size: 12.5px;
  max-width: 110px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.time {
  font-size: 12px;
  margin-left: auto;
}

.stats {
  display: flex;
  gap: 14px;
  font-size: 12.5px;
}

.stat {
  display: flex;
  align-items: center;
  gap: 5px;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--brand);
}

.stat.liked {
  color: var(--brand);
  font-weight: 600;
}
</style>
