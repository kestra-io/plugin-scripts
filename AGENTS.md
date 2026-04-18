# Kestra Scripts Plugin

## What

- Provides plugin components under `io.kestra.plugin`.
- Keeps the implementation focused on the integration scope exposed by this repository.

## Why

- This plugin integrates Kestra with Scripts.
- It adds workflow components that reflect the code in this repository.

## How

### Architecture

This is a **multi-module** plugin with 18 submodules:

- `plugin-script`
- `plugin-script-bun`
- `plugin-script-deno`
- `plugin-script-go`
- `plugin-script-groovy`
- `plugin-script-jbang`
- `plugin-script-julia`
- `plugin-script-jython`
- `plugin-script-lua`
- `plugin-script-nashorn`
- `plugin-script-node`
- `plugin-script-perl`
- `plugin-script-php`
- `plugin-script-powershell`
- `plugin-script-python`
- `plugin-script-r`
- `plugin-script-ruby`
- `plugin-script-shell`

### Key Plugin Classes

**plugin-script-bun:**

- `io.kestra.plugin.scripts.bun.Commands`
- `io.kestra.plugin.scripts.bun.Script`
**plugin-script-deno:**

- `io.kestra.plugin.scripts.deno.Commands`
- `io.kestra.plugin.scripts.deno.Script`
**plugin-script-go:**

- `io.kestra.plugin.scripts.go.Commands`
- `io.kestra.plugin.scripts.go.Script`
**plugin-script-groovy:**

- `io.kestra.plugin.scripts.groovy.Commands`
- `io.kestra.plugin.scripts.groovy.Eval`
- `io.kestra.plugin.scripts.groovy.FileTransform`
- `io.kestra.plugin.scripts.groovy.Script`
**plugin-script-jbang:**

- `io.kestra.plugin.scripts.jbang.Commands`
- `io.kestra.plugin.scripts.jbang.Script`
**plugin-script-julia:**

- `io.kestra.plugin.scripts.julia.Commands`
- `io.kestra.plugin.scripts.julia.Script`
**plugin-script-jython:**

- `io.kestra.plugin.scripts.jython.Eval`
- `io.kestra.plugin.scripts.jython.FileTransform`
**plugin-script-lua:**

- `io.kestra.plugin.scripts.lua.Commands`
- `io.kestra.plugin.scripts.lua.Script`
**plugin-script-nashorn:**

- `io.kestra.plugin.scripts.nashorn.Eval`
- `io.kestra.plugin.scripts.nashorn.FileTransform`
**plugin-script-node:**

- `io.kestra.core.tasks.scripts.Node`
- `io.kestra.plugin.scripts.node.Commands`
- `io.kestra.plugin.scripts.node.CommandsTrigger`
- `io.kestra.plugin.scripts.node.Script`
- `io.kestra.plugin.scripts.node.ScriptTrigger`
**plugin-script-perl:**

- `io.kestra.plugin.scripts.perl.Commands`
- `io.kestra.plugin.scripts.perl.Script`
**plugin-script-php:**

- `io.kestra.plugin.scripts.php.Commands`
- `io.kestra.plugin.scripts.php.Script`
**plugin-script-powershell:**

- `io.kestra.plugin.scripts.powershell.Commands`
- `io.kestra.plugin.scripts.powershell.Script`
**plugin-script-python:**

- `io.kestra.core.tasks.scripts.Python`
- `io.kestra.plugin.scripts.python.Commands`
- `io.kestra.plugin.scripts.python.Script`
**plugin-script-r:**

- `io.kestra.plugin.scripts.r.Commands`
- `io.kestra.plugin.scripts.r.Script`
**plugin-script-ruby:**

- `io.kestra.plugin.scripts.ruby.Commands`
- `io.kestra.plugin.scripts.ruby.Script`
**plugin-script-shell:**

- `io.kestra.core.tasks.scripts.Bash`
- `io.kestra.plugin.scripts.shell.Commands`
- `io.kestra.plugin.scripts.shell.CommandsTrigger`
- `io.kestra.plugin.scripts.shell.Script`
- `io.kestra.plugin.scripts.shell.ScriptTrigger`

### Project Structure

```
plugin-scripts/
├── plugin-script/
│   └── src/main/java/...
├── ...                                    # Other submodules
├── build.gradle
├── settings.gradle
└── README.md
```

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
