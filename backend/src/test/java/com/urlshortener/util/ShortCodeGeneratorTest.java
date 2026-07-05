package com.urlshortener.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ShortCodeGeneratorTest {

    private final ShortCodeGenerator generator = new ShortCodeGenerator();

    @Test
    @DisplayName("generate() returns a code of the configured length")
    void generateReturnsCodeOfConfiguredLength() {
        String code = generator.generate();

        assertThat(code).hasSize(ShortCodeGenerator.CODE_LENGTH);
    }

    @Test
    @DisplayName("generate() uses only Base62 [a-zA-Z0-9] characters")
    void generateUsesOnlyBase62Characters() {
        for (int i = 0; i < 500; i++) {
            assertThat(generator.generate())
                    .matches("[A-Za-z0-9]{" + ShortCodeGenerator.CODE_LENGTH + "}");
        }
    }

    @Test
    @DisplayName("generate() produces distinct codes across many calls (randomness)")
    void generateProducesDistinctCodes() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            codes.add(generator.generate());
        }

        // 62^7 ≈ 3.5e12 keyspace — 1000 draws colliding is astronomically unlikely.
        assertThat(codes).hasSize(1000);
    }

    @Test
    @DisplayName("generateUnique() returns immediately when there is no collision")
    @SuppressWarnings("unchecked")
    void generateUniqueReturnsOnFirstAttemptWhenNoCollision() {
        Predicate<String> exists = mock(Predicate.class);
        when(exists.test(anyString())).thenReturn(false);

        String code = generator.generateUnique(exists);

        assertThat(code).hasSize(ShortCodeGenerator.CODE_LENGTH);
        verify(exists, times(1)).test(anyString());
    }

    @Test
    @DisplayName("generateUnique() retries on collision and returns the first free code")
    @SuppressWarnings("unchecked")
    void generateUniqueRetriesOnCollision() {
        Predicate<String> exists = mock(Predicate.class);
        // First two candidates collide, the third is free.
        when(exists.test(anyString())).thenReturn(true, true, false);

        String code = generator.generateUnique(exists);

        ArgumentCaptor<String> candidates = ArgumentCaptor.forClass(String.class);
        verify(exists, times(3)).test(candidates.capture());
        // The returned code must be the one for which the check returned false.
        assertThat(code).isEqualTo(candidates.getAllValues().get(2));
    }

    @Test
    @DisplayName("generateUnique() gives up with IllegalStateException after max attempts")
    @SuppressWarnings("unchecked")
    void generateUniqueThrowsAfterMaxAttempts() {
        Predicate<String> exists = mock(Predicate.class);
        // Every candidate collides.
        when(exists.test(anyString())).thenReturn(true);

        assertThatThrownBy(() -> generator.generateUnique(exists))
                .isInstanceOf(IllegalStateException.class);

        // MAX_ATTEMPTS is 5 — the loop should try exactly that many times before failing.
        verify(exists, times(5)).test(anyString());
    }
}
