package com.tarento.commenthub.authentication.model;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class KeyDataTest {

    @Test
    void testConstructorAndGetters() throws NoSuchAlgorithmException {
        // Generate a dummy public key
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        KeyPair keyPair = keyGen.generateKeyPair();
        PublicKey publicKey = keyPair.getPublic();

        // Use constructor
        KeyData keyData = new KeyData("key123", publicKey);

        // Verify values
        assertEquals("key123", keyData.getKeyId());
        assertEquals(publicKey, keyData.getPublicKey());
    }

    @Test
    void testSettersAndGetters() throws NoSuchAlgorithmException {
        KeyData keyData = new KeyData(null, null);

        // Generate another dummy public key
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        KeyPair keyPair = keyGen.generateKeyPair();
        PublicKey newPublicKey = keyPair.getPublic();

        // Set new values
        keyData.setKeyId("newKey");
        keyData.setPublicKey(newPublicKey);

        // Verify updated values
        assertEquals("newKey", keyData.getKeyId());
        assertEquals(newPublicKey, keyData.getPublicKey());
    }
}
