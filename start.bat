@echo off
chcp 65001 >nul
title RAG 知识库问答系统
cd /d "C:\Users\陈学堃\WorkBuddy\求职\项目\rag-knowledge-qa"

echo ========================================
echo  RAG 知识库问答系统 - 启动
echo ========================================
echo.

set DASHSCOPE_API_KEY=sk-ws-H.EEXDIRL.tDQj.MEUCIFQX-wiflRVj-zH3PbM6lzMMPEX1cxDbxT0AzJf3HCT3AiEAvXTaOYvn1EYi66eg8n7-c1UvoTAuN63NWjcB77UmrII
set PG_JDBC_URL=jdbc:postgresql://ep-orange-fire-ax91j2ot-pooler.c-4.us-east-2.aws.neon.tech/neondb?sslmode=require
set PG_USERNAME=neondb_owner
set PG_PASSWORD=npg_PD5Eay4KFuUZ
set REDIS_URL=rediss://default:gQAAAAAAAezaAAIgcDJjZjk2MWY5YzJiNDI0YWNhOTdjZDhlZjYwNjMzZjVlNA@light-oriole-126170.upstash.io:6379

echo [1/2] 环境变量已加载（Neon PG + Upstash Redis + DashScope）
echo [2/2] 正在启动服务...
echo.
echo 启动成功标志：看到 "Started RagApplication" 即可
echo 访问地址：http://localhost:8080/api/kb/list
echo.
echo 不要关闭此窗口，服务才能保持运行！
echo 按 Ctrl+C 停止服务
echo ========================================
echo.

"D:\java\jdk-25.0.4+7\bin\java.exe" -jar target\rag-knowledge-qa-1.0.0.jar

pause
