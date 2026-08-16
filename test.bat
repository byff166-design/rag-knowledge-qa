@echo off
chcp 936 >nul
title RAG smoke test
cd /d "C:\Users\陈学堃\WorkBuddy\求职\项目\rag-knowledge-qa"

setlocal EnableDelayedExpansion

set BASE=http://localhost:8080
set SAMPLE_DOC=docs\sample-faq.txt

echo ========================================
echo   RAG smoke test
echo ========================================
echo.

REM 0. health check
echo [0] health check...
curl -s -f --max-time 5 "%BASE%/api/kb/list" >nul 2>&1
if !errorlevel! equ 0 (
    echo     [OK] service online
) else (
    echo     [FAIL] service not started, run start.bat first
    pause
    exit /b 1
)

echo.
echo [1] create knowledge base...
curl -s -X POST "%BASE%/api/kb" -H "Content-Type: application/json" -d "{\"name\":\"tech-faq\",\"description\":\"backend tech spec + RAG tuning guide\"}" > resp.json 2>nul
type resp.json
echo.

set /p KBID=<resp.json >nul 2>&1
for /f "tokens=2 delims=:" %%a in ('findstr "id" resp.json') do (
    set KBID=%%a
    set KBID=!KBID:~0,-1!
    set KBID=!KBID: =!
    goto :gotkb
)
:gotkb

echo.
echo [2] upload doc to kb !KBID!...
if exist "%SAMPLE_DOC%" (
    curl -s -X POST "%BASE%/api/kb/!KBID!/upload" -F "file=@%SAMPLE_DOC%" > resp.json 2>nul
    type resp.json
    echo.
) else (
    echo     skip: %SAMPLE_DOC% not found
)

echo.
echo [3] wait for embedding...
timeout /t 2 /nobreak >nul
echo     done
echo.

echo.
echo [4] streaming QA (SSE)...
echo     question: how to handle cache penetration?
echo.
curl -s -N --max-time 20 -X POST "%BASE%/api/chat?kbId=!KBID!" -H "Content-Type: application/json" -d "{\"question\":\"how to handle cache penetration?\"}" > sse_resp.txt 2>nul
type sse_resp.txt
echo.

echo.
echo [5] query session history...
curl -s "%BASE%/api/chat/messages?sessionId=1" > resp.json 2>nul
type resp.json
echo.

echo.
echo ========================================
echo   [OK] smoke test done
echo ========================================
echo.
echo list all kb: curl %BASE%/api/kb/list
echo list docs:   curl %BASE%/api/kb/!KBID!/documents
echo.

if exist resp.json del resp.json >nul 2>&1
if exist sse_resp.txt del sse_resp.txt >nul 2>&1

pause
