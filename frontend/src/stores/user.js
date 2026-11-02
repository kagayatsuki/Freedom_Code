import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

const ONE_DAY_MS = 24 * 60 * 60 * 1000

function loadUser() {
  try {
    const raw = localStorage.getItem('user')
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function loadLoginTime() {
  const time = localStorage.getItem('loginTime')
  return time ? parseInt(time, 10) : null
}

function isLoginExpired(loginTime) {
  if (!loginTime) return true
  return Date.now() - loginTime > ONE_DAY_MS
}

export const useUserStore = defineStore('user', () => {
  const user = ref(loadUser())
  const token = ref(localStorage.getItem('token') || '')
  const loginTime = ref(loadLoginTime())
  const lastMemberPopupDate = ref(localStorage.getItem('lastMemberPopupDate') || '')

  const isLoggedIn = computed(() => {
    if (!token.value || !user.value) return false
    if (isLoginExpired(loginTime.value)) {
      logout()
      return false
    }
    return true
  })

  const isMember = computed(() => {
    return (user.value?.status || 0) >= 2
  })

  const userStatus = computed(() => {
    return user.value?.status || 1
  })

  const tierName = computed(() => {
    const names = ['禁用', '普通用户', '升级版', '专业版']
    return names[userStatus.value] || '普通用户'
  })

  const tierBadge = computed(() => {
    const badges = ['', '', '⭐', '👑']
    return badges[userStatus.value] || ''
  })

  function setAuth(u, t) {
    user.value = u
    token.value = t
    loginTime.value = Date.now()
    localStorage.setItem('token', t)
    localStorage.setItem('user', JSON.stringify(u))
    localStorage.setItem('loginTime', Date.now().toString())
  }

  function logout() {
    user.value = null
    token.value = ''
    loginTime.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    localStorage.removeItem('loginTime')
  }

  function shouldShowMemberPopup() {
    const today = new Date().toDateString()
    const lastPopup = localStorage.getItem('lastMemberPopupDate')
    return lastPopup !== today
  }

  function markMemberPopupShown() {
    const today = new Date().toDateString()
    localStorage.setItem('lastMemberPopupDate', today)
    lastMemberPopupDate.value = today
  }

  return {
    user,
    token,
    isLoggedIn,
    isMember,
    userStatus,
    tierName,
    tierBadge,
    setAuth,
    logout,
    shouldShowMemberPopup,
    markMemberPopupShown
  }
})
