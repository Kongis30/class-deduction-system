package com.classdeduction.system.modules.enrollment.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.modules.enrollment.entity.PaymentMethodEntity;
import com.classdeduction.system.modules.enrollment.mapper.PaymentMethodMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Ensures the payment methods required by the registration form exist.
 */
@Component
@RequiredArgsConstructor
public class PaymentMethodBootstrapInitializer implements ApplicationRunner {

    private static final List<DefaultMethod> DEFAULT_METHODS = List.of(
            new DefaultMethod("CASH", "Cash", 10),
            new DefaultMethod("ZELLE", "Zelle", 20),
            new DefaultMethod("TRANSFER", "Transfer", 30),
            new DefaultMethod("CHECK", "Check", 40),
            new DefaultMethod("OTHER", "Other", 50)
    );

    private final PaymentMethodMapper paymentMethodMapper;

    @Override
    public void run(ApplicationArguments args) {
        DEFAULT_METHODS.forEach(this::ensureMethod);
    }

    private void ensureMethod(DefaultMethod method) {
        Long count = paymentMethodMapper.selectCount(new LambdaQueryWrapper<PaymentMethodEntity>()
                .eq(PaymentMethodEntity::getMethodCode, method.code()));
        if (count != null && count > 0) {
            return;
        }
        PaymentMethodEntity entity = new PaymentMethodEntity();
        entity.setMethodCode(method.code());
        entity.setMethodName(method.name());
        entity.setSortOrder(method.sortOrder());
        entity.setStatus("ACTIVE");
        paymentMethodMapper.insert(entity);
    }

    private record DefaultMethod(String code, String name, int sortOrder) {
    }
}
