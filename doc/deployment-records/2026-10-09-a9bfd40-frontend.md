# JIN V2 前端部署：Print roster 校区分组样式

- 部署对象：仅 Class Deduction V2 前端静态文件；未替换后端 JAR、未重启服务、未修改数据库或业务数据。
- 代码来源：`main_v2` 提交 `a9bfd40`。Print class roster 下拉框的校区标题增加深色文字、底色、边框和组间分隔。
- 验证：干净工作树执行 `npm run build` 成功；前端共 87 个文件。线上 `class-deduction.service` 为 active，公网首页 HTTP 200，健康接口返回 `{"status":"UP"}`；首页、DataSummary JS/CSS 与本地产物 SHA-256 一致。
- 产物：前端归档 SHA-256 `ae78f9e28ab3ed7965f6ab671edc548f4f255fdc79bb59a87f6b3043dd3c79b2`。
- 服务器：原子替换 `/home/class-deduction-system/front/dist`；回滚备份保留在 `/home/class-deduction-system/front/dist.bak_a9bfd40_20261009T092659Z`。
