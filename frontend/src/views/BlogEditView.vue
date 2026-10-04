<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type UploadFile } from 'element-plus'
import { blogApi, shopApi } from '@/api'
import type { Shop } from '@/api/types'
import { splitImages } from '@/utils/format'

const route = useRoute()
const router = useRouter()

/** 从 /blogs/edit?id=xx 进来就是编辑，否则是新建 */
const editId = ref<number | null>(route.query.id ? Number(route.query.id) : null)

const form = reactive({
  title: '',
  content: '',
  shopId: undefined as number | undefined,
  images: [] as string[],
})

const shopOptions = ref<Shop[]>([])
const shopSearching = ref(false)
const submitting = ref(false)
const uploading = ref(false)

onMounted(async () => {
  await searchShop('')

  if (editId.value != null) {
    try {
      const res = await blogApi.detail(editId.value)
      const blog = res.data
      if (blog) {
        form.title = blog.title
        form.content = blog.content
        form.shopId = blog.shopId
        form.images = splitImages(blog.images)
      }
    } catch {
      ElMessage.warning('笔记不存在，将以新建方式编辑')
      editId.value = null
    }
  }
})

/** 按关键词搜商铺，做远程搜索的下拉选择（商铺多时不能用一次性加载的静态下拉） */
async function searchShop(keyword: string) {
  shopSearching.value = true
  try {
    const res = await shopApi.byName({ name: keyword, current: 1 })
    shopOptions.value = res.data ?? []
  } finally {
    shopSearching.value = false
  }
}

async function handleUpload(file: UploadFile) {
  if (!file.raw) return
  uploading.value = true
  try {
    const res = await blogApi.upload(file.raw)
    if (res.data) {
      form.images.push(res.data)
    }
  } finally {
    uploading.value = false
  }
}

function removeImage(index: number) {
  form.images.splice(index, 1)
}

async function submit() {
  if (!form.title.trim()) {
    ElMessage.warning('请填写标题')
    return
  }
  if (!form.content.trim()) {
    ElMessage.warning('请写点内容吧')
    return
  }
  if (!form.shopId) {
    ElMessage.warning('请选择关联的商铺')
    return
  }

  submitting.value = true
  try {
    await blogApi.save({
      id: editId.value ?? undefined,
      title: form.title.trim(),
      content: form.content,
      shopId: form.shopId,
      images: form.images.join(','),
    })
    ElMessage.success(editId.value ? '已更新' : '发布成功')
    router.push('/blogs')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="page-narrow">
    <div class="section-title">
      <h2 class="bar-title">{{ editId ? '编辑探店笔记' : '写探店笔记' }}</h2>
    </div>

    <div class="card card-pad">
      <el-form label-position="top" :model="form">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="60" show-word-limit placeholder="一句话说清这家店好在哪" />
        </el-form-item>

        <el-form-item label="关联商铺" required>
          <el-select
            v-model="form.shopId"
            filterable
            remote
            clearable
            reserve-keyword
            placeholder="输入商铺名搜索"
            :remote-method="searchShop"
            :loading="shopSearching"
            style="width: 100%"
          >
            <el-option v-for="s in shopOptions" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="正文" required>
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="10"
            maxlength="2000"
            show-word-limit
            placeholder="写点真实的体验：环境、服务、必点的菜……"
          />
        </el-form-item>

        <el-form-item label="图片">
          <div class="upload-area">
            <div v-for="(img, i) in form.images" :key="i" class="thumb">
              <img :src="img" alt="" />
              <button class="remove" type="button" @click="removeImage(i)">×</button>
            </div>
            <el-upload
              v-if="form.images.length < 9"
              :show-file-list="false"
              :auto-upload="false"
              :on-change="handleUpload"
              accept="image/*"
            >
              <div class="add-btn" :class="{ loading: uploading }">
                {{ uploading ? '上传中…' : '+ 添加图片' }}
              </div>
            </el-upload>
          </div>
          <div class="faint hint">最多 9 张，单张不超过 5MB</div>
        </el-form-item>

        <div class="actions">
          <el-button type="primary" size="large" :loading="submitting" @click="submit">
            {{ editId ? '保存修改' : '发布' }}
          </el-button>
          <el-button size="large" @click="router.back()">取消</el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.upload-area {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.thumb {
  position: relative;
  width: 96px;
  height: 96px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--border);
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.remove {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 20px;
  height: 20px;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
}

.add-btn {
  width: 96px;
  height: 96px;
  display: grid;
  place-items: center;
  border: 1px dashed #d0d5dd;
  border-radius: 8px;
  color: var(--text-muted);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}

.add-btn:hover {
  border-color: var(--brand);
  color: var(--brand);
}

.add-btn.loading {
  cursor: wait;
  opacity: 0.7;
}

.hint {
  font-size: 12px;
  margin-top: 6px;
}

.actions {
  display: flex;
  gap: 12px;
  margin-top: 8px;
}
</style>
