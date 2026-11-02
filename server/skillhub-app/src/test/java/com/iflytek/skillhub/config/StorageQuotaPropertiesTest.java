package com.iflytek.skillhub.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

class StorageQuotaPropertiesTest {

    @Test
    void applicationYmlDefaultsTo100MegabytesWithAdminRolesExempt() throws IOException {
        StorageQuotaProperties properties = bind(Map.of());

        assertThat(properties.getPerUserBytes()).isEqualTo(104_857_600L);
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getExemptRoles()).containsExactlyInAnyOrder("SUPER_ADMIN", "SKILL_ADMIN", "USER_ADMIN");
    }

    @Test
    void environmentVariablesOverrideQuotaAndExemptRoles() throws IOException {
        StorageQuotaProperties properties = bind(Map.of(
                "SKILLHUB_USER_STORAGE_QUOTA_BYTES", "524288000",
                "SKILLHUB_USER_STORAGE_QUOTA_EXEMPT_ROLES", "SUPER_ADMIN"));

        assertThat(properties.getPerUserBytes()).isEqualTo(524_288_000L);
        assertThat(properties.getExemptRoles()).containsExactly("SUPER_ADMIN");
    }

    @Test
    void zeroOrNegativeQuotaDisablesTheQuota() throws IOException {
        assertThat(bind(Map.of("SKILLHUB_USER_STORAGE_QUOTA_BYTES", "0")).isEnabled()).isFalse();
        assertThat(bind(Map.of("SKILLHUB_USER_STORAGE_QUOTA_BYTES", "-1")).isEnabled()).isFalse();
    }

    @Test
    void codeDefaultsMatchApplicationYml() {
        StorageQuotaProperties properties = new StorageQuotaProperties();

        assertThat(properties.getPerUserBytes()).isEqualTo(StorageQuotaProperties.DEFAULT_PER_USER_BYTES);
        assertThat(properties.getExemptRoles()).containsExactlyInAnyOrderElementsOf(StorageQuotaProperties.DEFAULT_EXEMPT_ROLES);
    }

    private StorageQuotaProperties bind(Map<String, Object> envVars) throws IOException {
        ConfigurableEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("test-env", envVars));
        List<PropertySource<?>> yamlSources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        yamlSources.forEach(source -> environment.getPropertySources().addLast(source));
        ConfigurationPropertySources.attach(environment);
        return Binder.get(environment)
                .bind("skillhub.storage-quota", StorageQuotaProperties.class)
                .orElseThrow(() -> new IllegalStateException("Failed to bind storage quota properties"));
    }
}
