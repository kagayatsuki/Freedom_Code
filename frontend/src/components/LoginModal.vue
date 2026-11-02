<template>
  <div class="modal-backdrop" @click.self="$emit('close')">
    <div class="modal-card">
      <button class="modal-close" @click="$emit('close')">&times;</button>

      <div class="modal-tabs">
        <button
          :class="{ active: mode === 'login' }"
          @click="mode = 'login'"
        >登录</button>
        <button
          :class="{ active: mode === 'register' }"
          @click="mode = 'register'"
        >注册</button>
      </div>

      <form class="modal-form" @submit.prevent="handleSubmit">
        <div class="form-group">
          <label>用户名</label>
          <input
            v-model="form.username"
            type="text"
            placeholder="请输入用户名"
            required
          />
        </div>
        <div class="form-group">
          <label>密码</label>
          <input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            required
          />
        </div>
        <div v-if="mode === 'register'" class="form-group">
          <label>邮箱</label>
          <input
            v-model="form.email"
            type="email"
            placeholder="请输入邮箱"
            required
          />
        </div>
        <p v-if="errorMsg" class="error-msg">{{ errorMsg }}</p>
        <button type="submit" class="btn-submit" :disabled="loading">
          {{ loading ? '请稍候...' : (mode === 'login' ? '登录' : '注册') }}
        </button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, inject } from 'vue'
import { request, ApiError } from '../utils/api.js'

const emit = defineEmits(['close', 'loginSuccess'])
const showToast = inject('showToast')

const mode = ref('login')
const loading = ref(false)
const errorMsg = ref('')
const form = reactive({ username: '', password: '', email: '' })

async function handleSubmit() {
  errorMsg.value = ''
  loading.value = true
  try {
    const endpoint = mode.value === 'login' ? '/api/auth/login' : '/api/auth/register'
    const body =
      mode.value === 'login'
        ? { username: form.username, password: form.password }
        : { username: form.username, password: form.password, email: form.email }

    const result = await request(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    if (mode.value === 'register') {
      showToast('注册成功')
      mode.value = 'login'
      errorMsg.value = ''
      form.username = ''
      form.password = ''
      form.email = ''
      return
    }
    const payload = result.data
    showToast('登录成功')
    emit('loginSuccess', {
      username: form.username,
      status: payload.status || 1,
      email: payload.email || '',
      image: payload.image || ''
    }, payload.token)
  } catch (e) {
    errorMsg.value = e instanceof ApiError ? e.message : '网络错误，请稍后再试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
}

.modal-card {
  position: relative;
  width: 400px;
  max-width: 90vw;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 14px;
  padding: 32px;
  box-shadow: var(--shadow);
}

.modal-close {
  position: absolute;
  top: 12px;
  right: 16px;
  background: none;
  border: none;
  color: var(--text-muted);
  font-size: 22px;
  cursor: pointer;
}
.modal-close:hover { color: var(--text-primary); }

.modal-tabs {
  display: flex;
  gap: 0;
  margin-bottom: 24px;
  border-bottom: 1px solid var(--border-color);
}
.modal-tabs button {
  flex: 1;
  padding: 10px;
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-size: 15px;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: all 0.2s;
}
.modal-tabs button.active {
  color: var(--accent);
  border-bottom-color: var(--accent);
}

.modal-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-group label {
  display: block;
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.form-group input {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-input);
  color: var(--text-primary);
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
}
.form-group input:focus {
  border-color: var(--accent);
}

.error-msg {
  color: var(--danger);
  font-size: 13px;
}

.btn-submit {
  padding: 11px;
  border: none;
  border-radius: 8px;
  background: var(--accent);
  color: #fff;
  font-size: 15px;
  cursor: pointer;
  transition: background 0.2s;
}
.btn-submit:hover { background: var(--accent-hover); }
.btn-submit:disabled { opacity: 0.6; cursor: not-allowed; }
</style>
