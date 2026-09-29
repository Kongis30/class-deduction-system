-- 仅增加规则标记，不改写任何已有订单金额；旧订单保持 NULL。
ALTER TABLE enrollment_order
    ADD COLUMN billing_policy VARCHAR(32) NULL COMMENT 'New registration money policy; NULL for legacy orders';
