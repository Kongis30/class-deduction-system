# Checklist

- [x] Data Summary 页面只展示一张 Excel 式宽表。
- [x] 页面不展示页签、标题说明、摘要卡片、独立搜索区、辅助摘要、详情抽屉、付款弹窗或操作列。
- [x] 宽表依次展示 Class、Student Name、School、Grade、Sign Up Date、Phone Number、Phone Number #2、Pick up、Drop off、Pay Method、Period、Tuition、Pickup/Dropoff、Discount、Paid、Balance。
- [x] 每条报名课程明细独占一行，字段与现有 `/v1/data-summary` 数据口径一致。
- [x] 数据按 Class 稳定分组，使用浅蓝数据区和清晰的分组边界。
- [x] 表格采用紧凑深色表头、清晰网格线和单行单元格，整体视觉接近参考 Excel。
- [x] 宽表支持横向滚动，纵向滚动时表头保持可见。
- [x] Student Name、School、Phone Number、Phone Number #2 支持表头关键词筛选。
- [x] Class、Grade、Pick up、Drop off、Pay Method 支持表头选项筛选。
- [x] Sign Up Date、Period 支持日期范围筛选。
- [x] Tuition、Pickup/Dropoff、Discount、Paid、Balance 支持金额范围筛选。
- [x] 多列筛选按 AND 关系组合，激活表头有明确状态。
- [x] 用户可清除单列筛选及全部筛选；无匹配数据时显示可恢复的空状态。
- [x] Pick up、Drop off 和 Pay Method 使用紧凑、易识别的状态样式。
- [x] 金额字段格式统一并右对齐，欠费 Balance 使用红色突出。
- [x] 本次变更未新增 Excel 文件导出、表格内编辑、数据库字段或后端统计口径。
- [x] 前端类型检查、构建和 Data Summary 相关 E2E 验收通过。
