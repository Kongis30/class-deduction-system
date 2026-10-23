# JIN V2 前端部署：续课合并行右键打开收据

- 修复提交：`main_v2` `84a6af5`（`修复 Data Summary 跨订单续课收据入口`）。
- 部署日期：2026-10-24（Asia/Shanghai）。
- 线上基线：前端 `03d54f6`，后端 `4d1bb4d`。
- 部署范围：仅前端静态资源；后端、数据库结构和业务数据均未修改。

## 根因与修复

- Yuqi Chen 的两笔线上购买行属于同一个逻辑课程，但保留不同订单号。Data Summary 将两笔购买合为一行，右键进入“多订单选择”分支。
- 该分支引用 `purchasePickerRows` 和 `purchasePickerVisible`，而已部署组件没有声明它们，也没有渲染选择窗口。右键姓名时浏览器抛出 `ReferenceError: purchasePickerRows is not defined`，所以没有收据入口。
- 本次补齐选择窗口和状态。每笔购买各有 `View receipt` 按钮，点击后进入该笔购买所属的原订单；普通单订单行继续使用原右键菜单。
- 根目录 `tsconfig.json` 只声明项目引用，当前 `vue-tsc --noEmit` 没有实际检查 Vue 源文件，因此先前生产构建未发现未声明变量。针对该数据形态新增浏览器回归用例；完整前端严格类型检查仍有既有错误，需要单独清理后才能设为构建门禁。

## 构建与验证

- 按线上两笔订单共用 `logical_course_id` 的结构复现：修复前普通姓名右键成功，续课合并行右键报 `purchasePickerRows is not defined`；修复后两笔购买的 `View receipt` 均可显示，点击第二笔进入对应订单。
- 从 `84a6af5` 的干净 worktree 执行 `npm ci && npm run build` 成功；新回归用例 1/1 通过。
- 发布目录：`/home/class-deduction-system/releases/v2-20261024-84a6af5-cross-order-receipt/front/dist`。
- 发布包 SHA-256：`948d7cd638252e2897770902e9bd6dd0c09f7d32f8352efc30e461a73bb79b28`。
- 前端 `index.html` SHA-256：`5a52d7656fe07c7edaa2b9e41e3d4da00f4518aec94597eb9fd99bb0db20084d`。
- Data Summary JS SHA-256：`f1f39ba6e64ff217c94f793939092a57578329c10f420c9967ddfa1eb0c3ca8b`。
- 线上首页、Data Summary JS、公网健康接口均返回 HTTP 200，静态资源校验和与本地产物一致；Nginx 和 `class-deduction.service` 均为 active，检查时没有错误级服务日志。

## 回滚

将 `/home/class-deduction-system/front/dist` 指回 `/home/class-deduction-system/releases/v2-20261024-03d54f6-row-context-menu/front/dist`，执行 `nginx -t && systemctl reload nginx`。后端和数据库无需处理。
