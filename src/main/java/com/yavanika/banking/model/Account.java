package com.yavanika.banking.model;

import com.yavanika.banking.model.Enums.AccountStatus;
import com.yavanika.banking.model.Enums.AccountType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 12)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AccountType type;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AccountStatus status = AccountStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer owner;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** Optimistic-locking version: protects against lost updates. */
    @Version
    private Long version;

    protected Account() {
    }

    public Account(String accountNumber, AccountType type, Customer owner) {
        this.accountNumber = accountNumber;
        this.type = type;
        this.owner = owner;
        this.createdAt = LocalDateTime.now();
    }

    public void credit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }

    public void debit(BigDecimal amount) {
        this.balance = this.balance.subtract(amount);
    }

    public Long getId() { return id; }
    public String getAccountNumber() { return accountNumber; }
    public AccountType getType() { return type; }
    public BigDecimal getBalance() { return balance; }
    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }
    public Customer getOwner() { return owner; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
