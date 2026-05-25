package com.studysnap.backend.controller;

import com.studysnap.backend.dto.AdminIssueRefundRequest;
import com.studysnap.backend.dto.AdminIssueRefundResponse;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/billing")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminBillingController {
    private final RefundService refundService;

    @PostMapping("/refund")
    public AdminIssueRefundResponse issueRefund(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AdminIssueRefundRequest request
    ) {
        RefundService.RefundResult result = refundService.issueRefund(request.transactionId(), user.userId());
        return new AdminIssueRefundResponse(
                result.transactionId(),
                result.userEmail(),
                result.amount(),
                result.currency()
        );
    }
}
