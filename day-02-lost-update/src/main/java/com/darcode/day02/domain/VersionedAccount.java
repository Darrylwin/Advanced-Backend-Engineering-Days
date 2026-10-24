package com.darcode.day02.domain;


import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Same table, with @Version: Hibernate appends "AND version = ?" to every UPDATE and increments the
 * version. Zero updated rows means someone else committed first, and an exception is raised.
 * In a real project this would simply be the only Account entity.
 */
@Entity
@Table(name = "accounts")
public class VersionedAccount extends BaseAccount {

    @Version
    private long version;

    protected VersionedAccount() {
    }

    public long getVersion() {
        return version;
    }
}
