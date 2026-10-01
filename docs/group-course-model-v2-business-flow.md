# 班课模型 V2：现有链路与目标规则

## 现有链路

1. 管理员在 Products 创建 `GROUP` 产品；Prices 的 grade 配置同时承载年级、学科项目、季节和收费档位等含义。
2. 新建价格选项时，`CoursePriceOptionServiceImpl` 按产品、gradeBand、actualClass 自动建立或绑定 `class_group`。秋季、春季、全年因 gradeBand 不同而生成不同的实际班和周课表。
3. Schedule 在 `class_group` 上维护排课、教师、教室和假期。班级排课再生成 `lesson_occurrence`，确认后为每个学生写 `lesson_record`。
4. Registration 读取 `course_price_option`，按产品、gradeBand、校区验证 `class_group`，创建 `enrollment_order_item`、`class_enrollment`、支付分摊和 receipt。续费目前可能累加并覆盖既有 `class_enrollment` 的订单项目归属。
5. Enrolments、接送、教师/教室日历和 Data Summary 从订单项目、报名权益、班级及课次拼装；退款和删课依赖订单项目到班课报名的关联。

## V2 业务对象与不变量

- `course_product` 表示售卖的课程产品；`group_course_project` 表示产品内的教学项目；`class_group` 表示某项目、校区下唯一的实际上课班级；其 slots 和 occurrences 是该班级唯一的物理课表。
- `group_registration_plan` 表示学生购买范围与价格：固定日期/季节、月数、课次数。季节或全年只是报名权益，不创建额外的 class 或 schedule。
- `enrollment_order_item` 是财务事实；每个班课订单项目为每个学生创建独立 `class_enrollment` 权益。续费新增权益，不覆盖旧权益。`lesson_record.class_enrollment_id` 指向消耗的那份权益，同一学生同一 occurrence 只能确认一次。
- 已配置的 occurrences 是报名和收费的日期来源。项目假期在生成课次时跳过；报名校验所选方案覆盖范围及可用上课日。可用课次不足时阻止报名/保存，不静默少排或改价。
- 同一项目中，新的权益不得与学生已有权益在上课日期和出勤时段上重叠；历史冲突在迁移审查报告中标出，不自动挑选或删除。
- 全年权益可显示在秋季和春季名单中；支付、退款和 Data Summary 金额仍按原订单项目计一次。
- 1-on-1/2/3 继续使用现有 private-package 与 private-lesson 流程。

## DBv2 初始化与隔离

本地备份 `after-school-campus-clone-before-20260929T115053Z.sql.gz` 的 SHA-256 与同目录 manifest 一致；解压导入时只将 SQL 中的目标数据库名替换为 `class_deduction_system_v2`。原始压缩备份保持不变。DBv2 运行账号仅对新库授权，`group-course-v2` profile 在 Spring 启动早期校验 datasource 指向该库。

正式 V2 数据迁移需生成确定性映射报告：项目、报名方案、旧 class 到唯一新 class、订单项目及报名权益。只有唯一且可验证的映射才自动迁移；缺校区、项目分类冲突、重叠权益、未来已确认课次/教师教室不一致等情况列为阻断项。旧课次与 receipts 作为历史审计保留，不直接删除。

本地迁移顺序为：`20261001_group_course_model_v2.sql` → `20261001_02_project_scoped_holidays.sql` → `20261001_03_group_course_migration_audit.sql` → `20261001_04_migrate_group_course_data.sql` → `20261001_05_retire_legacy_group_price_options.sql` → `20261001_06_repair_group_course_history_links.sql`。每个 SQL 迁移都检查目标库名和连接来源必须为本机。首个 schema 文件是一次性 DBv2 初始化脚本，不支持重放；若其部分执行后中断，应从迁移前 DBv2 备份恢复后重跑。第 04 步是基于已验证备份的一次性数据映射，不能重复执行；它会将旧课次按班级、学生和日期绑定到唯一报名权益，并把旧的产品/年级及无产品假期迁移到教学项目。匹配不唯一时会中止，不猜测归属。第 05 步先检查所有未删除班课订单和在用班级都有 V2 关联，再停用旧的班课价格选项，并保存其原状态审计记录。第 06 步用于修复已完成模型迁移的本地 DBv2 中仍未关联的历史课次和旧假期；可重复执行，原课次假期外键保存在 `group_course_model_holiday_occurrence_audit` 中以支持回滚。
