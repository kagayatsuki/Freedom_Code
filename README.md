# AI 代码生成平台（Multi-Agent + ReAct）

基于 **Spring Boot + LangChain4j + Vue 3** 的 AI 代码生成平台。用户输入自然语言需求，系统通过「**外层状态机 + 内层 ReAct Agent 循环 + 独立评估器 sub-agent**」的混合式架构，自动生成可运行的 Vue/HTML 前端项目，并支持 RAG 相似案例检索、上下文记忆、在线文件编辑、多维度配额与限流。

## 核心特性

- **混合式 ReAct 架构**：外层显式状态机负责建沙箱、SSE 进度、配额、终态判定等确定性职责；内层 ReAct Agent 自主决策「写文件 → 审查 → 构建 → 精准修复」的完整闭环
- **Multi-Agent 分工**：主编码 Agent 负责执行，独立的 Completion Evaluator 每轮强制判断任务完成度，形成「执行 + 把关」的双智能体协作
- **显式 ReAct 循环**：Java `for` 循环显式控制 Reasoning-Acting-Observation 迭代（非 LangChain4j `AiServices` 隐式循环），可在循环间插入护栏检查、上下文裁剪、评估器判断
- **Tool Calling 代码生成**：大模型直接调用 `writeFile / readFile / modifyFile / getProjectTree / runBuild / reviewFile` 等工具操作文件系统，告别不稳定的 Markdown 正则解析
- **编译自愈闭环**：构建/审查失败时，Agent 读报错精准定位并修复单个文件（而非全量重生成），最多循环 40 步
- **四类护栏**：步数上限 / Token 预算 / 超时 / 同一动作连续重复上限，防止 Agent 死循环与 Token 爆炸
- **路径穿越防护**：canonical path 前缀校验，物理阻断 `../` 越权写文件
- **SSE 实时推送**：Java 21 虚拟线程驱动，节点状态、工具调用、文件变更实时推送到前端
- **RAG 相似案例检索**：BGE-small-zh + PostgreSQL pgvector，在需求增强阶段注入 Top-K 相似案例
- **上下文 Memory**：Redis 短期对话记忆（1 天 TTL）+ MySQL 长期持久化，支持长对话摘要压缩
- **联网 / 图片搜索**：可选 Tavily 联网搜索、Pexels 图片搜索，按任务动态装配
- **Monaco 代码编辑器**：工作台内嵌 VS Code 同款编辑器，支持语法高亮与 IntelliSense
- **用户等级限流**：普通/升级/专业三档配额 + 每日 Token 上限 + 令牌桶限流

## 技术栈

### 后端

| 技术 | 版本 | 用途 |
| --- | --- | --- |
| Java | 21 | 运行环境（虚拟线程） |
| Spring Boot | 4.0.6 | 应用框架 |
| LangChain4j | 0.36.2 | AI 编排（AiServices、Tool Calling、Embedding、pgvector） |
| Qwen 3.8-27B | OpenAI 兼容 | 大语言模型（chat / streaming，可切 DeepSeek） |
| BGE-small-zh-v1.5 | ONNX 本地 | 中文文本向量化（Embedding） |
| MyBatis-Plus | 3.5.16 | ORM（MySQL） |
| MySQL | 8.0+ | 业务数据库（用户、对话历史、每日用量） |
| Redis | 7.0+ | 短期对话记忆 + 令牌桶限流 |
| PostgreSQL + pgvector | 16 | RAG 向量存储（相似案例检索） |
| Spring Security + JWT | - | 认证鉴权 |
| WebFlux / SSE | - | 流式推送 |
| MinIO | 8.5.14 | 对象存储（可选） |
| Caffeine | - | 本地缓存 |

### 前端

| 技术 | 版本 | 用途 |
| --- | --- | --- |
| Vue | 3.5 | UI 框架 |
| Vite | 6.1 | 构建工具 |
| Element Plus | 2.14 | UI 组件库 |
| Pinia | 2.2 | 状态管理 |
| Monaco Editor | 0.55 | 代码编辑器 |
| marked + highlight.js | - | Markdown 渲染 + 代码高亮 |

## 架构设计

### 混合式分层架构

```
┌───────────────────────────────────────────────────────────────┐
│ 外层：WorkflowEngine（状态机骨架 + 安全网，Java 决策）           │
│   INIT → PROMPT_ENHANCED → ROUTED → GENERATING → COMPLETED    │
│   职责：建沙箱 / SSE 进度 / 配额限流 / 重试兜底 / 终态判定       │
└───────────────────────────┬───────────────────────────────────┘
                            │ GENERATING 节点内部（ReActCodingNode）
┌───────────────────────────▼───────────────────────────────────┐
│ 内层：ReActCodingAgent（Agent，LLM 决策）                       │
│   Thought → Action → Observation 显式循环                       │
│   工具：writeFile/readFile/modifyFile/getProjectTree           │
│         + runBuild + reviewFile + searchWeb/searchImages       │
└───────────┬───────────────────────────────┬───────────────────┘
            │ 每轮行动后                     │ 护栏
┌───────────▼──────────────────┐   ┌────────▼──────────────────┐
│ TaskEvaluator（评估器 sub-agent）│   │ AgentLoopConfig          │
│ 独立 PromptAiService，无工具    │   │ AgentStepTracker          │
│ 返回 {done, reason, next}      │   │ 步数/token/超时/防死循环    │
└──────────────────────────────┘   └───────────────────────────┘
```

### 工作流节点

外层状态机保留 5 个节点（`agent.react.enabled=true` 时，生成期三个旧节点被 `ReActCodingNode` 合并）：

| 节点 | 目标状态 | 职责 |
| --- | --- | --- |
| InitNode | INIT | 校验输入、创建项目沙箱 `D:/code/{user}/{project}`、路径安全过滤 |
| EnhanceNode | PROMPT_ENHANCED | 提示词增强（架构设计、技术选型、RAG 相似案例注入） |
| RouteNode | ROUTED | 智能路由，决策生成 Vue 还是 HTML 项目 |
| ReActCodingNode | GENERATING | **ReAct 循环**（替代 CodeGenNode + BuildNode + CheckNode） |
| CompleteNode | COMPLETED | 收尾：预览地址、限流计数、Token 落库 |

> 灰度开关：`agent.react.enabled=false` 时回退到旧的 `CodeGenNode → BuildNode → CheckNode` 三节点链路。

### ReAct 循环逻辑

```
1. 注册工具：ToolSpecification + 工具名→方法映射（反射扫描 @Tool）
2. 组装初始消息：SystemMessage（系统提示词）+ UserMessage（任务上下文）
3. for (step = 0; step < maxSteps; step++) {
     a. 检查取消 / token 预算超限
     b. 主 Agent 单步决策：chatModel.generate(messages, specs)
     c. 无工具调用 → 评估器确认是否完成
     d. 有工具调用 → 执行本轮所有工具，结果作为 Observation 拼回 messages
     e. 每次行动后：独立评估器强制判断 done？
     f. done → 返回 SUCCESS；否则把评估器反馈拼进 messages 继续
     g. 上下文裁剪（messages > 40 条）
   }
```

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.8+
- Node.js 18+
- MySQL 8.0+
- Redis 7.0+
- Docker Desktop（用于 PostgreSQL pgvector）

### 1. 克隆项目

```bash
git clone <repo-url>
cd agent
```

### 2. 启动基础设施

**MySQL & Redis**（需提前安装并启动）：

```bash
# MySQL：确保 localhost:3306 可连接，创建数据库
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS agent DEFAULT CHARACTER SET utf8mb4;"

# 运行表结构初始化
mysql -u root -p agent < src/main/resources/init.sql
# 运行限流相关迁移
mysql -u root -p agent < src/main/resources/rate-limit-migration.sql
```

**PostgreSQL + pgvector**（Docker）：

```bash
docker compose up -d
# 自动创建 agent_rag 数据库并启用 pgvector 扩展
```

### 3. 配置后端

编辑 `src/main/resources/application.yaml`，修改数据库与 LLM 配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/agent?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: root
    password: your_mysql_password
    postgresql:
      url: jdbc:postgresql://localhost:5432/agent_rag
      username: agent
      password: agent123

langchain4j:
  open-ai:
    chat-model:
      base-url: https://tokenrhythm.studio/v1       # OpenAI 兼容接口
      api-key: ${LLM_API_KEY}                        # 建议用环境变量注入
      model-name: qwen3.8-27b                        # 可切 deepseek-v4-pro
      max-tokens: 16384
      timeout: 120s

agent:
  react:
    enabled: true              # true 走 ReAct，false 回退旧状态机
    max-steps: 40              # 最大工具调用步数
    max-tokens: 1000000        # 单任务 token 预算
    timeout-minutes: 15        # 循环超时（分钟）
    max-consecutive-repeat: 3  # 同一动作连续重复上限（防死循环）
    enable-build: true         # 是否允许 Agent 主动构建
    enable-review: true        # 是否允许 Agent 主动审查
```

> ⚠️ 请勿将真实 API Key 提交到仓库，建议使用环境变量注入。

### 4. 启动后端

```bash
mvn spring-boot:run
# 后端运行在 http://localhost:8081
```

### 5. 启动前端

```bash
cd frontend
npm install
npm run dev
# 前端运行在 http://localhost:3000
```

### 6. 访问

浏览器打开 `http://localhost:3000`，注册账号后即可使用。

## 项目结构

```
agent/
├── src/main/java/com/coding/agent/
│   ├── config/              # 配置类（LangChain4j、Redis、Security、pgvector）
│   ├── controller/          # REST 控制器（Auth、Chat、File、Workflow）
│   ├── domain/
│   │   ├── dto/             # 数据传输对象
│   │   ├── po/              # 持久化对象（User、ChatHistory）
│   │   └── vo/              # 视图对象（LoginVO）
│   ├── exception/           # 全局异常处理
│   ├── mapper/              # MyBatis-Plus Mapper
│   ├── service/             # 业务服务（RateLimitService、RagIndexService、FileService）
│   ├── tools/               # AI 工具（FileStructureTools、BuildTool、CodeReviewTool、搜索）
│   ├── utils/               # 工具类（JWT、WorkflowContextHolder）
│   └── workflow/            # 工作流引擎
│       ├── agent/           # ReAct Agent + 评估器
│       │   ├── ReActCodingAgent.java   # 显式 ReAct 循环
│       │   ├── TaskEvaluator.java      # 完成度评估器 sub-agent
│       │   ├── AgentLoopConfig.java    # 护栏配置
│       │   ├── AgentStepTracker.java   # 步数/重复动作护栏
│       │   ├── AgentResult.java        # 终态结果
│       │   └── EvalResult.java         # 评估结果
│       ├── impl/            # 节点实现
│       │   ├── InitNode / EnhanceNode / RouteNode / CompleteNode
│       │   ├── ReActCodingNode.java    # ReAct 节点（替代旧三节点）
│       │   └── CodeGenNode / BuildNode / CheckNode  # 灰度回退用
│       └── WorkflowEngine / WorkflowContext / WorkflowState
├── src/main/resources/
│   ├── application.yaml     # 主配置文件
│   ├── init.sql             # MySQL 初始化脚本
│   └── rate-limit-migration.sql  # 限流表迁移
├── frontend/                # Vue 3 前端
│   └── src/
│       ├── components/      # 组件（ChatDialog、Workbench、TaskConsole、Login...）
│       ├── stores/          # Pinia 状态管理（user）
│       └── utils/           # 工具函数（API 请求）
├── docker-compose.yml       # PostgreSQL pgvector 容器
└── pom.xml                  # Maven 依赖
```

## AI 工具链

| 工具 | 说明 |
| --- | --- |
| `writeFile` | 创建或覆盖文件，内置 canonical path 校验 + 路径脱钩 |
| `readFile` | 读取指定项目文件内容 |
| `modifyFile` | 精准替换局部代码，三层降级匹配 |
| `getProjectTree` | 获取项目文件树 |
| `runBuild` | 执行 `npm install` + `npm run build`（仅 ReAct 模式） |
| `reviewFile` | 单文件代码审查，返回结构化 JSON（仅 ReAct 模式） |
| `searchWeb` | Tavily 联网搜索（按任务开关） |
| `searchImages` | Pexels 图片搜索（按任务开关） |

工具由 `CodingAiServiceFactory.resolveTools(ctx)` 按任务上下文动态装配。

## 用户等级与限流

| 等级 | status | 每日新建项目 | 每日对话轮数 | 每日 Token 上限 |
| --- | --- | --- | --- | --- |
| 禁用 | 0 | 0 | 0 | 0 |
| 普通用户 | 1 | 3 | 6 | 50 万 |
| 升级版 | 2 | 10 | 30 | 200 万 |
| 专业版 | 3 | 50 | 200 | 不限 |

- 令牌桶限流：每用户每分钟最多 10 次提交（Redis + Lua 原子脚本）
- 只有成功完成才计入用量，失败不消耗
- 新项目同时占「项目」+「对话」配额；修改老项目只占「对话」配额

## API 概览

| 端点 | 方法 | 说明 |
| --- | --- | --- |
| `/api/auth/login` | POST | 用户登录（返回 JWT + 用户等级） |
| `/api/auth/register` | POST | 用户注册 |
| `/api/workflow/create` | POST | 启动代码生成工作流（SSE 流式返回） |
| `/api/workflow/run-sync` | POST | 同步执行 ReAct 工作流（调试用，返回终态） |
| `/api/workflow/quota` | GET | 查询当日用量配额 |
| `/api/workflow/stream/{taskId}` | GET | 订阅任务 SSE 流（含 15s 心跳） |
| `/api/workflow/{taskId}/status` | GET | 查询任务实时状态 |
| `/api/workflow/{taskId}/cancel` | POST | 取消运行中的任务 |
| `/api/projects/{user}` | GET | 获取用户项目列表 |
| `/api/projects/{user}/{project}/tree` | GET | 获取项目文件树 |
| `/api/projects/{user}/{project}/file/content` | GET | 读取文件内容 |
| `/api/projects/{user}/{project}/edit` | POST | 编辑保存文件 |
| `/api/projects/{user}/{project}/download` | GET | 下载项目 ZIP |
| `/preview/{user}/{project}/**` | GET | 预览生成的项目页面 |

## 许可证

见 [LICENSE](./LICENSE)。
