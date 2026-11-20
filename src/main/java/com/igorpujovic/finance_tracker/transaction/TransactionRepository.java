package com.igorpujovic.finance_tracker.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;

public interface TransactionRepository
        extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    boolean existsByCategoryId(Long categoryId);

    /**
     * ADR-0003: the duplicate check for a whole statement is this one query. Asking per
     * line would be one round trip per row of a bank statement; asking for the set and
     * intersecting in memory is one. Only imported rows have a fingerprint, so manual
     * transactions cannot match and need no exclusion here.
     * <p>
     * The caller must not pass an empty collection: {@code in ()} is not valid SQL.
     */
    @Query("select t.fingerprint from Transaction t where t.fingerprint in :fingerprints")
    Set<String> findFingerprintsIn(@Param("fingerprints") Collection<String> fingerprints);
}
