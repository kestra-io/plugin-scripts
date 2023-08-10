package io.kestra.core.tasks.scripts;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

@MicronautTest
class ProcessBuilderBashTest extends AbstractBashTest {
    @Override
    protected Bash.BashBuilder<?, ?> configure(Bash.BashBuilder<?, ?> builder) {
        return builder
            .id(this.getClass().getSimpleName())
            .type(Bash.class.getName());
    }
}
