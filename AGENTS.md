# AGENTS.md

给任何接手本项目的 AI 编码代理（ZCode / Claude Code / Cursor 等）的最低限度守则。完整的项目地图、设计哲学与验证工作流见 [README「给 AI Agent 的使用指南」](README.md#给-ai-agent-的使用指南)，两者冲突时以 README 为准。

## 硬性约束

1. **绝不把密钥写进任何被 git 跟踪的文件。** Key 只允许出现在 `local.properties`（`deepseek.apiKey=...`）或环境变量 `DEEPSEEK_API_KEY`。提交前运行：`grep -rl "sk-" --exclude-dir=build . | grep -v local.properties`，必须无输出。
2. **`verification/` 永不提交。** 里面是用户真机验证证据（截图、数据库备份、个人阅读内容），只留在本机。
3. **不新增第三方依赖**，除非已与用户确认且无平台 API 可替代。
4. **不引入统计/上报 SDK**，不申请多余权限。

## 改动后必做

```bash
./gradlew :app:assembleDebug :app:lintDebug   # 0 错误底线，Lint 警告不新增
adb install -r app/build/outputs/apk/debug/app-debug.apk   # 上模拟器/真机跑通主链路再交付
```

## 性能与设计红线（详见 README）

- 字号预计算、列表保留组合树、统计网格用单 Canvas；交互路径无逐帧分配。
- 能力缺失走静默降级（隐藏入口/明确提示），不崩溃。
- 新 UI 先取 `Theme.kt` 的「暖纸词典」色板，保持极度简洁，中文文案。

## 已知坑

- 给设备灌种子 `zee.db`：必须 `PRAGMA user_version = 1` 且删除 `-journal`/`-wal`，否则启动即崩。
- adb 传含空格的 `EXTRA_PROCESS_TEXT`：外层引号包整条 `am start` 命令。
- TTS：进程级单例 + `onResume` 预热（`Speaker.warmup`），speak 前必须确认 `Speaker.ready`，一切调用包 try/catch。
