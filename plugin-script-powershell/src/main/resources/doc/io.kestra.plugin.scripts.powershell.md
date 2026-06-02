# How to use the PowerShell plugin

Run PowerShell scripts for Windows automation, Azure management, and cross-platform scripting from Kestra flows.

## Common properties

`containerImage` defaults to `mcr.microsoft.com/powershell`. `taskRunner` defaults to Docker and can be overridden for other execution environments.

## Tasks

`Script` runs inline PowerShell code defined in the `script` property — best for short, flow-specific logic. `Commands` runs PowerShell commands against script files; use it when your scripts live in [namespace files](https://kestra.io/docs/concepts/namespace-files) shared across flows, or are cloned from a Git repository with a preceding `Clone` task.

Install PowerShell modules in `beforeCommands` with `Install-Module -Name <Module> -Force -AllowClobber`. For Azure automation, `Az` module is the standard; for Microsoft Graph, use `Microsoft.Graph`. Use a custom `containerImage` pre-built with your modules to avoid long install times on each execution.
