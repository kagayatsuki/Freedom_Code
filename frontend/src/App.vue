<template>
  <div class="app-container">
    <header class="app-header">
      <div class="header-left">
        <span class="logo">&#x2B50;</span>
        <h1 class="app-title">智能代码生成平台</h1>
        <nav class="header-nav">
          <button
            class="nav-btn"
            :class="{ active: currentView === 'chat' }"
            @click="currentView = 'chat'"
          >对话</button>
          <button
            class="nav-btn"
            :class="{ active: currentView === 'workbench' }"
            @click="currentView = 'workbench'"
          >工作台</button>
        </nav>
      </div>
      <div class="header-right">
        <UserButton />
      </div>
    </header>

    <main class="app-main">
      <template v-if="currentView === 'chat'">
        <aside class="sidebar">
          <button class="btn-new-chat" @click="newChat">
            <span>+</span> 新对话
          </button>
          <div class="chat-list">
            <div
              v-for="chat in chatList"
              :key="chat.id"
              class="chat-item"
              :class="{ active: chat.id === currentChatId }"
              @click="switchChat(chat.id)"
            >
              <span class="chat-name">{{ chat.title }}</span>
            </div>
          </div>
        </aside>

        <section class="chat-area">
          <ChatDialog
            ref="chatDialogRef"
            :chat-id="currentChatId"
          />
        </section>
      </template>

      <Workbench v-else />
    </main>

    <LoginModal
      v-if="showLogin"
      @close="showLogin = false"
      @login-success="onLoginSuccess"
    />

    <MemberPopup
      v-if="showMemberPopup"
      @close="onMemberPopupClose"
      @subscribe="onSubscribe"
    />

    <div v-if="toast.visible" class="toast" :class="toast.type">{{ toast.message }}</div>
  </div>
</template>

<script setup>
import { ref, reactive, provide, onMounted } from 'vue'
import { useUserStore } from './stores/user.js'
import UserButton from './components/UserButton.vue'
import ChatDialog from './components/ChatDialog.vue'
import LoginModal from './components/LoginModal.vue'
import Workbench from './components/Workbench.vue'
import MemberPopup from './components/MemberPopup.vue'

const userStore = useUserStore()
const showLogin = ref(false)
const showMemberPopup = ref(false)
const toast = reactive({ visible: false, message: '', type: 'success' })

provide('showLogin', showLogin)
provide('showMemberPopup', showMemberPopup)
provide('showToast', (message, type = 'success') => {
  toast.message = message
  toast.type = type
  toast.visible = true
  setTimeout(() => { toast.visible = false }, 2000)
})

function onLoginSuccess(u, t) {
  userStore.setAuth(u, t)
  showLogin.value = false

  // 检查是否需要显示会员弹窗（每天第一次登录且未升级）
  setTimeout(() => {
    if (!userStore.isMember && userStore.shouldShowMemberPopup()) {
      showMemberPopup.value = true
    }
  }, 500)
}

function onMemberPopupClose() {
  showMemberPopup.value = false
  userStore.markMemberPopupShown()
}

function onSubscribe(planId) {
  console.log('订阅套餐:', planId)
  // 这里可以调用后端API进行订阅
  showMemberPopup.value = false
  userStore.markMemberPopupShown()
}

const currentView = ref('chat')
const currentChatId = ref(1)
const chatList = ref([{ id: 1, title: '新对话' }])
const chatDialogRef = ref(null)

function newChat() {
  const id = Date.now()
  chatList.value.unshift({ id, title: '新对话' })
  currentChatId.value = id
}

function switchChat(id) {
  currentChatId.value = id
}
</script>

<style scoped>
.app-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg-root);
}

/* ===== Header ===== */
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  height: 48px;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
  position: relative;
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo {
  font-size: 18px;
  filter: grayscale(0);
}

.app-title {
  font-size: 15px;
  font-weight: 700;
  letter-spacing: -0.01em;
  background: linear-gradient(135deg, #818cf8, #6366f1, #a78bfa);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.header-nav {
  display: flex;
  gap: 2px;
  margin-left: 20px;
  background: var(--bg-tertiary);
  border-radius: 8px;
  padding: 2px;
}

.nav-btn {
  padding: 5px 14px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-muted);
  font-size: 12.5px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}
.nav-btn:hover {
  color: var(--text-secondary);
}
.nav-btn.active {
  color: var(--text-primary);
  background: var(--bg-secondary);
  box-shadow: 0 1px 3px rgba(0,0,0,0.3);
}

/* ===== Main Content ===== */
.app-main {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* ===== Sidebar ===== */
.sidebar {
  width: 240px;
  background: var(--bg-sidebar);
  border-right: 1px solid var(--border-color);
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex-shrink: 0;
}

.btn-new-chat {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 9px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  background: transparent;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}
.btn-new-chat:hover {
  border-color: var(--accent);
  color: var(--text-primary);
  background: var(--bg-hover);
}
.btn-new-chat span { font-size: 16px; }

.chat-list {
  flex: 1;
  overflow-y: auto;
}

.chat-item {
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 12.5px;
  color: var(--text-muted);
  transition: all 0.15s ease;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  border-left: 2px solid transparent;
}
.chat-item:hover {
  background: var(--bg-hover);
  color: var(--text-secondary);
}
.chat-item.active {
  background: var(--bg-active);
  color: var(--text-primary);
  border-left-color: var(--accent);
}

.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--bg-primary);
}

/* ===== Toast ===== */
.toast {
  position: fixed;
  top: 64px;
  left: 50%;
  transform: translateX(-50%);
  padding: 8px 20px;
  border-radius: 20px;
  font-size: 13px;
  z-index: 300;
  animation: toast-in 0.3s ease;
  pointer-events: none;
  backdrop-filter: blur(8px);
  font-weight: 500;
}
.toast.success {
  background: rgba(34, 197, 94, 0.2);
  border: 1px solid rgba(34, 197, 94, 0.3);
  color: #4ade80;
}
.toast.error {
  background: rgba(244, 63, 94, 0.2);
  border: 1px solid rgba(244, 63, 94, 0.3);
  color: #fb7185;
}

@keyframes toast-in {
  from { opacity: 0; transform: translateX(-50%) translateY(-12px) scale(0.96); }
  to { opacity: 1; transform: translateX(-50%) translateY(0) scale(1); }
}

/* ===== Responsive ===== */
@media (max-width: 768px) {
  .sidebar { display: none; }
  .app-title { display: none; }
}
</style>
