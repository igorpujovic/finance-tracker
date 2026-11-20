package com.igorpujovic.finance_tracker.imports;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure logic, so no database. ADR-0006 keeps the parser out of the container: there is
 * nothing here a real PostgreSQL would reveal.
 */
class StatementParserTest {

    private final StatementParser parser = new StatementParser();

    private static String statement(String... dataRows) {
        return "date,description,amount,currency\n" + String.join("\n", dataRows) + "\n";
    }

    @Test
    @DisplayName("a header naming the wrong columns is refused, not read as data")
    void refusesWrongHeader() {
        assertThatThrownBy(() -> parser.parse("""
                datum,opis,iznos,valuta
                01.11.2026,PLATA OKTOBAR,95000.00,RSD
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("date,description,amount,currency");
    }

    @Test
    @DisplayName("a header with the right names in the wrong order is refused")
    void refusesReorderedHeader() {
        assertThatThrownBy(() -> parser.parse("date,amount,description,currency\n"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("an empty file is refused: it has no header at all")
    void refusesEmptyFile() {
        assertThatThrownBy(() -> parser.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a quoted description keeps its comma, and the raw line is kept verbatim")
    void readsQuotedDescription() {
        ParsedStatement statement = parser.parse(statement(
                "01.11.2026,PLATA OKTOBAR,95000.00,RSD",
                "04.11.2026,\"NETFLIX.COM, AMSTERDAM\",-9.99,EUR"));

        assertThat(statement.totalRows()).isEqualTo(2);
        assertThat(statement.rejected()).isEmpty();

        SourceLine netflix = statement.valid().get(1);
        assertThat(netflix.description()).isEqualTo("NETFLIX.COM, AMSTERDAM");
        assertThat(netflix.rawLine()).isEqualTo("04.11.2026,\"NETFLIX.COM, AMSTERDAM\",-9.99,EUR");
        assertThat(netflix.date()).isEqualTo(LocalDate.of(2026, 11, 4));
        assertThat(netflix.amount()).isEqualByComparingTo(new BigDecimal("-9.99"));
        assertThat(netflix.currency()).isEqualTo("EUR");
        assertThat(netflix.rowNumber()).isEqualTo(3);
    }

    @Test
    @DisplayName("the thirty-first of November is not a date, and is not quietly moved to the thirtieth")
    void refusesDayOutsideItsMonth() {
        ParsedStatement statement = parser.parse(statement("31.11.2026,BUS PLUS DOPUNA,-1500.00,RSD"));

        assertThat(statement.valid()).isEmpty();
        assertThat(statement.rejected()).singleElement().satisfies(rejected -> {
            assertThat(rejected.reason()).contains("not a real date");
            assertThat(rejected.rawLine()).isEqualTo("31.11.2026,BUS PLUS DOPUNA,-1500.00,RSD");
            assertThat(rejected.rowNumber()).isEqualTo(2);
        });
    }

    @Test
    @DisplayName("an ISO date is not the format this statement uses")
    void refusesForeignDateFormat() {
        ParsedStatement statement = parser.parse(statement("2026-11-01,PLATA OKTOBAR,95000.00,RSD"));

        assertThat(statement.valid()).isEmpty();
        assertThat(statement.rejected()).singleElement()
                .extracting(RejectedLine::reason).asString().contains("not a real date");
    }

    @Test
    @DisplayName("an amount that is not a number, is zero, or will not fit the column is rejected")
    void refusesUnusableAmounts() {
        ParsedStatement statement = parser.parse(statement(
                "07.11.2026,LIDL 112,abc,RSD",
                "07.11.2026,NOTHING,0.00,RSD",
                "07.11.2026,TOO PRECISE,-12.345,RSD",
                "07.11.2026,TOO BIG,99999999999.00,RSD"));

        assertThat(statement.valid()).isEmpty();
        assertThat(statement.rejected()).extracting(RejectedLine::reason).containsExactly(
                "the amount is not a number",
                "the amount is zero, so it is neither income nor an expense",
                "the amount has more than 2 decimal places",
                "the amount is too large to store");
    }

    @Test
    @DisplayName("a blank description and a currency that is not three letters are each rejected")
    void refusesBlankDescriptionAndBadCurrency() {
        ParsedStatement statement = parser.parse(statement(
                "07.11.2026,\"   \",-100.00,RSD",
                "07.11.2026,MAXI 245,-100.00,R",
                "07.11.2026,MAXI 245,-100.00,DINAR"));

        assertThat(statement.valid()).isEmpty();
        assertThat(statement.rejected()).extracting(RejectedLine::reason).containsExactly(
                "the description is empty",
                "the currency is not three letters",
                "the currency is not three letters");
    }

    @Test
    @DisplayName("a line wrong in two ways reports both reasons, not whichever was checked first")
    void reportsEveryProblemOnALine() {
        ParsedStatement statement = parser.parse(statement("31.11.2026,MAXI 245,abc,RSD"));

        assertThat(statement.rejected()).singleElement()
                .extracting(RejectedLine::reason).asString()
                .contains("not a real date")
                .contains("the amount is not a number");
    }

    @Test
    @DisplayName("a line with too few columns is rejected rather than read as a short one")
    void refusesShortLine() {
        ParsedStatement statement = parser.parse(statement("07.11.2026,MAXI 245,-100.00"));

        assertThat(statement.valid()).isEmpty();
        assertThat(statement.rejected()).singleElement()
                .extracting(RejectedLine::reason).asString().contains("3 columns, not 4");
    }

    @Test
    @DisplayName("a lowercase currency is the same currency, written carelessly")
    void acceptsLowercaseCurrency() {
        ParsedStatement statement = parser.parse(statement("01.11.2026,PLATA OKTOBAR,95000.00,rsd"));

        assertThat(statement.valid()).singleElement()
                .extracting(SourceLine::currency).isEqualTo("RSD");
    }

    @Test
    @DisplayName("every data row lands in exactly one of the two lists")
    void accountsForEveryRow() {
        ParsedStatement statement = parser.parse(statement(
                "01.11.2026,PLATA OKTOBAR,95000.00,RSD",
                "03.11.2026,MAXI 245 BEOGRAD,-2450.80,RSD",
                "31.11.2026,BUS PLUS DOPUNA,-1500.00,RSD",
                "07.11.2026,LIDL 112,abc,RSD"));

        assertThat(statement.totalRows()).isEqualTo(4);
        assertThat(statement.valid().size() + statement.rejected().size())
                .isEqualTo(statement.totalRows());
    }

    @Test
    @DisplayName("blank lines are not data rows and are not counted as any")
    void ignoresBlankLines() {
        ParsedStatement statement = parser.parse("""
                date,description,amount,currency
                01.11.2026,PLATA OKTOBAR,95000.00,RSD

                03.11.2026,MAXI 245 BEOGRAD,-2450.80,RSD
                """);

        assertThat(statement.totalRows()).isEqualTo(2);
        assertThat(statement.valid()).extracting(SourceLine::rawLine).containsExactly(
                "01.11.2026,PLATA OKTOBAR,95000.00,RSD",
                "03.11.2026,MAXI 245 BEOGRAD,-2450.80,RSD");
    }

    @Test
    @DisplayName("a row is numbered by the line it occupies, so a blank line shifts what follows")
    void numbersRowsByTheirLineInTheFile() {
        ParsedStatement statement = parser.parse("""
                date,description,amount,currency
                01.11.2026,PLATA OKTOBAR,95000.00,RSD

                03.11.2026,MAXI 245 BEOGRAD,-2450.80,RSD
                """);

        assertThat(statement.valid()).extracting(SourceLine::rowNumber).containsExactly(2, 4);
    }

    @Test
    @DisplayName("a newline inside a quoted description does not knock the later line numbers out")
    void countsALineBrokenAcrossTwoLines() {
        ParsedStatement statement = parser.parse("""
                date,description,amount,currency
                04.11.2026,"NETFLIX.COM,
                AMSTERDAM",-9.99,EUR
                07.11.2026,LIDL 112,-500.00,RSD
                """);

        assertThat(statement.valid()).extracting(SourceLine::rowNumber).containsExactly(2, 4);
    }
}
