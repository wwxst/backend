package com.web.project.redeem;

import com.web.project.common.exception.BusinessException;
import com.web.project.redeem.support.RedeemCodeGenerator;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RedeemCodeGeneratorTest {

    private final RedeemCodeGenerator generator = new RedeemCodeGenerator();

    @Test
    void shouldGenerateValidCode() {
        String code = generator.generateCode();
        assertNotNull(code);
        assertTrue(code.startsWith("KASI-"));
        assertEquals(21, code.length()); // KASI- + 16 chars
    }

    @Test
    void shouldNormalizeCode() {
        assertEquals("KASI7M4H9Q2XP8RT6KWD", generator.normalizeCode("kasi-7m4h9q2xp8rt6kwd"));
        assertEquals("KASI7M4H9Q2XP8RT6KWD", generator.normalizeCode("KASI-7M4H9Q2XP8RT6KWD"));
        assertEquals("KASI7M4H9Q2XP8RT6KWD", generator.normalizeCode(" KASI-7M4H9Q2XP8RT6KWD "));
        assertEquals("KASI7M4H9Q2XP8RT6KWD", generator.normalizeCode("KASI7M4H9Q2XP8RT6KWD"));
        assertNull(generator.normalizeCode(null));
    }

    @Test
    void shouldUpperCaseCode() {
        String normalized = generator.normalizeCode("kasi-abcd1234efgh5678");
        assertTrue(normalized.equals(normalized.toUpperCase()));
    }

    @Test
    void shouldRejectInvalidPrefix() {
        assertThrows(BusinessException.class, () -> generator.hashCode("XXXX-7M4H9Q2XP8RT6KWD"));
    }

    @Test
    void shouldRejectInvalidLength() {
        String shortCode = "KASI-123";
        assertThrows(BusinessException.class, () -> generator.hashCode(shortCode));
    }

    @Test
    void shouldRejectEmptyCode() {
        assertThrows(BusinessException.class, () -> generator.hashCode(""));
    }

    @Test
    void shouldGenerateSameHashForSameCode() {
        String code = "KASI-7M4H9Q2XP8RT6KWD";
        String hash1 = generator.hashCode(code);
        String hash2 = generator.hashCode("kasi-7m4h9q2xp8rt6kwd");
        assertEquals(hash1, hash2);
        assertEquals(64, hash1.length());
    }

    @Test
    void shouldGenerateDifferentHashForDifferentCode() {
        String hash1 = generator.hashCode("KASI-7M4H9Q2XP8RT6KWD");
        String hash2 = generator.hashCode("KASI-ABCD1234EFGH5678");
        assertNotEquals(hash1, hash2);
    }

    @Test
    void shouldMaskCode() {
        String fullCode = "KASI-7M4H9Q2XP8RT6KWD";
        String masked = generator.maskCode(fullCode);
        assertEquals("KASI-************6KWD", masked);
    }

    @Test
    void shouldGenerateUniqueCodes() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String code = generator.generateCode();
            assertTrue(codes.add(code), "Duplicate code generated: " + code);
        }
        assertEquals(1000, codes.size());
    }

    @Test
    void shouldGenerateUniqueBatchNos() {
        Set<String> batchNos = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            batchNos.add(generator.generateBatchNo());
        }
        assertEquals(100, batchNos.size());
    }

    @Test
    void shouldRemoveExtraDashesAndSpaces() {
        String normalized = generator.normalizeCode("KASI--7M4H-9Q2X-P8RT-6KWD");
        assertEquals("KASI7M4H9Q2XP8RT6KWD", normalized);
    }
}
