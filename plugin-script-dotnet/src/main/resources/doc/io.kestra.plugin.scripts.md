# How to use the .NET (C# and F#) plugin

The .NET plugin provides tasks and triggers for running C# and F# code inside Kestra workflows.

C# tasks and triggers are available under the `io.kestra.plugin.scripts.csharp` package.

F# tasks and triggers are available under the `io.kestra.plugin.scripts.fsharp` package.

Both languages run inside the existing `plugin-script-dotnet` module.

C# inline scripts use `dotnet-script` and are stored as `.csx` files before execution.

F# inline scripts use F# Interactive through `dotnet fsi` and are stored as `.fsx` files before execution.

Both languages can use the default .NET SDK container or a custom `containerImage`.

Each package also provides `Commands` and `CommandsTrigger` tasks for executing .NET-related commands.

Existing C# workflows using the old `io.kestra.plugin.scripts.dotnet.*` type names remain supported through compatibility aliases.

For C# tasks, commands, and triggers, see the [C# plugin documentation](io.kestra.plugin.scripts.csharp.md).

For F# tasks, commands, and triggers, see the [F# plugin documentation](io.kestra.plugin.scripts.fsharp.md).