# 07 — The build writes class files that name classes without their package

Status: resolved

Not a roadmap step, and blocked by nothing. Found while implementing 03, filed separately
because it predates it and affects every step — including 06, whose CI would be red on roughly
every other push.

## Symptom

`mvn clean test` reports success and writes a class file whose constant pool names another class
without its package: `TransactionService` referring to `LCategoryRepository;` instead of
`Lcom/igorpujovic/finance_tracker/category/CategoryRepository;`. The build is green. The failure
comes at class loading:

```
Caused by: java.lang.NoClassDefFoundError: TransactionResponse
Caused by: java.lang.ClassNotFoundException: TransactionResponse
```

— for a class that is present in `target/classes`. In the test suite every `@SpringBootTest`
fails to build an application context, reported as `Error creating bean with name
'transactionController': Lookup method resolution failed`. A built application would not start.

Which classes are affected varies between runs, and they are always the ones in or near the
packages holding the generated `*_` metamodel. A second `mvn clean test` on the same tree
usually passes, which is what makes it so easy to mistake for a flaky test.

## Frequency

Five of eight clean builds of untouched `9fbf401`. Not an edge case.

## Not what issue 02 fixed

02 recorded this family of bug as the incremental check dropping a class, and turned
`incrementalCompilation` off. That was a real bug of a similar shape — a class silently missing
rather than silently wrong — and the fix was right. This is a different failure that survived it.

## Scope

- Find the actual cause rather than another symptom, and show it with numbers rather than one
  green build.
- Fix it in the build, not by retrying.
- Record the decision where the next person to tidy the pom will see it.

## Done when

- A dozen consecutive clean builds produce no class file naming a project class without its
  package.
- The full test suite passes without a retry.
- The reason the compiler plugin is configured the way it is, is written down.

## Comments

Done. Two independent causes, each measured, both fixed; see ADR-0007 for the argument and the
pom for the configuration at each of the three executions.

The investigation, in the order it narrowed:

- **The generated sources are not at fault.** `target/generated-sources` from a corrupt build and
  from a clean one are byte-identical, so the processor's output is right and `javac`'s class
  writing is wrong.
- **Annotation processing in the consuming compilation is one cause.** With the generated
  metamodel copied into `src/main/java` as ordinary files and `-Dmaven.compiler.proc=none`, ten
  of ten builds were clean, against five of eight with processing on.
- **Reuse of the in-process compiler is the other.** A two-pass build — `proc:only` to generate,
  `proc:none` to compile — still corrupted five of twelve builds, always the same class, and
  always in the pass that was the second `javac` in that JVM. The first compilation in a JVM
  appears to be safe; later ones are not.
- **Together they are enough.** Two passes plus `<fork>true</fork>`: no corruption in
  twenty-four consecutive clean builds, main and test classes both scanned.

Measured and rejected: `maven-compiler-plugin` 3.14.0 (3/6 corrupt, same as 3.16.0),
`-XDuseUnsharedTable=false` (0/8 on `compile` alone, which looked like a fix until the second
execution was included: 3/6), and forking without the second pass (1/12 — much better, not
fixed). The first of those is worth remembering as a lesson: measuring only the first half of
the build made a flag that does nothing look like a cure.

The generated sources moved from `generated-sources/annotations` to
`generated-sources/metamodel`, because each compiler execution clears its own generated
directory before running and the two passes were deleting each other's work. That cost an hour
of a build that compiled 48 sources and then could not find `Transaction_`.

How to check it, if this is ever suspected again — the detector that drove every number above
greps the compiled classes for a descriptor naming a project class with no package:

```sh
names=$(find src/main/java src/test/java -name '*.java' -exec basename {} .java \; | sort -u)
classes=$(find target/classes target/test-classes -name '*.class')
for n in $names; do
  grep -a -l -E "(^|[^/[:alnum:]_])L$n;" $classes | sed "s|^|$n <- |"
done
```

Anything printed is a miscompiled class. A single green build proves nothing here; run a dozen.

The build is slower for it: `mvn clean test-compile` went from about 5.4 to about 7.2 seconds
here, for one extra compilation of the main sources and a `javac` process per execution. Three
consecutive `mvn clean test` runs then passed 92 tests on the first attempt each, where before
the fix a `clean test` had a worse than even chance of loading an application context at all.

Verified beyond the local build: `docker compose build app` succeeds with the two-pass compile
in the Maven image, and the jar it produces starts and serves `/api/rules`,
`/api/transactions` and `/api/rules/apply` with nothing in the container log. Forking needs a
real `javac`, which the `maven:3.9-eclipse-temurin-21` build stage has; a build image carrying
only a JRE would not work.
