package com.iflytek.skillhub.domain.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeFolderTreeTest {

    private static KnowledgeFolder folder(long id, Long parentId) throws ReflectiveOperationException {
        KnowledgeFolder folder = new KnowledgeFolder(1L, parentId, "f" + id, "owner");
        Field field = KnowledgeFolder.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(folder, id);
        return folder;
    }

    @Test
    void subtreeIncludesEveryDescendant() throws ReflectiveOperationException {
        KnowledgeFolderTree tree = new KnowledgeFolderTree(List.of(
                folder(1, null), folder(2, 1L), folder(3, 2L), folder(4, null)));

        assertThat(tree.subtreeIds(1L)).containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(tree.subtreeIds(4L)).containsExactly(4L);
        assertThat(tree.contains(3L)).isTrue();
        assertThat(tree.contains(9L)).isFalse();
    }

    @Test
    void rejectsMovingIntoItselfOrADescendant() throws ReflectiveOperationException {
        KnowledgeFolderTree tree = new KnowledgeFolderTree(List.of(folder(1, null), folder(2, 1L), folder(3, null)));

        assertThatThrownBy(() -> tree.assertCanMove(1L, 1L)).isInstanceOf(DomainBadRequestException.class);
        assertThatThrownBy(() -> tree.assertCanMove(1L, 2L)).isInstanceOf(DomainBadRequestException.class);
        assertThatCode(() -> tree.assertCanMove(1L, 3L)).doesNotThrowAnyException();
        assertThatCode(() -> tree.assertCanMove(2L, null)).doesNotThrowAnyException();
    }
}
