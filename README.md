# 🦊 灵犀智能体平台（LingXi AI Agent Platform）

> 企业级 **Java + AI Agent** 全栈平台：多智能体编排 · ReAct 工具调用 · RAG 知识库 · AIOps 智能运维演示场景 · Docker 一键部署。
>
> 无需任何大模型 API Key —— 内置离线 Mock 模型即可完整体验「多轮工具调用 + 流式推理」全链路。

![tech](https://img.shields.io/badge/Java%2021-Spring%20Boot%203.5-6db33f) ![ai](https://img.shields.io/badge/Spring%20AI-1.0.0-6f42c1) ![fe](https://img.shields.io/badge/Vue%203-Element%20Plus-409eff) ![deploy](https://img.shields.io/badge/Docker%20Compose-deploy-2496ed)

---

## ✨ 核心能力

| 能力 | 说明 |
|---|---|
| 🤖 自研 ReAct 编排循环 | 关闭框架黑盒工具执行，编排器手工驱动「流式推理 → 工具调用 → 结果回填」多轮循环，全过程 SSE 实时透出 |
| 🧭 多智能体体系 | 全能管家 / 知识问答 / 运维诊断 / 数据分析 / 报告撰写，5 类智能体各配专属系统提示词与工具子集 |
| 🔧 9 类工具 | 知识库检索、告警、指标、CMDB、**只读 SQL（五重安全护栏）**、联网搜索(模拟)、报告保存、通知、时间 |
| 📚 RAG 知识库 | 上传 MD/TXT/PDF/DOCX → Tika 解析 → 分块(600/120) → 向量化 → Qdrant，混合检索（0.7·语义 + 0.3·词面） |
| 🩺 AIOps 演示场景 | 预置 CMDB/告警/指标/处置手册，支持「一键 AI 诊断」「AI 巡检报告」完整闭环 |
| 🔌 供应商无关 | OpenAI 兼容协议接入 DeepSeek / 通义千问 / OpenAI；管理端热切换 + 连通性测试 |
| 🛡️ 安全 | JWT 双令牌、RBAC、Redis Lua 限流、SQL 白名单、DOMPurify 防注入 |
| 📊 可观测 | Agent 推理现场落库可回放、Prometheus + Grafana（可选 profile） |

## 🚀 快速开始（Docker 一键部署）

前置要求：Docker 24+ 与 Docker Compose v2。

```bash
# 1. 配置环境（可选：默认 Mock 模型无需任何 Key）
cp .env.example .env

# 2. 构建并启动（6 个基础设施 + 前后端）
docker compose up -d --build

# 3. 等待就绪（首次构建约 5-10 分钟）
docker compose ps          # 全部 healthy 即就绪
docker compose logs -f backend

# 4. 访问
#    前端          http://localhost:8000
#    后端 API      http://localhost:8080
#    Swagger       http://localhost:8080/swagger-ui.html
#    RabbitMQ 控制台  http://localhost:15672  (lingxi/lingxi123)
#    MinIO 控制台    http://localhost:9001   (minioadmin/minioadmin123)
```

**演示账号**：`admin / admin123`（管理员）、`opsuser / user123`（普通用户）


### 从 IDEA / 本机启动后端

本机运行默认连接 `localhost:3306/lingxi`。Compose 中的 MySQL 仅在容器网络暴露 `3306`，所以容器里的 `lingxi` 不等于本机 MySQL 的同名库，数据也不共享。

1. 在本机 MySQL 执行 [scripts/init-local-db.sql](scripts/init-local-db.sql)，先创建 `lingxi` 数据库。Flyway 在后端启动时自动创建表；`Unknown database 'lingxi'` 表示数据库本身缺失，不能通过关闭 Flyway 解决。
2. 确保 Redis、RabbitMQ、Qdrant 和 MinIO 可访问。后端默认 RabbitMQ、MinIO 凭据已与本项目 Compose 一致；使用默认值时无需额外配置。自定义过凭据时，在 IDEA 运行配置中覆盖相应环境变量：

   ```text
   MYSQL_HOST=localhost
   MYSQL_PORT=3306
   MYSQL_DATABASE=lingxi
   MYSQL_USERNAME=root
   MYSQL_PASSWORD=<本机 MySQL 密码>
   RABBITMQ_USERNAME=lingxi
   RABBITMQ_PASSWORD=lingxi123
   MINIO_ACCESS_KEY=minioadmin
   MINIO_SECRET_KEY=minioadmin123
   ```

   IDEA 直接启动不会自动加载项目根目录的 `.env` 文件；需要覆盖的变量应放在运行配置的 Environment variables 中。本机 MySQL 默认密码为 `root`，其他密码请显式设置。
3. 使用 JDK 21 运行 `com.lingxi.LingXiApplication`。若 Docker 后端已占用 `8080`，先执行 `docker compose stop backend`，再启动 IDEA 后端。不要同时运行连接不同数据库、却消费同一文档队列的两个后端。
4. 本地开发前端使用 `cd frontend` 后执行 `npm run dev`，默认代理到本机 `8080`。容器前端代理的是容器后端，不能直接替代本地开发前端。

成功日志应包含 Flyway 迁移完成（首次启动为 V1~V5）及 `Started LingXiApplication`。本机新库是独立的空库，默认种子器会初始化本机演示账号和演示数据。

### 接入真实大模型（可选）

编辑 `.env` 后重启 backend 即可（或直接在网页「系统管理 → 模型配置」热更新）：

```ini
LLM_PROVIDER=openai
OPENAI_BASE_URL=https://api.deepseek.com   # 或 https://dashscope.aliyuncs.com/compatible-mode
OPENAI_API_KEY=sk-xxxx
OPENAI_MODEL=deepseek-chat                 # 或 qwen-plus 等
```

### 启用监控（可选）

```bash
docker compose --profile monitoring up -d
# Prometheus http://localhost:9090 · Grafana http://localhost:3000 (admin/admin123)
```

## 🎮 演示剧本（登录后直接试）

| 去哪玩 | 做什么 |
|---|---|
| 智能对话 | 发送「**订单服务 CPU 告警，帮我诊断一下**」—— 观察Agent 的 3 轮工具调用与流式诊断报告 |
| 智能对话 | 发送「**统计一下各服务当前活跃告警数量**」—— 观察只读 SQL 工具（白名单护栏） |
| 智能对话 | 发送「**帮我生成一份巡检报告**」→ 到「报告中心」查看下载 |
| 运维中心 | 告警列表点「🤖 AI 诊断」→ 自动跳转对话页开始诊断；指标页看 CPU/内存时序 |
| 知识中心 | 上传 MD/PDF 文档，看异步摄取状态流转；用「检索测试」验证 RAG 效果 |
| 系统管理 | 模型配置热切换 Mock ↔ DeepSeek，一键连通性测试 |

## 📂 目录结构

```
lingxi-platform/
├── backend/                 # Spring Boot 3.5 + Spring AI 1.0（Java 21）
│   ├── src/main/java/com/lingxi/
│   │   ├── agent/           # ★ Agent 核心：编排器/智能体/工具/记忆/Mock模型
│   │   ├── rag/             # ★ RAG：摄取管道/分块/混合检索/Qdrant
│   │   ├── modules/         # 业务：auth/chat/ops/report/admin
│   │   ├── security/        # JWT/RBAC
│   │   └── config/          # 配置与种子数据
│   ├── src/main/resources/db/migration/   # Flyway V1~V4
│   ├── src/test/            # 38 个单元/集成测试
│   └── Dockerfile           # 多阶段构建（非 root 运行）
├── frontend/                # Vue 3 + Vite + Element Plus + ECharts
│   ├── src/views/           # 仪表盘/对话/知识/运维/报告/管理
│   ├── tests/               # vitest 13 个测试
│   └── Dockerfile           # node 构建 → nginx 运行（SSE 反代）
├── deploy/                  # Prometheus / Grafana 配置
├── scripts/api_smoke.sh     # API 冒烟测试脚本
├── docs/                    # ★ 设计报告 + 测试报告
└── docker-compose.yml       # 6 基础设施 + 前后端 + 可选监控
```

## 🧪 测试

```bash
# 后端（38 个用例）
cd backend && mvn test

# 前端（13 个用例）
cd frontend && npm test

# 部署后 API 冒烟（13 项端到端检查）
./scripts/api_smoke.sh
```

详细测试结论见 [docs/测试报告.md](docs/测试报告.md)，完整设计说明见 [docs/设计报告.md](docs/设计报告.md)。

## ⚠️ 生产使用提示

- 替换 `JWT_SECRET` 为强随机值；修改数据库/MinIO/RabbitMQ 默认密码。
- `LINGXI` 默认开启演示数据种子，生产设 `SEED_ENABLED=false`。
- SQL 工具、联网搜索等工具请按企业安全策略评审后再开放。

### 本地启动回归检查

后端启动且演示数据初始化完成后，在项目根目录执行（Node.js 22+）：

```bash
node scripts/local_smoke.mjs
```

覆盖健康状态、OpenAPI/Swagger、认证、仪表盘、MinIO 上传下载、RabbitMQ 文档摄取、Qdrant 检索、重新解析和 SSE 工具调用。测试仅面向本机开发环境，会创建并清理临时文档和会话，不修改现有告警或个人资料。可用 `BASE_URL`、`SMOKE_USERNAME`、`SMOKE_PASSWORD` 覆盖地址和演示账号。

API 文档使用 springdoc 2.8.x，与 Spring Boot 3.5 的[官方兼容表](https://springdoc.org/v2/faq.html)一致；旧版 2.6.0 会触发 `ControllerAdviceBean` 构造方法缺失错误。
