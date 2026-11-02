<template>
  <div class="chat-container">
    <!-- 项目选择器 -->
    <div class="project-bar">
      <div class="project-select-row">
        <el-icon :size="14"><FolderOpened /></el-icon>
        <el-select
          v-model="selectedProject"
          placeholder="选择项目以开启对话"
          size="small"
          :loading="projectsLoading"
          class="project-select"
          @change="onProjectChange"
        >
          <el-option
            v-for="p in projects"
            :key="p"
            :label="p"
            :value="p"
          />
        </el-select>
        <span v-if="selectedProject" class="project-context-hint">
          当前对话基于项目 <strong>{{ selectedProject }}</strong>
        </span>
      </div>
    </div>

    <div class="messages-area" ref="messagesRef">
      <!-- 未选项目状态 -->
      <div v-if="!selectedProject" class="empty-state">
        <div class="empty-icon">📁</div>
        <h2>请先选择一个项目</h2>
        <p>选择项目后，AI 将基于该项目代码回答您的问题</p>
      </div>

      <!-- 已选项目但无消息 -->
      <div v-else-if="messages.length === 0" class="empty-state">
        <div class="empty-icon">💬</div>
        <h2>项目：{{ selectedProject }}</h2>
        <p>向 AI 提问关于此项目的任何问题</p>
        <div class="quick-prompts">
          <button
            v-for="p in quickPrompts"
            :key="p"
            class="prompt-chip"
            @click="sendMessage(p)"
          >{{ p }}</button>
        </div>
      </div>

      <!-- 消息列表 -->
      <div
        v-for="(msg, idx) in messages"
        :key="idx"
        class="message-row"
        :class="msg.role"
      >
        <div class="message-bubble">
          <div class="message-content" v-html="renderContent(msg.content)"></div>
          <div v-if="msg.code" class="code-actions">
            <button class="btn-copy" @click="copyCode(msg.code)">复制代码</button>
          </div>
        </div>
      </div>

      <div v-if="streaming" class="message-row assistant">
        <div class="message-bubble">
          <div class="typing-indicator">
            <span></span><span></span><span></span>
          </div>
        </div>
      </div>
    </div>

    <div class="input-area">
      <div class="input-wrapper">
        <textarea
          ref="inputRef"
          v-model="inputText"
          class="chat-input"
          :placeholder="inputPlaceholder"
          rows="3"
          @keydown.enter.exact.prevent="sendMessage()"
          @keydown.shift.enter="insertNewline"
          @input="autoResize"
        ></textarea>
        <button
          class="btn-send"
          :disabled="!inputText.trim() || streaming || !selectedProject"
          @click="sendMessage()"
        >
          发送
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, watch, onMounted } from 'vue'
import { FolderOpened } from '@element-plus/icons-vue'
import { useUserStore } from '../stores/user.js'
import { request, ApiError } from '../utils/api.js'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'

const props = defineProps({ chatId: [Number, String] })

const userStore = useUserStore()

// ----- 项目选择 -----
const projects = ref([])
const selectedProject = ref('')
const projectsLoading = ref(false)
const userName = ref(userStore.user?.username || 'guest')

async function fetchProjects() {
  const u = userStore.user?.username || 'guest'
  userName.value = u
  projectsLoading.value = true
  try {
    const res = await fetch(`/api/projects/${u}`)
    const data = await res.json()
    if (data?.code === 200 && Array.isArray(data.data)) {
      projects.value = data.data
    } else if (Array.isArray(data)) {
      projects.value = data
    }
    // 默认选中第一个项目
    if (projects.value.length > 0 && !selectedProject.value) {
      selectedProject.value = projects.value[0]
    }
  } catch {
    // 静默
  } finally {
    projectsLoading.value = false
  }
}

function onProjectChange(projectName) {
  // 切换项目时清空消息
  messages.value = []
}

// ----- 对话 -----
const messages = ref([])
const inputText = ref('')
const streaming = ref(false)
const messagesRef = ref(null)
const inputRef = ref(null)

const inputPlaceholder = selectedProject.value
  ? `向 AI 提问关于「${selectedProject.value}」的问题...`
  : '请先在上方选择一个项目'

const quickPrompts = [
  '这个项目的整体架构是怎样的？',
  '有哪些文件？各自的作用是什么？',
  '如何修改样式/主题？',
  '代码中有没有潜在的问题？',
  '帮我解释一下核心逻辑',
]

marked.setOptions({
  breaks: true,
  highlight(code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      return hljs.highlight(code, { language: lang }).value
    }
    return hljs.highlightAuto(code).value
  },
})

function renderContent(text) {
  return marked.parse(text)
}

function autoResize() {
  const el = inputRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 200) + 'px'
}

function insertNewline() {
  const el = inputRef.value
  if (!el) return
  const start = el.selectionStart
  const end = el.selectionEnd
  inputText.value = inputText.value.substring(0, start) + '\n' + inputText.value.substring(end)
  nextTick(() => {
    el.selectionStart = el.selectionEnd = start + 1
  })
}

async function sendMessage(promptText) {
  const text = promptText || inputText.value.trim()
  if (!text || streaming.value || !selectedProject.value) return

  messages.value.push({ role: 'user', content: text })
  inputText.value = ''
  if (inputRef.value) {
    inputRef.value.style.height = 'auto'
  }
  await scrollToBottom()

  streaming.value = true
  try {
    const data = await request('/api/chat/send', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${userStore.token}`,
      },
      body: JSON.stringify({
        chatId: props.chatId,
        message: text,
        userName: userName.value,
        projectName: selectedProject.value,
      }),
    })
    messages.value.push({
      role: 'assistant',
      content: data.data || data.reply || data,
      code: data.code,
    })
  } catch (e) {
    if (e instanceof ApiError) {
      if (e.status === 401) {
        userStore.logout()
        messages.value.push({ role: 'assistant', content: '请先登录后再进行对话。' })
      } else {
        messages.value.push({ role: 'assistant', content: e.message })
      }
    } else {
      messages.value.push({ role: 'assistant', content: '抱歉，请求失败，请稍后再试。' })
    }
  } finally {
    streaming.value = false
    await scrollToBottom()
  }
}

async function copyCode(code) {
  try {
    await navigator.clipboard.writeText(code)
  } catch {
    const ta = document.createElement('textarea')
    ta.value = code
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
  }
}

async function scrollToBottom() {
  await nextTick()
  const el = messagesRef.value
  if (el) el.scrollTop = el.scrollHeight
}

watch(
  () => props.chatId,
  () => {
    messages.value = []
  }
)

onMounted(() => {
  fetchProjects()
})
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 100%;
}

/* ===== 项目选择条 ===== */
.project-bar {
  padding: 8px 16px;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-tertiary);
  flex-shrink: 0;
}
.project-select-row {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--text-muted);
}
.project-select {
  width: 200px;
}
.project-context-hint {
  font-size: 12px;
  color: var(--text-secondary);
}
.project-context-hint strong {
  color: var(--accent-light);
  font-weight: 600;
}

/* ===== 消息区域 ===== */
.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-align: center;
  gap: 12px;
}
.empty-icon { font-size: 48px; opacity: 0.6; }
.empty-state h2 {
  font-size: 22px;
  background: linear-gradient(135deg, var(--accent), #a78bfa);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  margin-top: 8px;
}
.empty-state p {
  color: var(--text-muted);
  font-size: 14px;
  margin-bottom: 12px;
}

.quick-prompts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  max-width: 500px;
}
.prompt-chip {
  padding: 8px 16px;
  border: 1px solid var(--border-color);
  border-radius: 20px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}
.prompt-chip:hover {
  border-color: var(--accent);
  color: var(--accent);
  background: rgba(79, 140, 255, 0.06);
}

.message-row {
  display: flex;
  margin-bottom: 20px;
}
.message-row.user { justify-content: flex-end; }

.message-bubble {
  max-width: 75%;
  padding: 12px 18px;
  border-radius: 14px;
  line-height: 1.7;
  font-size: 14px;
}
.message-row.user .message-bubble {
  background: var(--bg-bubble-user);
  border-bottom-right-radius: 4px;
}
.message-row.assistant .message-bubble {
  background: var(--bg-bubble-ai);
  border: 1px solid var(--border-color);
  border-bottom-left-radius: 4px;
}

.message-content :deep(pre) {
  background: var(--bg-code);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 14px 16px;
  overflow-x: auto;
  margin: 8px 0;
  font-size: 13px;
  line-height: 1.5;
}
.message-content :deep(code) {
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
}
.message-content :deep(p) { margin: 4px 0; }
.message-content :deep(ul), .message-content :deep(ol) {
  padding-left: 20px;
  margin: 4px 0;
}

.code-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 6px;
}
.btn-copy {
  padding: 4px 12px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-copy:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 8px 0;
}
.typing-indicator span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--text-muted);
  animation: bounce 1.2s infinite;
}
.typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.4s; }

@keyframes bounce {
  0%, 60%, 100% { transform: translateY(0); }
  30% { transform: translateY(-6px); }
}

/* ===== 输入区域 ===== */
.input-area {
  padding: 12px 24px 20px;
  flex-shrink: 0;
}
.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  background: var(--bg-input);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 8px 14px;
  transition: border-color 0.2s;
}
.input-wrapper:focus-within {
  border-color: var(--accent);
}

.chat-input {
  flex: 1;
  border: none;
  background: transparent;
  color: var(--text-primary);
  font-size: 14px;
  outline: none;
  resize: none;
  max-height: 200px;
  min-height: 60px;
  line-height: 1.5;
  font-family: inherit;
}
.chat-input::placeholder { color: var(--text-muted); }

.btn-send {
  padding: 7px 18px;
  border: none;
  border-radius: 8px;
  background: var(--accent);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.2s;
  flex-shrink: 0;
}
.btn-send:hover { background: var(--accent-hover); }
.btn-send:disabled { opacity: 0.4; cursor: not-allowed; }
</style>
