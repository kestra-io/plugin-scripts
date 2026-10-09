# How to use the F# plugin

Run F# scripts and .NET CLI commands from Kestra workflows using F# Interactive (`dotnet fsi`) inside a .NET SDK container.

## Authentication

This plugin has no authentication properties. Use environment variables for secrets
(e.g., connection strings, API keys) passed via `env` on the task, or use `{{ secret('SECRET_NAME') }}` in your script body.

## Tasks

### Script

Runs an inline F# script defined in the `script` property. The script is written to a temporary `.fsx` file and executed with `dotnet fsi`.

NuGet package references work out of the box — place `#r "nuget:PackageName,Version"` directives at the top of your script. The first run with a new package reference triggers a NuGet restore which may take 30–60 seconds.

The task type is:

`io.kestra.plugin.scripts.fsharp.Script`

Required properties:

* `script` — inline F# script body in `.fsx` format

Optional:

* `containerImage` — defaults to `mcr.microsoft.com/dotnet/sdk:10.0`
* `beforeCommands` — shell commands to run before the script
* `inputFiles` — additional files to stage alongside the script
* `outputFiles` — glob patterns for files to capture into Kestra internal storage
* `taskRunner` — override the execution environment (default: Docker)

#### Hello World

```yaml
id: fsharp_hello_world
namespace: company.team

tasks:
  - id: hello_fsharp
    type: io.kestra.plugin.scripts.fsharp.Script
    script: |
      printfn "Hello from F# and Kestra!"
```

#### NuGet dependency

```yaml
id: fsharp_nuget
namespace: company.team

tasks:
  - id: hello_fsharp
    type: io.kestra.plugin.scripts.fsharp.Script
    script: |
      #r "nuget:Newtonsoft.Json,13.0.3"

      open Newtonsoft.Json

      let data = {| message = "Hello from Kestra" |}
      printfn "%s" (JsonConvert.SerializeObject(data))
```


#### Generate output files

Files declared in `outputFiles` are persisted in Kestra's internal storage and available to downstream tasks through the task output.

```yaml
id: fsharp_generate_files
namespace: company.team

tasks:
  - id: write_file
    type: io.kestra.plugin.scripts.fsharp.Script
    outputFiles:
      - hello.txt
    script: |
      System.IO.File.WriteAllText("hello.txt", "Hello from F#!")
      printfn "Created hello.txt successfully."
```

### Commands

Runs arbitrary .NET CLI or shell commands sequentially inside a .NET SDK container.

The task type is:

`io.kestra.plugin.scripts.fsharp.Commands`

Required properties:

* `commands` — list of commands to execute in order

Optional:

* `containerImage` — defaults to `mcr.microsoft.com/dotnet/sdk:10.0`
* `beforeCommands`
* `inputFiles`
* `namespaceFiles`
* `outputFiles`
* `taskRunner`

## Triggers

### ScriptTrigger

Polls on an interval by running an inline F# script in a fresh container using `dotnet fsi`, and starts an execution when `exitCondition` matches.

The trigger type is:

`io.kestra.plugin.scripts.fsharp.ScriptTrigger`

Required properties:

* `script` — inline F# script body in `.fsx` format
* `exitCondition` — either `exit N`, which matches when the script exits with code N, or a regex (with substring fallback) matched against the vars the script emits with `::{"outputs":{...}}::`

Optional:

* `interval` — time between polls, defaults to `PT60S`
* `edge` — defaults to `true`, so the trigger fires only when the condition changes from not matching to matching. The previous result is kept in the namespace KV store under a key starting with `trigger-edge-`. Set to `false` to fire on every matching poll
* `containerImage` — defaults to `mcr.microsoft.com/dotnet/sdk:10.0`

The trigger outputs are available as `{{ trigger.timestamp }}`, `{{ trigger.condition }}`, `{{ trigger.exitCode }}` and `{{ trigger.vars }}`. A failed run has no vars, so only an `exit N` condition can match a failure.

### CommandsTrigger

Runs a list of .NET CLI or shell commands the way the `Commands` task does and starts an execution when `exitCondition` matches.

The trigger type is:

`io.kestra.plugin.scripts.fsharp.CommandsTrigger`

Required properties:

* `commands` — list of commands to execute
* `exitCondition` — condition evaluated after each poll

Optional:

* `interval` — time between polls, defaults to `PT60S`
* `edge` — defaults to `true`
* `containerImage` — defaults to `mcr.microsoft.com/dotnet/sdk:10.0`