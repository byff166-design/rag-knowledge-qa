@echo off
chcp 936 >nul
title RAG service start
cd /d "C:\Users\陈学堃\WorkBuddy\求职\项目\rag-knowledge-qa"

set DASHSCOPE_API_KEY=sk-ws-H.EEXDIRL.tDQj.MEUCIFQX-wiflRVj-zH3PbM6lzMMPEX1cxDbxT0AzJf3HCT3AiEAvXTaOYvn1EYi66eg8n7-c1UvoTAuN63NWjcB77UmrII
set PG_JDBC_URL=jdbc:postgresql://ep-orange-fire-ax91j2ot-pooler.c-4.us-east-2.aws.neon.tech/neondb?sslmode=require
set PG_USERNAME=neondb_owner
set PG_PASSWORD=npg_PD5Eay4KFuUZ
set REDIS_URL=rediss://default:gQAAAAAAAezaAAIgcDJjZjk2MWY5YzJiNDI0YWNhOTdjZDhlZjYwNjMzZjVlNA@light-oriole-126170.upstash.io:6379

echo ========================================
echo   RAG service start
echo ========================================
echo.
echo env loaded:
echo   DASHSCOPE_API_KEY = sk-***
echo   PG_JDBC_URL       = jdbc:postgresql://ep-orange-fire-ax91j2ot-pooler.c-4.us-east-2.aws.neon.tech/neondb?sslmode=require
echo   PG_USERNAME       = neondb_owner
echo   REDIS_URL         = rediss://default:***@light-oriole-126170.upstash.io:6379
echo.
echo starting...
echo.

"D:\java\jdk-25.0.4+7\bin\java.exe" -jar target\rag-knowledge-qa-1.0.0.jar

pause
