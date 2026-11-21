package com.igorpujovic.finance_tracker.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

/**
 * How the browser is allowed to cache the client (ADR-0008).
 *
 * The two kinds of file the client build produces want opposite answers, and
 * giving them the same one breaks the application in one direction or the
 * other.
 *
 * <p><b>{@code /assets/*} may be cached forever.</b> Vite puts a hash of the
 * contents in each filename, so {@code index-DEzV3-XK.js} is not a name that
 * can ever hold different bytes. A new build produces a new name, which is a
 * cache miss by construction — there is nothing to revalidate and no reason to
 * ask.
 *
 * <p><b>{@code index.html} must not be cached at all.</b> It is the one file
 * with a fixed name, and it is the file that names the hashed ones. Cached, it
 * goes on pointing at the assets of the build it came from — and those are
 * gone, because the jar only carries the current build's. The symptom is a
 * blank page with a 404 for a script, for returning visitors only, after a
 * deployment that tested perfectly in a fresh browser. Without this class
 * Spring sends no cache headers for it at all, which leaves the decision to
 * each browser's heuristics; Chrome's is to cache it.
 */
@Configuration
public class StaticResources implements WebMvcConfigurer {

    private static final Duration ONE_YEAR = Duration.ofDays(365);

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(ONE_YEAR).immutable());

        // The location is the directory the file sits in, not the file: a
        // resource location naming a file resolves nothing and turns every
        // request for the client into a 404.
        //
        // `/` is not registered here. It is mapped by SpaRoutes, which forwards
        // it to this very path, so the root and a reloaded client route are
        // answered by one handler with one set of headers.
        registry.addResourceHandler("/index.html")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noStore());
    }
}
