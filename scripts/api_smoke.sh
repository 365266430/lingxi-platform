#!/usr/bin/env bash
# 灵犀智能体平台 API 冒烟测试 v3（通过前端 nginx 网关访问，覆盖 v1.1.0 新功能）
# 说明：
#  1) Git Bash 的 curl 直接 -d 中文会按 GBK 发送导致后端 UTF-8 解析失败，
#     因此所有含中文的请求体统一写入 UTF-8 临时文件后用 --data-binary @file 发送；
#  2) 后端统一响应 HTTP 200 + 业务码（Result.code），安全断言基于业务码而非 HTTP 状态码
#     （仅认证拦截器/限流器直接返回 HTTP 401/403/429）。
# 用法: BASE=http://localhost:8000/api ./scripts/api_smoke.sh
set -u
BASE="${BASE:-http://localhost:8000/api}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
PASS=0
FAIL=0

green() { echo -e "\033[32m[PASS]\033[0m $1"; }
red()   { echo -e "\033[31m[FAIL]\033[0m $1"; }

check() {
  if [ "$2" -eq 0 ]; then PASS=$((PASS+1)); green "$1"; else FAIL=$((FAIL+1)); red "$1"; fi
}

jsonval() {
  echo "$1" | grep -o "\"$2\":\"[^\"]*\"" | head -1 | cut -d'"' -f4
}

post_json() { # url data-file token
  curl -s -X POST "$1" -H 'Content-Type: application/json' ${3:+-H "Authorization: Bearer $3"} --data-binary @"$2"
}
put_json() {
  curl -s -X PUT "$1" -H 'Content-Type: application/json' ${3:+-H "Authorization: Bearer $3"} --data-binary @"$2"
}

echo "=============================="
echo " 灵犀智能体平台 API 冒烟测试 v3"
echo " BASE=$BASE"
echo "=============================="

# 测试初始化：清理登录限流计数（登录限流 5 次/分钟/IP，连续执行冒烟会触发限流）
docker exec lingxi-redis redis-cli --scan --pattern 'lingxi:rl:*' 2>/dev/null | \
  xargs -r -n1 docker exec lingxi-redis redis-cli DEL > /dev/null 2>&1 || true

# ---------- 1. 认证与权限 ----------
LOGIN=$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}')
TOKEN=$(jsonval "$LOGIN" accessToken)
[ -n "$TOKEN" ] && check "管理员登录获取 accessToken" 0 || check "管理员登录" 1
AUTH="Authorization: Bearer $TOKEN"

printf '{"username":"admin","password":"wrongpwd"}' > "$TMP/badpwd.json"
BAD=$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' --data-binary @"$TMP/badpwd.json")
echo "$BAD" | grep -q '"code":401' && check "错误密码业务码 401" 0 || check "错误密码业务码 401 (got $BAD)" 1

CODE=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/dashboard/summary")
[ "$CODE" = "401" ] && check "未认证访问返回 HTTP 401" 0 || check "未认证访问返回 HTTP 401 (got $CODE)" 1

ULOGIN=$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"opsuser","password":"user123"}')
UTOKEN=$(jsonval "$ULOGIN" accessToken)
[ -n "$UTOKEN" ] && check "普通用户登录" 0 || check "普通用户登录" 1
CODE=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/admin/users" -H "Authorization: Bearer $UTOKEN")
[ "$CODE" = "403" ] && check "普通用户访问管理接口被拒 HTTP 403" 0 || check "普通用户越权 (got $CODE)" 1

# ---------- 2. 平台数据接口 ----------
AGENTS=$(curl -s "$BASE/agents" -H "$AUTH")
echo "$AGENTS" | grep -q "ops_diagnosis" && check "智能体列表（含运维诊断）" 0 || check "智能体列表" 1

DASH=$(curl -s "$BASE/dashboard/summary" -H "$AUTH")
echo "$DASH" | grep -q "activeAlerts" && check "仪表盘统计" 0 || check "仪表盘统计" 1
echo "$DASH" | grep -q "feedbackUp" && check "仪表盘含反馈统计（v1.1）" 0 || check "仪表盘反馈统计" 1

ALERTS=$(curl -s "$BASE/ops/alerts?status=ACTIVE" -H "$AUTH")
echo "$ALERTS" | grep -q '"severity"' && check "活跃告警列表结构正确" 0 || check "活跃告警列表" 1
echo "$ALERTS" | grep -q "order-service\|payment-service\|api-gateway" \
  && check "活跃告警含演示服务" 0 || check "活跃告警含演示服务" 1
ALERT_ID=$(echo "$ALERTS" | grep -o '"id":"[0-9]*"' | head -1 | cut -d'"' -f4)

DIAG=$(curl -s -X POST "$BASE/ops/alerts/$ALERT_ID/diagnose" -H "$AUTH")
echo "$DIAG" | grep -q "sessionId" && check "一键 AI 诊断创建会话" 0 || check "一键 AI 诊断" 1

# ---------- 3. 对话流（Agent ReAct 链路） ----------
printf '{"content":"订单服务 CPU 告警，帮我诊断一下"}' > "$TMP/chat.json"
echo "---- SSE 对话流（截取前 10 行）----"
SSE=$(curl -s -N -X POST "$BASE/chat/stream?agentType=ops_diagnosis" -H "$AUTH" \
  -H 'Content-Type: application/json' --data-binary @"$TMP/chat.json" --max-time 40)
echo "$SSE" | head -10
echo "----------------------------------"
echo "$SSE" | grep -q "tool_call" && check "SSE 输出工具调用事件" 0 || check "SSE 输出工具调用事件" 1
echo "$SSE" | grep -q "tool_result" && check "SSE 输出工具结果事件" 0 || check "SSE 输出工具结果事件" 1
echo "$SSE" | grep -q '"done"' && check "SSE 正常收尾（done 事件）" 0 || check "SSE 正常收尾" 1
# 从 SSE start 事件提取本次对话的 sessionId（列表第一条可能是诊断创建的空会话）
SESSION_ID=$(echo "$SSE" | grep -o '"sessionId":"[0-9]*"' | head -1 | cut -d'"' -f4)
[ -n "$SESSION_ID" ] && check "会话列表可查询" 0 || check "会话列表" 1

MESSAGES=$(curl -s "$BASE/chat/sessions/$SESSION_ID/messages" -H "$AUTH")
echo "$MESSAGES" | grep -q '"role":"assistant"' && check "会话消息含助手回复" 0 || check "会话消息" 1
ASSISTANT_MSG_ID=$(echo "$MESSAGES" | tr '{' '\n' | grep '"role":"assistant"' | grep -o '"id":"[0-9]*"' | head -1 | cut -d'"' -f4)

# ---------- 4. v1.1 新功能 ----------
printf '{"feedback":1}' > "$TMP/fb.json"
FB=$(post_json "$BASE/chat/messages/$ASSISTANT_MSG_ID/feedback" "$TMP/fb.json" "$TOKEN")
echo "$FB" | grep -q '"success":true' && check "消息反馈 👍 成功" 0 || check "消息反馈 (msgId=$ASSISTANT_MSG_ID resp: $FB)" 1

HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/chat/sessions/$SESSION_ID/export" -H "$AUTH")
[ "$HTTP_CODE" = "200" ] && check "会话导出 Markdown（HTTP 200）" 0 || check "会话导出 (got $HTTP_CODE)" 1
EXPORT_BODY=$(curl -s "$BASE/chat/sessions/$SESSION_ID/export" -H "$AUTH")
echo "$EXPORT_BODY" | grep -q "会话记录" && check "导出内容包含会话标题与结构" 0 || check "导出内容" 1

TPL=$(curl -s "$BASE/prompt-templates" -H "$AUTH")
echo "$TPL" | grep -q "诊断 CPU 告警" && check "快捷提示词模板列表（种子数据）" 0 || check "快捷提示词模板" 1

printf '{"title":"冒烟测试模板","icon":"🧪","content":"这是一条冒烟测试模板","sort":99}' > "$TMP/tpl.json"
NEW_TPL=$(post_json "$BASE/admin/prompt-templates" "$TMP/tpl.json" "$TOKEN")
NEW_TPL_ID=$(jsonval "$NEW_TPL" id)
[ -n "$NEW_TPL_ID" ] && check "管理员新增模板" 0 || check "管理员新增模板 (got $NEW_TPL)" 1
curl -s -X DELETE "$BASE/admin/prompt-templates/$NEW_TPL_ID" -H "$AUTH" | grep -q '"success":true' \
  && check "管理员删除模板" 0 || check "管理员删除模板" 1

ME=$(curl -s "$BASE/users/me" -H "$AUTH")
echo "$ME" | grep -q "admin" && check "个人中心资料" 0 || check "个人中心资料" 1
printf '{"nickname":"平台管理员"}' > "$TMP/nick.json"
NICK=$(put_json "$BASE/users/me" "$TMP/nick.json" "$TOKEN")
echo "$NICK" | grep -q "平台管理员" && check "修改昵称" 0 || check "修改昵称 (got $NICK)" 1
printf '{"oldPassword":"bad-old","newPassword":"newpass123"}' > "$TMP/pwd.json"
BADPWD=$(put_json "$BASE/users/me/password" "$TMP/pwd.json" "$TOKEN")
echo "$BADPWD" | grep -q "原密码不正确" && check "错误原密码改密被拒" 0 || check "错误原密码改密 (got $BADPWD)" 1

RESOLVE=$(curl -s -X POST "$BASE/ops/alerts/$ALERT_ID/resolve" -H "$AUTH")
echo "$RESOLVE" | grep -q '"status":"RESOLVED"' && check "手动恢复告警" 0 || check "手动恢复告警" 1
RE_RESOLVE=$(curl -s -X POST "$BASE/ops/alerts/$ALERT_ID/resolve" -H "$AUTH")
echo "$RE_RESOLVE" | grep -q "已恢复" && check "重复恢复被拒绝" 0 || check "重复恢复 (got $RE_RESOLVE)" 1

# ---------- 5. 知识库 ----------
SPACES=$(curl -s "$BASE/kb/spaces" -H "$AUTH")
echo "$SPACES" | grep -q "运维手册库" && check "知识库种子空间存在" 0 || check "知识库种子空间" 1

printf '{"query":"磁盘空间不足如何清理","topK":3}' > "$TMP/search.json"
SEARCH=$(post_json "$BASE/kb/search" "$TMP/search.json" "$TOKEN")
echo "$SEARCH" | grep -q "磁盘" && check "知识库混合检索命中磁盘手册" 0 || check "知识库检索 (got ${SEARCH:0:120})" 1

# ---------- 6. 报告与管理端 ----------
REPORTS=$(curl -s "$BASE/reports" -H "$AUTH")
echo "$REPORTS" | grep -q "total" && check "报告中心接口" 0 || check "报告中心接口" 1

MC=$(curl -s "$BASE/admin/model-config" -H "$AUTH")
echo "$MC" | grep -q "provider" && check "模型配置读取" 0 || check "模型配置读取" 1
MTEST=$(curl -s -X POST "$BASE/admin/model-config/test" -H "$AUTH")
echo "$MTEST" | grep -q '"ok":true' && check "模型连通性测试" 0 || check "模型连通性测试 ($MTEST)" 1

echo "=============================="
echo " 结果: PASS=$PASS FAIL=$FAIL"
echo "=============================="
exit $([ "$FAIL" -eq 0 ] && echo 0 || echo 1)
