package com.darcode.day01.repository;

import com.darcode.day01.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * Loads the account with a row lock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Account> findWithLockById(Long id);
}
