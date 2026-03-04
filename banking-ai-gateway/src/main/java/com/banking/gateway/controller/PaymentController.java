package com.banking.gateway.controller;

import com.banking.common.domain.ApiResponse;
import com.banking.common.domain.PagedResponse;
import com.banking.payment.dto.PaymentDtos.*;
import com.banking.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /** POST /api/v1/payments */
    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> initiatePayment(
            @Valid @RequestBody InitiatePaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payment initiated", paymentService.initiatePayment(request)));
    }

    /** POST /api/v1/payments/{paymentId}/process */
    @PostMapping("/{paymentId}/process")
    public ResponseEntity<ApiResponse<PaymentResponse>> processPayment(
            @PathVariable String paymentId) {
        return ResponseEntity.ok(ApiResponse.success("Payment processed", paymentService.processPayment(paymentId)));
    }

    /** GET /api/v1/payments/{paymentId} */
    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(
            @PathVariable String paymentId) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getPayment(paymentId)));
    }

    /** GET /api/v1/payments?accountId=ACC-001&page=0&size=20 */
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<PaymentSummary>>> getPayments(
            @RequestParam String accountId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = paymentService.getAccountPayments(
            accountId, PageRequest.of(page, size, Sort.by("initiatedAt").descending()));
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.of(result)));
    }

    /** POST /api/v1/payments/{paymentId}/reverse */
    @PostMapping("/{paymentId}/reverse")
    public ResponseEntity<ApiResponse<PaymentResponse>> reversePayment(
            @PathVariable String paymentId,
            @RequestParam String reason) {
        return ResponseEntity.ok(ApiResponse.success("Payment reversed", paymentService.reversePayment(paymentId, reason)));
    }

    /** GET /api/v1/payments/accounts/{accountId}/daily-summary */
    @GetMapping("/accounts/{accountId}/daily-summary")
    public ResponseEntity<ApiResponse<DailySpendingSummary>> getDailySummary(
            @PathVariable String accountId) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getDailySpendingSummary(accountId)));
    }
}
