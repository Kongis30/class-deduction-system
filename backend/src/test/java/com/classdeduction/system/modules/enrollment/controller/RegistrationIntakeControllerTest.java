package com.classdeduction.system.modules.enrollment.controller;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.modules.enrollment.dto.RegistrationIntakeRequest;
import com.classdeduction.system.modules.enrollment.service.RegistrationIntakeService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RegistrationIntakeControllerTest {

    @Test
    void managerHandlerOverridesRegistrationAndAllPaymentLines() {
        RegistrationIntakeService service = mock(RegistrationIntakeService.class);
        RegistrationIntakeController controller = new RegistrationIntakeController(service);
        RegistrationIntakeRequest request = new RegistrationIntakeRequest();
        request.setHandledByName("Edited name");

        RegistrationIntakeRequest.PaymentPayload orderPayment = payment("Edited order payment name");
        RegistrationIntakeRequest.PaymentPayload participantPayment = payment("Edited participant payment name");
        RegistrationIntakeRequest.ParticipantBillingPayload billing = new RegistrationIntakeRequest.ParticipantBillingPayload();
        billing.setPayments(List.of(participantPayment));
        request.setPayments(List.of(orderPayment));
        request.setParticipantBillings(List.of(billing));

        CurrentUserResponse manager = new CurrentUserResponse(
                42L, "manager", "Taylor Manager", "STAFF", null, null, List.of("MANAGER"));

        controller.submitIntake(request, manager);

        assertThat(request.getHandledByName()).isEqualTo("Taylor Manager");
        assertThat(request.getCashierUserId()).isEqualTo(42L);
        assertHandler(orderPayment, manager);
        assertHandler(participantPayment, manager);
        verify(service).submitIntake(request, manager);
    }

    private RegistrationIntakeRequest.PaymentPayload payment(String handler) {
        RegistrationIntakeRequest.PaymentPayload payment = new RegistrationIntakeRequest.PaymentPayload();
        payment.setHandledByName(handler);
        payment.setHandledByUserId(7L);
        return payment;
    }

    private void assertHandler(RegistrationIntakeRequest.PaymentPayload payment, CurrentUserResponse manager) {
        assertThat(payment.getHandledByName()).isEqualTo(manager.displayName());
        assertThat(payment.getHandledByUserId()).isEqualTo(manager.userId());
    }
}
