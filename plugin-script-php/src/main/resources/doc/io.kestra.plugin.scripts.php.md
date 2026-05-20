# How to use the PHP plugin

Run PHP scripts inside a container as flow steps.

## Common properties

`containerImage` defaults to a PHP image. `taskRunner` defaults to Docker and can be overridden for other execution environments.

## Tasks

`Script` runs inline PHP code defined in the `script` property — best for short, flow-specific logic. `Commands` runs shell commands (e.g., `php main.php`) against script files; use it when your code lives in [namespace files](https://kestra.io/docs/concepts/namespace-files) shared across flows, or is cloned from a Git repository with a preceding `Clone` task.

Manage dependencies with Composer — include a `composer.json` via namespace files and run `composer install` in `beforeCommands`. For projects without Composer dependencies, a plain PHP image is sufficient.
