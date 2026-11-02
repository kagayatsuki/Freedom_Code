<template>
  <div class="user-btn" @click="toggleMenu">
    <div v-if="userStore.isLoggedIn" class="user-avatar">
      {{ userStore.user?.username?.charAt(0)?.toUpperCase() || 'U' }}
    </div>
    <span v-else class="login-text">登录</span>

    <div v-if="menuVisible" class="dropdown-menu" @click.stop>
      <template v-if="userStore.isLoggedIn">
        <div class="menu-user">
          <div class="menu-avatar">
            {{ userStore.user?.username?.charAt(0)?.toUpperCase() || 'U' }}
            <div v-if="userStore.isMember" class="member-badge">👑</div>
          </div>
          <div class="menu-user-info">
            <div class="menu-username">
              {{ userStore.user?.username }}
              <span class="tier-tag" :class="'tier-' + userStore.userStatus">
                {{ userStore.tierName }}
              </span>
            </div>
            <div class="menu-email">{{ userStore.user?.email }}</div>
          </div>
        </div>
        <div class="menu-divider"></div>
        <button v-if="!userStore.isMember" class="menu-item upgrade" @click="handleUpgrade">
          ⭐ 升级会员
        </button>
        <button class="menu-item danger" @click="handleLogout">退出登录</button>
      </template>
      <template v-else>
        <button class="menu-item" @click="showLogin = true">登录 / 注册</button>
      </template>
    </div>
  </div>

  <div v-if="menuVisible" class="overlay" @click="menuVisible = false"></div>
</template>

<script setup>
import { ref, inject } from 'vue'
import { useUserStore } from '../stores/user.js'

const userStore = useUserStore()
const menuVisible = ref(false)
const showLogin = inject('showLogin')
const showMemberPopup = inject('showMemberPopup')

function toggleMenu() {
  if (!userStore.isLoggedIn) {
    showLogin.value = true
    return
  }
  menuVisible.value = !menuVisible.value
}

function handleLogout() {
  userStore.logout()
  menuVisible.value = false
}

function handleUpgrade() {
  menuVisible.value = false
  // 触发会员弹窗
  if (showMemberPopup) {
    showMemberPopup.value = true
  }
}
</script>

<style scoped>
.user-btn {
  position: relative;
  cursor: pointer;
  user-select: none;
}

.user-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--accent), #a78bfa);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  transition: box-shadow 0.2s;
}
.user-avatar:hover {
  box-shadow: 0 0 0 3px rgba(79, 140, 255, 0.3);
}

.login-text {
  font-size: 14px;
  color: var(--accent);
  padding: 6px 14px;
  border: 1px solid var(--accent);
  border-radius: 6px;
  transition: all 0.2s;
}
.login-text:hover {
  background: var(--accent);
  color: #fff;
}

.dropdown-menu {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  min-width: 200px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  z-index: 100;
  padding: 8px;
}

.menu-user {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
}
.menu-avatar {
  position: relative;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--accent), #a78bfa);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 15px;
  flex-shrink: 0;
}

.member-badge {
  position: absolute;
  bottom: -2px;
  right: -2px;
  font-size: 14px;
  background: var(--bg-secondary);
  border-radius: 50%;
  padding: 1px;
}
.menu-username {
  font-size: 14px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 6px;
}

.tier-tag {
  font-size: 10px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 4px;
}
.tier-tag.tier-1 { color: #94a3b8; background: rgba(148, 163, 184, 0.12); }
.tier-tag.tier-2 { color: #fbbf24; background: rgba(251, 191, 36, 0.15); }
.tier-tag.tier-3 { color: #e879f9; background: rgba(232, 121, 249, 0.15); }

.member-tag {
  font-size: 11px;
  font-weight: 600;
  color: #fbbf24;
  background: rgba(251, 191, 36, 0.15);
  padding: 2px 6px;
  border-radius: 4px;
}
.menu-email {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 2px;
}

.menu-divider {
  height: 1px;
  background: var(--border-color);
  margin: 6px 0;
}

.menu-item {
  width: 100%;
  padding: 9px 12px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-primary);
  font-size: 13px;
  cursor: pointer;
  text-align: left;
  transition: background 0.15s;
}
.menu-item:hover {
  background: rgba(255, 255, 255, 0.05);
}
.menu-item.danger {
  color: var(--danger);
}
.menu-item.danger:hover {
  background: rgba(224, 85, 106, 0.1);
}

.menu-item.upgrade {
  color: #fbbf24;
  background: rgba(251, 191, 36, 0.1);
}
.menu-item.upgrade:hover {
  background: rgba(251, 191, 36, 0.2);
}

.overlay {
  position: fixed;
  inset: 0;
  z-index: 99;
}
</style>
