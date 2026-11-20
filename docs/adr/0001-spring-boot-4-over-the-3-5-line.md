# The project runs on Spring Boot 4, not the 3.5 line

Spring Initializr produced this project on Spring Boot 4.2.0 and it stays there. The
alternative was dropping to 3.5.x, which is where almost every tutorial, StackOverflow
answer and blog post about Spring Boot still is.

The reason to consider dropping was dependency risk, and it was measured rather than
assumed. The specification requires springdoc-openapi for API documentation, and springdoc
has historically lagged Spring Boot major versions by months. It does not lag this one:
springdoc-openapi 3.2.0 is itself built on `spring-boot-starter-parent` 4.1.2, and both the
Swagger UI and Scalar starters exist for it. Nothing else in the stack is version-sensitive
— Apache Commons CSV, Testcontainers, Flyway and `RestClient` are all indifferent.

What remains is a documentation gap rather than a capability gap. Boot 4 renamed starters
(`spring-boot-starter-webmvc`, `spring-boot-starter-flyway`) and moved some autoconfiguration,
so a snippet copied from a 3.x answer can fail in a way that reads like a mistake in this
code rather than a version mismatch. That is a real cost, paid repeatedly, in exchange for a
`pom.xml` that a reviewer opens and recognises as current.

The decision is recorded because it is the expensive one to reverse. Changing majors once
the import pipeline, the Flyway migrations and the Testcontainers suite exist means touching
every starter, every test slice and the datasource wiring at once.
