package com.darcode.day02.web;

import com.darcode.day02.service.WithdrawalService;
import com.darcode.day02.web.dto.WithdrawalRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounts")
public class WithdrawalController {
    private final WithdrawalService withdrawalService;

    public WithdrawalController(WithdrawalService withdrawalService) {
        this.withdrawalService = withdrawalService;
    }

    @PostMapping("/{accountId}/withdrawals")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(
            @PathVariable long accountId,
            @Valid @RequestBody WithdrawalRequest request
    ) {
        withdrawalService.withdraw(accountId, request.amountCents());
    }
}
