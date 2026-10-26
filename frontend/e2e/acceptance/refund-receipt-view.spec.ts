import { expect, test } from '@playwright/test';

const admin = {
  userId: '1',
  username: 'admin',
  displayName: 'Admin',
  userType: 'ADMIN',
  roleCodes: ['ADMIN'],
};

const student = {
  id: 'student-1',
  studentNo: 'ST-1',
  studentName: 'Refund View Student',
  gender: 'FEMALE',
  guardianName: 'Parent',
  phone: '555-0100',
  status: 'ACTIVE',
  receiptCount: 1,
};

const orderId = 'order-1';
const originalReceiptNo = 'RCP-ORIGINAL-001';

test('refund receipt View opens the original enrollment receipt for its order', async ({ page }) => {
  await page.addInitScript((user) => {
    localStorage.setItem('jbt_auth_token', 'test-token');
    localStorage.setItem('jbt_auth_user', JSON.stringify(user));
  }, admin);

  await page.route('**/api/**', async (route) => {
    const requestUrl = new URL(route.request().url());
    const path = requestUrl.pathname;
    if (!path.startsWith('/api/')) return route.continue();
    let data: unknown = [];

    if (path.endsWith('/v1/auth/me')) {
      data = admin;
    } else if (path.endsWith('/v1/base/students')) {
      data = { records: [student], total: 1, pageNum: 1, pageSize: 200 };
    } else if (path.endsWith('/v1/enrollment/receipts')) {
      const isRefundQuery = requestUrl.searchParams.get('templateCode') === 'REFUND_RECEIPT';
      data = {
        records: isRefundQuery
          ? [{
            id: 'refund-1',
            receiptNo: 'RFD-001',
            enrollmentOrderId: orderId,
            studentId: student.id,
            studentName: student.studentName,
            templateCode: 'REFUND_RECEIPT',
            receiptPayload: { enrollmentOrderId: orderId },
            issuedAt: '2026-10-25T15:58:00',
          }]
          : [],
        total: isRefundQuery ? 1 : 0,
        pageNum: 1,
        pageSize: 10,
      };
    } else if (path.endsWith(`/v1/enrollment/orders/${orderId}`)) {
      data = {
        id: orderId,
        enrollmentNo: 'EO-ORIGINAL-001',
        studentId: student.id,
        studentNo: student.studentNo,
        studentName: student.studentName,
        studentNameSnapshot: student.studentName,
        gradeSnapshot: '4',
        schoolSnapshot: 'PS 10',
        campusNameSnapshot: 'North Campus',
        orderStatus: 'CONFIRMED',
        signupDate: '2026-09-01',
        originalAmount: 800,
        discountAmount: 0,
        receivedAmount: 800,
        amountDue: 800,
        balanceAmount: 0,
        paymentStatus: 'PAID',
        items: [{
          id: 'item-1',
          enrollmentOrderId: orderId,
          lineNo: 1,
          courseProductId: 'product-1',
          courseProductName: '2026-2027 After School',
          registerClass: 'G1-G5',
          className: 'G4',
          startDate: '2026-09-10',
          purchasedMonths: 2,
          priceAmount: 800,
          amountDue: 800,
          paidAmount: 800,
          balanceAmount: 0,
          itemStatus: 'ACTIVE',
        }],
        participants: [],
        payments: [],
        packages: [],
        receipts: [{
          id: 'registration-receipt-1',
          receiptNo: originalReceiptNo,
          enrollmentOrderId: orderId,
          studentId: student.id,
          studentName: student.studentName,
          templateCode: 'DEFAULT_RECEIPT',
          receiptPayload: {
            receiptNo: originalReceiptNo,
            studentName: student.studentName,
            signupDate: '2026-09-01',
            courses: [{ courseProductName: '2026-2027 After School', className: 'G4' }],
            payments: [{ amount: 800, paymentMethod: 'Cash', paidAt: '2026-09-01' }],
          },
          issuedAt: '2026-09-01T12:00:00',
        }],
      };
    }

    await route.fulfill({ json: { success: true, code: 'OK', data } });
  });

  await page.goto('/enrollment/receipts');
  await expect(page.getByText(student.studentName).first()).toBeVisible();
  await page.getByRole('tab', { name: 'Refund receipts' }).click();
  await expect(page.getByText('RFD-001')).toBeVisible();
  await page.getByRole('button', { name: 'View', exact: true }).click();

  await expect(page).toHaveURL(new RegExp(`/registration\\?orderId=${orderId}$`));
  await expect(page.getByText('Saved receipt', { exact: true })).toBeVisible();
  await expect(page.locator('.paper-form').getByText('2026-2027 After School').first()).toBeVisible();
  await expect(page.getByText('Refund transaction record')).toHaveCount(0);
});
