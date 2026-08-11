# Tasks

- [x] Task 1: 重构 Data Summary 报名检查区的数据表结构
  - [x] SubTask 1.1: 移除现有页签、标题说明、摘要卡片、独立搜索区、辅助摘要、详情抽屉、付款弹窗及其他表格外区域。
  - [x] SubTask 1.2: 按参考 Excel 的列顺序展开 Class、学生资料、报名日期、电话、接送、付款方式、周期和金额字段。
  - [x] SubTask 1.3: 按 Class 对当前结果稳定分组，并为缺少 Class 的记录提供可识别的兜底分组。

- [x] Task 2: 实现表头列级筛选
  - [x] SubTask 2.1: 为文本列实现不区分大小写的关键词包含筛选。
  - [x] SubTask 2.2: 为 Class、Grade、Pick up、Drop off、Pay Method 实现基于当前数据选项的筛选。
  - [x] SubTask 2.3: 为 Sign Up Date、Period 实现日期范围筛选，为金额列实现最小值和最大值筛选。
  - [x] SubTask 2.4: 以 AND 关系组合多列筛选，并实现激活状态、清除单列和清除全部。
  - [x] SubTask 2.5: 为无匹配结果提供表格内空状态和清除筛选入口。

- [x] Task 3: 对齐参考 Excel 的宽表视觉
  - [x] SubTask 3.1: 实现紧凑深色表头、浅蓝数据区、班级分组间隔和清晰网格线。
  - [x] SubTask 3.2: 实现固定表头、横向滚动、单行单元格和必要的截断提示。
  - [x] SubTask 3.3: 为 Pick up、Drop off、Pay Method 提供紧凑状态样式，并统一金额格式和对齐。

- [x] Task 4: 验证实现质量
  - [x] SubTask 4.1: 补充或更新 Data Summary E2E 验收，覆盖主要列、表头筛选、多条件组合、清除筛选和空状态。
  - [x] SubTask 4.2: 验证页面只展示单一表格，且横向滚动、固定表头和班级分组符合参考图目标。
  - [x] SubTask 4.3: 运行前端类型检查、构建和相关 Playwright 用例。

## Task Dependencies

- Task 2 depends on Task 1
- Task 3 depends on Task 1
- Task 4 depends on Tasks 1 through 3
