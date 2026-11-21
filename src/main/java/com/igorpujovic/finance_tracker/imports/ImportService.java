package com.igorpujovic.finance_tracker.imports;

import com.igorpujovic.finance_tracker.common.NotFoundException;
import com.igorpujovic.finance_tracker.rates.Conversion;
import com.igorpujovic.finance_tracker.transaction.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Importing a statement in the three movements of ADR-0005: the file is read and
 * validated, then what the valid lines need is resolved, and only then does a
 * transaction open and write. This class deliberately carries no {@code @Transactional}
 * on the import path — the write lives in {@link ImportWriter} — because the whole
 * point of the ordering is that nothing slow happens while a transaction is held.
 */
@Service
public class ImportService {

    /** Large enough for years of statements, small enough to hold in memory and hash. */
    static final long MAX_BYTES = 5L * 1024 * 1024;

    private static final String EXTENSION = ".csv";
    private static final int FILE_NAME_LIMIT = 255;
    private static final char BYTE_ORDER_MARK = '﻿';

    private final StatementParser parser;
    private final StatementConversion conversion;
    private final ImportWriter writer;
    private final ImportBatchRepository batches;
    private final ImportErrorRepository errors;
    private final TransactionRepository transactions;

    public ImportService(StatementParser parser, StatementConversion conversion, ImportWriter writer,
                  ImportBatchRepository batches, ImportErrorRepository errors,
                  TransactionRepository transactions) {
        this.parser = parser;
        this.conversion = conversion;
        this.writer = writer;
        this.batches = batches;
        this.errors = errors;
        this.transactions = transactions;
    }

    public ImportBatch importStatement(String fileName, byte[] content) {
        String name = requireStatementFile(fileName, content);
        ParsedStatement statement = parser.parse(decode(content));

        List<RejectedLine> rejected = new ArrayList<>(statement.rejected());
        Set<String> alreadyImported = fingerprintsAlreadyImported(statement.valid());

        // ADR-0005: the rates are resolved here, before the write opens, and only for
        // the lines that could use one — one line per fingerprint, skipping what the
        // database already has. Lines sharing a fingerprint share a date and a currency
        // too, so resolving the first of them answers for all of them.
        Map<String, SourceLine> firstOfEachLine = new LinkedHashMap<>();
        for (SourceLine line : statement.valid()) {
            if (!alreadyImported.contains(line.fingerprint())) {
                firstOfEachLine.putIfAbsent(line.fingerprint(), line);
            }
        }
        Map<Integer, Conversion> conversions =
                conversion.convert(List.copyOf(firstOfEachLine.values()));

        List<ResolvedLine> toWrite = new ArrayList<>();
        Set<String> written = new HashSet<>();
        int duplicates = 0;
        for (SourceLine line : statement.valid()) {
            if (alreadyImported.contains(line.fingerprint())) {
                duplicates++;
                continue;
            }
            switch (conversionOf(conversions, firstOfEachLine.get(line.fingerprint()))) {
                // A line nothing can convert is this line's own problem, and so is the
                // second copy of it: calling that copy a duplicate would say an earlier
                // line had been imported, and leave it with no error row to explain it.
                case Conversion.Unavailable(var reason) -> rejected.add(line.rejectedBecause(reason));
                case Conversion.DinarAmount(var amountRsd) -> {
                    if (written.add(line.fingerprint())) {
                        toWrite.add(new ResolvedLine(line, amountRsd));
                    } else {
                        duplicates++;
                    }
                }
            }
        }
        rejected.sort(Comparator.comparingInt(RejectedLine::rowNumber));

        return writer.write(name, statement.totalRows(), toWrite, duplicates, rejected);
    }

    private Conversion conversionOf(Map<Integer, Conversion> conversions, SourceLine line) {
        return Objects.requireNonNull(conversions.get(line.rowNumber()),
                () -> "The rate pre-pass returned nothing for row " + line.rowNumber());
    }

    @Transactional(readOnly = true)
    public Page<ImportBatch> list(Pageable pageable) {
        return batches.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public ImportBatch get(Long id) {
        return batches.findById(id)
                .orElseThrow(() -> new NotFoundException("Import batch", id));
    }

    @Transactional(readOnly = true)
    public List<ImportError> errorsOf(Long importBatchId) {
        return errors.findByImportBatchIdOrderByRowNumberAsc(importBatchId);
    }

    /**
     * The database half of ADR-0003: one set-based lookup asks about every fingerprint
     * in the file at once, rather than one round trip per line of a bank statement. The
     * other half is the {@code written} set in {@link #importStatement}, which catches a
     * line repeated inside this upload. The unique index behind
     * {@code transactions.fingerprint} is a backstop that should never fire.
     */
    private Set<String> fingerprintsAlreadyImported(List<SourceLine> valid) {
        Set<String> fingerprints = valid.stream().map(SourceLine::fingerprint).collect(Collectors.toSet());
        // An empty IN list is not valid SQL, and a file with no valid line has nothing to ask about.
        return fingerprints.isEmpty() ? Set.of() : transactions.findFingerprintsIn(fingerprints);
    }

    /**
     * Checks about the file rather than about its contents, so they can refuse the
     * upload before a single line is read and before anything is written.
     *
     * @return the file name to record, stripped of any directory the client sent
     */
    private String requireStatementFile(String fileName, byte[] content) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("An uploaded statement must carry a file name");
        }
        String name = baseNameOf(fileName);
        if (!name.toLowerCase(Locale.ROOT).endsWith(EXTENSION)) {
            throw new IllegalArgumentException("A statement must be a " + EXTENSION + " file; got " + name);
        }
        if (name.length() > FILE_NAME_LIMIT) {
            throw new IllegalArgumentException("A file name longer than " + FILE_NAME_LIMIT
                    + " characters cannot be recorded");
        }
        if (content.length == 0) {
            throw new IllegalArgumentException("The uploaded statement is empty");
        }
        if (content.length > MAX_BYTES) {
            throw new IllegalArgumentException("A statement may not exceed " + MAX_BYTES
                    + " bytes; this one is " + content.length);
        }
        return name;
    }

    /**
     * Some clients send a whole path as the file name. Only the last segment is the
     * name of the statement, and keeping the rest would store one machine path in
     * another machine audit record.
     */
    private String baseNameOf(String fileName) {
        int lastSeparator = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        return fileName.substring(lastSeparator + 1).strip();
    }

    /**
     * Statements are read as UTF-8, with malformed bytes becoming replacement
     * characters rather than an exception: a description a bank mis-encoded should cost
     * that line its accents, not the whole upload. A leading byte-order mark is dropped
     * so that a file saved by a spreadsheet is not refused for a header of
     * {@code ﻿date}.
     */
    private String decode(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        return text.isEmpty() || text.charAt(0) != BYTE_ORDER_MARK ? text : text.substring(1);
    }
}
