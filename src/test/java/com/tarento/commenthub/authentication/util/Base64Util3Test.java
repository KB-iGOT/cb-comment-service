package com.tarento.commenthub.authentication.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    @ParameterizedTest
    @CsvSource({
            "TWFu, true, Valid complete Base64",
            "TWE=, true, Valid partial Base64 with one pad",
            "TQ==, true, Valid partial Base64 with two pads",
            "TQ===, false, Invalid padding too many",
            "#WFu, true, Invalid character should still pass",
            "TW==, true, State 2 to state 4 transition",
            "TWF=, true, State 3 to state 5 transition"
    })
    void testBase64Processing(String input, boolean expected, String description) {
        byte[] inputBytes = input.getBytes();
        boolean result = decoder.process(inputBytes, 0, inputBytes.length, true);
        assertEquals(expected, result, description);
    }

}