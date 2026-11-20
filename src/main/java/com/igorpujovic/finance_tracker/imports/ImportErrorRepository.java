package com.igorpujovic.finance_tracker.imports;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImportErrorRepository extends JpaRepository<ImportError, Long> {

    List<ImportError> findByImportBatchIdOrderByRowNumberAsc(Long importBatchId);
}
