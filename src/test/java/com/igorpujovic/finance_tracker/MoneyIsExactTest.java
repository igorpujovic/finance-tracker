package com.igorpujovic.finance_tracker;

import com.igorpujovic.finance_tracker.support.PostgresTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rule 10, which is a rule about types rather than about any one request: money is
 * {@code BigDecimal} in Java, {@code numeric} in the database, and never a binary
 * floating-point number anywhere between them.
 * <p>
 * It is tested as a property of the whole application because that is the only way it
 * can be tested. A {@code double} introduced into the money path breaks nothing on the
 * amounts these tests use — 0.1 + 0.2 is wrong in the seventeenth decimal place, not the
 * second — so no assertion about a single report would notice it. What would notice it is
 * a year of real statements and a balance nobody can reconcile. So the type is asserted
 * directly, in three places: the columns through {@code information_schema}, every
 * declared signature through reflection, and the compiled bodies through the one thing a
 * signature cannot show — a call that converts an exact number into an inexact one.
 * <p>
 * What it does not check: a local variable of type {@code double} that never reaches a
 * signature and never calls one of those conversions, a {@code double[]}, and a type
 * argument such as {@code Map<String, Double>}, which erasure removes. Each would have to
 * be written deliberately and none is a way money arrives at a float by accident.
 * <p>
 * The companion half of the rule, that an amount is never zero, is a constraint rather
 * than a type and is asserted in {@link SchemaConstraintsTest}.
 */
class MoneyIsExactTest extends PostgresTest {

    private static final String CLASS_FILE = ".class";

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("every money column is numeric, carrying the precision and scale the spec names")
    void storesMoneyAsNumeric() {
        assertThat(typeOf("transactions", "amount")).isEqualTo("numeric(12,2)");
        assertThat(typeOf("transactions", "amount_rsd")).isEqualTo("numeric(12,2)");
        assertThat(typeOf("exchange_rates", "rate_to_rsd")).isEqualTo("numeric(12,4)");
    }

    /**
     * Not only the money columns: nothing in this schema has any business being a float,
     * so the whole of it is asserted. A later migration adding one — to a budget, a
     * target, a percentage — is caught by the table it lands in rather than by whether
     * somebody thought to extend this test.
     */
    @Test
    @DisplayName("no column in the schema is a binary floating-point number")
    void storesNothingAsAFloat() {
        assertThat(jdbc.queryForList("""
                select table_name || '.' || column_name
                from information_schema.columns
                where table_schema = 'public' and data_type in ('double precision', 'real')
                """, String.class)).isEmpty();
    }

    @Test
    @DisplayName("not one field, parameter or return type in the application is a float or a double")
    void keepsEveryNumberExactInJava() throws Exception {
        List<String> floating = new ArrayList<>();
        for (Class<?> type : applicationClasses()) {
            for (Field field : type.getDeclaredFields()) {
                if (!field.isSynthetic()) {
                    appendIfFloating(floating, field.getType(),
                            type.getSimpleName() + "." + field.getName());
                }
            }
            for (Method method : type.getDeclaredMethods()) {
                if (method.isSynthetic() || method.isBridge()) {
                    continue;
                }
                String where = type.getSimpleName() + "." + method.getName() + "()";
                appendIfFloating(floating, method.getReturnType(), where);
                for (Class<?> parameter : method.getParameterTypes()) {
                    appendIfFloating(floating, parameter, where);
                }
            }
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                for (Class<?> parameter : constructor.getParameterTypes()) {
                    appendIfFloating(floating, parameter, "new " + type.getSimpleName() + "()");
                }
            }
        }
        assertThat(floating)
                .as("money is BigDecimal end to end; a float in the path would be wrong by amounts "
                        + "too small for any other test here to see")
                .isEmpty();
    }

    /**
     * The hole the signature scan leaves: {@code amount.doubleValue()} in the middle of a
     * method leaves every signature a {@code BigDecimal} and loses the money anyway. The
     * call is in the constant pool of whatever class makes it, under the name it was
     * compiled against, so the compiled bodies are searched for the four conversions that
     * turn an exact number into an inexact one.
     * <p>
     * A class file is read as Latin-1 rather than parsed: these names are ASCII, and the
     * constant pool stores them verbatim. The cost of that shortcut is that the same word
     * in a string literal would also be found, which is a false positive worth having.
     */
    @Test
    @DisplayName("nothing in the application converts an exact amount into a floating-point one")
    void convertsNothingToAFloatInsideAMethod() throws Exception {
        List<String> conversions = List.of("doubleValue", "floatValue", "parseDouble", "parseFloat");
        List<String> guilty = new ArrayList<>();

        for (Path classFile : applicationClassFiles()) {
            String bytes = Files.readString(classFile, StandardCharsets.ISO_8859_1);
            conversions.stream()
                    .filter(bytes::contains)
                    .forEach(conversion -> guilty.add(classFile.getFileName() + " calls " + conversion));
        }
        assertThat(guilty).isEmpty();
    }

    private String typeOf(String table, String column) {
        return jdbc.queryForObject("""
                select data_type || '(' || numeric_precision || ',' || numeric_scale || ')'
                from information_schema.columns
                where table_schema = 'public' and table_name = ? and column_name = ?
                """, String.class, table, column);
    }

    private static void appendIfFloating(List<String> floating, Class<?> type, String where) {
        if (type == double.class || type == float.class || type == Double.class || type == Float.class) {
            floating.add(where + " is a " + type.getSimpleName());
        }
    }

    /**
     * Every class the application itself compiled, loaded by name. Loaded without
     * initializing them: this asks what the types are, and running a static initializer
     * to find out would be a side effect for nothing.
     */
    private static List<Class<?>> applicationClasses() throws Exception {
        Path root = applicationClassesDirectory();
        return applicationClassFiles().stream()
                .map(path -> root.relativize(path).toString().replace(File.separatorChar, '.'))
                .<Class<?>>map(file -> load(file.substring(0, file.length() - CLASS_FILE.length())))
                .toList();
    }

    /**
     * The compiled application, as files. The generated metamodel is in here and the test
     * classes — with their JSON literals, which really are doubles — are not, because the
     * directory is the one the application's own entry point was loaded from.
     */
    private static List<Path> applicationClassFiles() throws Exception {
        try (Stream<Path> files = Files.walk(applicationClassesDirectory())) {
            List<Path> classFiles = files
                    .filter(path -> path.getFileName().toString().endsWith(CLASS_FILE))
                    .toList();
            assertThat(classFiles)
                    .as("the scan found the application, not an empty directory")
                    .hasSizeGreaterThan(50);
            return classFiles;
        }
    }

    private static Path applicationClassesDirectory() throws Exception {
        return Path.of(FinanceTrackerApplication.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name, false, MoneyIsExactTest.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new AssertionError("A compiled class could not be loaded by name: " + name, e);
        }
    }
}
