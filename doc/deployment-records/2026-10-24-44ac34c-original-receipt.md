# JIN V2 部署：Edit Receipt 保留原订单和原收据

- 源码：`main_v2` 提交 `44ac34c`。
- 发布目录：`/home/class-deduction-system/releases/v2-20261024-44ac34c-original-receipt`。
- 范围：前端、后端；无数据库迁移或线上数据修改。
- 原后端：`v2-20261024-4d1bb4d-same-order-renewal`；原前端：`v2-20261024-84a6af5-cross-order-receipt`。

## 业务结果

- Data Summary 按订单与逻辑课程合并购买行。同一订单续报原课程保留一行；加报不同课程新增一行，两行的 View receipt 均打开原订单收据。独立订单不互相合并，也不串联 Period。
- 多笔购买属于同一课程时，退款窗口只列出该课程的购买行，须明确选中一行；其他课程和整张收据退款选项不混入该入口。
- 后端 Period 按本次订单中的有效权益计算，不借用其他订单的期限。
- 核对 Yuqi Chen 历史数据时，先前额外订单及其收据、课程行已是软删除状态；有效报名仍在原订单，故本次不执行数据修复。

## 验证

- 干净源码快照：前端生产构建成功；Data Summary 收据和退款浏览器用例 1/1 通过；原收据、Add payment、Add course 浏览器用例 3/3 通过。
- Data Summary 后端定向测试 34/34 通过，后端生产 JAR 构建成功。全量测试源码仍有其他测试类的既有编译错误，故定向测试使用临时 Maven 配置隔离编译；临时配置未提交。
- 后端 JAR SHA-256：`5122528ddb0a8d9bc44ec7fb0c7c7ec39e4bdd5f814fe16545c7ec121574cbbf`。
- 前端 `index.html` SHA-256：`9a40b55cad14ee0c1173627682fd462eb566b990d097dd613f1e8b8ab9eddd2c`。
- Data Summary JS SHA-256：`6753dc933a3f44f76dbf2c32a21a049f09380d4104ff716d1ca0c15060e42e19`。
- 线上前后端均指向本发布目录；Nginx 和 `class-deduction.service` 为 active；公网健康接口返回 `UP`；首页与 Data Summary JS 的公网 SHA-256 与构建产物一致；部署后 5 分钟错误级服务日志无记录。

## 回滚

发布目录的 `backend/v2.conf.before`、`backend/current.before`、`backend/DEPLOY_VERSION.before`、`front/dist.before` 保存了旧配置和指向。恢复 systemd 配置及后端 `current`、`DEPLOY_VERSION`，重启 `class-deduction.service`；再把 `front/dist` 指回旧目录，执行 `nginx -t && systemctl reload nginx`。
