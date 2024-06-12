package io.kestra.core.tasks.scripts;

import io.kestra.core.junit.annotations.KestraTest;

@KestraTest
class ProcessBuilderBashTest extends AbstractBashTest {
    @Override
    protected Bash.BashBuilder<?, ?> configure(Bash.BashBuilder<?, ?> builder) {
        return builder
            .id(this.getClass().getSimpleName())
            .type(Bash.class.getName());
    }
}
