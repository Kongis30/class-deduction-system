# JIN V2 部署：续课时显示原课程完整信息

- 源码提交：`main_v2` `84d983a`（`Show full original course details during renewal`）。
- 发布目录：`/home/class-deduction-system/releases/v2-20261023-84d983a-full-course-row`。
- 部署范围：仅替换 V2 前端静态资源；后端和数据库未变更。
- 页面变化：Edit Receipt 使用 Add course 续课时，原课程只读行展示教学项目、报名计划、实际班级、首课日期、月份或课次、出勤、权益结束日期及原价；新课程产品选择框保持完整宽度。
- 构建：从干净 worktree 执行 `npm ci && npm run build` 成功，包含 `vue-tsc --noEmit`。
- `index.html` SHA-256：`da6af9866a2ee524af9200671fc5b8edb642c48682b34097fcb1200492351458`。
- Edit Receipt JS SHA-256：`06ce6f4f6eb5f8ae53b3f3c13f33998d4ba6f4067478a2b80103b07ec78468a2`。

## 生产验证

- 线上首页与 Edit Receipt JS 可访问，返回文件哈希与本地构建一致。
- 公网健康接口返回 `{"status":"UP"}`；`class-deduction.service` 和 Nginx 均为 active。

## 回滚

- 前端上一版本：`/home/class-deduction-system/releases/v2-20261023-c732b96-add-course/front/dist`。
- 将 `/home/class-deduction-system/front/dist` 指向上一版本，执行 `nginx -t && systemctl reload nginx`。
