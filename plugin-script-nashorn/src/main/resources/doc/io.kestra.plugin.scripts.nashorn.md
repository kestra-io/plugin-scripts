# How to use the Nashorn plugin

Execute JavaScript in the Kestra JVM using the Nashorn engine — no container required, with direct access to Java classes.

## Tasks

`Eval` runs inline JavaScript directly in the Kestra JVM — no `containerImage` or `taskRunner` is needed. `FileTransform` processes Kestra internal storage files (Ion, Avro, JSON) record by record, transforming or filtering rows without writing intermediate files to disk.

Nashorn runs on the Java 11 engine and supports ES5 with partial ES6 coverage. For modern JavaScript (ES6 modules, async/await, npm packages), use the [Node](https://kestra.io/plugins/io.kestra.plugin.scripts.node) or [Deno](https://kestra.io/plugins/io.kestra.plugin.scripts.deno) plugin instead.
