# How to use the R plugin

Run R scripts for statistical computing and data analysis inside a container as flow steps.

## Common properties

`containerImage` defaults to an R image (e.g., `r-base`). For data science workflows, consider images from the Rocker project (e.g., `rocker/tidyverse`) which include common packages pre-installed. `taskRunner` defaults to Docker and can be overridden for other execution environments.

## Tasks

`Script` runs inline R code defined in the `script` property — best for short, flow-specific logic. `Commands` runs shell commands (e.g., `Rscript main.R`) against script files; use it when your code lives in [namespace files](https://kestra.io/docs/concepts/namespace-files) shared across flows, or is cloned from a Git repository with a preceding `Clone` task.

Install CRAN packages in `beforeCommands` with `Rscript -e 'install.packages(c("dplyr", "ggplot2"), repos="https://cloud.r-project.org")'`. For reproducible environments, use a `renv.lock` file via namespace files and restore with `Rscript -e 'renv::restore()'` — or build a custom `containerImage` with packages pre-installed to avoid per-run install time.
