package com.darcode.day02.repository;

import com.darcode.day02.domain.VersionedAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VersionedAccountRepository extends JpaRepository<VersionedAccount, Long> {
}
