# JIN V2 前端部署：修复收据打印按钮无法触发打印

- 部署对象：Class Deduction V2 前端静态文件（nginx `class.jinbesttutoring.com`）；后端与数据库未变更。
- 代码来源：`main_v2` 提交 `20333df`（合并 `codex/receipt-print-readiness`，修复 `4cdc926` 引入的收据打印就绪校验缺陷）。
- 部署时间：2026-10-14 03:24 Asia/Shanghai（2026-10-13 19:24 UTC）。
- 部署方式：仅替换前端 `dist`。

## 问题与修复

- 线上收据页（`/registration?orderId=...`）的 Print 按钮点击后只弹出
  “The saved receipt is still rendering …” 警告，打印预览始终不打开。
- 根因：`printForm()` 的就绪校验使用 `source.innerText.includes(studentName)`。
  只读收据页的学生姓名只渲染在 `.printed-student-name` 节点，而该节点在屏幕上为
  `display:none`（仅 `@media print` 可见），`innerText` 取不到隐藏子树文本；
  备用姓名输入框在只读模式下被禁用，`innerText` 也不包含控件值。
  因此校验恒为失败，函数提前 `return`，`window.print()` 永不执行。
- 修复：
  - 就绪校验改用 `source.textContent`（可覆盖 `display:none` 的打印节点），并做空白归一化。
  - `isolated-print.ts` 等待 webfont 时增加 1.2s 超时，避免字体加载异常时打印预览被无限阻塞。
  - 同步更新收据打印验收测试断言（`innerText` → `textContent`）。

## 构建

- 从 `20333df` 创建干净 detached worktree 执行 `npm ci && npm run build`，构建成功。
- dist 文件数：88。文件集与上一版一致，仅内容哈希变化。

## 产物

- 发布包：`dist-receipt-print-20333df.tar.gz`，SHA-256 `114db8525608ee330ae8540ec92df08bd8b7dbe25f91ce3919466eb5ccc1ed73`。
- 关键资产 SHA-256：
  - `assets/RegistrationIntakeView-Blc60N0e.js`: `5d8d2bc15b1d8dd47176530f5a59d933e17404268cd41e83693ddb0151afadf4`
  - `assets/RegistrationIntakeView-BdfrUkDx.css`: `a3fe9d14c4b5541254d09f6fb851278856ab12ad3344308f27489e04ab6afe99`
  - `assets/index-v2Ej9fHk.js`: `82338996b892b1764f15a0400049e2db9e897019c304bc85814780df58a9ff0a`
  - `assets/index-B_v4aszs.css`: `ed8f46a3485db130a50adf0989e77b94f5686debfd0cb5e98777e11ae50ea9b1`
- 线上发布目录：`/home/class-deduction-system/releases/v2-20261014-20333df/front/dist`。
- 线上旧前端备份：`/home/class-deduction-system/front/dist.bak_20333df_20261013T192554Z`。

## 生产验证

- `nginx -t` 通过；`systemctl reload nginx` 成功；`nginx`、`class-deduction.service` 均 active。
- `https://class.jinbesttutoring.com/` 返回 HTTP 200，`index` 引用新的 `index-v2Ej9fHk.js`。
- `https://class.jinbesttutoring.com/api/actuator/health` 返回 HTTP 200。
- 线上 `assets/RegistrationIntakeView-Blc60N0e.js` 的 SHA-256 与干净构建产物一致；该文件含 `textContent`、不含 `innerText`。
- 旧资产 `assets/RegistrationIntakeView-BV452CVm.js`、`assets/index-DPQ0_elK.js` 均返回 HTTP 404。
- 本地验收用例 `e2e/acceptance/registration-receipt-print.spec.ts` 在 `20333df` 上通过；对照还原旧实现时该用例在 `window.print()` 未触发处失败。

## 回滚

```bash
cd /home/class-deduction-system/front
mv dist dist.failed_20333df_rollback_$(date -u +%Y%m%dT%H%M%SZ)
mv dist.bak_20333df_20261013T192554Z dist
nginx -t && systemctl reload nginx
```
