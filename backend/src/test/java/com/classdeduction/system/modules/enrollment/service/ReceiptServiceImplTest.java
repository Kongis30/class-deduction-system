package com.classdeduction.system.modules.enrollment.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.enrollment.dto.ReceiptPageQuery;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderItemEntity;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderItemMapper;
import com.classdeduction.system.modules.enrollment.entity.ReceiptRecordEntity;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderMapper;
import com.classdeduction.system.modules.enrollment.mapper.ReceiptRecordMapper;
import com.classdeduction.system.common.util.JsonHelper;
import com.classdeduction.system.modules.enrollment.service.impl.ReceiptServiceImpl;
import com.classdeduction.system.modules.enrollment.vo.ReceiptRecordVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 收据查询服务测试：覆盖历史收据快照从持久化记录到接口响应的映射。
 */
@ExtendWith(MockitoExtension.class)
class ReceiptServiceImplTest {

    private static final String RECEIPT_PAYLOAD = "{\"receiptNo\":\"RCP202607150001\",\"items\":[{\"detail\":\"SHSAT\"}]}";

    @Mock
    private ReceiptRecordMapper receiptRecordMapper;

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private EnrollmentOrderMapper enrollmentOrderMapper;

    @Mock
    private EnrollmentOrderItemMapper enrollmentOrderItemMapper;

    @Mock
    private JsonHelper jsonHelper;

    private ReceiptServiceImpl receiptService;

    @BeforeEach
    void setUp() {
        receiptService = new ReceiptServiceImpl(
                receiptRecordMapper, studentMapper, enrollmentOrderMapper, enrollmentOrderItemMapper, jsonHelper);
    }

    @Test
    void shouldReturnReceiptPayloadForDetail() {
        ReceiptRecordEntity receipt = buildReceipt();
        when(receiptRecordMapper.selectById(6001L)).thenReturn(receipt);
        when(studentMapper.selectById(101L)).thenReturn(buildStudent());
        when(enrollmentOrderMapper.selectById(2001L)).thenReturn(buildOrder());
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of());

        ReceiptRecordVO result = receiptService.getReceipt(6001L);

        assertThat(result.receiptPayload()).isEqualTo(RECEIPT_PAYLOAD);
    }

    @Test
    void shouldReturnReceiptPayloadForPage() {
        ReceiptRecordEntity receipt = buildReceipt();
        Page<ReceiptRecordEntity> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(receipt));
        when(receiptRecordMapper.selectPage(any(), any())).thenReturn(page);
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(buildStudent()));
        when(enrollmentOrderMapper.selectBatchIds(any())).thenReturn(List.of(buildOrder()));
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of());

        ReceiptPageQuery query = new ReceiptPageQuery();
        query.setPageNum(1);
        query.setPageSize(10);

        PageResponse<ReceiptRecordVO> result = receiptService.pageReceipts(query);

        assertThat(result.records()).singleElement()
                .extracting(ReceiptRecordVO::receiptPayload)
                .isEqualTo(RECEIPT_PAYLOAD);
    }

    @Test
    void shouldReturnExactCourseItemIdsForReceiptDeletion() {
        long itemId = 2104092472449245186L;
        ReceiptRecordEntity receipt = buildReceipt();
        receipt.setReceiptPayload("{\"items\":[{\"orderItemId\":" + itemId + "}]}");
        EnrollmentOrderItemEntity item = new EnrollmentOrderItemEntity();
        item.setId(itemId);
        item.setEnrollmentOrderId(2001L);
        Page<ReceiptRecordEntity> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(receipt));
        when(receiptRecordMapper.selectPage(any(), any())).thenReturn(page);
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(buildStudent()));
        when(enrollmentOrderMapper.selectBatchIds(any())).thenReturn(List.of(buildOrder()));
        when(enrollmentOrderItemMapper.selectList(any())).thenReturn(List.of(item));

        ReceiptPageQuery query = new ReceiptPageQuery();
        query.setPageNum(1);
        query.setPageSize(10);

        ReceiptRecordVO result = receiptService.pageReceipts(query).records().get(0);

        assertThat(result.activeCourseItemIds()).containsExactly("2104092472449245186");
        assertThat(result.receiptPayload()).contains("\"orderItemId\":2104092472449245186");
    }

    private ReceiptRecordEntity buildReceipt() {
        ReceiptRecordEntity receipt = new ReceiptRecordEntity();
        receipt.setId(6001L);
        receipt.setReceiptNo("RCP202607150001");
        receipt.setEnrollmentOrderId(2001L);
        receipt.setStudentId(101L);
        receipt.setTemplateCode("DEFAULT_RECEIPT");
        receipt.setReceiptPayload(RECEIPT_PAYLOAD);
        receipt.setIssuedAt(LocalDateTime.of(2026, 7, 15, 10, 0));
        return receipt;
    }

    private StudentEntity buildStudent() {
        StudentEntity student = new StudentEntity();
        student.setId(101L);
        student.setStudentName("Olivia Lin");
        return student;
    }

    private EnrollmentOrderEntity buildOrder() {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setId(2001L);
        return order;
    }
}
