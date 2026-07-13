# How to use the .NET (C#) plugin

Run C# scripts and dotnet CLI commands from Kestra workflows using [dotnet-script](https://github.com/dotnet-script/dotnet-script) inside a .NET SDK container.

## Authentication

This plugin has no authentication properties. Use environment variables for secrets (e.g., connection strings, API keys) passed via `env` on the task, or use `{{ secret('SECRET_NAME') }}` in your script body.

## Tasks

### Script

Runs an inline C# script defined in the `script` property. The script is written to a temporary `.csx` file and executed with `dotnet-script`.

`dotnet-script` is installed automatically via `dotnet tool install -g dotnet-script` before each run. To avoid the installation overhead, use a custom `containerImage` that already includes `dotnet-script`.

NuGet package references work out of the box — place `#r "nuget:PackageName,Version"` directives at the top of your script. The first run with a new package reference triggers a NuGet restore which may take 30–60 seconds.

Required properties:
- `script` — inline C# script body in `.csx` format

Optional:
- `containerImage` — defaults to `mcr.microsoft.com/dotnet/sdk:10.0`
- `beforeCommands` — shell commands to run before the script (e.g., set environment variables)
- `inputFiles` — additional files to stage alongside the script
- `outputFiles` — glob patterns for files to capture into Kestra internal storage
- `taskRunner` — override the execution environment (default: Docker)

### Commands

Runs arbitrary shell commands sequentially inside a .NET SDK container. Use this task when script files live in namespace files or are cloned from a Git repository, or when you need raw `dotnet` CLI access (e.g., `dotnet build`, `dotnet test`).

`dotnet-script` is **not** pre-installed in the default image. Add the following to `beforeCommands` when running `.csx` files:

```yaml
beforeCommands:
  - dotnet tool install -g dotnet-script --ignore-failed-sources || true
  - export PATH="$PATH:$HOME/.dotnet/tools"
```

Required properties:
- `commands` — list of shell commands to execute in order

Optional:
- `containerImage` — defaults to `mcr.microsoft.com/dotnet/sdk:10.0`
- `beforeCommands`, `inputFiles`, `namespaceFiles`, `outputFiles`, `taskRunner` — same as `Script`
