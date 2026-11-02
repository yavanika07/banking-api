package com.yavanika.banking.dto;

import com.yavanika.banking.model.Enums.AccountStatus;
import com.yavanika.banking.model.Enums.AccountType;
import com.yavanika.banking.model.Enums.TransactionType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class AccountDtos {

    private AccountDtos() {
    }

    public record CreateAccountRequest(
            @NotNull(message = "type is required (SAVINGS or CURRENT)") AccountType type,
            @DecimalMin(value = "0.00", message = "initialDeposit cannot be negative")
            @Digits(integer = 13, fraction = 2, message = "initialDeposit can have at most 2 decimal places")
            BigDecimal initialDeposit
    ) {
    }

    public record AmountRequest(
            @NotNull(message = "amount is required")
            @DecimalMin(value = "0.01", message = "amount must be at least 0.01")
            @Digits(integer = 13, fraction = 2, message = "amount can have at most 2 decimal places")
            BigDecimal amount,
            @Size(max = 140) String description
    ) {
    }

    public record TransferRequest(
            @NotBlank(message = "fromAccount is required") String fromAccount,
            @NotBlank(message = "toAccount is required") String toAccount,
            @NotNull(message = "amount is required")
            @DecimalMin(value = "0.01", message = "amount must be at least 0.01")
            @Digits(integer = 13, fraction = 2, message = "amount can have at most 2 decimal places")
            BigDecimal amount,
            @Size(max = 140) String description
    ) {
    }

    public record AccountResponse(String accountNumber, AccountType type, BigDecimal balance,
                                  AccountStatus status, String ownerName, LocalDateTime createdAt) {
    }

    public record TransactionResponse(Long id, TransactionType type, BigDecimal amount, BigDecimal balanceAfter,
                                      String reference, String counterpartyAccount, String description,
                                      LocalDateTime createdAt) {
    }

    public record TransferResponse(String reference, String fromAccount, String toAccount, BigDecimal amount,
                                   BigDecimal fromBalanceAfter, LocalDateTime completedAt) {
    }
}
