package com.igorpujovic.finance_tracker.report;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Reads an exported report back the way a spreadsheet would: strip the byte-order mark,
 * decode, parse as RFC 4180.
 * <p>
 * Shared by the test that renders the file and the test that fetches it over HTTP, so
 * that both ask the same question of the same bytes. Deliberately a parser rather than a
 * string comparison — a test that compares the file with a string it built itself is
 * only checking that two copies of the same format agree.
 */
final class ExportedCsv {

    static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private ExportedCsv() {
    }

    /** The file minus its BOM, decoded as UTF-8. */
    static String textOf(byte[] file) {
        return textOf(file, StandardCharsets.UTF_8);
    }

    /**
     * The same, decoded as whatever charset is named — so that a test can show a Serbian
     * name coming out wrong under the single-byte charset the BOM exists to prevent.
     */
    static String textOf(byte[] file, Charset charset) {
        return new String(file, BOM.length, file.length - BOM.length, charset);
    }

    static List<CSVRecord> recordsOf(byte[] file) {
        try (CSVParser parser = CSVParser.parse(textOf(file),
                CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).get())) {
            return parser.getRecords();
        } catch (IOException e) {
            throw new AssertionError("The exported file is not readable as RFC 4180 CSV", e);
        }
    }
}
