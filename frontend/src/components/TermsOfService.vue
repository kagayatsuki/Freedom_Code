<template>
  <div class="terms-backdrop" @click.self="$emit('close')">
    <div class="terms-card">
      <button class="close-btn" @click="$emit('close')">&times;</button>

      <div class="terms-header">
        <h1>服务条款</h1>
        <p class="last-updated">最后更新日期：2024年1月1日</p>
      </div>

      <div class="terms-content">
        <section>
          <h2>1. 服务简介</h2>
          <p>本服务条款（以下简称"本条款"）适用于您使用智能代码生成平台（以下简称"本平台"）的所有服务。通过注册、访问或使用本平台，您同意受本条款的约束。如果您不同意本条款，请勿使用本平台。</p>
        </section>

        <section>
          <h2>2. 账户注册与使用</h2>
          <p>2.1 您需要提供真实、准确、完整的注册信息，并及时更新您的账户信息。</p>
          <p>2.2 您应当妥善保管您的账户密码，不得将账户转让、出借或授权他人使用。</p>
          <p>2.3 您对通过您的账户进行的所有活动承担全部责任。</p>
          <p>2.4 如发现任何未经授权的账户使用，请立即通知我们。</p>
        </section>

        <section>
          <h2>3. 服务内容</h2>
          <p>3.1 本平台提供AI代码生成、项目预览、文件管理等服务。</p>
          <p>3.2 我们保留随时修改、暂停或终止部分或全部服务的权利，恕不另行通知。</p>
          <p>3.3 会员服务的具体权益以会员页面说明为准。</p>
        </section>

        <section>
          <h2>4. 会员服务</h2>
          <p>4.1 会员服务包括但不限于：无限对话次数、高级AI模型、文件上传、优先支持等。</p>
          <p>4.2 会员费用根据您选择的套餐类型而定，具体价格以平台显示为准。</p>
          <p>4.3 会员服务一旦开通，不支持退款，除非法律另有规定。</p>
          <p>4.4 我们保留调整会员价格和权益的权利，调整前会提前通知会员。</p>
        </section>

        <section>
          <h2>5. 退款政策</h2>
          <p>5.1 会员服务开通后，原则上不支持退款。</p>
          <p>5.2 如因技术故障导致服务无法正常使用，经核实后可申请延期或部分退款。</p>
          <p>5.3 如您对服务不满意，可在开通后24小时内申请退款，我们将收取10%的手续费。</p>
          <p>5.4 退款申请需通过客服渠道提交，我们将在7个工作日内处理。</p>
          <p>5.5 虚拟商品一经使用，不支持退款。</p>
        </section>

        <section>
          <h2>6. 用户行为规范</h2>
          <p>6.1 您不得利用本平台从事任何违法、违规活动。</p>
          <p>6.2 您不得上传、传播含有病毒、恶意代码或其他有害内容的文件。</p>
          <p>6.3 您不得干扰本平台的正常运行，或尝试破解、逆向工程本平台。</p>
          <p>6.4 您不得冒充他人或机构，或虚假陈述您与他人或机构的关系。</p>
        </section>

        <section>
          <h2>7. 知识产权</h2>
          <p>7.1 本平台的所有内容，包括但不限于文字、软件、图形、图标等，均受知识产权法保护。</p>
          <p>7.2 您在使用本平台过程中生成的代码，其知识产权归您所有。</p>
          <p>7.3 未经我们书面许可，您不得复制、修改、传播本平台的内容。</p>
        </section>

        <section>
          <h2>8. 免责声明</h2>
          <p>8.1 本平台按"现状"和"可得到"的状态提供，我们不作任何明示或暗示的保证。</p>
          <p>8.2 我们不对因使用本平台而产生的任何直接、间接、附带、特殊、后果性或惩罚性损害承担责任。</p>
          <p>8.3 您理解并同意，使用本平台的风险由您自行承担。</p>
        </section>

        <section>
          <h2>9. 服务变更与终止</h2>
          <p>9.1 我们保留随时修改、暂停或终止本服务的权利。</p>
          <p>9.2 如您违反本条款，我们有权随时终止您的账户和服务。</p>
          <p>9.3 服务终止后，您将无法继续使用本平台的服务。</p>
        </section>

        <section>
          <h2>10. 争议解决</h2>
          <p>10.1 本条款的解释及争议的解决均适用中华人民共和国法律。</p>
          <p>10.2 如发生争议，双方应友好协商解决；协商不成的，任何一方均有权向平台所在地人民法院提起诉讼。</p>
        </section>

        <section>
          <h2>11. 其他条款</h2>
          <p>11.1 本条款构成您与我们之间关于使用本平台的完整协议。</p>
          <p>11.2 我们保留随时修改本条款的权利，修改后的条款将在平台上公布。</p>
          <p>11.3 如您对本条款有任何疑问，请通过客服渠道联系我们。</p>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup>
defineEmits(['close'])
</script>

<style scoped>
.terms-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.7);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  padding: 20px;
}

.terms-card {
  position: relative;
  width: 800px;
  max-width: 95vw;
  max-height: 90vh;
  overflow-y: auto;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 20px;
  padding: 40px;
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

.close-btn:hover {
  background: rgba(255, 255, 255, 0.2);
  color: var(--text-primary);
}

.terms-header {
  text-align: center;
  margin-bottom: 32px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--border-color);
}

.terms-header h1 {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 8px;
  background: linear-gradient(135deg, var(--accent), #a78bfa);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.last-updated {
  font-size: 13px;
  color: var(--text-muted);
}

.terms-content {
  line-height: 1.8;
  color: var(--text-secondary);
}

.terms-content section {
  margin-bottom: 28px;
}

.terms-content h2 {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 12px;
  padding-top: 8px;
}

.terms-content p {
  margin-bottom: 10px;
  text-align: justify;
}

.terms-content p:last-child {
  margin-bottom: 0;
}
</style>
