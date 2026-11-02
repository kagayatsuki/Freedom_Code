<template>
  <div class="workbench" :class="{ 'is-resizing': isResizing }">
    <aside class="file-explorer" :style="{ width: leftPanelWidth + 'px' }">
      <div class="panel-header">
        <el-icon :size="16"><FolderOpened /></el-icon>
        <span>文件浏览器</span>
      </div>
      <!-- 项目选择器 -->
      <div class="project-selector">
        <div class="project-selector-row">
          <el-select
            v-model="selectedProject"
            placeholder="选择项目"
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
          <el-button
            size="small"
            :icon="Plus"
            class="btn-add-project"
            @click="showAddDialog = true"
          />
        </div>
      </div>
      <div class="panel-body">
        <el-tree
            :data="fileTree"
            :props="treeProps"
            node-key="id"
            :default-expanded-keys="expandedKeys"
            highlight-current
            :expand-on-click-node="false"
            @node-click="handleNodeClick"
            @node-expand="onNodeExpand"
            @node-collapse="onNodeCollapse"
        >
          <template #default="{ node, data }">
            <span class="tree-node">
              <template v-if="data.folder">
                <span class="folder-arrow">{{ node.expanded ? '▾' : '▸' }}</span>
                <span
                  class="node-icon-svg"
                  v-html="node.expanded ? iconSvgs.folderOpen : iconSvgs.folderClosed"
                ></span>
              </template>
              <template v-else>
                <span
                  class="node-icon-svg file-svg"
                  v-html="getFileSvg(data.label)"
                ></span>
              </template>
              <span class="node-label">{{ data.label }}</span>
            </span>
          </template>
        </el-tree>
      </div>
    </aside>

    <div class="resize-handle" @mousedown="startResize"></div>

    <section class="code-viewer">
      <div class="editor-pane">
        <div v-if="!selectedFile" class="empty-state">
          <div class="empty-icon">
            <el-icon :size="48"><Document /></el-icon>
          </div>
          <h2>AI 任务工作台</h2>
          <p>请在左侧文件浏览器中选择文件进行预览</p>
          <p class="sub-text">支持语法高亮与实时预览，下方控制台可创建生成任务</p>
        </div>

        <div v-else class="editor-wrapper">
          <div class="panel-header">
            <span class="file-icon-header" v-html="getFileSvg(selectedFile.path)"></span>
            <span class="file-path">{{ selectedFile.path }}</span>
            <span class="file-lang-tag">{{ selectedFile.language }}</span>
            <div class="editor-actions">
              <template v-if="!isEditing">
                <el-button size="small" :icon="Edit" @click="startEdit">编辑</el-button>
              </template>
              <template v-else>
                <el-button size="small" @click="cancelEdit">取消</el-button>
                <el-button size="small" type="primary" :loading="saving" @click="handleSave">保存</el-button>
              </template>
            </div>
          </div>

          <div class="editor-body" v-loading="loading" element-loading-text="正在加载文件内容...">
            <div ref="editorContainer" class="monaco-container"></div>
          </div>
        </div>
      </div>

      <TaskConsole
        :project-name="selectedProject || 'default'"
        :has-project="!!selectedProject"
        :user-name="userStore.user?.username || 'guest'"
        @task-update="onTaskUpdate"
        @generation-started="onGenerationStarted"
        @generation-ended="onGenerationEnded"
      />
    </section>

    <!-- 新建项目弹窗 -->
    <el-dialog
      v-model="showAddDialog"
      title="新建项目"
      width="400px"
      :close-on-click-modal="false"
    >
      <el-input
        v-model="newProjectName"
        placeholder="请输入项目名称"
        maxlength="50"
        @keyup.enter="handleAddProject"
      />
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" :loading="addingProject" @click="handleAddProject">
          确认
        </el-button>
      </template>
    </el-dialog>

    <!-- 保存确认弹窗 -->
    <el-dialog
      v-model="showSaveDialog"
      title="确认保存"
      width="400px"
      :close-on-click-modal="false"
    >
      <p>确定要将修改保存到 <strong>{{ selectedFile?.path }}</strong> 吗？</p>
      <template #footer>
        <el-button @click="showSaveDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doSave">
          确认保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, inject, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { Folder, FolderOpened, Document, Plus, Edit } from '@element-plus/icons-vue'
import axios from 'axios'
import { useUserStore } from '../stores/user.js'
import TaskConsole from './TaskConsole.vue'

// ==================== Monaco Workers ====================
import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker'
import jsonWorker from 'monaco-editor/esm/vs/language/json/json.worker?worker'
import cssWorker from 'monaco-editor/esm/vs/language/css/css.worker?worker'
import htmlWorker from 'monaco-editor/esm/vs/language/html/html.worker?worker'
import tsWorker from 'monaco-editor/esm/vs/language/typescript/ts.worker?worker'

self.MonacoEnvironment = {
  getWorker(_, label) {
    if (label === 'json') return new jsonWorker()
    if (label === 'css' || label === 'scss' || label === 'less') return new cssWorker()
    if (label === 'html' || label === 'handlebars' || label === 'razor') return new htmlWorker()
    if (label === 'typescript' || label === 'javascript') return new tsWorker()
    return new editorWorker()
  },
}

// ==================== Mock 数据 ====================
const mockFileTree = [
  {
    id: '1', label: 'src', isFolder: true, path: 'src',
    children: [
      {
        id: '1-1', label: 'components', isFolder: true, path: 'src/components',
        children: [
          { id: '1-1-1', label: 'App.vue', isFolder: false, path: 'src/components/App.vue' },
          { id: '1-1-2', label: 'HelloWorld.vue', isFolder: false, path: 'src/components/HelloWorld.vue' },
        ],
      },
      { id: '1-2', label: 'main.ts', isFolder: false, path: 'src/main.ts' },
    ],
  },
  { id: '2', label: 'package.json', isFolder: false, path: 'package.json' },
]

const mockFileContents = {
  'src/main.ts': `import { createApp } from 'vue'\nimport App from './App.vue'\nconst app = createApp(App)\napp.mount('#app')\n`,
  'src/components/App.vue': `<template>\n  <div class="app-root">\n    <h1>Hello AI Coder</h1>\n  </div>\n</template>\n`,
  'package.json': `{\n  "name": "ai-code-platform",\n  "version": "1.0.0"\n}\n`
}

const userStore = useUserStore()
const showToast = inject('showToast')

function getStorageKey() {
  return `lastProject_${userStore.user?.username || 'guest'}`
}

function getExpandedKey() {
  const proj = selectedProject.value || localStorage.getItem(getStorageKey()) || 'default'
  return `expandedKeys_${userStore.user?.username || 'guest'}_${proj}`
}

function loadExpandedKeys() {
  try {
    return JSON.parse(localStorage.getItem(getExpandedKey())) || []
  } catch {
    return []
  }
}

function saveExpandedKeys() {
  localStorage.setItem(getExpandedKey(), JSON.stringify(expandedKeys.value))
}

function onNodeExpand(data) {
  if (!expandedKeys.value.includes(data.id)) {
    expandedKeys.value.push(data.id)
  }
  saveExpandedKeys()
}

function onNodeCollapse(data) {
  expandedKeys.value = expandedKeys.value.filter(id => id !== data.id)
  saveExpandedKeys()
}

const treeProps = { children: 'children', label: 'label' }

const extToLanguage = {
  '.vue': 'html', '.ts': 'typescript', '.js': 'javascript', '.json': 'json',
  '.css': 'css', '.html': 'html', '.md': 'markdown',
}

function getLanguageFromPath(path) {
  const dot = path.lastIndexOf('.')
  if (dot === -1) return 'plaintext'
  return extToLanguage[path.slice(dot).toLowerCase()] || 'plaintext'
}

// ==================== SVG 文件类型图标系统 ====================
const iconSvgs = {
  folderClosed: `<svg viewBox="0 0 24 24" fill="none" stroke="#60a5fa" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>`,
  folderOpen:   `<svg viewBox="0 0 24 24" fill="none" stroke="#93c5fd" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M6 14l1.5-2.9A2 2 0 0 1 9.2 10H20a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2v2"/></svg>`,
}

const fileSvgMap = {
  '.html': `<svg viewBox="0 0 24 24" fill="none"><path d="M4 3h16l-2 18L12 22 6 21 4 3z" fill="#E34F26"/><path d="M12 5v15.5l5-1.2L18.5 5H12z" fill="#F16529"/><path d="M7.5 8.5h9M7.5 11.5h9M7.5 14.5h6" stroke="#fff" stroke-width="0.6" opacity="0.5"/></svg>`,
  '.css':  `<svg viewBox="0 0 24 24" fill="none"><path d="M4 3h16l-2 18L12 22 6 21 4 3z" fill="#1572B6"/><path d="M12 5v15.5l5-1.2L18.5 5H12z" fill="#33A9DC"/><path d="M7.5 8.5h9M7.5 11.5h9M7.5 14.5h6" stroke="#fff" stroke-width="0.6" opacity="0.5"/></svg>`,
  '.scss': `<svg viewBox="0 0 24 24" fill="none"><path d="M4 3h16l-2 18L12 22 6 21 4 3z" fill="#CD6799"/><path d="M12 5v15.5l5-1.2L18.5 5H12z" fill="#E495B6"/></svg>`,
  '.less': `<svg viewBox="0 0 24 24" fill="none"><path d="M4 3h16l-2 18L12 22 6 21 4 3z" fill="#1D365D"/><path d="M12 5v15.5l5-1.2L18.5 5H12z" fill="#2A4A8C"/></svg>`,

  '.js':   `<svg viewBox="0 0 24 24" fill="none"><rect x="2" y="2" width="20" height="20" rx="2" fill="#F7DF1E"/><text x="6" y="17" font-family="sans-serif" font-size="9" font-weight="900" fill="#000">JS</text></svg>`,
  '.mjs':  `<svg viewBox="0 0 24 24" fill="none"><rect x="2" y="2" width="20" height="20" rx="2" fill="#F7DF1E"/><text x="4" y="17" font-family="sans-serif" font-size="7.5" font-weight="900" fill="#000">MJS</text></svg>`,
  '.cjs':  `<svg viewBox="0 0 24 24" fill="none"><rect x="2" y="2" width="20" height="20" rx="2" fill="#F7DF1E"/><text x="4" y="17" font-family="sans-serif" font-size="7.5" font-weight="900" fill="#000">CJS</text></svg>`,
  '.ts':   `<svg viewBox="0 0 24 24" fill="none"><rect x="2" y="2" width="20" height="20" rx="2" fill="#3178C6"/><text x="5" y="17" font-family="sans-serif" font-size="9" font-weight="900" fill="#fff">TS</text></svg>`,
  '.tsx':  `<svg viewBox="0 0 24 24" fill="none"><rect x="2" y="2" width="20" height="20" rx="2" fill="#3178C6"/><text x="2" y="17" font-family="sans-serif" font-size="8" font-weight="900" fill="#61DAFB">TSX</text></svg>`,
  '.jsx':  `<svg viewBox="0 0 24 24" fill="none"><rect x="2" y="2" width="20" height="20" rx="2" fill="#F7DF1E"/><text x="2" y="17" font-family="sans-serif" font-size="8" font-weight="900" fill="#61DAFB">JSX</text></svg>`,

  '.vue':  `<svg viewBox="0 0 24 24" fill="none"><rect x="2" y="2" width="20" height="20" rx="2" fill="#213547"/><path d="M18 4L12 14 6 4H2l10 17L22 4h-4z" fill="#42B883"/></svg>`,

  '.json': `<svg viewBox="0 0 24 24" fill="none"><rect x="2" y="2" width="20" height="20" rx="2" fill="#505050"/><text x="4" y="10" font-family="sans-serif" font-size="6" font-weight="700" fill="#F0C040">{</text><text x="4" y="16" font-family="sans-serif" font-size="6" font-weight="700" fill="#F0C040">}</text></svg>`,
  '.md':   `<svg viewBox="0 0 24 24" fill="none"><rect x="2" y="2" width="20" height="20" rx="2" fill="#505050"/><text x="7" y="17" font-family="sans-serif" font-size="9" font-weight="700" fill="#E0E0E0">MD</text></svg>`,

  '.svg':  `<svg viewBox="0 0 24 24" fill="none"><rect x="2" y="2" width="20" height="20" rx="2" fill="#505050"/><g transform="translate(4,4) scale(0.55)"><rect width="24" height="24" rx="3" fill="#FFB13B"/><circle cx="8" cy="8" r="3" fill="none" stroke="#fff" stroke-width="1.5"/><path d="M20 12l-5 6-3-3-4 4" stroke="#fff" stroke-width="1.5" fill="none"/></g></svg>`,
  '.png':  `<svg viewBox="0 0 24 24" fill="none"><rect x="2" y="2" width="20" height="20" rx="2" fill="#505050"/><g transform="translate(4,4) scale(0.55)"><rect width="24" height="24" rx="3" fill="#4CAF50"/><circle cx="8" cy="8" r="3" fill="none" stroke="#fff" stroke-width="1.5"/><path d="M20 12l-5 6-3-3-4 4" stroke="#fff" stroke-width="1.5" fill="none"/></g></svg>`,
  '.jpg':  `<svg viewBox="0 0 24 24" fill="none"><rect x="2" y="2" width="20" height="20" rx="2" fill="#505050"/><g transform="translate(4,4) scale(0.55)"><rect width="24" height="24" rx="3" fill="#4CAF50"/><circle cx="8" cy="8" r="3" fill="none" stroke="#fff" stroke-width="1.5"/><path d="M20 12l-5 6-3-3-4 4" stroke="#fff" stroke-width="1.5" fill="none"/></g></svg>`,

  'other': `<svg viewBox="0 0 24 24" fill="none" stroke="#8888aa" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"/><polyline points="13 2 13 9 20 9"/></svg>`,
}

function getFileSvg(filename) {
  const dot = filename.lastIndexOf('.')
  if (dot === -1) return fileSvgMap['other']
  const ext = filename.slice(dot).toLowerCase()
  return fileSvgMap[ext] || fileSvgMap['other']
}

// ==================== 状态声明 ====================
const projects = ref([])
const selectedProject = ref(localStorage.getItem(getStorageKey()) || '')
const projectsLoading = ref(false)
const showAddDialog = ref(false)
const newProjectName = ref('')
const addingProject = ref(false)
const fileTree = ref([])
const expandedKeys = ref(loadExpandedKeys())
const selectedFile = ref(null)
const loading = ref(false)
const isEditing = ref(false)
const saving = ref(false)
const showSaveDialog = ref(false)
const editorContainer = ref(null)
let monacoEditor = null
let monacoInstance = null
let monacoConfigured = false

function configureTsJsIntelliSense(monaco) {
  if (monacoConfigured) return
  monacoConfigured = true

  const compilerOptions = {
    target: monaco.languages.typescript.ScriptTarget.ESNext,
    module: monaco.languages.typescript.ModuleKind.ESNext,
    allowJs: true,
    checkJs: false,
    jsx: monaco.languages.typescript.JsxEmit.React,
    allowNonTsExtensions: true,
    lib: ['esnext', 'dom', 'dom.iterable'],
  }

  const sharedOptions = {
    noSemanticValidation: false,
    noSyntaxValidation: false,
    noSuggestionDiagnostics: true,
  }

  monaco.languages.typescript.javascriptDefaults.setCompilerOptions(compilerOptions)
  monaco.languages.typescript.javascriptDefaults.setDiagnosticsOptions(sharedOptions)
  monaco.languages.typescript.typescriptDefaults.setCompilerOptions(compilerOptions)
  monaco.languages.typescript.typescriptDefaults.setDiagnosticsOptions(sharedOptions)

  // 启用自动补全：输入时自动弹出建议
  monaco.languages.typescript.javascriptDefaults.setEagerModelSync(true)
  monaco.languages.typescript.typescriptDefaults.setEagerModelSync(true)
}

// ==================== 🛠️ 核心修复：拖拽大小逻辑 ====================
const leftPanelWidth = ref(260) // 初始宽度
const isResizing = ref(false)   // 是否正在拖拽

function startResize(e) {
  e.preventDefault()
  isResizing.value = true // 开启拖拽状态，激活外层蒙版样式

  const startX = e.clientX
  const startWidth = leftPanelWidth.value

  const onMove = (ev) => {
    ev.preventDefault()
    const delta = ev.clientX - startX
    // 限制左侧菜单的最小宽度为 180px，最大为 600px
    leftPanelWidth.value = Math.max(180, Math.min(600, startWidth + delta))

    // 🛠️ 关键：在拖拽过程中，如果 Monaco 已经初始化了，强制让它重新计算布局
    if (monacoEditor) {
      monacoEditor.layout()
    }
  }

  const onUp = () => {
    isResizing.value = false
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
  }

  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

// ==================== 点击树节点 ====================
async function handleNodeClick(data) {
  if (data.isFolder) return
  if (selectedFile.value && selectedFile.value.path === data.path) return

  const lang = getLanguageFromPath(data.path)
  selectedFile.value = { ...data, language: lang }

  loading.value = true
  try {
    let content = mockFileContents[data.path]
    // 优先从后端获取文件内容，失败时降级到 Mock
    if (!content) {
      try {
        const userName = userStore.user?.username || 'guest'
        const res = await axios.get(`/api/projects/${userName}/${selectedProject.value}/file/content`, {
          params: { path: data.path },
        })
        // 后端返回纯文本字符串，axios 将其放在 data 字段中
        if (typeof res.data === 'string') {
          content = res.data
        } else if (res.data?.code === 200 && res.data?.data) {
          content = res.data.data
        }
      } catch {
        content = `// 文件: ${data.path}\n// 后端暂未返回内容，请检查接口对接。\n`
      }
    }
    await nextTick()
    initOrUpdateEditor(content, lang)
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

// ==================== 初始化与更新 Monaco ====================
async function initOrUpdateEditor(content, language, readOnly = true) {
  if (!monacoInstance) { monacoInstance = await import('monaco-editor'); configureTsJsIntelliSense(monacoInstance) }

  if (!monacoEditor) {
    if (!editorContainer.value) return
    monacoEditor = monacoInstance.editor.create(editorContainer.value, {
      value: content,
      language: language,
      theme: 'vs-dark',
      readOnly,
      minimap: { enabled: false },
      automaticLayout: true,
      fontSize: 14,
      fontFamily: "'Fira Code', 'Consolas', monospace",
      // IntelliSense 增强配置
      suggestOnTriggerCharacters: true,
      quickSuggestions: { other: true, comments: false, strings: false },
      wordBasedSuggestions: 'off',
      suggest: {
        showKeywords: true,
        showSnippets: true,
        showClasses: true,
        showFunctions: true,
        showVariables: true,
        showMethods: true,
        showProperties: true,
      },
      tabCompletion: 'on',
    })
  } else {
    const model = monacoEditor.getModel()
    if (model) {
      model.setValue(content)
      monacoInstance.editor.setModelLanguage(model, language)
    }
    monacoEditor.updateOptions({ readOnly })
  }
}

function startEdit() {
  isEditing.value = true
  monacoEditor?.updateOptions({ readOnly: false })
}

function cancelEdit() {
  isEditing.value = false
  monacoEditor?.updateOptions({ readOnly: true })
}

function handleSave() {
  showSaveDialog.value = true
}

async function doSave() {
  if (!monacoEditor) return
  saving.value = true
  try {
    const content = monacoEditor.getValue()
    const userName = userStore.user?.username || 'guest'
    await axios.post(
      `/api/projects/${userName}/${selectedProject.value}/edit`,
      content,
      {
        params: { path: selectedFile.value.path },
        headers: { 'Content-Type': 'text/plain' },
      },
    )
    showToast('保存成功')
    showSaveDialog.value = false
    isEditing.value = false
    monacoEditor?.updateOptions({ readOnly: true })
  } catch (e) {
    const msg = e.response?.data?.msg || '保存失败'
    showToast(msg, 'error')
  } finally {
    saving.value = false
  }
}

onBeforeUnmount(() => {
  if (fileTreePollTimer) {
    clearInterval(fileTreePollTimer)
    fileTreePollTimer = null
  }
  monacoEditor?.dispose()
  monacoEditor = null
})

// ==================== 获取项目列表 ====================
async function fetchProjects() {
  const userName = userStore.user?.username || 'guest'
  projectsLoading.value = true
  try {
    const res = await axios.get(`/api/projects/${userName}`)
    // 后端返回 R<List<String>> 结构
    if (res.data?.code === 200 && Array.isArray(res.data.data)) {
      projects.value = res.data.data
    } else if (Array.isArray(res.data)) {
      projects.value = res.data
    }
  } catch {
    projects.value = ['demo-project']
  } finally {
    projectsLoading.value = false
  }
}

async function handleAddProject() {
  const name = newProjectName.value.trim()
  if (!name) {
    showToast('请输入项目名称', 'error')
    return
  }
  addingProject.value = true
  const userName = userStore.user?.username || 'guest'
  try {
    await axios.post('/api/projects/add', {
      projectName: name,
      userName,
    })
    showToast('项目创建成功')
    showAddDialog.value = false
    newProjectName.value = ''
    // 清除新项目的旧 localStorage 数据（避免同名项目残留）
    localStorage.removeItem(`buildStatus_${userName}_${name}`)
    localStorage.removeItem(`logs_${userName}_${name}`)
    localStorage.removeItem(`appType_${userName}_${name}`)
    await fetchProjects()
    selectedProject.value = name
    await fetchFileTree(name)
  } catch (e) {
    const msg = e.response?.data?.msg || '创建失败'
    showToast(msg, 'error')
  } finally {
    addingProject.value = false
  }
}

// ==================== 项目切换 → 加载文件树 ====================
async function onProjectChange(projectName) {
  if (!projectName) {
    fileTree.value = []
    expandedKeys.value = []
    localStorage.removeItem(getStorageKey())
    return
  }
  localStorage.setItem(getStorageKey(), projectName)
  await fetchFileTree(projectName)
  expandedKeys.value = loadExpandedKeys()
}

async function fetchFileTree(projectName) {
  const userName = userStore.user?.username || 'guest'
  try {
    const res = await axios.get(`/api/projects/${userName}/${projectName}/tree`)
    if (res.data?.code === 200 && res.data?.data) {
      fileTree.value = res.data.data
    } else {
      fileTree.value = mockFileTree
    }
  } catch {
    fileTree.value = mockFileTree
  }
}

function onTaskUpdate({ taskId, state }) {
  if (state === 'COMPLETED') {
    fetchFileTree(selectedProject.value)
    showToast(`任务 ${taskId} 执行成功`)
  } else if (state === 'FAILED') {
    showToast(`任务 ${taskId} 执行失败`, 'error')
  }
}

// 文件树轮询（代码生成期间每 1 秒刷新）
let fileTreePollTimer = null

function onGenerationStarted() {
  // 清除已有定时器，避免重复
  if (fileTreePollTimer) clearInterval(fileTreePollTimer)
  // 立即拉取一次，然后每 1 秒轮询
  fetchFileTree(selectedProject.value)
  fileTreePollTimer = setInterval(() => {
    fetchFileTree(selectedProject.value)
  }, 1000)
}

function onGenerationEnded() {
  if (fileTreePollTimer) {
    clearInterval(fileTreePollTimer)
    fileTreePollTimer = null
  }
  // 最后拉取一次，获取完整文件树
  fetchFileTree(selectedProject.value)
}

onMounted(async () => {
  await fetchProjects()
  if (selectedProject.value && projects.value.includes(selectedProject.value)) {
    await fetchFileTree(selectedProject.value)
  }
})
</script>

<style scoped>
.workbench {
  display: flex;
  height: 100%;
  width: 100%;
  overflow: hidden;
  position: relative;
  background: var(--bg-root);
}
.workbench.is-resizing .code-viewer {
  pointer-events: none !important;
}

/* ===== 左侧文件浏览器 ===== */
.file-explorer {
  display: flex;
  flex-direction: column;
  background: var(--bg-sidebar);
  flex-shrink: 0;
  overflow: hidden;
  border-right: 1px solid var(--border-color);
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  font-size: 11.5px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--text-muted);
  border-bottom: 1px solid var(--border-color);
  height: 38px;
  flex-shrink: 0;
}

.project-selector {
  padding: 10px 12px;
  border-bottom: 1px solid var(--border-color);
}
.project-selector-row {
  display: flex;
  gap: 6px;
  align-items: center;
}
.project-select { flex: 1; }
.project-selector :deep(.el-select) { width: 100%; }
.btn-add-project {
  flex-shrink: 0;
  --el-button-bg-color: var(--bg-tertiary);
  --el-button-border-color: var(--border-color);
}

.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0;
}

.file-explorer :deep(.el-tree) {
  background: transparent;
  color: var(--text-secondary);
  font-size: 12.5px;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  padding: 1px 0;
  transition: color 0.15s ease;
}
.tree-node:hover { color: var(--text-primary); }

.folder-arrow {
  font-size: 10px;
  color: var(--text-muted);
  flex-shrink: 0;
  width: 10px;
  text-align: center;
  transition: transform 0.15s ease;
}

.node-icon-svg {
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.node-icon-svg :deep(svg) {
  width: 16px;
  height: 16px;
  display: block;
}
.file-svg :deep(svg) {
  width: 18px;
  height: 18px;
}

.node-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ===== 拖拽手柄 ===== */
.resize-handle {
  width: 3px;
  cursor: col-resize;
  flex-shrink: 0;
  background: var(--border-color);
  position: relative;
  z-index: 999;
  transition: background 0.15s;
}
.resize-handle::before {
  content: '';
  position: absolute;
  top: 0; bottom: 0; left: -8px; right: -8px;
  background: transparent;
}
.resize-handle:hover,
.workbench.is-resizing .resize-handle {
  background: var(--accent) !important;
}

/* ===== 右侧代码区 ===== */
.code-viewer {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--bg-primary);
  width: 0;
  min-width: 200px;
}

.editor-pane {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}
.editor-wrapper {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
}
.editor-body {
  flex: 1;
  width: 100%;
  height: 100%;
  position: relative;
  overflow: hidden;
}
.monaco-container {
  width: 100%;
  height: 100%;
}

.file-icon-header {
  flex-shrink: 0;
  width: 18px;
  height: 18px;
}
.file-icon-header :deep(svg) {
  width: 18px;
  height: 18px;
  display: block;
}
.file-path {
  color: var(--text-secondary);
  font-size: 11.5px;
  font-family: var(--font-mono);
}
.file-lang-tag {
  padding: 1px 7px;
  border-radius: 4px;
  background: rgba(99,102,241,0.15);
  color: var(--accent-light);
  font-size: 10px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}
.editor-actions {
  margin-left: auto;
  display: flex;
  gap: 6px;
}

/* ===== 空状态 ===== */
.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--text-muted);
}
.empty-state h2 {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-secondary);
}
.empty-state p {
  font-size: 13px;
  color: var(--text-muted);
}
.empty-state .sub-text {
  font-size: 11.5px;
  color: rgba(255,255,255,0.15);
}

/* ===== TaskConsole height ===== */
.code-viewer :deep(.task-console) {
  flex-shrink: 0;
  max-height: 28%;
}
.code-viewer :deep(.task-console:not(.collapsed)) {
  height: 28%;
}
</style>