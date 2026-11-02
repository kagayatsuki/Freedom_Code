<template>
  <div class="member-popup-backdrop" @click.self="close">
    <div class="member-popup-card">
      <button
        class="close-btn"
        :class="{ disabled: !canClose }"
        :disabled="!canClose"
        @click="close"
      >
        {{ canClose ? '×' : countdown }}
      </button>

      <!-- 服务条款页面 -->
      <TermsOfService
        v-if="showTerms"
        @close="showTerms = false"
      />

      <!-- 隐私政策页面 -->
      <PrivacyPolicy
        v-if="showPrivacy"
        @close="showPrivacy = false"
      />

      <div class="popup-header">
        <div class="crown-icon">👑</div>
        <h2>开通会员，解锁更多权益</h2>
        <p class="subtitle">成为会员，享受专属特权</p>
      </div>

      <div class="benefits-section">
        <h3>会员权益</h3>
        <div class="benefits-grid">
          <div class="benefit-item" v-for="benefit in benefits" :key="benefit.title">
            <div class="benefit-icon">{{ benefit.icon }}</div>
            <div class="benefit-content">
              <div class="benefit-title">{{ benefit.title }}</div>
              <div class="benefit-desc">{{ benefit.desc }}</div>
            </div>
          </div>
        </div>
      </div>

      <div class="pricing-section">
        <h3>选择会员套餐</h3>
        <div class="pricing-grid">
          <div
            v-for="plan in plans"
            :key="plan.id"
            class="pricing-card"
            :class="{ popular: plan.popular, selected: selectedPlan === plan.id }"
            @click="selectedPlan = plan.id"
          >
            <div v-if="plan.popular" class="popular-badge">最受欢迎</div>
            <div class="plan-name">{{ plan.name }}</div>
            <div class="plan-price">
              <span class="price">¥{{ plan.price }}</span>
              <span class="period">/{{ plan.period }}</span>
            </div>
            <div class="plan-desc">{{ plan.desc }}</div>
            <ul class="plan-features">
              <li v-for="feature in plan.features" :key="feature">{{ feature }}</li>
            </ul>
          </div>
        </div>
      </div>

      <div class="action-section">
        <button class="btn-primary" @click="handleSubscribe" :disabled="!selectedPlan">
          立即开通
        </button>
        <button class="btn-secondary" :disabled="!canClose" @click="close">
          {{ canClose ? '暂不开通' : `请浏览权益 (${countdown}s)` }}
        </button>
      </div>

      <div class="footer-note">
        <p>开通即表示同意 <a href="#" @click.prevent="showTerms = true">服务条款</a> 和 <a href="#" @click.prevent="showPrivacy = true">隐私政策</a></p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import TermsOfService from './TermsOfService.vue'
import PrivacyPolicy from './PrivacyPolicy.vue'

const emit = defineEmits(['close', 'subscribe'])

const selectedPlan = ref('monthly')
const showTerms = ref(false)
const showPrivacy = ref(false)
const canClose = ref(false)
const countdown = ref(5)

onMounted(() => {
  const timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      canClose.value = true
      clearInterval(timer)
    }
  }, 1000)
})

const benefits = [
  { icon: '⚡', title: '无限对话', desc: '无限制的对话次数，随时随地使用' },
  { icon: '🎨', title: '高级模型', desc: '使用更强大的AI模型，质量更高' },
  { icon: '📁', title: '文件上传', desc: '支持上传文档、图片等文件进行分析' },
  { icon: '🔧', title: '优先支持', desc: '享受优先客服支持和技术服务' },
  { icon: '📊', title: '使用统计', desc: '查看详细的使用数据和分析报告' },
  { icon: '🎁', title: '专属福利', desc: '会员专属活动和优惠福利' }
]

const plans = [
  {
    id: 'monthly',
    name: '月度会员',
    price: 29,
    period: '月',
    desc: '适合短期使用',
    features: ['无限对话', '高级模型', '文件上传', '优先支持'],
    popular: false
  },
  {
    id: 'quarterly',
    name: '季度会员',
    price: 79,
    period: '季',
    desc: '性价比更高',
    features: ['无限对话', '高级模型', '文件上传', '优先支持', '使用统计'],
    popular: true
  },
  {
    id: 'yearly',
    name: '年度会员',
    price: 299,
    period: '年',
    desc: '最划算的选择',
    features: ['无限对话', '高级模型', '文件上传', '优先支持', '使用统计', '专属福利'],
    popular: false
  }
]

function close() {
  if (!canClose.value) return
  emit('close')
}

function handleSubscribe() {
  if (selectedPlan.value) {
    emit('subscribe', selectedPlan.value)
  }
}
</script>

<style scoped>
.member-popup-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.7);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 20px;
}

.member-popup-card {
  position: relative;
  width: 700px;
  max-width: 95vw;
  max-height: 90vh;
  overflow-y: auto;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 20px;
  padding: 32px 40px;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
}

.close-btn {
  position: absolute;
  top: 16px;
  right: 20px;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  color: var(--text-muted);
  font-size: 24px;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
}

.close-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.2);
  color: var(--text-primary);
}

.close-btn.disabled,
.close-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  font-size: 14px;
  font-weight: 700;
  color: var(--accent);
}

.popup-header {
  text-align: center;
  margin-bottom: 32px;
}

.crown-icon {
  font-size: 48px;
  margin-bottom: 12px;
}

.popup-header h2 {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 8px;
  background: linear-gradient(135deg, var(--accent), #fbbf24);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.subtitle {
  color: var(--text-muted);
  font-size: 15px;
}

.benefits-section {
  margin-bottom: 32px;
}

.benefits-section h3,
.pricing-section h3 {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 16px;
  color: var(--text-secondary);
}

.benefits-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.benefit-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.03);
  border-radius: 12px;
  transition: all 0.2s;
}

.benefit-item:hover {
  background: rgba(255, 255, 255, 0.06);
  transform: translateY(-2px);
}

.benefit-icon {
  font-size: 24px;
  flex-shrink: 0;
}

.benefit-content {
  flex: 1;
}

.benefit-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 4px;
}

.benefit-desc {
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.4;
}

.pricing-section {
  margin-bottom: 32px;
}

.pricing-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.pricing-card {
  position: relative;
  padding: 24px 20px;
  background: rgba(255, 255, 255, 0.03);
  border: 2px solid transparent;
  border-radius: 16px;
  cursor: pointer;
  transition: all 0.2s;
}

.pricing-card:hover {
  background: rgba(255, 255, 255, 0.06);
}

.pricing-card.selected {
  border-color: var(--accent);
  background: rgba(79, 140, 255, 0.1);
}

.pricing-card.popular {
  border-color: #fbbf24;
  background: rgba(251, 191, 36, 0.08);
}

.popular-badge {
  position: absolute;
  top: -10px;
  left: 50%;
  transform: translateX(-50%);
  background: linear-gradient(135deg, #fbbf24, #f59e0b);
  color: #000;
  font-size: 11px;
  font-weight: 700;
  padding: 4px 12px;
  border-radius: 12px;
}

.plan-name {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 8px;
}

.plan-price {
  margin-bottom: 8px;
}

.price {
  font-size: 28px;
  font-weight: 700;
  color: var(--accent);
}

.period {
  font-size: 14px;
  color: var(--text-muted);
}

.plan-desc {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 12px;
}

.plan-features {
  list-style: none;
  padding: 0;
  margin: 0;
  font-size: 12px;
  color: var(--text-secondary);
}

.plan-features li {
  padding: 4px 0;
  padding-left: 16px;
  position: relative;
}

.plan-features li::before {
  content: '✓';
  position: absolute;
  left: 0;
  color: var(--accent);
}

.action-section {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-bottom: 20px;
}

.btn-primary {
  padding: 14px 48px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--accent), #6366f1);
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-primary:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(79, 140, 255, 0.4);
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-secondary {
  padding: 14px 32px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-secondary:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.05);
  color: var(--text-primary);
}

.btn-secondary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.footer-note {
  text-align: center;
  font-size: 12px;
  color: var(--text-muted);
}

.footer-note a {
  color: var(--accent);
  text-decoration: none;
}

.footer-note a:hover {
  text-decoration: underline;
}
</style>
