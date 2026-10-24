# JIN V2 部署：Data Summary 按班级和报名计划打印名单

- 源码：`main_v2` 提交 `ea4c680`。
- 发布目录：`/home/class-deduction-system/releases/v2-20261024-ea4c680-data-summary-roster`。
- 部署范围：前端和后端；无数据库结构或业务数据修改。
- 前一版本：`v2-20261024-44ac34c-original-receipt`。

## 功能

- Print class roster 先选班级，再选该班级的 registration plan。
- 打印名单按所选班级和计划汇总，不受当前表格月份及列筛选影响。
- 学生姓名和学校并排排版，按名单数量压缩布局；打印名单控制在单页内。
- Data Summary API 返回报名计划 ID 和名称，供计划筛选及打印标题使用。

## 构建与验证

- 在 `ea4c680` 的干净 worktree 执行 `npm ci && npm run build` 成功。
- `data-summary-roster-print.spec.ts` 浏览器回归测试通过，验证班级与计划联动、名单范围及 PDF 单页。
- 后端 `mvn -Dmaven.test.skip=true package` 成功。
- 前端 `index.html` SHA-256：`e0e9ba86806f10672954c59677fa3b8ffe1457061f9ebe8fae11051822afbf31`。
- Data Summary JS SHA-256：`6b57072a75aa20b99df9202c3b3bf072a1737b898a28cbe255a3145b48ebfc5f`。
- 后端 JAR SHA-256：`e6f85dd8996afe20a1ef86f275c8d3067fe65e6d2ec6a96b18d59a771525ccea`。
- 部署后前后端均指向本发布目录；Nginx 与 `class-deduction.service` 为 active，公网健康接口返回 `UP`。公网首页和 Data Summary JS 哈希与构建产物一致。

## 回滚

- 前端：将 `/home/class-deduction-system/front/dist` 指回 `/home/class-deduction-system/releases/v2-20261024-44ac34c-original-receipt/front/dist`，执行 `nginx -t && systemctl reload nginx`。
- 后端：将 `/etc/systemd/system/class-deduction.service.d/v2.conf` 恢复为本发布目录下的 `backend/v2.conf.before`，恢复旧 `DEPLOY_VERSION`，执行 `systemctl daemon-reload && systemctl restart class-deduction.service`。
