package com.tarento.commenthub.authentication.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class Base64Util3Test {

    private Base64Util.Decoder decoder;
    private byte[] output;

    @BeforeEach
    void setUp() {
        output = new byte[100];
        decoder = new Base64Util.Decoder(Base64Util.DEFAULT, output);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = Base64Util.Decoder.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(decoder, value);
    }

    @Test
    void testValidCompleteBase64() {
        byte[] input = "TWFu".getBytes(); // "Man"
        boolean result = decoder.process(input, 0, input.length, true);
        assertTrue(result);
    }

    @Test
    void testValidPartialBase64_OnePad() {
        byte[] input = "TWE=".getBytes(); // "Ma"
        boolean result = decoder.process(input, 0, input.length, true);
        assertTrue(result);
    }

    @Test
    void testValidPartialBase64_TwoPads() {
        byte[] input = "TQ==".getBytes(); // "M"
        boolean result = decoder.process(input, 0, input.length, true);
        assertTrue(result);
    }

    @Test
    void testInvalidPaddingTooMany() {
        byte[] input = "TQ===".getBytes(); // Invalid
        boolean result = decoder.process(input, 0, input.length, true);
        assertFalse(result);
    }

    @Test
    void testInvalidChar_shouldFail() {
        byte[] input = "#WFu".getBytes(); // Invalid character
        boolean result = decoder.process(input, 0, input.length, true);
        assertTrue(result);
    }

    @Test
    void testInvalidStateEarlyExit() throws Exception {
        setField("state", 6); // already failed
        byte[] input = "TWFu".getBytes();
        assertFalse(decoder.process(input, 0, input.length, true));
    }

    @Test
    void testFinishFalsePreservesState() {
        byte[] input = "TQ==".getBytes();
        boolean result = decoder.process(input, 0, input.length, false);
        assertTrue(result);
    }

    @Test
    void testFinishTrue_invalidInState1() throws Exception {
        setField("state", 1);
        byte[] input = new byte[0];
        assertFalse(decoder.process(input, 0, input.length, true));
    }

    @Test
    void testFinishTrue_invalidInState4() throws Exception {
        setField("state", 4); // Expecting second padding character
        byte[] input = new byte[0];
        assertFalse(decoder.process(input, 0, input.length, true));
    }

    @Test
    void testWebSafeDecoder() {
        decoder = new Base64Util.Decoder(Base64Util.URL_SAFE, output);
        byte[] input = "TWF-".getBytes(); // URL-safe variant
        boolean result = decoder.process(input, 0, input.length, true);
        assertTrue(result);
    }

    @Test
    void testEmptyInput() {
        byte[] input = new byte[0];
        boolean result = decoder.process(input, 0, input.length, true);
        assertTrue(result);
    }

    @Test
    void testState2ToState4Transition() {
        byte[] input = "TW==".getBytes(); // Should transition from state 2 to 4
        boolean result = decoder.process(input, 0, input.length, true);
        assertTrue(result);
    }

    @Test
    void testState3ToState5Transition() {
        byte[] input = "TWF=".getBytes(); // Should transition from state 3 to 5
        boolean result = decoder.process(input, 0, input.length, true);
        assertTrue(result);
    }

}