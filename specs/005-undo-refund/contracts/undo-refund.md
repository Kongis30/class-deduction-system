# API 契约：退款交易候选和撤销

所有路径相对 `/api/v1`，使用项目既有响应包装；以下为待实现契约，不表示接口已存在。仅 ADMIN/STAFF 可访问，服务端强制鉴权。

## 查询某汇总行关联的退款

`GET /data-summary/refund-candidates?orderId={id}&orderItemId={id}&studentId={id}&participantIndex={index}`

参数必须服务器端交叉校验；不得信任浏览器提交的学生或课程归属。仅返回能够证明对应该学生及该课程购买记录的行级退款，合并显示行须逐购买记录查询。整单或跨行历史退款不得伪装为行级退款或部分撤销，返回不可自动撤销原因。响应 `data.candidates[]`：`receiptId`、`receiptNo`、`paymentId`、`paymentNo`、`refundedAt`、`amount`、`paymentMethod`、`disposition`、`affectedStudents[]`、`affectedCourses[]`、`wholeOrderAffected`、`eligible`、`ineligibleReason`。金额为交易总额，另可显示行分摊额但须明确区分；多笔不默认选中。`data.unavailableReason` 用于无法证明关联而无法列出候选。返回内容不得含 `receiptPayload` 或内部快照，MANAGER 必须收到授权拒绝。

候选和影响范围可能随并发变化；`eligible` 仅为预检，不保证提交成功。查询应按订单批量处理，不逐行 N+1。

## 查询整笔退款影响

`GET /enrollment/receipts/{receiptId}/undo-preview`：ADMIN/STAFF 可获得与上面相同的整笔影响与可操作性信息，供 Data Summary 和既有收据页共同确认。必须提示系统不会追回外部资金。预览不能代替提交时重新锁定与校验。

## 提交撤销

`DELETE /enrollment/receipts/{receiptId}`：沿用既有接口；权限 ADMIN/STAFF。整笔调用统一领域服务，成功响应沿用 `ApiResponse<Void>`；服务端重新检查真实交易、快照、并发、冲突和 Credit。单次成功后其他请求返回已处理/冲突。操作不接受客户端快照或待恢复状态。

## 失败语义

使用项目统一业务错误包装及现有 HTTP 错误处理规范；新增明确错误码与本地化信息，至少区分 `REFUND_UNDO_SNAPSHOT_REQUIRED`、`REFUND_UNDO_SNAPSHOT_INVALID`、`REFUND_UNDO_LINK_AMBIGUOUS`、`REFUND_UNDO_STATE_CHANGED`、`REFUND_UNDO_SCHEDULE_CONFLICT`、`REFUND_UNDO_CREDIT_UNAVAILABLE`、`REFUND_UNDO_ALREADY_PROCESSED` 及权限拒绝。错误不得泄漏快照、内部 SQL 或其他学生敏感信息。任何失败业务数据均回滚；独立持久化失败审计。

## 数据访问限制

MANAGER 的 Data Summary 不含可执行撤销的收据/流水 ID 或候选信息；所有角色的收据列表/详情均不得返回内部快照。退款行可因其他有效退款继续显示，最终展示以刷新后的服务端状态为准。
