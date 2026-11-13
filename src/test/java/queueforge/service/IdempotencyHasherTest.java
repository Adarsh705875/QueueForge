package queueforge.service;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class IdempotencyHasherTest {
    @Test void sameRequestProducesSameHash() {
        var time = LocalDateTime.of(2026, 11, 13, 10, 0);
        assertEquals(IdempotencyHasher.hash("SEND_EMAIL", "hello", 5, 5, time),
                IdempotencyHasher.hash("SEND_EMAIL", "hello", 5, 5, time));
    }
    @Test void differentPayloadProducesDifferentHash() {
        var time = LocalDateTime.of(2026, 11, 13, 10, 0);
        assertNotEquals(IdempotencyHasher.hash("SEND_EMAIL", "hello", 5, 5, time),
                IdempotencyHasher.hash("SEND_EMAIL", "goodbye", 5, 5, time));
    }
    @Test void lengthPrefixAvoidsDelimiterAmbiguity() {
        assertNotEquals(IdempotencyHasher.hash("A|B", "C", 5, 5, null),
                IdempotencyHasher.hash("A", "B|C", 5, 5, null));
    }
}
