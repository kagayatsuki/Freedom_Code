<template>
  <div class="privacy-backdrop" @click.self="$emit('close')">
    <div class="privacy-card">
      <button class="close-btn" @click="$emit('close')">&times;</button>

      <div class="privacy-header">
        <h1>隐私政策</h1>
        <p class="last-updated">最后更新日期：2024年1月1日</p>
      </div>

      <div class="privacy-content">
        <section>
          <h2>1. 引言</h2>
          <p>智能代码生成平台（以下简称"我们"或"本平台"）尊重并保护您的隐私。本隐私政策解释了我们如何收集、使用、披露和保护您的个人信息。请仔细阅读本政策，了解我们对您信息的处理方式。</p>
          <p>通过使用本平台，您同意我们按照本隐私政策处理您的个人信息。</p>
        </section>

        <section>
          <h2>2. 我们收集的信息</h2>
          <p>2.1 账户信息：当您注册账户时，我们收集您的用户名、电子邮件地址、密码等信息。</p>
          <p>2.2 使用信息：我们收集您使用本平台的记录，包括但不限于：</p>
          <ul>
            <li>您创建的项目和文件</li>
            <li>您的对话记录和AI生成的代码</li>
            <li>您的操作日志和使用习惯</li>
            <li>设备信息和IP地址</li>
          </ul>
          <p>2.3 支付信息：当您购买会员服务时，我们收集您的支付信息，但这些信息由第三方支付平台处理，我们不直接存储您的银行卡信息。</p>
          <p>2.4 您主动提供的信息：您在使用过程中主动提供的其他信息。</p>
        </section>

        <section>
          <h2>3. 信息的使用</h2>
          <p>我们使用收集的信息用于以下目的：</p>
          <ul>
            <li>提供和维护本平台的服务</li>
            <li>处理您的账户注册和登录</li>
            <li>处理您的会员订阅和支付</li>
            <li>改进和优化本平台的功能和用户体验</li>
            <li>向您发送服务相关的通知和更新</li>
            <li>检测和防止欺诈、滥用等安全问题</li>
            <li>遵守法律法规的要求</li>
          </ul>
        </section>

        <section>
          <h2>4. 信息的共享与披露</h2>
          <p>4.1 我们不会将您的个人信息出售给第三方。</p>
          <p>4.2 在以下情况下，我们可能会共享您的信息：</p>
          <ul>
            <li>获得您的明确同意</li>
            <li>为提供服务需要，与第三方服务提供商共享（如云服务、支付平台）</li>
            <li>为遵守法律法规、法院命令或其他法律程序</li>
            <li>为保护我们或他人的合法权益</li>
            <li>在发生合并、收购或资产转让时</li>
          </ul>
          <p>4.3 我们要求所有第三方服务提供商遵守严格的隐私保护标准。</p>
        </section>

        <section>
          <h2>5. 信息安全</h2>
          <p>5.1 我们采取适当的技术和组织措施保护您的个人信息，防止未经授权的访问、使用、修改或披露。</p>
          <p>5.2 我们使用加密技术保护数据传输过程中的安全。</p>
          <p>5.3 我们定期审查我们的信息安全措施。</p>
          <p>5.4 尽管我们采取了合理的安全措施，但互联网上的数据传输无法保证100%安全。</p>
        </section>

        <section>
          <h2>6. 您的权利</h2>
          <p>您享有以下权利：</p>
          <ul>
            <li><strong>访问权：</strong>您有权访问我们持有的您的个人信息</li>
            <li><strong>更正权：</strong>您有权要求更正不准确的个人信息</li>
            <li><strong>删除权：</strong>您有权要求删除您的个人信息（法律要求保留的除外）</li>
            <li><strong>撤回同意权：</strong>您有权随时撤回对我们处理您信息的同意</li>
            <li><strong>限制处理权：</strong>您有权要求限制我们对您信息的处理</li>
            <li><strong>数据可携权：</strong>您有权获取您的信息副本并转移给其他服务提供商</li>
          </ul>
          <p>如需行使上述权利，请通过客服渠道联系我们。</p>
        </section>

        <section>
          <h2>7. Cookie和类似技术</h2>
          <p>7.1 我们使用Cookie和类似技术来改善您的用户体验。</p>
          <p>7.2 Cookie是存储在您设备上的小文件，用于记住您的偏好设置。</p>
          <p>7.3 您可以通过浏览器设置管理或删除Cookie，但这可能影响某些功能的使用。</p>
        </section>

        <section>
          <h2>8. 未成年人保护</h2>
          <p>8.1 本平台不面向14周岁以下的未成年人。</p>
          <p>8.2 如您是未成年人的监护人，发现未成年人使用本平台，请联系我们，我们将采取措施保护未成年人的信息。</p>
        </section>

        <section>
          <h2>9. 数据存储</h2>
          <p>9.1 我们在中国境内的服务器上存储您的个人信息。</p>
          <p>9.2 我们仅在实现本政策所述目的所需的时间内保留您的信息。</p>
          <p>9.3 服务终止后，我们将删除或匿名化您的个人信息，法律法规另有规定的除外。</p>
        </section>

        <section>
          <h2>10. 隐私政策的变更</h2>
          <p>10.1 我们可能不时更新本隐私政策。</p>
          <p>10.2 重大变更时，我们将通过平台公告或邮件通知您。</p>
          <p>10.3 继续使用本平台即表示您接受更新后的隐私政策。</p>
        </section>

        <section>
          <h2>11. 联系我们</h2>
          <p>如您对本隐私政策或我们处理您信息的方式有任何疑问、意见或建议，请通过以下方式联系我们：</p>
          <ul>
            <li>电子邮件：privacy@example.com</li>
            <li>在线客服：通过平台内的客服功能</li>
          </ul>
          <p>我们将在收到您的联系后尽快回复。</p>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup>
defineEmits(['close'])
</script>

<style scoped>
.privacy-backdrop {
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

.privacy-card {
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

.privacy-header {
  text-align: center;
  margin-bottom: 32px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--border-color);
}

.privacy-header h1 {
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

.privacy-content {
  line-height: 1.8;
  color: var(--text-secondary);
}

.privacy-content section {
  margin-bottom: 28px;
}

.privacy-content h2 {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 12px;
  padding-top: 8px;
}

.privacy-content p {
  margin-bottom: 10px;
  text-align: justify;
}

.privacy-content ul {
  margin-bottom: 10px;
  padding-left: 24px;
}

.privacy-content li {
  margin-bottom: 6px;
}

.privacy-content li:last-child {
  margin-bottom: 0;
}
</style>
