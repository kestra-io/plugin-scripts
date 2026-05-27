# How to use the Groovy plugin

Execute Groovy code directly in the Kestra JVM — no container or task runner required.

## Tasks

`Eval` runs inline Groovy code and is the primary task for general scripting. `Script` runs a Groovy script file. Both execute in-process on the Kestra worker with access to the full JVM classpath — no `containerImage` or `taskRunner` is needed.

`FileTransform` processes Kestra internal storage files (Ion, Avro, JSON) record by record, transforming or filtering rows without writing intermediate files to disk. It is the right choice when you need lightweight row-level data transformation between tasks.

Add Maven dependencies inline using Grape annotations: `@Grab('group:artifact:version')` at the top of your script resolves the dependency from Maven Central at runtime.
