# How to use the R plugin

Run R scripts for statistical computing and data analysis inside a container as flow steps.

## Common properties

`containerImage` defaults to an R image (e.g., `r-base`). For data science workflows, consider images from the Rocker project (e.g., `rocker/tidyverse`) which include common packages pre-installed. `taskRunner` defaults to Docker and can be overridden for other execution environments.

## Tasks

`Script` runs inline R code defined in the `script` property — best for short, flow-specific logic. `Commands` runs shell commands (e.g., `Rscript main.R`) against script files; use it when your code lives in [namespace files](https://kestra.io/docs/concepts/namespace-files) shared across flows, or is cloned from a Git repository with a preceding `Clone` task.

Install CRAN packages in `beforeCommands` with `Rscript -e 'install.packages(c("dplyr", "ggplot2"), repos="https://cloud.r-project.org")'`. For reproducible environments, use a `renv.lock` file via namespace files and restore with `Rscript -e 'renv::restore()'` — or build a custom `containerImage` with packages pre-installed to avoid per-run install time.

## Triggers

### ScriptTrigger

Polls on an interval by running an inline R script the same way the `Script` task does, and starts an execution when `exitCondition` matches. The script runs in a fresh container on every poll, so keep it quick — and remember that installing CRAN packages on each poll is rarely what you want; prefer an image that already has them.

Required properties:
- `script`: inline R script body
- `exitCondition`: either `exit N`, which matches when the script exits with code N, or a regex (with substring fallback) matched against the vars the script emits with `::{"outputs":{...}}::`

Optional:
- `interval`: time between polls, defaults to `PT60S`
- `edge`: defaults to `true`, so the trigger fires only when the condition changes from not matching to matching. The previous result is kept in the namespace KV store under a key starting with `trigger-edge-`. Set to `false` to fire on every matching poll
- `containerImage`: defaults to `r-base`

The trigger outputs are available as `{{ trigger.timestamp }}`, `{{ trigger.condition }}`, `{{ trigger.exitCode }}` and `{{ trigger.vars }}`. A failed run has no vars, so only an `exit N` condition can match a failure.

### CommandsTrigger

Same behavior as `ScriptTrigger`, but runs a list of shell commands the way the `Commands` task does. Required properties are `commands` and `exitCondition`; `interval`, `edge` and `containerImage` work as above.
