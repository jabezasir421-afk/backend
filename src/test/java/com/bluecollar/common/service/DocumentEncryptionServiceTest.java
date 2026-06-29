package com.bluecollar.common.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@ActiveProfiles("test")
class DocumentEncryptionServiceTest {

    @Autowired
    private DocumentEncryptionService documentEncryptionService;

    @Test
    void encryptAndDecryptShouldRoundTripPlaintext() {
        String plaintext = "ABCDE1234F";

        String ciphertext = documentEncryptionService.encrypt(plaintext);
        String decrypted = documentEncryptionService.decrypt(ciphertext);

        assertEquals(plaintext, decrypted);
    }

    @Test
    void encryptShouldProduceDifferentCiphertextForSamePlaintext() {
        String plaintext = "PAN1234567890";

        String first = documentEncryptionService.encrypt(plaintext);
        String second = documentEncryptionService.encrypt(plaintext);

        assertEquals(plaintext, documentEncryptionService.decrypt(first));
        assertEquals(plaintext, documentEncryptionService.decrypt(second));
    }

    @Test
    void encryptAndDecryptShouldReturnNullForNullInput() {
        assertNull(documentEncryptionService.encrypt(null));
        assertNull(documentEncryptionService.decrypt(null));
    }

    @Test
    void encryptAndDecryptShouldReturnBlankForBlankInput() {
        assertEquals("", documentEncryptionService.encrypt(""));
        assertEquals("   ", documentEncryptionService.decrypt("   "));
    }
}
