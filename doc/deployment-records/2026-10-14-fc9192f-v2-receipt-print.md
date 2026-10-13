# JIN V2 部署：修复收据打印预览空白

- 部署对象：Class Deduction V2 前端静态资源；后端和数据库未变更。
- 前端代码版本：`main_v2` 提交 `fc9192fc48a3d3dac701754c5283b19d776d5dca`。
- 部署日期：2026-10-14（Asia/Shanghai）。
- 构建方式：从该提交的干净 worktree 执行 `npm ci && npm run build`；构建成功，共 87 个静态文件。
- 线上发布目录：`/home/class-deduction-system/releases/v2-20261014-fc9192f`。
- 线上旧前端备份：`/home/class-deduction-system/front/dist.bak_fc9192f_20261013T171710Z`。
- 前端资产 SHA-256：
  - `assets/RegistrationIntakeView-BD42m-ce.js`: `030a1b1e0e52492d8f3664942d793a1de9925e706dc4eae963971ec7eeefc473`
  - `assets/RegistrationIntakeView-D6DCsGXn.css`: `1b87614772dde24b921f5b3b208cd30c03ad89d2e68b7c9649b1799903c9dace`
  - `assets/index-CVZ3Hhn5.css`: `478798e255350e948a35ee461a873558d7799a082098afabb9c8109d93572ed7`

## 变更

- 屏幕窄视口布局规则仅应用于 `screen`，避免打印纸张宽度触发屏幕滚动布局。
- 打印样式恢复收据的正常文档流，并清除页面滚动容器的裁切。

## 生产验证

- Nginx 配置测试通过并成功 reload。
- `https://class.jinbesttutoring.com/` 返回 HTTP 200。
- `https://class.jinbesttutoring.com/api/actuator/health` 返回 `{"status":"UP"}`。
- 线上收据组件 JS/CSS 的 SHA-256 与干净构建产物一致。
- 未重启后端服务；未执行数据库 DDL、迁移或数据修改。

## 回滚

将当前 `/home/class-deduction-system/front/dist` 移开，并将上述 `dist.bak_fc9192f_20261013T171710Z` 恢复为 `/home/class-deduction-system/front/dist`，然后执行 `nginx -t && systemctl reload nginx`。
