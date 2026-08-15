@echo off
chcp 65001 >nul
title RAG 冒烟测试
cd /d "C:\Users\陈学堃\WorkBuddy\求职\项目\rag-knowledge-qa"

setlocal EnableDelayedExpansion

set BASE=http://localhost:8080
set SAMPLE_DOC=docs\sample-faq.txt

echo ========================================
echo   RAG 知识库问答系统 - 接口冒烟测试
echo ========================================
echo.

REM 0. 健康检查
echo [0] 健康检查...
curl -s -f --max-time 5 "%BASE%/api/kb/list" >nul 2>&1
if !errorlevel! equ 0 (
    echo     ^u2713 服务在线
) else (
    echo     ^u2717 服务未启动，请先运行 start.bat
    pause
    exit /b 1
)

echo.
echo [1] 创建知识库...
curl -s -X POST "%BASE%/api/kb" -H "Content-Type: application/json" -d "{\"name\":\"技术规范FAQ\",\"description\":\"云启科技后端技术规范 + RAG调参指南\"}" > resp.json 2>nul
type resp.json
echo.

set /p KBID=<resp.json >nul 2>&1
REM 简单提取 kbId
for /f "tokens=2 delims=:" %%a in ('findstr "id" resp.json') do (
    set KBID=%%a
    set KBID=!KBID:~0,-1!
    set KBID=!KBID: =!
    goto :gotkb
)
:gotkb

echo.
echo [2] 上传文档到知识库 !KBID!...
if exist "%SAMPLE_DOC%" (
    curl -s -X POST "%BASE%/api/kb/!KBID!/upload" -F "file=@%SAMPLE_DOC%" > resp.json 2>nul
    type resp.json
    echo.
) else (
    echo     跳过：未找到 %SAMPLE_DOC%
)

echo.
echo [3] 等待向量化完成...
timeout /t 2 /nobreak >nul
echo     完成
echo.

echo [4] 发起流式问答（SSE）...
echo     提问：项目里缓存击穿怎么处理？
echo.
curl -s -N --max-time 20 -X POST "%BASE%/api/chat?kbId=!KBID!" -H "Content-Type: application/json" -d "{\"question\":\"项目里缓存击穿怎么处理？\"}" > sse_resp.txt 2>nul
type sse_resp.txt
echo.

echo.
echo [5] 查询会话列表...
curl -s "%BASE%/api/chat/messages?sessionId=1" > resp.json 2>nul
type resp.json
echo.

echo.
echo ========================================
echo   ^u2713 冒烟测试完成
echo ========================================
echo.
echo 查看所有知识库: curl %BASE%/api/kb/list
echo 查看所有文档:   curl %BASE%/api/kb/!KBID!/documents
echo.

if exist resp.json del resp.json >nul 2>&1
if exist sse_resp.txt del sse_resp.txt >nul 2>&1

pause
