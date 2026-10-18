# JIN V2：隐藏排课作废课次

- 前端来源：`main_v2` 提交 `61f211b`。
- 部署目录：`/home/class-deduction-system/releases/v2-20261018-61f211b-voided-lessons/front/dist`。
- 仅更新前端静态资源；后端沿用 `d2b0adf`，未重启后端或更改生产数据。
- Lesson Records 隐藏排课调整时作废、且未经管理员最终确认的旧计划课次。记录总数、待老师和待管理员数量、桌面及移动列表统一按可见课次计算；仅含作废课次的空周期分隔线也不再显示。管理员已确认的消课／收费取消仍显示。
- 构建：`npm run build` 成功（Vue 类型检查及 Vite 生产构建）；构建产物包含 `OneOnOneDeductionView-D2j9SJKO.js`。
- `index.html` SHA-256：`2088bbb03c67e0062643884b4124a98d8e95ef57d85b7b6243e91793ed8b7bb7`。
- 1-on-1 页面 JS SHA-256：`60c8c2ca34186d1c19574fb563bd07ec3c7ae6a6058324da09d0961ccf2fab14`；公网资源返回 HTTP 200，线上与本地产物哈希一致。
- Nginx 配置检查通过并已 reload。旧静态目录保存在 `/home/class-deduction-system/front/dist.before_61f211b`。Nginx 和后端服务均为 active，后端健康检查为 `UP`。

## 回滚

把 `/home/class-deduction-system/front/dist` 删除后恢复 `/home/class-deduction-system/front/dist.before_61f211b`，再执行 `nginx -t && systemctl reload nginx`。
