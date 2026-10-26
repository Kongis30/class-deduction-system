# JIN V2 部署：恢复 Data Summary 周六班季节筛选

- 源码：`main_v2` 提交 `c813c3e`。
- 部署时间：2026-10-26（Asia/Shanghai）。
- 发布目录：`/home/class-deduction-system/releases/v2-20261026-c813c3e-saturday-season-filters`。
- 部署范围：仅前端静态资源；未重启后端，未修改数据库。
- 部署前前端：`v2-20261026-e9b9fbb-multi-period-schedule`。

## 调整

- 恢复 Data Summary 周六班 Fall、Spring、All 筛选；移动端更多菜单也可切换。
- Fall 和 Spring 包含全年班；无法识别季节的历史记录只出现在 All。
- 筛选作用于正常、退款和停课记录；班级名单打印范围不随季节筛选改变。

## 验证

- `node --test frontend/tests/saturday-roster.test.mjs`：4/4 通过。
- `npx playwright test e2e/acceptance/saturday-roster-filter.spec.ts`：桌面和移动端 2/2 通过。
- `npm run build`：成功。
- `nginx -t` 通过，Nginx reload 成功。
- 公网首页及 Data Summary JS SHA-256 与本地构建产物一致：
  - `index.html`: `d32431480b4cc031488d52bdbbeaedbb7cb1e0948c7404d6de0be9cd6f5442c6`
  - `DataSummaryView-BzFiIV1a.js`: `24daf1417da6883eb873643b33d39f6ae98695137f002335fc91c4cf43e3f35b`

## 回滚

将 `/home/class-deduction-system/front/dist` 原子切回 `/home/class-deduction-system/releases/v2-20261026-e9b9fbb-multi-period-schedule/front/dist`，然后执行 `nginx -t && systemctl reload nginx`。
