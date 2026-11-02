package com.yavanika.banking.service;

import com.yavanika.banking.dto.AccountDtos.*;
import com.yavanika.banking.exception.BankingException;
import com.yavanika.banking.model.Account;
import com.yavanika.banking.model.Customer;
import com.yavanika.banking.model.Enums.AccountStatus;
import com.yavanika.banking.model.Enums.AccountType;
import com.yavanika.banking.model.Enums.TransactionType;
import com.yavanika.banking.model.Transaction;
import com.yavanika.banking.repository.AccountRepository;
import com.yavanika.banking.repository.CustomerRepository;
import com.yavanika.banking.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;
    private final BigDecimal savingsMinBalance;
    private final BigDecimal perTransactionLimit;

    public AccountService(AccountRepository accountRepository,
                          CustomerRepository customerRepository,
                          TransactionRepository transactionRepository,
                          @Value("${app.rules.savings-min-balance}") BigDecimal savingsMinBalance,
                          @Value("${app.rules.per-transaction-limit}") BigDecimal perTransactionLimit) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.transactionRepository = transactionRepository;
        this.savingsMinBalance = savingsMinBalance;
        this.perTransactionLimit = perTransactionLimit;
    }

    // ---------------------------------------------------------------- accounts

    @Transactional
    public AccountResponse openAccount(String email, CreateAccountRequest req) {
        Customer owner = customer(email);
        Account account = accountRepository.save(new Account(newAccountNumber(), req.type(), owner));

        BigDecimal initial = req.initialDeposit() == null ? BigDecimal.ZERO : money(req.initialDeposit());
        if (initial.signum() > 0) {
            account.credit(initial);
            transactionRepository.save(new Transaction(account, TransactionType.DEPOSIT, initial,
                    UUID.randomUUID().toString(), null, "Initial deposit"));
        }
        return toResponse(account);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> myAccounts(String email) {
        return accountRepository.findByOwnerOrderByCreatedAtAsc(customer(email))
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(String email, String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> BankingException.notFound("Account " + accountNumber + " not found"));
        requireOwner(account, email);
        return toResponse(account);
    }

    // ------------------------------------------------------------ money movement

    @Transactional
    public TransactionResponse deposit(String email, String accountNumber, AmountRequest req) {
        BigDecimal amount = money(req.amount());
        Account account = lockOwned(email, accountNumber);
        requireActive(account);

        account.credit(amount);
        Transaction txn = transactionRepository.save(new Transaction(account, TransactionType.DEPOSIT, amount,
                UUID.randomUUID().toString(), null, describe(req.description(), "Cash deposit")));
        return toResponse(txn);
    }

    @Transactional
    public TransactionResponse withdraw(String email, String accountNumber, AmountRequest req) {
        BigDecimal amount = money(req.amount());
        checkLimit(amount);
        Account account = lockOwned(email, accountNumber);
        requireActive(account);
        requireSufficientFunds(account, amount);

        account.debit(amount);
        Transaction txn = transactionRepository.save(new Transaction(account, TransactionType.WITHDRAWAL, amount,
                UUID.randomUUID().toString(), null, describe(req.description(), "Cash withdrawal")));
        return toResponse(txn);
    }

    /**
     * Moves money between two accounts atomically: both legs commit together or neither does.
     * Rows are locked in a fixed (account-number) order so two opposite transfers can't deadlock.
     */
    @Transactional
    public TransferResponse transfer(String email, TransferRequest req) {
        BigDecimal amount = money(req.amount());
        String fromNo = req.fromAccount().trim();
        String toNo = req.toAccount().trim();

        if (fromNo.equals(toNo)) {
            throw BankingException.rule("SAME_ACCOUNT", "Cannot transfer to the same account");
        }
        checkLimit(amount);

        boolean fromFirst = fromNo.compareTo(toNo) < 0;
        Account first = lock(fromFirst ? fromNo : toNo);
        Account second = lock(fromFirst ? toNo : fromNo);
        Account from = fromFirst ? first : second;
        Account to = fromFirst ? second : first;

        requireOwner(from, email);
        requireActive(from);
        requireActive(to);
        requireSufficientFunds(from, amount);

        String reference = UUID.randomUUID().toString();
        String note = describe(req.description(), "Fund transfer");

        from.debit(amount);
        transactionRepository.save(new Transaction(from, TransactionType.TRANSFER_OUT, amount,
                reference, to.getAccountNumber(), note));
        to.credit(amount);
        transactionRepository.save(new Transaction(to, TransactionType.TRANSFER_IN, amount,
                reference, from.getAccountNumber(), note));

        return new TransferResponse(reference, from.getAccountNumber(), to.getAccountNumber(), amount,
                from.getBalance(), LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> statement(String email, String accountNumber, int page, int size) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> BankingException.notFound("Account " + accountNumber + " not found"));
        requireOwner(account, email);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return transactionRepository
                .findByAccountOrderByCreatedAtDescIdDesc(account, PageRequest.of(Math.max(page, 0), safeSize))
                .map(this::toResponse);
    }

    @Transactional
    public AccountResponse changeStatus(String email, String accountNumber, AccountStatus newStatus) {
        Account account = lockOwned(email, accountNumber);
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw BankingException.rule("ACCOUNT_CLOSED", "A closed account cannot be changed");
        }
        if (newStatus == AccountStatus.CLOSED && account.getBalance().signum() != 0) {
            throw BankingException.rule("NON_ZERO_BALANCE", "Withdraw the full balance before closing the account");
        }
        account.setStatus(newStatus);
        return toResponse(account);
    }

    // ------------------------------------------------------------------ helpers

    private Customer customer(String email) {
        return customerRepository.findByEmail(email)
                .orElseThrow(() -> BankingException.notFound("Customer not found"));
    }

    private Account lock(String accountNumber) {
        return accountRepository.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> BankingException.notFound("Account " + accountNumber + " not found"));
    }

    private Account lockOwned(String email, String accountNumber) {
        Account account = lock(accountNumber);
        requireOwner(account, email);
        return account;
    }

    private void requireOwner(Account account, String email) {
        if (!account.getOwner().getEmail().equals(email)) {
            // 404 rather than 403 so other customers' account numbers can't be discovered.
            throw BankingException.notFound("Account " + account.getAccountNumber() + " not found");
        }
    }

    private void requireActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw BankingException.rule("ACCOUNT_NOT_ACTIVE",
                    "Account " + account.getAccountNumber() + " is " + account.getStatus());
        }
    }

    private void requireSufficientFunds(Account account, BigDecimal amount) {
        BigDecimal minimum = account.getType() == AccountType.SAVINGS ? savingsMinBalance : BigDecimal.ZERO;
        BigDecimal after = account.getBalance().subtract(amount);
        if (after.compareTo(minimum) < 0) {
            String msg = minimum.signum() > 0
                    ? "Insufficient funds: savings accounts must keep a minimum balance of " + minimum
                    : "Insufficient funds";
            throw BankingException.rule("INSUFFICIENT_FUNDS", msg);
        }
    }

    private void checkLimit(BigDecimal amount) {
        if (amount.compareTo(perTransactionLimit) > 0) {
            throw BankingException.rule("LIMIT_EXCEEDED",
                    "Amount exceeds the per-transaction limit of " + perTransactionLimit);
        }
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static String describe(String given, String fallback) {
        return given == null || given.isBlank() ? fallback : given.trim();
    }

    private String newAccountNumber() {
        String number;
        do {
            number = String.valueOf(100_000_000_000L + (long) (RANDOM.nextDouble() * 899_999_999_999L));
        } while (accountRepository.existsByAccountNumber(number));
        return number;
    }

    private AccountResponse toResponse(Account a) {
        return new AccountResponse(a.getAccountNumber(), a.getType(), a.getBalance(), a.getStatus(),
                a.getOwner().getFullName(), a.getCreatedAt());
    }

    private TransactionResponse toResponse(Transaction t) {
        return new TransactionResponse(t.getId(), t.getType(), t.getAmount(), t.getBalanceAfter(),
                t.getReference(), t.getCounterpartyAccount(), t.getDescription(), t.getCreatedAt());
    }
}
