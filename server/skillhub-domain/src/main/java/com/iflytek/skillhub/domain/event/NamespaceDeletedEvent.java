package com.iflytek.skillhub.domain.event;

import java.util.List;

public record NamespaceDeletedEvent(String namespace, List<String> storageKeys) {
    public NamespaceDeletedEvent {
        storageKeys = List.copyOf(storageKeys);
    }
}
