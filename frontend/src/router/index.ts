import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

/**
 * 路由表。
 *
 * meta.public = true 表示不需要登录。
 * 需要登录的页面由下面的守卫统一拦截，而不是在每个页面里各写一次判断 ——
 * 判断散在各处，早晚会漏掉一个。
 */
const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', name: 'home', component: () => import('@/views/HomeView.vue'), meta: { public: true } },
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
    {
      path: '/shops',
      name: 'shops',
      component: () => import('@/views/ShopListView.vue'),
      meta: { public: true },
    },
    {
      path: '/shops/:id',
      name: 'shop-detail',
      component: () => import('@/views/ShopDetailView.vue'),
      props: true,
      meta: { public: true },
    },
    {
      path: '/seckill/:voucherId',
      name: 'seckill',
      component: () => import('@/views/SeckillView.vue'),
      props: true,
    },
    { path: '/orders', name: 'orders', component: () => import('@/views/OrderListView.vue') },
    { path: '/blogs', name: 'blogs', component: () => import('@/views/BlogListView.vue'), meta: { public: true } },
    { path: '/blogs/edit', name: 'blog-edit', component: () => import('@/views/BlogEditView.vue') },
    {
      path: '/blogs/:id',
      name: 'blog-detail',
      component: () => import('@/views/BlogDetailView.vue'),
      props: true,
      meta: { public: true },
    },
    { path: '/me', name: 'me', component: () => import('@/views/ProfileView.vue') },
    { path: '/me/edit', name: 'me-edit', component: () => import('@/views/ProfileEditView.vue') },
    {
      path: '/users/:id',
      name: 'user',
      component: () => import('@/views/UserProfileView.vue'),
      props: true,
      meta: { public: true },
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach(async (to) => {
  const userStore = useUserStore()

  // 首次进入时用本地令牌换一次用户信息。放在守卫里做而不是在 main.ts 里做，
  // 是为了保证"第一个页面渲染时登录态已经恢复完毕"——否则刷新后会闪一下未登录状态。
  if (!userStore.restored) {
    await userStore.restore()
  }

  if (!to.meta.public && !userStore.isLoggedIn) {
    // 带上原始目标，登录后能回到用户本来想去的页面
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
