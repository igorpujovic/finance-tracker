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
- The editor still resolves the generated metamodel. A green `mvn clean test` is not the whole
  of "the build works" — this one was learned the hard way, see below.
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

Each compiler execution clears its own generated-sources directory before running, so the two
passes were deleting each other's work. That cost an hour of a build that compiled 48 sources
and then could not find `Transaction_`.

Moving the metamodel out of `generated-sources/annotations` to solve that was the wrong half to
move, and it broke the editor: the build stayed green while the Java language server reported
334 unresolved-symbol errors, because an IDE resolves `Transaction_` by looking in the
conventional directory and the metamodel was no longer in it. The metamodel stays where it was;
the pass that compiles is the one pointed at a scratch directory it never writes to, and
`annotationProcessorPaths` stays declared for the plugin so an IDE can still find the processor.
The lesson is that `mvn clean test` passing is not the whole of "the build works".

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

### The editor was broken too, and not by the same thing

Reported after the fix above: 334 errors in the editor, on a tree whose `mvn clean test` was
green. They were not about the metamodel — the language server could not resolve the project's
own types, `Transaction cannot be resolved to a type`, `CategoryRepository cannot be resolved to
a type`, `The import com.igorpujovic.finance_tracker.common cannot be resolved`.

The cause is in the language server's log, which had been repeating it since 13:45, well before
any of this: `could not write entity index: Source file already created`, hundreds of times.
The Hibernate processor writes an index of entity names as a side product — twenty-one resources
with names like `entity/index/.CategoryType.EXPENSE` and `entity/index/.EXPENSE` — and the
editor's own processor run cannot create them, which fails its build and leaves the whole
project unresolved. The index is for validating HQL written in annotations across compilations,
which this project never does, so it is now off: `-Aindex=false`, declared for the plugin so the
editor switches it off too. The option is `index`; `entity.index` appears in the processor's own
strings but is not one of its supported options, and javac warns and ignores it.

Two notes from the same pass. The `<fork>true</fork>` added above could not autodetect `javac`
and was silently falling back to whatever was on the PATH, so the executable is now named
explicitly. And `incrementalCompilation`, recorded in 02 as the cure for a class going missing,
is not a parameter `maven-compiler-plugin` has — it was ignored all along, with a warning in
every build nobody read. Its real name, `useIncrementalCompilation`, means the opposite of what
it looks like: set to `false` it compiled 43 of 54 sources after a `clean` and brought the
corruption back at its worst, so it is gone and the default stands.

Measured again on the final configuration: no corruption and no stray index files in twelve
consecutive clean builds, 92 tests green, and no warnings left in the build output.
