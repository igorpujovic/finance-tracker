package com.igorpujovic.finance_tracker.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface TransactionRepository
        extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    boolean existsByCategoryId(Long categoryId);

    /**
     * A transaction is read with its category, because the response that is built from
     * it names the category and {@code spring.jpa.open-in-view=false} leaves no session
     * open by the time it is built. Lazily mapped, the field would be a proxy nothing
     * can initialize, and the endpoint would answer 500 for every categorized row.
     * <p>
     * An entity graph rather than a {@code join fetch}: the paged query needs a count
     * query too, and a fetch join cannot appear in one. It also makes a listing one
     * query instead of one per distinct category on the page.
     */
    @Override
    @EntityGraph(attributePaths = Transaction_.CATEGORY)
    Page<Transaction> findAll(Specification<Transaction> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = Transaction_.CATEGORY)
    Optional<Transaction> findById(Long id);

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
