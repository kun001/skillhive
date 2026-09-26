package com.iflytek.skillhub.domain.label;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LabelPermissionCheckerTest {

    private final LabelPermissionChecker checker = new LabelPermissionChecker();

    private LabelDefinition label(LabelType type) {
        return new LabelDefinition("verified", type, true, 0, "creator");
    }
}
