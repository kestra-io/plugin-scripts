# How to use the Ruby plugin

Run Ruby scripts inside a container as flow steps.

## Common properties

`containerImage` defaults to a Ruby image. Pin a specific version (e.g., `ruby:3.3`) for reproducibility. `taskRunner` defaults to Docker and can be overridden for other execution environments.

## Tasks

`Script` runs inline Ruby code defined in the `script` property — best for short, flow-specific logic. `Commands` runs shell commands (e.g., `ruby main.rb`) against script files; use it when your code lives in [namespace files](https://kestra.io/docs/concepts/namespace-files) shared across flows, or is cloned from a Git repository with a preceding `Clone` task.

Install gems in `beforeCommands` with `gem install <gem-name>`. For Bundler-managed projects, include a `Gemfile` (and optionally `Gemfile.lock`) via namespace files and run `bundle install` in `beforeCommands`.
