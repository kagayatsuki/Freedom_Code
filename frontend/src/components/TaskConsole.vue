<template>
  <div class="task-console" :class="{ collapsed: !visible }">
    <div class="console-header">
      <div class="console-header-left">
        <el-icon :size="14"><Monitor /></el-icon>
        <span class="console-title">任务控制台</span>
        <span v-if="active" class="task-badge">运行中</span>
        <span v-else-if="completed" class="task-badge done">已完成</span>
        <span v-else-if="failed" class="task-badge failed">失败</span>
      </div>
      <div class="console-header-right">
        <el-progress
          v-if="active || completed"
          :percentage="progress"
          :status="progressStatus"
          :stroke-width="6"
          style="width: 160px"
        />
        <el-button
          type="success"
          size="small"
          class="preview-btn"
          :disabled="!hasPreview"
          @click="openPreview"
          title="一键预览"
        >
          预览
        </el-button>
        <el-button
          v-if="completed"
          size="small"
          class="download-btn"
          @click="downloadProject"
          title="下载项目代码"
        >
          下载
        </el-button>
        <el-button
          v-if="logs.length > 0"
          :icon="Delete"
          size="small"
          text
          @click="clearLogs"
          title="清空日志"
        />
        <el-button
          :icon="visible ? ArrowDown : ArrowUp"
          size="small"
          text
          @click="visible = !visible"
          :title="visible ? '隐藏控制台' : '显示控制台'"
        />
      </div>
    </div>

    <div v-show="visible" class="console-body">
      <!-- 左侧：输入 + 选项 + 按钮 -->
      <div class="input-panel">
        <el-input
          v-model="prompt"
          type="textarea"
          :rows="3"
          placeholder="描述你需要生成的项目... (Shift+Enter 换行，Enter 发送)"
          size="small"
          class="prompt-input"
          :disabled="active"
          @keydown="handlePromptKeydown"
        />

        <div class="toggle-row">
          <button
            class="toggle-btn"
            :class="{ active: useWebSearch }"
            :disabled="active"
            @click="useWebSearch = !useWebSearch"
          >
            <span class="toggle-icon">🌐</span>
            <span>联网搜索</span>
          </button>
          <button
            class="toggle-btn"
            :class="{ active: useImageSearch }"
            :disabled="active"
            @click="useImageSearch = !useImageSearch"
          >
            <span class="toggle-icon">🖼️</span>
            <span>图片搜索</span>
          </button>
        </div>

        <!-- 配额展示 -->
        <div v-if="userStore.isLoggedIn" class="quota-bar">
          <div class="quota-row">
            <span class="quota-label">🏗️ 项目</span>
            <span class="quota-nums">
              <strong>{{ quota.projectRemaining }}</strong> / {{ quota.projectLimit }}
            </span>
          </div>
          <div class="quota-row">
            <span class="quota-label">💬 对话</span>
            <span class="quota-nums" :class="{ 'quota-low': quota.modifyRemaining <= 2 }">
              <strong>{{ quota.modifyRemaining }}</strong> / {{ quota.modifyLimit }}
            </span>
          </div>
          <div class="quota-row">
            <span class="quota-label">🪙 Token</span>
            <span
              class="quota-nums"
              :class="{ 'quota-low': quota.tokenRemaining >= 0 && quota.tokenRemaining < 50000 }"
            >
              {{ formatTokens(quota.tokenUsed) }} / {{ formatTokens(quota.tokenLimit) }}
            </span>
          </div>
        </div>

        <!-- 刷新后恢复提示 -->
        <div v-if="resumeBanner" class="resume-banner" :class="resumeBanner">
          <span class="resume-text">
            <template v-if="resumeBanner === 'running'">
              ⏳ 刷新前提交的任务仍在后台运行，正在自动跟踪状态…
            </template>
            <template v-else>
              ⚠️ 任务已中断（页面刷新或服务异常）
            </template>
          </span>
          <div class="resume-actions">
            <el-button
              v-if="resumeBanner === 'running'"
              size="small"
              type="danger"
              plain
              @click="cancelStaleTask"
            >取消任务</el-button>
            <el-button v-else size="small" @click="dismissBanner">知道了</el-button>
          </div>
        </div>

        <el-tooltip
          content="请选择项目名或新建项目！"
          placement="top"
          :disabled="hasProject"
          trigger="click"
          :hide-after="2000"
        >
          <el-button
            type="primary"
            :loading="active"
            :disabled="!prompt.trim() || active || !hasProject || quotaBlocked"
            class="send-btn"
            @click="startTask"
          >
            {{ quotaBlocked ? '今日额度已用完' : (active ? '生成中...' : '生成') }}
          </el-button>
        </el-tooltip>
      </div>

      <!-- 右侧：日志 -->
      <div ref="logContainer" class="log-panel">
        <div v-if="logs.length === 0 && !active" class="log-placeholder">
          输入项目描述，点击"生成"开始任务
        </div>
        <div
          v-for="(log, idx) in logs"
          :key="idx"
          class="log-line"
          :class="{ 'log-error': log.type === 'error', 'log-done': log.type === 'done' }"
        >
          <span class="log-time">{{ log.time }}</span>
          <span class="log-msg">{{ log.message }}</span>
        </div>
        <div v-if="active" class="log-line streaming">
          <span class="log-time">--:--:--</span>
          <span class="log-msg">
            <span class="spinner-ring"></span>
            <span>生成中，请耐心等待...</span>
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick, computed, onMounted, inject } from 'vue'
import { Delete, ArrowDown, ArrowUp, Monitor } from '@element-plus/icons-vue'
import { useUserStore } from '../stores/user.js'

const props = defineProps({
  projectName: { type: String, default: 'default' },
  hasProject: { type: Boolean, default: false },
  userName: { type: String, default: 'guest' },
})

const emit = defineEmits(['task-update', 'generation-started', 'generation-ended'])

const userStore = useUserStore()
const showLogin = inject('showLogin')

// 配额状态
  const visible = ref(true)
const quota = ref({
  projectLimit: 0, projectRemaining: 0,
  modifyLimit: 0, modifyRemaining: 0,
  tokenLimit: 0, tokenUsed: 0, tokenRemaining: 0
})
const quotaBlocked = ref(false)

// token 数量格式化：-1 表示不限
function formatTokens(n) {
  if (n == null || n < 0) return '不限'
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1).replace(/\.0$/, '') + 'M'
  if (n >= 1_000) return Math.round(n / 1_000) + 'k'
  return String(n)
}

async function fetchQuota() {
  if (!userStore.isLoggedIn) return
  try {
    const res = await fetch(`/api/workflow/quota?userName=${encodeURIComponent(userStore.user?.username)}`)
    const data = await res.json()
    if (data?.code === 200 && data.data) {
      quota.value = data.data
      const noProject = data.data.modifyRemaining <= 0 && data.data.projectRemaining <= 0
      const noToken = data.data.tokenRemaining === 0
      quotaBlocked.value = noProject || noToken
    }
  } catch {
    // 静默失败
  }
}
const prompt = ref('')
const useImageSearch = ref(false)
const useWebSearch = ref(false)
const active = ref(false)
const completed = ref(false)
const failed = ref(false)
const hasPreview = ref(false)
const previewPath = ref('')
const progress = ref(0)
const logs = ref([])
const logContainer = ref(null)

// 刷新恢复：'running' 后台任务仍在运行 / 'interrupted' 任务已中断
const resumeBanner = ref('')
const staleTaskId = ref('')
let pollTimer = null

let eventSource = null
let abortController = null

// 构建状态持久化
function getBuildStatusKey() {
  return `buildStatus_${props.userName}_${props.projectName}`
}

function getLogsKey() {
  return `logs_${props.userName}_${props.projectName}`
}

function getAppTypeKey() {
  return `appType_${props.userName}_${props.projectName}`
}

// 当前任务状态持久化（taskId + 是否仍在运行），用于刷新后恢复/识别中断
function getTaskStateKey() {
  return `taskState_${props.userName}_${props.projectName}`
}

function saveTaskState(taskId, running) {
  try {
    if (!taskId) {
      localStorage.removeItem(getTaskStateKey())
    } else {
      localStorage.setItem(getTaskStateKey(), JSON.stringify({ taskId, running: !!running }))
    }
  } catch (e) {
    console.error('Failed to save task state:', e)
  }
}

function loadTaskState() {
  try {
    const raw = localStorage.getItem(getTaskStateKey())
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function getStoredAppType() {
  try {
    return localStorage.getItem(getAppTypeKey()) || ''
  } catch {
    return ''
  }
}

function saveAppType(appType) {
  if (!appType) return
  try {
    localStorage.setItem(getAppTypeKey(), appType.toUpperCase())
  } catch (e) {
    console.error('Failed to save appType:', e)
  }
}

function loadBuildStatus() {
  try {
    const status = localStorage.getItem(getBuildStatusKey())
    return status === 'completed'
  } catch {
    return false
  }
}

function saveBuildStatus(isCompleted) {
  try {
    if (isCompleted) {
      localStorage.setItem(getBuildStatusKey(), 'completed')
    } else {
      localStorage.removeItem(getBuildStatusKey())
    }
  } catch (e) {
    console.error('Failed to save build status:', e)
  }
}

// 日志持久化
function saveLogs() {
  try {
    if (logs.value.length > 0) {
      localStorage.setItem(getLogsKey(), JSON.stringify(logs.value))
    } else {
      localStorage.removeItem(getLogsKey())
    }
  } catch (e) {
    console.error('Failed to save logs:', e)
  }
}

function loadLogs() {
  try {
    const stored = localStorage.getItem(getLogsKey())
    if (stored) {
      const parsed = JSON.parse(stored)
      if (Array.isArray(parsed) && parsed.length > 0) {
        logs.value = parsed
        return true
      }
    }
  } catch (e) {
    console.error('Failed to load logs:', e)
  }
  return false
}

// 检查项目是否有历史记录（有构建状态或有日志）
function hasProjectHistory() {
  return localStorage.getItem(getBuildStatusKey()) !== null ||
         localStorage.getItem(getLogsKey()) !== null
}

const stateProgressMap = {
  'INIT': 5,
  'PROMPT_ENHANCED': 15,
  'ROUTED': 25,
  'GENERATING': 40,
  'QC_CHECKING': 55,
  'BUILDING': 70,
  'VALIDATING': 85,
  'COMPLETED': 100,
  'FAILED': 0,
}

const progressStatus = computed(() => {
  if (failed.value) return 'exception'
  if (completed.value) return 'success'
  return ''
})

function now() {
  return new Date().toLocaleTimeString('zh-CN', { hour12: false })
}

function handlePromptKeydown(e) {
  // Enter 发送（不含 Shift），Shift+Enter 换行
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    startTask()
  }
}

function addLog(message, type = 'info') {
  logs.value.push({ time: now(), message, type })
  // 自动保存日志到 localStorage
  saveLogs()
  nextTick(() => {
    const el = logContainer.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function clearLogs() {
  logs.value = []
  saveLogs()
}

async function startTask() {
  const text = prompt.value.trim()
  if (!text || active.value) return

  // 未登录（guest）用户禁止生成文件和创建项目，点击后立即弹出登录框
  if (!userStore.isLoggedIn) {
    if (showLogin) showLogin.value = true
    return
  }

  // 清理上一次任务的恢复状态
  stopPolling()
  resumeBanner.value = ''
  staleTaskId.value = ''
  saveTaskState(null)

  active.value = true
  completed.value = false
  failed.value = false
  progress.value = 0
  logs.value = []
  saveBuildStatus(false)
  saveLogs()

  addLog(`任务已提交: ${text}`)

  const userName = userStore.user?.username || props.userName
  const projectName = props.projectName

  abortController = new AbortController()

  try {
    const response = await fetch('/api/workflow/create', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ prompt: text, projectName, userName, useImageSearch: useImageSearch.value, useWebSearch: useWebSearch.value }),
      signal: abortController.signal,
    })

    // 限流或其他错误（非 SSE 流，返回 JSON）
    if (!response.ok) {
      const contentType = response.headers.get('content-type') || ''
      if (contentType.includes('application/json')) {
        const errData = await response.json()
        throw new Error(errData.message || errData.msg || errData.error || '请求被拒绝')
      }
    }

    if (!response.ok) {
      throw new Error(`请求失败: ${response.status}`)
    }

    const contentType = response.headers.get('content-type') || ''

    if (contentType.includes('text/event-stream')) {
      // 后端直接返回 SSE 流，手动解析
      await readSseStream(response)
    } else {
      // 兼容旧 JSON 模式
      const data = await response.json()
      const taskId = data.data || data.taskId
      if (!taskId) throw new Error('未获取到 taskId')
      addLog(`任务ID: ${taskId}，正在连接日志流...`)
      connectEventSource(taskId)
    }
  } catch (e) {
    if (e.name === 'AbortError') {
      emit('generation-ended')
      return
    }
    const errMsg = e.message || '未知错误'
    addLog(`任务失败: ${errMsg}`, 'error')
    active.value = false
    failed.value = true
    fetchQuota() // 刷新配额
    emit('generation-ended')
  }
}

async function readSseStream(response) {
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      let eventType = ''
      for (const line of lines) {
        if (line.startsWith('event:')) {
          eventType = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          const json = line.slice(5).trim()
          try {
            const event = JSON.parse(json)
            if (eventType === 'workflow-progress' || eventType === 'log') {
              handleWorkflowEvent(event)
            }
          } catch {
            // malformed JSON, skip
          }
        }
      }
    }
  } finally {
    reader.releaseLock()
    active.value = false
    emit('generation-ended')
  }
}

function handleWorkflowEvent(event) {
  const state = event.state
  const msg = event.message
  const stateName = event.stateName

  // 记录当前任务，供页面刷新后恢复/识别中断
  if (event.taskId) {
    staleTaskId.value = event.taskId
    saveTaskState(event.taskId, state !== 'COMPLETED' && state !== 'FAILED')
  }

  // 保存项目类型（路由决策后或完成时携带）
  if (event.appType) {
    saveAppType(event.appType)
  }

  const percentage = stateProgressMap[state]
  if (percentage !== undefined && percentage > 0) {
    progress.value = percentage
  }

  addLog(`[${stateName}] ${msg}`)

  // 代码生成阶段开始 → 通知父组件开始轮询文件树
  if (state === 'GENERATING') {
    emit('generation-started')
  }

  if (state === 'COMPLETED') {
    addLog('任务执行成功!', 'done')
    active.value = false
    completed.value = true
    progress.value = 100
    saveBuildStatus(true)
    saveLogs()
    fetchQuota()
    checkPreview()
    emit('task-update', { taskId: event.taskId, state: 'COMPLETED' })
    emit('generation-ended')
  }

  if (state === 'FAILED') {
    addLog('任务执行失败', 'error')
    active.value = false
    failed.value = true
    progress.value = 0
    saveLogs()
    fetchQuota()
    emit('task-update', { taskId: event.taskId, state: 'FAILED' })
    emit('generation-ended')
  }
}

function connectEventSource(taskId) {
  if (eventSource) eventSource.close()
  eventSource = new EventSource('/api/workflow/stream/' + taskId)

  eventSource.addEventListener('log', e => {
    try {
      handleWorkflowEvent(JSON.parse(e.data))
    } catch {
      // parse error, skip
    }
  })

  eventSource.onerror = () => {
    if (!completed.value && !failed.value) {
      addLog('连接中断，正在重连...', 'error')
    }
    eventSource.close()
    active.value = false
    emit('generation-ended')
  }
}

// ==================== 刷新后恢复 / 识别中断 ====================

// 页面刷新后检查是否有未结束的任务，并向后端确认真实状态
async function restoreStaleTask() {
  const st = loadTaskState()
  if (!st || !st.taskId || !st.running) return

  staleTaskId.value = st.taskId
  try {
    const res = await fetch(`/api/workflow/${encodeURIComponent(st.taskId)}/status`)
    const data = await res.json()
    const info = data?.data ?? data

    if (info?.active === true) {
      // 后端仍在跑：显示提示并自动轮询状态
      resumeBanner.value = 'running'
      active.value = true
      startPolling(st.taskId)
    } else {
      // 已结束或不存在：视为中断（若已 COMPLETED 则按完成处理）
      resumeBanner.value = info?.state === 'COMPLETED' ? '' : 'interrupted'
      if (info?.state === 'COMPLETED') {
        completed.value = true
        progress.value = 100
        saveBuildStatus(true)
      }
      saveTaskState(st.taskId, false)
    }
  } catch {
    // 后端不可达或任务已过期 → 视为中断
    resumeBanner.value = 'interrupted'
    saveTaskState(st.taskId, false)
  }
}

// 轮询后端任务状态（当前后端不支持 SSE 重连，用轮询代替）
function startPolling(taskId) {
  stopPolling()
  pollTimer = setInterval(async () => {
    try {
      const res = await fetch(`/api/workflow/${encodeURIComponent(taskId)}/status`)
      const data = await res.json()
      const info = data?.data ?? data

      if (info?.state) {
        const p = stateProgressMap[info.state]
        if (p !== undefined && p > 0) progress.value = p
      }

      if (info?.active === false) {
        stopPolling()
        resumeBanner.value = ''
        active.value = false
        if (info.state === 'COMPLETED') {
          completed.value = true
          progress.value = 100
          saveBuildStatus(true)
          addLog('后台任务已完成', 'done')
        } else {
          failed.value = true
          addLog(`后台任务已结束: ${info.message || info.state || '中断'}`, 'error')
        }
        fetchQuota()
        checkPreview()
        saveTaskState(taskId, false)
        emit('generation-ended')
      }
    } catch {
      // 轮询失败继续重试
    }
  }, 3000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function cancelStaleTask() {
  const taskId = staleTaskId.value
  if (!taskId) return
  try {
    await fetch(`/api/workflow/${encodeURIComponent(taskId)}/cancel`, { method: 'POST' })
    addLog('已请求取消后台任务', 'error')
  } catch {
    addLog('取消请求失败', 'error')
  }
  stopPolling()
  resumeBanner.value = 'interrupted'
  active.value = false
  failed.value = true
  saveTaskState(taskId, false)
}

function dismissBanner() {
  resumeBanner.value = ''
  staleTaskId.value = ''
}

function openPreview() {
  const userName = props.userName || 'guest'
  const projectName = props.projectName || 'default'
  // 优先用后端探测到的入口路径；探测失败时回退到旧的 appType 判断
  let path = previewPath.value
  if (!path) {
    const appType = getStoredAppType()
    // HTML 项目入口在根目录，Vue 项目构建产物在 dist/ 下
    path = appType === 'VUE' ? 'dist/index.html' : 'index.html'
  }
  window.open(`/preview/${userName}/${projectName}/${path}`, '_blank')
}

async function checkPreview() {
  const userName = props.userName || 'guest'
  const projectName = props.projectName || 'default'
  hasPreview.value = false
  previewPath.value = ''
  try {
    const res = await fetch(`/api/projects/${encodeURIComponent(userName)}/${encodeURIComponent(projectName)}/preview-path`)
    const data = await res.json()
    const payload = data?.data ?? data
    if (payload?.exists) {
      hasPreview.value = true
      previewPath.value = payload.path || ''
    }
  } catch {
    hasPreview.value = false
  }
}

function downloadProject() {
  const userName = props.userName || 'guest'
  const projectName = props.projectName || 'default'
  window.open(`/api/projects/${userName}/${projectName}/download`, '_blank')
}

watch(
  () => props.projectName,
  (newProjectName, oldProjectName) => {
    // 关闭旧项目的 SSE 连接和中止请求
    if (eventSource) eventSource.close()
    if (abortController) abortController.abort()
    if (active.value) emit('generation-ended')
    active.value = false

    // 保存旧项目的日志到旧项目的 localStorage key
    if (oldProjectName && logs.value.length > 0) {
      try {
        const oldLogsKey = `logs_${props.userName}_${oldProjectName}`
        localStorage.setItem(oldLogsKey, JSON.stringify(logs.value))
      } catch (e) {
        console.error('Failed to save old project logs:', e)
      }
    }

    // 检查新项目是否有历史记录
    if (hasProjectHistory()) {
      // 打开以前的项目 → 恢复之前的操作日志和构建状态
      const hasLogs = loadLogs()
      completed.value = loadBuildStatus()
      failed.value = !completed.value && !hasLogs ? false : failed.value
      progress.value = completed.value ? 100 : 0
      if (!hasLogs && !completed.value) {
        // 有构建记录但没有日志，清空日志区域
        logs.value = []
      }
    } else {
      // 打开新的项目 → 刷新控制台，不显示旧内容
      logs.value = []
      completed.value = false
      failed.value = false
      progress.value = 0
    }
  }
)

// Load build status on component mount
onMounted(() => {
  fetchQuota()
  checkPreview()
  if (hasProjectHistory()) {
    loadLogs()
    completed.value = loadBuildStatus()
    if (completed.value) {
      progress.value = 100
    }
  } else {
    logs.value = []
    completed.value = false
    failed.value = false
    progress.value = 0
  }
  // 检查刷新前是否有未结束的任务
  restoreStaleTask()
})

// 切换项目或账号时，重新探测预览产物是否存在（决定「预览」按钮置灰）
watch(
  () => [props.projectName, props.userName],
  () => checkPreview(),
)
</script>

<style scoped>
.task-console {
  border-top: 1px solid var(--border-color);
  background: var(--bg-secondary);
  display: flex;
  flex-direction: column;
  transition: height 0.25s ease;
}
.task-console.collapsed { height: auto; }

/* ===== Header ===== */
.console-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 5px 14px;
  height: 30px;
  flex-shrink: 0;
  background: var(--bg-tertiary);
  border-bottom: 1px solid var(--border-color);
}
.console-header-left {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
.console-title { color: var(--text-secondary); }

.task-badge {
  font-size: 10px;
  padding: 1px 7px;
  border-radius: 8px;
  font-weight: 600;
  text-transform: none;
  letter-spacing: 0;
}
.task-badge { background: rgba(99, 102, 241, 0.15); color: var(--accent-light); }
.task-badge.done { background: var(--success-bg); color: var(--success); }
.task-badge.failed { background: var(--danger-bg); color: var(--danger); }

.console-header-right {
  display: flex;
  align-items: center;
  gap: 6px;
}

.preview-btn {
  background: linear-gradient(135deg, #16a34a, #22c55e) !important;
  border: none !important;
  color: #fff !important;
  font-weight: 600;
  font-size: 11px !important;
  padding: 3px 10px !important;
  border-radius: 6px !important;
  transition: all 0.2s;
}
.preview-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(34, 197, 94, 0.3);
}
.preview-btn:disabled {
  background: var(--bg-tertiary) !important;
  color: var(--text-muted) !important;
  box-shadow: none !important;
  cursor: not-allowed;
  opacity: 0.5;
}

.download-btn {
  background: var(--bg-tertiary) !important;
  border: 1px solid var(--border-color) !important;
  color: var(--text-secondary) !important;
  font-size: 11px !important;
  padding: 3px 10px !important;
  border-radius: 6px !important;
  transition: all 0.2s;
}
.download-btn:hover {
  border-color: var(--accent) !important;
  color: var(--text-primary) !important;
}

/* ===== Body — Side-by-side ===== */
.console-body {
  display: flex;
  flex: 1;
  overflow: hidden;
  min-height: 0;
}

/* ===== Left: AI Command Bar ===== */
.input-panel {
  width: 42%;
  min-width: 280px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  border-right: 1px solid var(--border-color);
  flex-shrink: 0;
  background: var(--bg-sidebar);
}

.prompt-input {
  flex: 1;
}
.prompt-input :deep(.el-textarea__inner) {
  background: var(--bg-input);
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  color: var(--text-primary);
  resize: none;
  max-height: 90px;
  min-height: 60px;
  font-size: 13px;
  line-height: 1.5;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.prompt-input :deep(.el-textarea__inner):focus {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-glow);
}

/* ===== Toggle Buttons (DeepSeek-style) ===== */
.toggle-row {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.toggle-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 11px;
  border: 1px solid var(--border-color);
  border-radius: 16px;
  background: transparent;
  color: var(--text-muted);
  font-size: 11.5px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}
.toggle-btn:hover:not(:disabled) {
  border-color: var(--accent);
  color: var(--text-secondary);
}
.toggle-btn.active {
  border-color: var(--accent);
  background: rgba(99, 102, 241, 0.12);
  color: var(--accent-light);
  box-shadow: 0 0 12px var(--accent-glow);
}
.toggle-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}
.toggle-icon { font-size: 13px; }

/* ===== Quota Bar ===== */
.quota-bar {
  display: flex;
  gap: 16px;
  padding: 6px 12px;
  background: var(--bg-tertiary);
  border-radius: 8px;
  font-size: 11px;
}
.quota-row {
  display: flex;
  align-items: center;
  gap: 4px;
}
.quota-label {
  color: var(--text-muted);
}
.quota-nums {
  color: var(--text-secondary);
  font-variant-numeric: tabular-nums;
}
.quota-nums strong {
  color: var(--text-primary);
  font-size: 12px;
}
.quota-low strong {
  color: #f87171;
}

/* ===== Resume Banner（刷新后任务恢复提示） ===== */
.resume-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.4;
  border: 1px solid;
}
.resume-banner.running {
  background: rgba(99, 102, 241, 0.12);
  border-color: rgba(99, 102, 241, 0.4);
  color: #a5b4fc;
}
.resume-banner.interrupted {
  background: rgba(248, 113, 113, 0.12);
  border-color: rgba(248, 113, 113, 0.4);
  color: #fca5a5;
}
.resume-banner .resume-text {
  flex: 1;
}
.resume-banner .resume-actions {
  flex-shrink: 0;
}

/* ===== Send Button ===== */
.send-btn {
  width: 100% !important;
  height: 36px !important;
  font-weight: 600 !important;
  font-size: 13px !important;
  border-radius: var(--radius) !important;
  background: linear-gradient(135deg, #6366f1, #4f46e5) !important;
  border: none !important;
  box-shadow: 0 2px 12px rgba(99, 102, 241, 0.25) !important;
  transition: all 0.2s ease !important;
}
.send-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 20px rgba(99, 102, 241, 0.4) !important;
}
.send-btn:disabled {
  opacity: 0.4 !important;
}

/* ===== Right: Terminal-style Logs ===== */
.log-panel {
  flex: 1;
  overflow-y: auto;
  padding: 10px 14px;
  font-family: var(--font-mono);
  font-size: 11.5px;
  line-height: 1.75;
  color: var(--text-secondary);
  min-width: 0;
  background: linear-gradient(to bottom, var(--bg-primary), var(--bg-root));
}

.log-placeholder {
  color: var(--text-muted);
  text-align: center;
  padding-top: 24px;
  font-family: var(--font-sans);
  font-size: 12.5px;
  opacity: 0.5;
}

.log-line {
  display: flex;
  gap: 10px;
  padding: 1px 0;
  transition: background 0.15s;
}
.log-line:hover { background: var(--bg-hover); }

.log-line.log-error { color: #f87171; }
.log-line.log-done { color: #4ade80; font-weight: 500; }
.log-line.streaming .log-msg { color: var(--accent-light); }

.log-time {
  color: var(--text-muted);
  flex-shrink: 0;
  width: 68px;
  font-size: 10.5px;
  opacity: 0.6;
}
.log-msg { word-break: break-all; }

.spinner-ring {
  display: inline-block;
  width: 12px;
  height: 12px;
  border: 2px solid rgba(255,255,255,0.08);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
  vertical-align: middle;
  margin-right: 6px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ===== Responsive ===== */
@media (max-width: 768px) {
  .input-panel { min-width: 220px; width: 100%; border-right: none; }
  .log-panel { display: none; }
}
</style>
