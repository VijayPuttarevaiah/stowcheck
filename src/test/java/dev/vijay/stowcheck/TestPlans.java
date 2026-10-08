package dev.vijay.stowcheck;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public final class TestPlans {

    private TestPlans() {
    }

    public static String load(String name) {
        try (InputStream in = TestPlans.class.getResourceAsStream("/plans/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("No fixture " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
