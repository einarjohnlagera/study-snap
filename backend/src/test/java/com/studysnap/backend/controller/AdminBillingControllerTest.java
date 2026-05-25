package com.studysnap.backend.controller;

import com.studysnap.backend.dto.AdminIssueRefundRequest;
import com.studysnap.backend.dto.AdminIssueRefundResponse;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.RefundService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBillingControllerTest {
    @Mock
    private RefundService refundService;

    @Test
    void controller_requiresAdminRole() {
        PreAuthorize annotation = AdminBillingController.class.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasRole('ADMIN')");
    }

    @Test
    void issueRefund_returnsRefundResponse() {
        UUID adminUserId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(adminUserId, UserRole.ADMIN, true, 0);
        when(refundService.issueRefund(transactionId, adminUserId)).thenReturn(new RefundService.RefundResult(
                transactionId,
                "learner@notelib.app",
                new BigDecimal("249.00"),
                "PHP"
        ));
        AdminBillingController controller = new AdminBillingController(refundService);

        AdminIssueRefundResponse response = controller.issueRefund(user, new AdminIssueRefundRequest(transactionId));

        assertThat(response.transactionId()).isEqualTo(transactionId);
        assertThat(response.userEmail()).isEqualTo("learner@notelib.app");
        assertThat(response.amount()).isEqualByComparingTo("249.00");
        assertThat(response.currency()).isEqualTo("PHP");
        verify(refundService).issueRefund(transactionId, adminUserId);
    }
}
