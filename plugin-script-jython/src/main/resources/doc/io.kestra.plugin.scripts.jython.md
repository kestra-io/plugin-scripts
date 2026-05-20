# How to use the Jython plugin

Execute Python 2.7 code on the JVM with direct access to Java classes — no container required.

## Tasks

`Eval` runs inline Python code directly in the Kestra JVM via the Jython interpreter — no `containerImage` or `taskRunner` is needed. `FileTransform` processes Kestra internal storage files (Ion, Avro, JSON) record by record, transforming or filtering rows without writing intermediate files to disk.

Jython implements Python 2.7. For Python 3 compatibility or access to native C extensions (NumPy, pandas, etc.), use the [Python plugin](https://kestra.io/plugins/io.kestra.plugin.scripts.python) instead, which runs in a container with the full CPython runtime.
