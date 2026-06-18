# Tasks
- [x] Task 1: 确认展开列表数据字段
  - [x] SubTask 1.1: 检查 `CoursePackageItem` 是否已有 `packageStatus`、`startDate`、`estimatedEndDate`、`createdAt`、`totalUnits`、`consumedUnits`
  - [x] SubTask 1.2: 如排序或筛选需要缺失字段，补齐类型或后端返回

- [x] Task 2: 实现 1 on 1 展开学生进度筛选
  - [x] SubTask 2.1: 在每个产品展开区增加简洁的状态筛选按钮
  - [x] SubTask 2.2: 默认筛选为 `ACTIVE`
  - [x] SubTask 2.3: 支持切换到显示全部或非有效记录

- [x] Task 3: 实现展开学生进度排序
  - [x] SubTask 3.1: 默认按 `createdAt` 倒序排列
  - [x] SubTask 3.2: `Progress` 表头支持点击升序/降序排序
  - [x] SubTask 3.3: `Period` 支持按开始时间和结束时间排序

- [x] Task 4: 验证变更
  - [x] SubTask 4.1: 运行前端类型检查
  - [x] SubTask 4.2: 手动检查默认筛选、状态切换、进度排序、周期排序行为

# Task Dependencies
- Task 2 depends on Task 1
- Task 3 depends on Task 1
- Task 4 depends on Task 2 and Task 3
