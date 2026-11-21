package com.igorpujovic.finance_tracker.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Makes the client's own routes survive a page reload.
 *
 * The client is a single page with routing done in the browser (ADR-0008), and
 * it is served from this application's static resources. Clicking from
 * {@code /} to {@code /transactions} never reaches the server, but opening or
 * reloading {@code /transactions} does — and there is no such resource, so
 * without this it is a 404 on a page that works perfectly when navigated to.
 * Each route below forwards to {@code index.html}, which boots the client, which
 * reads the URL and shows the right page.
 *
 * <h2>Why these are listed one by one</h2>
 *
 * The obvious implementation is a catch-all at {@code /**}, and it is a trap:
 * this application also serves {@code /api/**}, {@code /scalar},
 * {@code /swagger-ui.html}, {@code /swagger-ui/**} and {@code /v3/api-docs},
 * and a catch-all competes with all of them. Rather than a pattern plus a list
 * of things to carve out of it — where anything added later is swallowed
 * silently, and the symptom is the API returning HTML — the seven routes the
 * client actually has are named. A route added to the client and not to this
 * list works until someone reloads the page on it, which is the one failure
 * this class exists to prevent, so the two lists are changed together and each
 * says so.
 *
 * <p>{@code /} needs no entry: Boot's welcome-page handling serves
 * {@code index.html} for it already.
 */
@Controller
public class SpaRoutes {

    /**
     * Forwards rather than redirects: the browser's URL has to stay as it is, or
     * the client cannot tell which page was asked for. A forward is resolved
     * inside this application and the reader never sees it.
     *
     * <p>{@code /import/*} covers the detail page of one batch. It is a single
     * wildcard and not {@code **}, because the client has no deeper route and a
     * greedier pattern would start claiming paths nothing serves.
     *
     * <p>{@code /} is in the list even though Boot's welcome-page handling
     * would serve {@code index.html} for it anyway. Routing it through the same
     * forward as every other client route means it also picks up the
     * {@code no-store} of {@link StaticResources}, which the welcome page would
     * not — and a cached root is the whole of the stale-{@code index.html}
     * problem that class exists to prevent.
     */
    @GetMapping({
            "/",
            "/transactions",
            "/import",
            "/import/*",
            "/categories",
            "/rules",
            "/reports",
            "/settings"
    })
    public String clientRoute() {
        return "forward:/index.html";
    }
}
