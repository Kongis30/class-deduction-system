# JIN V2 部署：区分 1 对 1 课包与老师课时

- 代码来源：`main_v2` 提交 `dd40105`；部署后补充此记录。
- 部署时间：2026-10-16 10:58 Asia/Shanghai（2026-10-16 02:58 UTC）。
- 部署范围：仅 Class Deduction V2 前端静态资源；后端、数据库结构和业务数据未变更。
- 构建：前端 `npm run build` 成功，共 88 个文件。构建提示现有大 chunk 体积警告。
- 页面调整：1-on-1 Lessons 页眉左侧显示课包 Total、Starting、Remaining Hours；竖线右侧显示所选老师的 Allocated、Deducted、Pending deduction。窄屏改为上下分组。
- 口径文档：`docs/one-on-one-hours-business-rules.md`。
- 线上发布目录：`/home/class-deduction-system/releases/v2-20261016-dd40105/front/dist`。
- 前端回滚备份：`/home/class-deduction-system/front/dist.bak_dd40105_20261016T025852Z`。
- 线上后端版本未变，`DEPLOY_VERSION` 仍为 `a3ca763`。
- 线上 `index.html` SHA-256：`8ed9c1b66604080bb7b41f421090e7420680589ac7a96e8e4aa263a84bcff7eb`。
- 关键 JS `assets/OneOnOneDeductionView-A-1wykdP.js` SHA-256：`f3da07c40a1c7bfd2609e09625b8c923d0c0897354f0c5eed51196cad1e9b038`。
- 关键 CSS `assets/OneOnOneDeductionView-DQe3y-z3.css` SHA-256：`1c0d49b0056cef28582fcadc73aa1bebc890f98ce8489d85df77740760c137b7`。

## 生产验证

- Nginx 配置检查通过；Nginx 与 `class-deduction.service` 均为 active。
- `https://class.jinbesttutoring.com/` 返回 HTTP 200；`/api/actuator/health` 返回 `{"status":"UP"}`。
- 线上关键 JS/CSS 哈希与本地构建一致；JS 包含 `Remaining Hours` 文案。
- 线上前端目录共 88 个文件；回滚备份存在。

## 回滚

将当前 `/home/class-deduction-system/front/dist` 移走，再将 `/home/class-deduction-system/front/dist.bak_dd40105_20261016T025852Z` 恢复为 `dist`，然后执行 `nginx -t && systemctl reload nginx`。
