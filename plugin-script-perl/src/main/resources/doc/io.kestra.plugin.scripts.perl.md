# How to use the Perl plugin

Run Perl scripts inside a container as flow steps.

## Common properties

`containerImage` defaults to a Perl image. `taskRunner` defaults to Docker and can be overridden for other execution environments.

## Tasks

`Script` runs inline Perl code defined in the `script` property — best for short, flow-specific logic. `Commands` runs shell commands (e.g., `perl main.pl`) against script files; use it when your code lives in [namespace files](https://kestra.io/docs/concepts/namespace-files) shared across flows, or is cloned from a Git repository with a preceding `Clone` task.

Install CPAN modules in `beforeCommands` with `cpanm <Module::Name>` (App::cpanminus is included in the default image), or use a custom `containerImage` pre-built with your dependencies.
