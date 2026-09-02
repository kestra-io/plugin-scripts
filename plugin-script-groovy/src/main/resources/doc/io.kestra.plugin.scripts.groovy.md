# How to use the Groovy plugin

Execute Groovy code in the Kestra JVM, or run Groovy scripts and commands on a task runner.

## Tasks

`Eval` runs inline Groovy code and is the primary task for general scripting. It executes in-process on the Kestra worker with access to the full JVM classpath — no `containerImage` or `taskRunner` is needed.

`Script` runs an inline Groovy script and `Commands` runs Groovy commands against script files. Both execute on a task runner — Docker by default, using the `groovy` image.

`FileTransform` processes Kestra internal storage files (Ion, Avro, JSON) record by record, transforming or filtering rows without writing intermediate files to disk. It is the right choice when you need lightweight row-level data transformation between tasks.

Add Maven dependencies inline using Grape annotations: `@Grab('group:artifact:version')` at the top of your script resolves the dependency from Maven Central at runtime.

## Container user

The working directory Kestra mounts is not necessarily owned by the image's default user (`groovy`, uid 1000, on `groovy:jdk21`), so a non-root process cannot always write the files that `outputFiles` collects. On the Docker task runner, `Commands` therefore runs the container as `root` unless you set `taskRunner.user` explicitly; set it if you need the container to keep its own default user, and make sure that user can write to the working directory. `Script` always runs as `root` on Docker and ignores `taskRunner.user`.
