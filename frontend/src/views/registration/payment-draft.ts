interface AllocatedPaymentDraft {
  amount: number;
  paymentType?: string;
  courseAllocations: Record<string, number>;
}

/** 收据编辑以当前课程付款分配为准；没有分配记录的旧付款保留原金额供人工编辑。 */
export function paymentDraftAmount(payment: AllocatedPaymentDraft, courseCount: number): number {
  if (courseCount <= 1 || payment.paymentType === 'REFUND' || payment.amount < 0) {
    return payment.amount;
  }
  const allocations = Object.values(payment.courseAllocations);
  return allocations.length > 0 ? sumPaymentAllocations(payment.courseAllocations) : payment.amount;
}

/** 多课程付款的表单金额必须等于该付款在各课程输入框中的金额之和。 */
export function sumPaymentAllocations(allocations: Record<string, number>): number {
  const cents = Object.values(allocations)
    .reduce((total, amount) => total + Math.round(Number(amount || 0) * 100), 0);
  return cents / 100;
}
