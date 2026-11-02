package com.yavanika.banking.controller;

import com.yavanika.banking.dto.AccountDtos.*;
import com.yavanika.banking.model.Enums.AccountStatus;
import com.yavanika.banking.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/accounts")
    public ResponseEntity<AccountResponse> open(Authentication auth, @Valid @RequestBody CreateAccountRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.openAccount(auth.getName(), req));
    }

    @GetMapping("/accounts")
    public List<AccountResponse> myAccounts(Authentication auth) {
        return accountService.myAccounts(auth.getName());
    }

    @GetMapping("/accounts/{accountNumber}")
    public AccountResponse get(Authentication auth, @PathVariable String accountNumber) {
        return accountService.getAccount(auth.getName(), accountNumber);
    }

    @PostMapping("/accounts/{accountNumber}/deposit")
    public TransactionResponse deposit(Authentication auth, @PathVariable String accountNumber,
                                       @Valid @RequestBody AmountRequest req) {
        return accountService.deposit(auth.getName(), accountNumber, req);
    }

    @PostMapping("/accounts/{accountNumber}/withdraw")
    public TransactionResponse withdraw(Authentication auth, @PathVariable String accountNumber,
                                        @Valid @RequestBody AmountRequest req) {
        return accountService.withdraw(auth.getName(), accountNumber, req);
    }

    @GetMapping("/accounts/{accountNumber}/transactions")
    public Page<TransactionResponse> statement(Authentication auth, @PathVariable String accountNumber,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return accountService.statement(auth.getName(), accountNumber, page, size);
    }

    @PatchMapping("/accounts/{accountNumber}/status")
    public AccountResponse changeStatus(Authentication auth, @PathVariable String accountNumber,
                                        @RequestParam AccountStatus status) {
        return accountService.changeStatus(auth.getName(), accountNumber, status);
    }

    @PostMapping("/transfers")
    public TransferResponse transfer(Authentication auth, @Valid @RequestBody TransferRequest req) {
        return accountService.transfer(auth.getName(), req);
    }
}
