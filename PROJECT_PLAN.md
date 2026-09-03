# LingXi 灵犀智能体平台 — 项目计划存档（内部工作文档）

> 本文档是开发过程的决策存档，供长会话中随时回读。最终对外文档见 `docs/设计报告.md` 与 `docs/测试报告.md`。

## 0. 环境事实（已验证 2026-09-02）
- Windows 11, Git Bash。
- JDK: `D:\jspraoject\JDK21`（21.0.9）。**JAVA_HOME 系统变量配错了一层**（指向 `D:\jspraoject\JDK21\JDK21`，不存在）。
  每条构建命令必须前缀：`export JAVA_HOME='D:\jspraoject\JDK21'`。
- Maven 3.9.16 at `D:\jspraoject\MAVEN\apache-maven-3.9.16`（已在 PATH）。
- Node v22.22.1 / npm 10.9.4，registry=registry.npmmirror.com。
- Docker 28.5.1 + Compose v2.40.2，Docker Desktop 已尝试启动（daemon 需等待就绪，验证命令 `docker info`）。
- 网络可达 Maven Central 与 npm registry。

## 1. 项目定位
**灵犀智能体平台（LingXi AI Agent Platform）**：企业级 Java AI 智能体平台，演示场景 = **AIOps 智能运维助手**。
核心能力：
1. 多 Agent 编排（Supervisor 自动路由 + 4 个专家 Agent）
2. 自研 ReAct 循环（手动工具调用编排，SSE 全过程事件流：message/tool_call/tool_result/done）
3. RAG 知识库（文档上传→MinIO→RabbitMQ 异步解析(Tika)→分块→向量化→Qdrant，混合检索）
4. LLM 供应商无关（OpenAI 兼容协议：DeepSeek/Qwen/OpenAI；内置 **MockChatModel 脚本化模型**，无 API Key 也能全链路演示与测试）
5. 运维场景业务面：CMDB、告警、指标、一键 AI 诊断、AI 巡检报告
6. 平台面：用户/角色(JWT)、会话历史、仪表盘、系统管理（模型配置可热切换+连通性测试）、限流、审计

## 2. 技术选型（最终锁定）
- 后端：Java 21 + Spring Boot **3.5.4** + Spring AI **1.0.0**（**BOM 1.0.0**，用核心模块非 starter：
  `spring-ai-openai`, `spring-ai-qdrant-store`, `spring-ai-tika-document-reader`，避免自动配置与我们动态 ChatModel 冲突）
- ORM：MyBatis-Plus **3.5.7**（mybatis-plus-spring-boot3-starter）+ Flyway（Boot 管理 11.x）
- 安全：Spring Security + jjwt **0.12.6**，BCrypt，ROLE_ADMIN/ROLE_USER
- 存储：MySQL 8.4、Redis 7、Qdrant（向量库）、MinIO（对象存储）、RabbitMQ 3.13（异步管道）
- 文档：springdoc-openapi **2.6.0**（/swagger-ui.html）
- 可观测：Actuator + micrometer-prometheus；可选 profile：prometheus+grafana
- 前端：Vue 3.5 + TypeScript + Vite 5.4 + Pinia 2 + Vue Router 4 + Element Plus 2.x + ECharts 5 + markdown-it + DOMPurify + highlight.js + axios；测试 vitest 2
- 部署：docker compose（8+ 服务），多阶段 Dockerfile（maven 构建 / node 构建），nginx 反代（SSE 需 proxy_buffering off）

## 3. 关键技术决策（写代码时必须遵守）
- **MockChatModel**（`agent/llm/mock`）：实现 `ChatModel`（call/stream）。逻辑：读 prompt 最后一条 UserMessage 关键词 + ToolCallingChatOptions 中可用工具 + 已有 ToolResponseMessage 轮数（=第几轮），按 `ScriptLibrary` 脚本发工具调用或最终回答。流式时把参数 JSON 拆成多个 chunk（模拟真实增量工具调用）。**默认开启**（`lingxi.llm.mock=true`），保证无 Key 可演示/测试。
- **MockEmbeddingModel**：512 维，char-bigram+word hash 词袋向量，L2 归一化（中文相似度可用）。
- **AgentOrchestrator 自研 ReAct 循环**（`agent/core/AgentOrchestrator`）：
  `ToolCallingChatOptions.builder().toolCallbacks(...).internalToolExecutionEnabled(false)`，
  `chatModel.stream(prompt)` 消费 delta，`ToolCallAccumulator` 合并跨 chunk 的 toolCall 片段（按 id/index 合并 arguments），
  无工具调用→输出完成；有→ emit `tool_call` 事件 → `toolCallingManager.executeToolCalls(prompt, response)` → conversationHistory 续环（max 8 轮）。
  运行在独立 executor 线程，SseEmitter 推送。事件协议 JSON：`{event, sessionId, messageId?, seq, data}`。
- **SSE 事件类型**：`message`（增量文本）、`tool_call`、`tool_result`、`round`、`done`（含完整答案+统计）、`error`。
- 工具全部用 `@Tool`/`@ToolParam` 注解的 Spring bean，`ToolCallbacks.from(bean)` 转 ToolCallback。
- Long→JSON 用 ToStringSerializer（防 JS 精度丢失），统一 `Result<T>{code,message,data,success,timestamp}`，code=0 成功。
- 分页 MyBatis-Plus 分页插件；create_time/update_time 用 MetaObjectHandler 自动填充。
- 删除向量：chunk 的 vectorId = UUID.nameUUIDFromBytes(docId+"-"+idx)，Qdrant delete by id。
- 检索：`vectorStore.similaritySearch` 取 top 3x，元数据过滤 spaceId，混合得分=0.7*cos+0.3*词面重叠，返回 topK。
- SQL 工具白名单：仅 SELECT/WITH 单条；禁 DML/DDL 关键词；表名必须 `ops_` 前缀；强制 LIMIT；JdbcTemplate queryTimeout=5s。

## 4. 包结构（com.lingxi）
- common: api(Result,PageResult), exception(BizException,GlobalExceptionHandler), util
- config: AppProperties(@ConfigurationProperties lingxi), SecurityConfig, MybatisPlusConfig, AsyncConfig, RabbitConfig, OpenApiConfig, JacksonConfig, RateLimitInterceptor+WebConfig
- security: JwtService, JwtAuthFilter, SecurityUtils
- modules: auth(AuthController,AuthService,dto), user(SysUser+mapper+UserController), chat(ChatController,ChatService,ChatSession,ChatMessage,mappers,dto), ops(OpsController,OpsBizService,DiagnosisService, entities: OpsHost/OpsServiceInfo/OpsAlert/OpsMetric+mappers), report(ReportController,ReportService,Report), admin(AdminUserController,ModelConfigController,DashboardController/DashboardService)
- agent: core(AgentType enum, AgentDescriptor, AgentRegistry, AgentOrchestrator, ToolCallAccumulator, ConversationPersistence 接口), llm(ChatModelFactory, AiModelConfig+mapper+ModelConfigService), mock(MockChatModel, MockEmbeddingModel, ScriptLibrary), memory(ConversationMemoryService), tools(KnowledgeBaseTool, AlertTool, MetricsTool, CmdbTool, SqlQueryTool, DateTimeTool, WebSearchTool, ReportTool, NotificationTool)
- rag: pipeline(DocIngestMessage, DocIngestConsumer), service(ChunkingService, IngestionService, RetrievalService, FileStorageService(MinIO)), controller(KnowledgeController), store(VectorStoreConfig), entity(KbSpace,KbDocument,KbChunk)+mappers
- 测试：ConversationPersistence 与 IngestionService 的持久化依赖做成接口或在测试中用 SimpleVectorStore/Mockito，避免依赖真实 MySQL。SQL 工具测试用 H2(test scope)。

## 5. 数据库（Flyway V1~V4）
V1__identity_and_chat.sql: sys_user, chat_session, chat_message, agent_tool_invocation
V2__knowledge.sql: kb_space, kb_document, kb_chunk
V3__ops.sql: ops_host, ops_service_info, ops_alert, ops_metric
V4__platform_and_seed.sql: ai_model_config, rpt_report + 运维演示数据（hosts/services/alerts）；用户与指标序列与 Runbook 知识库由 Java 种子器 `DemoDataSeeder`（幂等）完成。
- 种子账号：admin/admin123(ADMIN), opsuser/user123(USER)
- 告警种子：order-service CPU 92% P1（演示诊断主剧本）、mysql 磁盘 85% P2、payment 连接池 98% P1、gateway 5xx P2、web-01 内存 P3 等
- Runbook 知识库 4 篇（CPU飙高排查/磁盘空间不足/连接池耗尽/Redis内存告警）作为 KB 种子文档直接走 IngestionService

## 6. API 契约（全部 /api 前缀）
- POST /api/auth/register {username,password,nickname} ; POST /api/auth/login → {accessToken,refreshToken,user} ; POST /api/auth/refresh {refreshToken} ; GET /api/auth/me
- GET /api/agents → Agent 列表（id,name,description,icon）
- POST /api/chat/sessions {title,agentType} ; GET /api/chat/sessions?page ; GET /api/chat/sessions/{id}/messages ; DELETE /api/chat/sessions/{id}
- POST /api/chat/stream?sessionId=&agentType=  body{content} → SSE 流（fetch+ReadableStream，非 EventSource，因需 POST+header）
- POST /api/kb/spaces {name,description} ; GET /api/kb/spaces
- POST /api/kb/documents/upload (multipart: spaceId,file) ; GET /api/kb/documents?spaceId= ; DELETE /api/kb/documents/{id} ; POST /api/kb/search {spaceId,query,topK}
- GET /api/ops/alerts?status= ; POST /api/ops/alerts/{id}/diagnose → {sessionId,prompt} ; GET /api/ops/hosts ; GET /api/ops/services ; GET /api/ops/metrics?serviceId=&hours=24
- GET /api/reports ; GET /api/reports/{id}
- GET /api/dashboard/summary
- GET/PUT /api/admin/model-config ; POST /api/admin/model-config/test ; GET /api/admin/users ; PUT /api/admin/users/{id}/status ; PUT /api/admin/users/{id}/role
- 允许匿名：/api/auth/**, swagger, actuator health/prometheus。其余 authenticated。限流：login 5/min/IP，chat stream 30/min/user（Redis INCR+EXPIRE，注解 @RateLimit）。

## 7. 前端页面（/frontend）
- views: LoginView, RegisterView, MainLayout(侧栏+顶栏), DashboardView(4 stat卡+3 ECharts), ChatView(左会话列表/中对话/右 Agent 选择；工具调用卡片渲染), KnowledgeView(space切换+上传+文档表+检索测试), OpsView(告警tab+CMDB tab+指标tab, 一键诊断跳转Chat并自动发送), ReportsView, AdminView(用户管理+模型配置), NotFound
- stores: auth(token+user, localStorage), app(collapse等)
- utils: sse.ts(fetch SSE 解析器), markdown.ts(markdown-it+DOMPurify+highlight), format.ts(时间/字节)
- api/http.ts: axios 实例，请求带 Bearer，401→单飞 refresh→重放，失败登出
- vite dev 代理 /api→localhost:8080；build 仅 vite build（不做 vue-tsc 阻塞）
- vitest：tests/sse.spec.ts, markdown.spec.ts, format.spec.ts

## 8. Docker 部署
- backend/Dockerfile: maven:3.9-eclipse-temurin-21 构建 → eclipse-temurin:21-jre-alpine 运行（构建在容器内，绕开本机 JAVA_HOME 问题）
- frontend/Dockerfile: node:20-alpine build → nginx:1.27-alpine
- deploy/nginx/default.conf: / → SPA; /api → backend:8080，SSE 路由 proxy_buffering off
- docker-compose.yml（项目根）：mysql8.4(健康检查,utf8mb4), redis7, qdrant, minio(+mc 初始化桶), rabbitmq(管理台), backend, frontend；profiles: monitoring(prometheus+grafana)
- 端口：前端 8000 / 后端 8080 / mysql 3306 / redis 6379 / qdrant 6333,6334 / minio 9000,9001 / rabbit 5672,15672 / prometheus 9090 / grafana 3000
- .env.example；LLM_PROVIDER=mock 默认；openai 兼容示例用 DeepSeek

## 9. 执行顺序与当前状态
[已完成] 环境验证 → [进行中] PROJECT_PLAN → 后端骨架 → 各模块 → Flyway → 测试跑通 → 前端 → docker → 冒烟 → 两份报告(含真实测试输出与缺陷记录)
缺陷记录暂存于本文档末尾「缺陷日志」。

## 10. 缺陷日志（测试报告素材，随开发记录）
- E-01: 系统 JAVA_HOME 指向不存在的 `D:\jspraoject\JDK21\JDK21`（应为 `D:\jspraoject\JDK21`），构建需手动 export。属环境缺陷，非代码缺陷。
- E-02: Docker Desktop 未随开机启动，daemon 需手动拉起后等待就绪。
- E-03: Maven 实际 localRepository 为 `D:\jspraoject\MAVEN\my_maven_repository`（非 ~/.m2），且 grpc 被 qdrant client 声明为 runtime 作用域，编译期不可见 → pom 显式补 io.grpc:grpc-api:1.65.1（compile）。
- C-01: Spring AI 1.0.0 不存在 `ToolCallbacks` 工具类 → 改用 `MethodToolCallbackProvider.builder().toolObjects(bean).build().getToolCallbacks()`。
- C-02: `ChatOptions` 实际位于 `org.springframework.ai.chat.prompt`（初写误为 chat.model）。
- C-03: `Embedding` 构造器参数顺序为 (float[], Integer index)；`Usage` 接口必须实现 `getNativeUsage()`。
- C-04: 自研 `LoginPrincipal` 为 record，访问器 `userId()` 非 `getUserId()`（3 处调用点编译报错）。
- C-05: SQL 护栏规则顺序缺陷（测试发现）：DML/DDL 关键字检查需先于 SELECT 前缀检查，否则 INSERT 只报"仅允许 SELECT"而非"禁止关键字"。已修复并补强断言。
- C-06: 检索评分方向缺陷（测试发现）：Spring AI 约定 metadata 的 `distance` 为距离（越小越好），初版误当相似度 → 排名完全倒置。修复为 semantic = 1 - distance。该缺陷由 RetrievalServiceTest 首轮运行捕获，验证了测试有效性。
- C-07: AgentOrchestrator 重构时丢失 `persistence` 字段与 `ChatMessage` 导入（编译期发现修复）；循环变量 round 需复制为 effectively-final 才能在 lambda 中使用。
- C-08: SseEmitter 构造参数为 Long，int 常量需显式 long。
