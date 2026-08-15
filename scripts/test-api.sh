#!/usr/bin/env bash
# RAG 知识库问答系统 - 接口冒烟测试脚本
# 用法：在项目根目录执行 bash scripts/test-api.sh
# 前置：项目已启动（mvn spring-boot:run）+ 已配置 DASHSCOPE_API_KEY

set -euo pipefail

BASE=${BASE:-http://localhost:8080}
SAMPLE_DOC="docs/sample-faq.txt"

echo "=========================================="
echo "  RAG 知识库问答系统 - 接口冒烟测试"
echo "=========================================="

# 0. 健康检查
echo ""
echo "[0] 健康检查..."
if curl -sf --max-time 5 "${BASE}/api/kb/list" >/dev/null 2>&1; then
    echo "    ✅ 服务在线"
else
    echo "    ❌ 服务未启动，请先跑 mvn spring-boot:run"
    exit 1
fi

# 1. 创建知识库
echo ""
echo "[1] 创建知识库..."
KB=$(curl -sf -X POST "${BASE}/api/kb" \
    -H "Content-Type: application/json" \
    -d '{"name":"技术规范FAQ","description":"云启科技后端技术规范 + RAG调参指南"}')
echo "    返回: ${KB}"
KB_ID=$(echo "${KB}" | grep -oE '"id":[0-9]+' | head -1 | grep -oE '[0-9]+')
echo "    ✅ 知识库 ID = ${KB_ID}"

# 2. 上传样例文档
echo ""
echo "[2] 上传样例文档 ${SAMPLE_DOC}..."
DOC=$(curl -sf -X POST "${BASE}/api/kb/${KB_ID}/document" \
    -F "file=@${SAMPLE_DOC}")
echo "    返回: ${DOC}"
DOC_ID=$(echo "${DOC}" | grep -oE '"id":[0-9]+' | head -1 | grep -oE '[0-9]+')

# 3. 轮询文档处理状态（向量化是异步的，等它变成 DONE）
echo ""
echo "[3] 等待文档向量化完成（最多 30s）..."
for i in $(seq 1 15); do
    STATUS=$(curl -sf "${BASE}/api/kb/${KB_ID}/documents" \
        | grep -oE '"status":"[A-Z]+"' | head -1 | grep -oE '[A-Z]+')
    echo "    [${i}/15] 文档状态 = ${STATUS}"
    if [ "${STATUS}" = "DONE" ]; then
        echo "    ✅ 向量化完成"
        break
    fi
    if [ "${STATUS}" = "FAILED" ]; then
        echo "    ❌ 向量化失败，检查 API Key 和网络"
        exit 1
    fi
    sleep 2
done

if [ "${STATUS}" != "DONE" ]; then
    echo "    ⚠️  超时未完成，但仍继续尝试问答（可能慢一点）"
fi

# 4. SSE 流式问答
echo ""
echo "[4] 发起 SSE 流式问答..."
echo "    问题: 项目里缓存击穿怎么处理？"
echo "    ---"
curl -N -X POST "${BASE}/api/chat?kbId=${KB_ID}" \
    -H "Content-Type: application/json" \
    -d '{"question":"项目里缓存击穿怎么处理？"}' \
    --max-time 60 2>/dev/null
echo ""
echo "    ---"

# 5. 查看会话列表
echo ""
echo "[5] 知识库 ${KB_ID} 的会话列表..."
curl -sf "${BASE}/api/chat/sessions?kbId=${KB_ID}" | head -c 500
echo ""

# 6. 查看消息历史
SESSION_ID=$(curl -sf "${BASE}/api/chat/sessions?kbId=${KB_ID}" \
    | grep -oE '"id":[0-9]+' | head -1 | grep -oE '[0-9]+')
echo ""
echo "[6] 会话 ${SESSION_ID} 的消息历史..."
curl -sf "${BASE}/api/chat/messages?sessionId=${SESSION_ID}" | head -c 800
echo ""

echo ""
echo "=========================================="
echo "  ✅ 冒烟测试完成"
echo "=========================================="
echo ""
echo "查看所有知识库: curl ${BASE}/api/kb/list"
echo "查看所有文档:   curl ${BASE}/api/kb/${KB_ID}/documents"
