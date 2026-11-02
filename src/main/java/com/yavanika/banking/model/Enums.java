package com.yavanika.banking.model;

public final class Enums {

    private Enums() {
    }

    public enum AccountType { SAVINGS, CURRENT }

    public enum AccountStatus { ACTIVE, FROZEN, CLOSED }

    public enum TransactionType { DEPOSIT, WITHDRAWAL, TRANSFER_IN, TRANSFER_OUT }
}
