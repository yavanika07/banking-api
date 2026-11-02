package com.yavanika.banking.model;

import com.yavanika.banking.model.Enums.TransactionType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id")
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private TransactionType type;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balanceAfter;

    /** Shared by both legs of a transfer so they can be matched. */
    @Column(nullable = false, length = 36)
    private String reference;

    @Column(length = 12)
    private String counterpartyAccount;

    @Column(length = 140)
    private String description;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected Transaction() {
    }

    public Transaction(Account account, TransactionType type, BigDecimal amount, String reference,
                       String counterpartyAccount, String description) {
        this.account = account;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = account.getBalance();
        this.reference = reference;
        this.counterpartyAccount = counterpartyAccount;
        this.description = description;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Account getAccount() { return account; }
    public TransactionType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public String getReference() { return reference; }
    public String getCounterpartyAccount() { return counterpartyAccount; }
    public String getDescription() { return description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
