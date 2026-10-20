# JIN V2 部署：调整课次保留课程名称

- 源码分支与提交：`main_v2` `24b31c4d4d7b22225f22d3b7f253e7991332bb35`。
- 部署时间：2026-10-20（Asia/Shanghai）。
- 发布目录：`/home/class-deduction-system/releases/v2-20261020-24b31c4-adjust-subject-name`。
- 部署范围：V2 后端 JAR 与完整前端静态资源；无数据库结构变更或数据修复。
- 修复内容：调整已有课次时，未提供课程名称不再覆盖已有 subjectName；创建新课次时必须取得明确课程名称，优先使用明确输入或唯一可判定的课包科目，无法判定则拒绝保存；重复排课新增课次同步携带并校验课程名称。
- 构建：干净检出 `24b31c4`；前端 `npm ci && npm run build` 成功；后端 Java 24 `mvn -Dmaven.test.skip=true clean package` 成功；未运行测试。
- 后端 JAR SHA-256：`f03e2b3d4a927d16e0309c112798981dc98df4ab205c638f8a24cba3bcada860`。
- 前端 `index.html` SHA-256：`7fea42a4b869f6349df6d191a1029b2c289a0b6e84fa80ece2c654315bc5ca4c`。
- 1-on-1 JS `OneOnOneDeductionView-BZBC3ch-.js` SHA-256：`d40308f6ef75239f130131249e9b07888005a2b288ac346e0b301e4322f483fa`。
- 排课 JS `RecurringScheduleView-BiVqxo6w.js` SHA-256：`9dc9ed328b6b327d4a1e2e1d6bdfe358d9d65b2e3c4ca1dcee3935b4dc0d04d1`。

## 生产验证

- `class-deduction.service` 与 Nginx 均为 active；`nginx -t` 通过。
- 线上首页返回 HTTP 200，公网 `/api/actuator/health` 返回 `{"status":"UP"}`。
- `current`、`front/dist` 与版本标记均指向本次发布。
- 公网首页、1-on-1 JS、排课 JS 的内容哈希与本地产物一致。

## 回滚

- 恢复本发布目录 `backend/v2.conf.before_24b31c4` 和 `backend/DEPLOY_VERSION.before_24b31c4`。
- 按 `current.previous-target`、`front/dist.previous-target` 记录恢复两个链接，然后运行 `systemctl daemon-reload && systemctl restart class-deduction.service && nginx -t && systemctl reload nginx`。
