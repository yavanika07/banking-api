package com.yavanika.banking.repository;

import com.yavanika.banking.model.Account;
import com.yavanika.banking.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Page<Transaction> findByAccountOrderByCreatedAtDescIdDesc(Account account, Pageable pageable);
}
