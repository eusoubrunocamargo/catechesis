package com.catechesis.backend.common.slug;

import java.security.SecureRandom;
import java.util.Random;

public class SlugGenerator {

    private static final String CHARSET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int SLUG_LENGTH = 10;

    private final Random random;

    public SlugGenerator() {
        this(new SecureRandom());
    }

    SlugGenerator(Random random) {
        this.random = random;
    }

    public String generate() {
        char[] buffer = new char[SLUG_LENGTH];
        for (int i = 0; i < SLUG_LENGTH; i++) {
            int index = random.nextInt(CHARSET.length());
            buffer[i] = CHARSET.charAt(index);
        }
        return new String(buffer);
    }
}
