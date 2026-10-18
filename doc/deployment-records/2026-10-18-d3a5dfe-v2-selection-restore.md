# JIN V2：消课后保留当前老师选中项

- 前端来源：`main_v2` 提交 `d3a5dfe`。
- 发布目录：`/home/class-deduction-system/releases/v2-20261018-d3a5dfe-selection-restore/front/dist`。
- 仅更新前端静态资源；后端服务保持原版本。
- 同一课包出现在多位老师分组时，消课或调整后刷新按老师、学生、课包共同恢复选中项；左侧选中高亮使用相同身份。原老师分组消失时清空选中项。
- 前端 `npm run build` 成功；针对两位老师共享同一课包的浏览器回归测试通过。
- `index.html` SHA-256：`6fa8406660579ae06b9b9d15345cc1792a60c4315c6fbdd937293b280c0ecd20`。
- 1-on-1 页面 JS SHA-256：`2c3034ce5e543ac2f7a9e9eb0485015d9eba10865a4fe400e9ed66b8910fd606`。
- 发布目录和公网 HTTPS 返回资源的哈希均与本地构建一致；`nginx -t` 通过并已 reload，公网 `/api/actuator/health` 返回 `UP`。

## 回滚

将 `/home/class-deduction-system/front/dist` 符号链接切回保留的上一版 `/home/class-deduction-system/releases/v2-20261018-61f211b-voided-lessons/front/dist`，然后执行 `nginx -t` 并 reload Nginx。
