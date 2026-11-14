# JIN V2 部署：Monthly Revenue Breakdown 报表

- 源码提交：`main_v2` `a70f213`（`对齐月度收入与 Data Summary 口径：排除已删除订单`），功能提交 `2f4a51d`。
- 部署日期：2026-11-14（Asia/Shanghai）。
- 发布目录：`/home/class-deduction-system/releases/v2-20261114-a70f213-monthly-revenue`。
- 部署范围：后端 JAR 与前端静态资源；**未修改数据库结构或业务数据**。

## 功能内容

- 新增 `GET /api/v1/reports/monthly-revenue?fromMonth=yyyy-MM&toMonth=yyyy-MM`（ADMIN/STAFF；MANAGER 返回 FORBIDDEN）。
- 按「月份 → 收据校区 → 课程产品」汇总实收净额，产品级粒度；退款按发生月份冲减。
- 前端新增菜单 `Monthly Revenue Breakdown`（`Daily Registrations` 下方）与页面 `/monthly-revenue`，表头 `yy-MMM`，支持月份区间与 Excel 导出。

## 收入口径（已与 Data Summary 对齐）

- 数据源为 `payment_record` 实际流水，排除 `VOIDED` 与软删流水。
- **已软删除的报名订单整单排除**，与 Data Summary `totalIncomeAmount` 一致。
- Student Credit 与其它支付方式同等处理：用 Credit 支付计入实收，退款转 Credit 相应冲减，无特殊逻辑。
- 历史无分摊流水按订单课程价格权重回退分摊；课程项缺失时归入 `Unassigned product`，保证列合计等于流水。

## 构建与验证

- 后端 JAR SHA-256：`e333c69937e54efc0f954f4612b186cd36173f357d231a5b4ea27bc9ee1781d7`。
- 前端 `index.html` SHA-256：`0053dc63ea628442d5649bf5c074191ca423bbc635b94a9cdce70c9a270c23be`。
- 上传后服务器产物哈希与本地完全一致。
- 单元测试 `MonthlyRevenueServiceImplTest` 4/4 通过；后端 `mvn package` 与前端 `vite build` 通过。
- 线上验证：`https://class.jinbesttutoring.com/` 返回 200，`/api/health` 返回 `UP`，`class-deduction.service` active。
- **口径一致性验证**：全时段（2025-01..2026-12）Monthly Revenue 合计 `947,094.00`，Data Summary `totalIncomeAmount` 与逐行 `paidAmount` 合计均为 `947,094.00`，完全一致。
- 权限验证：MANAGER 调用报表接口返回 `FORBIDDEN`。

## 回滚

- 后端上一运行版本：`/home/class-deduction-system/releases/v2-20261114-df804fa/backend/backend.jar`。
- 前端上一目录：`/home/class-deduction-system/front/dist.previous-a70f213`（指向 `v2-20261114-df804fa/front/dist`）。
- 回滚操作：将 `/etc/systemd/system/class-deduction.service.d/v2.conf` 的 `ExecStart` JAR 路径改回上一版本（原文件已备份为 `/root/v2.conf.bak-20261114_103220`），前端软链接指回上一目录，然后 `systemctl daemon-reload && systemctl restart class-deduction.service && nginx -t && systemctl reload nginx`。
