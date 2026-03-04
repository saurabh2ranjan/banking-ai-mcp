package com.banking.gateway.controller;

import com.banking.account.dto.AccountDtos.*;
import com.banking.account.service.AccountService;
import com.banking.common.domain.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    /** POST /api/v1/accounts — Open a new account */
    @PostMapping
    public ResponseEntity<ApiResponse<AccountResponse>> openAccount(
            @Valid @RequestBody OpenAccountRequest request) {
        AccountResponse account = accountService.openAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Account opened successfully", account));
    }

    /** GET /api/v1/accounts/{accountId} */
    @GetMapping("/{accountId}")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccount(
            @PathVariable String accountId) {
        return ResponseEntity.ok(ApiResponse.success(accountService.getAccount(accountId)));
    }

    /** GET /api/v1/accounts/{accountId}/balance */
    @GetMapping("/{accountId}/balance")
    public ResponseEntity<ApiResponse<BalanceResponse>> getBalance(
            @PathVariable String accountId) {
        return ResponseEntity.ok(ApiResponse.success(accountService.getBalance(accountId)));
    }

    /** GET /api/v1/accounts?customerId=CUST-001 */
    @GetMapping
    public ResponseEntity<ApiResponse<List<AccountSummary>>> getCustomerAccounts(
            @RequestParam String customerId) {
        return ResponseEntity.ok(ApiResponse.success(accountService.getCustomerAccounts(customerId)));
    }

    /** POST /api/v1/accounts/{accountId}/block */
    @PostMapping("/{accountId}/block")
    public ResponseEntity<ApiResponse<AccountResponse>> blockAccount(
            @PathVariable String accountId,
            @RequestParam String reason) {
        return ResponseEntity.ok(ApiResponse.success("Account blocked", accountService.blockAccount(accountId, reason)));
    }

    /** POST /api/v1/accounts/{accountId}/unblock */
    @PostMapping("/{accountId}/unblock")
    public ResponseEntity<ApiResponse<AccountResponse>> unblockAccount(
            @PathVariable String accountId) {
        return ResponseEntity.ok(ApiResponse.success("Account unblocked", accountService.unblockAccount(accountId)));
    }
}
