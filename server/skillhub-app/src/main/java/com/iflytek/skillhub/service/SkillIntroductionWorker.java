package com.iflytek.skillhub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.skill.SkillIntroduction;
import com.iflytek.skillhub.domain.skill.SkillIntroductionGenerator;
import com.iflytek.skillhub.domain.skill.SkillIntroductionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.infra.llm.SkillIntroductionProperties;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Uses short atomic DB operations around the HTTP request, never a transaction during inference. */
@Component
@ConditionalOnProperty(name = "skillhub.introduction.enabled", havingValue = "true")
public class SkillIntroductionWorker {
    private static final Logger log = LoggerFactory.getLogger(SkillIntroductionWorker.class);
    private final SkillIntroductionRepository repository;
    private final SkillVersionRepository versions;
    private final SkillIntroductionGenerator generator;
    private final SkillIntroductionProperties properties;
    private final ObjectMapper mapper;
    private final Clock clock;

    public SkillIntroductionWorker(SkillIntroductionRepository repository, SkillVersionRepository versions,
            SkillIntroductionGenerator generator, SkillIntroductionProperties properties, ObjectMapper mapper, Clock clock) {
        this.repository = repository;
        this.versions = versions;
        this.generator = generator;
        this.properties = properties;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${skillhub.introduction.poll-interval-ms:5000}",
            initialDelayString = "${skillhub.introduction.poll-interval-ms:5000}", scheduler = "skillIntroductionScheduler")
    public void poll() {
        repository.expireExhausted(clock.instant(), properties.maxAttempts());
        for (var job : repository.findReady(clock.instant(), properties.maxAttempts(), PageRequest.of(0, 5))) {
            process(job);
        }
    }

    void process(SkillIntroduction job) {
        String token = UUID.randomUUID().toString();
        var now = clock.instant();
        var lease = now.plus(properties.readTimeout()).plus(properties.connectTimeout()).plusSeconds(60);
        if (repository.claim(job.getVersionId(), token, now, lease, properties.maxAttempts()) == 0) {
            return;
        }
        try {
            var version = versions.findById(job.getVersionId()).orElseThrow();
            var metadata = mapper.readTree(version.getParsedMetadataJson());
            var input = new LinkedHashMap<String, String>();
            // Only explicitly selected prose is sent, excluding unrelated package files and audit data.
            input.put("name", metadata.path("name").asText("").substring(0, Math.min(metadata.path("name").asText("").length(), 300)));
            input.put("description", metadata.path("description").asText(""));
            // Long code samples and API signatures distract from the user-facing capability.
            // The original package and metadata remain untouched.
            String prose = metadata.path("body").asText("")
                    .replaceAll("(?ms)^```[^\\n]*\\n.*?^```[^\\n]*$", "")
                    .replaceAll("(?ms)^~~~[^\\n]*\\n.*?^~~~[^\\n]*$", "");
            input.put("body", prose.lines().filter(line -> line.length() <= 500)
                    .collect(java.util.stream.Collectors.joining("\n")));
            var content = generator.generate(mapper.writeValueAsString(input));
            repository.complete(job.getVersionId(), token, content.zh().functionDescription(), content.zh().usageInstructions(),
                    content.en().functionDescription(), content.en().usageInstructions(), clock.instant());
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            boolean exhausted = job.getAttempts() + 1 >= properties.maxAttempts();
            repository.fail(job.getVersionId(), token, exhausted ? "FAILED" : "PENDING",
                    clock.instant().plus(properties.retryDelay().multipliedBy(job.getAttempts() + 1L)),
                    exception instanceof IllegalArgumentException ? "INVALID_OUTPUT" : "GENERATION_FAILED");
            // Do not log provider errors, response text, uploaded content, URLs, or credentials.
            log.warn("Skill introduction generation failed for version {}; attempt {}", job.getVersionId(), job.getAttempts() + 1);
        }
    }
}
