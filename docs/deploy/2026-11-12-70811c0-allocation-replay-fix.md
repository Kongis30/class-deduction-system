# 部署记录：2026-11-12 — 死行换课付款分摊超容修复

## 版本
- Commit: `70811c0`（main_v2）— fix: 死行换课更正跳过前置付款分摊重放，修复超收订单保存报错
- Release 目录: `/home/class-deduction-system/releases/v2-20261112-70811c0-allocation-replay-fix/`
- 上一版本: `v2-20261112-2649155-edit-receipt-fix`

## 修复内容
线上报错 "Saved course payment allocation exceeds the course's valid payment amount."
（Ze Mu Ma 订单 EO202609130008 死行换课保存时触发）。

根因：11-03 删除课程行后订单处于"5 笔付款 $2,745 无归属 + 超收（received 3244 >
due 499）"中间态。今天死行换课保存时，correctOrder 的前置 replay 按"超收 +
唯一活行"规则把全部未分摊付款塞进唯一活行 1026（容量 499）；换课新建课程行后
订单 due 回升不再超收，后置 replay 校验已存分摊 3244 > 499 抛错，整个事务回滚。

修复：本次更正包含新增课程行（items 中存在 orderItemId 为空的行，死行换课即此
形态）时跳过改价前的前置 replay 补齐，改由后置 replay（moneyChanged 分支）以
最终行集合统一分配：499 留旧活行，$2,745 按容量进入新行，其余未分摊。

回归测试：`EnrollmentOrderServiceImplTest.shouldSkipPrePriceReplayWhenCorrectionAddsCourseRow`
（81 例全绿）。

## 产物校验和
- backend.jar: `339e434989681c966380f771fbd6e17ce7ca856cdf80d5364f492344def35f47`（本地与服务器一致）
- front/dist/index.html: `7c21c58386d7223798298a92bbb8ea8dba1355f153473496fe27da040c0f7540`（本地与服务器一致）

## DB 备份
- `/root/jin-v2-backups/pre-70811c0-20261112-024409.sql.gz`
- SHA-256: `48c41278c1e516c08a370a5e4a35267e9d5632c534899f4aa9c1af5871d70875`
- `gzip -t` 通过

## 验证结果
- systemd `class-deduction.service`: active (running)，NRestarts=0
- API 正常响应：`/api/v1/auth/login` 返回标准 JSON（INVALID_CREDENTIALS 格式正确）
- 符号链接：current/backend.jar、front/dist 均指向新 release

## 回滚
将 current/backend.jar 与 front/dist 符号链接切回
`/home/class-deduction-system/releases/v2-20261112-2649155-edit-receipt-fix/`，
`systemctl restart class-deduction`。
