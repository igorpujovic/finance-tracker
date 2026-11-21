package com.igorpujovic.finance_tracker.report;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * A monthly report as an RFC 4180 file: the category rows and nothing else.
 * <p>
 * No summary block is appended. A file with a header, some records and then three rows
 * of a different shape is not a CSV any more — a spreadsheet reads the trailer as data
 * and a parser reads it as malformed — and the same three figures are one request away
 * from the JSON endpoint.
 * <p>
 * Everything here is a formatting decision, and each one has a failure it prevents.
 */
final class MonthlyReportCsv {

    /**
     * The one place the uncategorized bucket is given a name. The JSON leaves it null,
     * which is the truth of it, but a blank first cell in a spreadsheet reads as a row
     * somebody forgot to fill in rather than as real money with no category.
     */
    static final String UNCATEGORIZED = "Uncategorized";

    /**
     * Excel reads a UTF-8 file as the local code page unless it finds this, which turns
     * every Serbian name in an exported statement into mojibake. It is prepended to the
     * bytes rather than written through the writer, so the character cannot end up
     * inside the first field.
     */
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /**
     * {@code RFC4180} rather than {@code DEFAULT} for the record separator: the default
     * dialect writes the platform's line ending, so the same report would export
     * differently on Windows and in the container. RFC 4180 is CRLF everywhere.
     */
    private static final CSVFormat FORMAT = CSVFormat.RFC4180.builder()
            .setHeader("category", "type", "total_rsd")
            .get();

    private MonthlyReportCsv() {
    }

    static byte[] render(MonthlyReportResponse report) {
        StringWriter text = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(text, FORMAT)) {
            for (CategoryTotalResponse row : report.categories()) {
                printer.printRecord(
                        row.categoryName() == null ? UNCATEGORIZED : row.categoryName(),
                        // The uncategorized bucket has no category and therefore no type
                        // (ADR-0002). An empty cell is the honest answer; inventing one
                        // would have to choose between income and expense for a row that
                        // can hold both.
                        row.categoryType() == null ? "" : row.categoryType().name(),
                        // toPlainString, not toString: a BigDecimal can print itself in
                        // scientific notation, and String.format would read the JVM's
                        // locale and write a decimal comma into a comma-delimited file.
                        row.totalRsd().toPlainString());
            }
        } catch (IOException e) {
            // A StringWriter cannot fail; the checked exception is CSVPrinter's.
            throw new UncheckedIOException(e);
        }
        return withBom(text.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] withBom(byte[] body) {
        byte[] file = Arrays.copyOf(BOM, BOM.length + body.length);
        System.arraycopy(body, 0, file, BOM.length, body.length);
        return file;
    }
}
