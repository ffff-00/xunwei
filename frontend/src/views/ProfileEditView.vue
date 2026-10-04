<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi } from '@/api'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const form = reactive({
  city: '',
  introduce: '',
  /** 用字符串绑到 el-radio-group，提交时再转回布尔（后端 gender 是 Boolean） */
  gender: '' as '' | 'male' | 'female',
  birthday: '',
})

const loading = ref(true)
const submitting = ref(false)

onMounted(async () => {
  const me = userStore.info
  if (!me) {
    router.replace('/login')
    return
  }
  try {
    const res = await userApi.getProfile(me.id)
    const p = res.data
    if (p) {
      form.city = p.city ?? ''
      form.introduce = p.introduce ?? ''
      form.gender = p.gender == null ? '' : p.gender ? 'female' : 'male'
      form.birthday = p.birthday ?? ''
    }
  } finally {
    loading.value = false
  }
})

async function submit() {
  submitting.value = true
  try {
    await userApi.saveProfile({
      city: form.city,
      introduce: form.introduce,
      // '' 表示不填 → 传 null 保持"未填写"，而不是当成 false（男）
      gender: form.gender === '' ? null : form.gender === 'female',
      birthday: form.birthday || null,
    })
    ElMessage.success('资料已保存')
    router.push('/me')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="page-narrow">
    <div class="section-title">
      <h2 class="bar-title">编辑资料</h2>
    </div>

    <el-skeleton v-if="loading" :rows="5" animated />

    <div v-else class="card card-pad">
      <el-form label-position="top" :model="form">
        <el-form-item label="城市">
          <el-input v-model="form.city" maxlength="20" placeholder="如：杭州" />
        </el-form-item>

        <el-form-item label="性别">
          <el-radio-group v-model="form.gender">
            <el-radio value="male">男</el-radio>
            <el-radio value="female">女</el-radio>
            <el-radio value="">保密</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="生日">
          <el-date-picker
            v-model="form.birthday"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="个人介绍">
          <el-input
            v-model="form.introduce"
            type="textarea"
            :rows="4"
            maxlength="128"
            show-word-limit
            placeholder="介绍一下自己，最多 128 字"
          />
        </el-form-item>

        <div class="actions">
          <el-button type="primary" size="large" :loading="submitting" @click="submit">
            保存
          </el-button>
          <el-button size="large" @click="router.back()">取消</el-button>
        </div>
      </el-form>

      <p class="faint note">
        粉丝数、关注数、积分、会员等级由系统维护，不在这里修改。
      </p>
    </div>
  </div>
</template>

<style scoped>
.actions {
  display: flex;
  gap: 12px;
  margin-top: 8px;
}

.note {
  font-size: 12px;
  margin: 18px 0 0;
}
</style>
