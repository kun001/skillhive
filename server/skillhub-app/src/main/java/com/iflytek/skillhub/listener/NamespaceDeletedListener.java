package com.iflytek.skillhub.listener;

import com.iflytek.skillhub.domain.event.NamespaceDeletedEvent;
import com.iflytek.skillhub.domain.skill.service.SkillStorageDeletionCompensationService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Queues object cleanup in the deleting transaction, so rollbacks cannot remove files. */
@Component
public class NamespaceDeletedListener {
    private final SkillStorageDeletionCompensationService cleanup;

    public NamespaceDeletedListener(SkillStorageDeletionCompensationService cleanup) {
        this.cleanup = cleanup;
    }

    @EventListener
    public void onDeleted(NamespaceDeletedEvent event) {
        cleanup.enqueueDeletion(event.namespace(), "namespace-knowledge", event.storageKeys());
    }
}
