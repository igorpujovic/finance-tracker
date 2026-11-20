package com.igorpujovic.finance_tracker.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * The Kurs API as a real HTTP server on loopback, programmed per date.
 * <p>
 * ADR-0006 asks for this at the HTTP boundary rather than behind a Java interface: the
 * distinction the import rests on — an unpublished currency is the line's own problem, a
 * transport failure is not — is a distinction between HTTP outcomes, and a Java stub
 * programmed to throw proves only that a stub can be programmed to throw. So this serves
 * a genuine 503, a genuine read timeout and a genuine malformed body over a socket.
 * <p>
 * It is the JDK's own {@code com.sun.net.httpserver} rather than WireMock or
 * MockWebServer because Spring Boot 4.2.0 manages no mock-server dependency: either
 * would have meant a version pinned by hand and maintained against the parent, to buy
 * stubbing this needs sixty lines of.
 * <p>
 * The response bodies are written as text rather than serialized from the production
 * DTOs, deliberately. They are the shape the published API documents; building them
 * from {@code KursRates.Rate} would make every test agree with the client by construction
 * and notice nothing if the mapping were wrong.
 */
public final class FakeKurs {

    /** The path the client asks for, per {@code https://kurs.resenje.org/doc}. */
    private static final String RATES = "/api/v1/rates/";

    private final HttpServer server;
    private final Map<LocalDate, Reply> programmed = new ConcurrentHashMap<>();
    private final List<LocalDate> asked = Collections.synchronizedList(new ArrayList<>());

    /** What the fake answers for one date. */
    private sealed interface Reply {

        record Publishing(List<Rate> rates) implements Reply {
        }

        record Unavailable() implements Reply {
        }

        record Garbage() implements Reply {
        }

        record Silence(Duration beforeAnswering) implements Reply {
        }
    }

    /**
     * One published rate, as the API reports it.
     *
     * @param attributedTo the {@code date_from} the API puts on it, which for a weekend
     *                     or a holiday is an earlier working day than the one requested
     * @param exchangeMiddle the middle rate for {@code parity} units, written as the API
     *                       writes it
     */
    public record Rate(String currency, LocalDate attributedTo, String exchangeMiddle, int parity) {

        /** A currency quoted per single unit, which is most of them. */
        public static Rate of(String currency, LocalDate date, String exchangeMiddle) {
            return new Rate(currency, date, exchangeMiddle, 1);
        }

        /**
         * A currency quoted per hundred units, which the National Bank really does do for
         * the ones whose single unit is worth a fraction of a dinar.
         */
        public static Rate perHundred(String currency, LocalDate date, String exchangeMiddle) {
            return new Rate(currency, date, exchangeMiddle, 100);
        }
    }

    private FakeKurs(HttpServer server) {
        this.server = server;
    }

    /** Starts a fake on a port the operating system picks, bound to loopback only. */
    public static FakeKurs started() {
        try {
            HttpServer server = HttpServer.create(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            FakeKurs kurs = new FakeKurs(server);
            server.createContext(RATES, kurs::answer);
            // A pool rather than the default in-line executor, so that the handler
            // sleeping through a read-timeout test does not also block the dispatcher.
            // Daemon threads, because such a handler is still sleeping when the test that
            // timed out on it has long since passed, and a live one would hold the
            // surefire JVM open after the last test finished.
            server.setExecutor(Executors.newCachedThreadPool(runnable -> {
                Thread handler = new Thread(runnable, "fake-kurs");
                handler.setDaemon(true);
                return handler;
            }));
            server.start();
            return kurs;
        } catch (IOException e) {
            throw new UncheckedIOException("The fake Kurs server could not be started", e);
        }
    }

    public String baseUrl() {
        return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
    }

    /** Forgets every programmed reply and every request, so one test cannot read as another. */
    public void reset() {
        programmed.clear();
        asked.clear();
    }

    /** The bank publishes these rates for this date, and nothing else. */
    public void publishes(LocalDate date, Rate... rates) {
        programmed.put(date, new Reply.Publishing(List.of(rates)));
    }

    /** A transport failure in the plainest form the API can produce. */
    public void isUnavailableOn(LocalDate date) {
        programmed.put(date, new Reply.Unavailable());
    }

    /** Answers, eventually — long after any client here is willing to wait. */
    public void hangsOn(LocalDate date) {
        programmed.put(date, new Reply.Silence(Duration.ofSeconds(5)));
    }

    /** Answers 200 with a body that is not the JSON this client was promised. */
    public void answersGarbageOn(LocalDate date) {
        programmed.put(date, new Reply.Garbage());
    }

    /** Every date asked about, in order, including a date asked about twice. */
    public List<LocalDate> datesAsked() {
        synchronized (asked) {
            return List.copyOf(asked);
        }
    }

    private void answer(HttpExchange exchange) throws IOException {
        try (exchange) {
            LocalDate date = dateOf(exchange);
            if (date == null) {
                send(exchange, 404, "{\"code\":404,\"message\":\"Not Found\"}");
                return;
            }
            asked.add(date);
            // An unprogrammed date publishes nothing rather than failing, so that a test
            // about something else is not derailed by a 503 it never set up.
            switch (programmed.getOrDefault(date, new Reply.Publishing(List.of()))) {
                case Reply.Publishing(var rates) -> send(exchange, 200, bodyOf(date, rates));
                case Reply.Unavailable() ->
                        send(exchange, 503, "{\"code\":503,\"message\":\"Service Unavailable\"}");
                case Reply.Garbage() -> send(exchange, 200, "<html>the bank's load balancer is confused</html>");
                case Reply.Silence(var beforeAnswering) -> {
                    sleep(beforeAnswering);
                    send(exchange, 200, bodyOf(date, List.of()));
                }
            }
        }
    }

    /** @return the date in the request path, or null if the path does not name one */
    private LocalDate dateOf(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        try {
            return LocalDate.parse(path.substring(RATES.length()));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * The documented shape: an object wrapping a {@code rates} array, each entry carrying
     * the cash and exchange fields as well as the two this project reads. The extra
     * fields are here on purpose — a client that cannot ignore them is broken against the
     * real API.
     */
    private String bodyOf(LocalDate requested, List<Rate> rates) {
        return rates.stream()
                .map(rate -> """
                        {"code":"%s","date":"%s","date_from":"%s","number":215,"parity":%d,\
                        "cash_buy":1.0,"cash_sell":2.0,"exchange_buy":3.0,\
                        "exchange_middle":%s,"exchange_sell":4.0}"""
                        .formatted(rate.currency(), requested, rate.attributedTo(),
                                rate.parity(), rate.exchangeMiddle()))
                .collect(Collectors.joining(",", "{\"rates\":[", "]}"));
    }

    private void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
