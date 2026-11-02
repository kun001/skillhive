package com.iflytek.skillhub.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

class SkillPublishPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void bindsAllowedFileExtensionsFromEnvironmentStyleProperty() {
        contextRunner
                .withInitializer((context) -> context.getEnvironment().getPropertySources().addFirst(
                        new SystemEnvironmentPropertySource(
                                "test-env",
                                Map.of("SKILLHUB_PUBLISH_ALLOWED_FILE_EXTENSIONS", ".docx,.xsd,.pptx")
                        )
                ))
                .run((context) -> {
                    SkillPublishProperties properties = context.getBean(SkillPublishProperties.class);

                    assertThat(properties.getAllowedFileExtensions())
                            .containsExactly(".docx", ".xsd", ".pptx");
                });
    }

    @Test
    void applicationYml_defaultsMaxFileCountTo500() throws IOException {
        SkillPublishProperties properties = bindFromApplicationYml(Map.of());

        assertThat(properties.getMaxFileCount()).isEqualTo(500);
    }

    @Test
    void applicationYml_allowsEnvironmentVariableToOverrideMaxFileCount() throws IOException {
        SkillPublishProperties properties = bindFromApplicationYml(
                Map.of("SKILLHUB_PUBLISH_MAX_FILE_COUNT", "800")
        );

        assertThat(properties.getMaxFileCount()).isEqualTo(800);
    }

    @Test
    void singleFileLimitDefaultsTo15MegabytesAndPackageLimitStays100() throws IOException {
        SkillPublishProperties properties = bindFromApplicationYml(Map.of());

        assertThat(properties.getMaxSingleFileSize()).isEqualTo(15L * 1024 * 1024);
        assertThat(properties.getMaxPackageSize()).isEqualTo(100L * 1024 * 1024);
        assertThat(new SkillPublishProperties().getMaxSingleFileSize()).isEqualTo(15L * 1024 * 1024);
    }

    private SkillPublishProperties bindFromApplicationYml(Map<String, Object> envVars) throws IOException {
        ConfigurableEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("test-env", envVars));
        List<PropertySource<?>> yamlSources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        yamlSources.forEach(source -> environment.getPropertySources().addLast(source));
        ConfigurationPropertySources.attach(environment);

        return Binder.get(environment)
                .bind("skillhub.publish", SkillPublishProperties.class)
                .orElseThrow(() -> new IllegalStateException("Failed to bind skill publish properties"));
    }

    @Configuration
    @EnableConfigurationProperties(SkillPublishProperties.class)
    static class TestConfig {
    }
}
