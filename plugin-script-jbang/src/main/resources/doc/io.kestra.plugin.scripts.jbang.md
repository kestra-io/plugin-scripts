# How to use the JBang plugin

Run Java source files directly without a build system — JBang compiles and executes them on the fly.

## Common properties

`containerImage` defaults to a JBang image. `taskRunner` defaults to Docker and can be overridden for other execution environments.

## Tasks

`Script` runs inline Java code defined in the `script` property — best for short, flow-specific logic. `Commands` runs JBang commands (e.g., `jbang main.java`) against source files; use it when your code lives in [namespace files](https://kestra.io/docs/concepts/namespace-files) shared across flows, or is cloned from a Git repository with a preceding `Clone` task.

Declare Maven dependencies directly in the source file using JBang's `//DEPS` directive at the top: `//DEPS com.google.guava:guava:32.0.0-jre`. JBang resolves and caches them automatically at runtime — no `pom.xml` or `build.gradle` required.
