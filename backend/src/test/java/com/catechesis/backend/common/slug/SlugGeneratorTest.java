package com.catechesis.backend.common.slug;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SlugGeneratorTest {

    // tests go here
    @Test
    void generatesSlugOfExpectedLength() {
        SlugGenerator generator = new SlugGenerator();

        String slug = generator.generate();

        assertThat(slug).hasSize(10);
    }

    @Test
    void generatesSlugUsingOnlyBase62Characters() {
        SlugGenerator generator = new SlugGenerator();
        Pattern base62 = Pattern.compile("^[A-Za-z0-9]+$");

        for (int i = 0; i < 100; i++) {
            String slug = generator.generate();
            assertThat(slug).matches(base62);
        }
    }

    @Test
    void generatesDistinctSlugsAcrossManyCalls() {
        SlugGenerator generator = new SlugGenerator();
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < 10_000; i++) {
            String slug = generator.generate();
            assertThat(seen.add(slug))
                    .as("duplicate slug after %d generations: %s", i, slug)
                    .isTrue();
        }
    }

    @Test
    void producesDeterministicOutputForSeededRandom() {
        SlugGenerator generator = new SlugGenerator(new Random(42L));

        String slug = generator.generate();

        assertThat(slug).isEqualTo("Gpi2C7DgXD");
    }
}
