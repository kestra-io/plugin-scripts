# How to use the Groovy plugin

Execute Groovy code in the Kestra JVM, or run Groovy scripts and commands on a task runner.

## Tasks

`Eval` runs inline Groovy code and is the primary task for general scripting. It executes in-process on the Kestra worker with access to the full JVM classpath — no `containerImage` or `taskRunner` is needed.

`Script` runs an inline Groovy script and `Commands` runs Groovy commands against script files. Both execute on a task runner — Docker by default, using the `groovy` image.

`FileTransform` processes Kestra internal storage files (Ion, Avro, JSON) record by record, transforming or filtering rows without writing intermediate files to disk. It is the right choice when you need lightweight row-level data transformation between tasks.

Add Maven dependencies inline using Grape annotations: `@Grab('group:artifact:version')` at the top of your script resolves the dependency from Maven Central at runtime.

## Container user

The working directory Kestra mounts is not necessarily owned by the image's default user (`groovy`, uid 1000, on `groovy:jdk21`), so a non-root process cannot always write the files that `outputFiles` collects. On the Docker task runner, `Commands` therefore runs the container as `root` unless you set `taskRunner.user` explicitly; set it if you need the container to keep its own default user, and make sure that user can write to the working directory. `Script` always runs as `root` on Docker and ignores `taskRunner.user`.

## Polling triggers

`ScriptTrigger` runs its own inline Groovy script on an interval; `CommandsTrigger` runs its own list of commands. Both delegate to the existing task runner tasks, with the `groovy` image and a 60-second interval by default.

Set the required `exitCondition` to `exit N` to match a known process exit code, including nonzero exits. Alternatively, use a regex against structured vars emitted with `::{"outputs":{...}}::`. Invalid or excessive regexes fall back to substring matching. Raw stdout/stderr and comparison expressions such as `exitCode != 0` are not condition inputs.

Downstream tasks can read `trigger.timestamp`, `trigger.condition`, `trigger.exitCode`, and `trigger.vars`. Vars emitted before a process failure are preserved. Container startup, image, rendering, and other setup failures cannot satisfy a process exit condition.

With `edge: true` (the default), the first matching evaluation emits; further matches are suppressed until a nonmatch. The previous match is stored in the namespace KV store under `groovy_edge_<flowId length>_<flowId>_<triggerId length>_<triggerId>`, so it survives recreation of the trigger object. Deleting that key resets the edge state. With `edge: false`, each evaluation runs the script/commands again and may emit. A fresh process run is a new observation; this is not an event listener or an exactly-once delivery guarantee across crashes.
