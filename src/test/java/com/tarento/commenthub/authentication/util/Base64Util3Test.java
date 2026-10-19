package com.tarento.commenthub.authentication.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(strings = {
        "TWFu",  // "Man" - complete base64
        "TWE=",  // "Ma" - one padding char
        "TQ==",  // "M" - two padding chars
        "#WFu",  // invalid character
        "",      // empty input
        "TW==",  // state 2 to 4 transition
        "TWF="   // state 3 to 5 transition
    })
    void testValidBase64Inputs(String encoded) {
        byte[] input = encoded.getBytes();
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

    @ParameterizedTest
    @ValueSource(strings = {
        "=",     // '=' as the very first byte is illegal in state 0
        "A=",    // one valid char moves state 0 -> 1, then '=' is illegal in state 1
        "TQ=A"   // after one '=' (state 4), a real data character instead of the second '=' is illegal
    })
    void testIllegalPaddingPlacement_Fails(String encoded) {
        byte[] input = encoded.getBytes();
        assertFalse(decoder.process(input, 0, input.length, true));
    }

    @Test
    void testSlowPathState3ValidData_Succeeds() {
        // Embedded whitespace forces the slow per-byte path (bypassing the
        // fast 4-byte loop) so state 3's "emit output triple" branch is exercised.
        byte[] input = "TW Fu".getBytes();
        assertTrue(decoder.process(input, 0, input.length, true));
    }

    @Test
    void testDecoderMaxOutputSize() {
        assertEquals(10 * 3 / 4 + 10, decoder.maxOutputSize(10));
    }

    @Test
    void testEncodeNoPadding_LengthMultipleOfThree() {
        byte[] input = "Man".getBytes(); // length 3, len % 3 == 0
        byte[] result = Base64Util.encode(input, Base64Util.NO_PADDING | Base64Util.NO_WRAP);
        assertNotNull(result);
        assertEquals("TWFu", new String(result));
    }

    @Test
    void testEncodeNoPadding_LengthRemainderTwo() {
        byte[] input = "Ma".getBytes(); // length 2, len % 3 == 2
        byte[] result = Base64Util.encode(input, Base64Util.NO_PADDING | Base64Util.NO_WRAP);
        assertNotNull(result);
        assertEquals("TWE", new String(result));
    }

    /**
     * Sets state to a value outside the 0-6 range handled explicitly by the state machine, to
     * exercise the default arm of both the per-byte switch inside the main loop and the
     * finalization switch reached when finish == true. This state is unreachable through the
     * public API, but is poked the same way the file's other reflection-based tests (e.g.
     * testInvalidStateEarlyExit) already set the internal state field directly.
     */
    @Test
    void testInvalidStateValue_HitsDefaultBranches() throws Exception {
        setField("state", 7);
        byte[] input = "AB".getBytes();
        boolean result = decoder.process(input, 0, input.length, true);
        assertTrue(result);
        assertEquals(0, decoder.op); // default arm performs no decoding work
    }

    /**
     * Embedded whitespace must be skipped transparently regardless of which decoder state it
     * lands in, rather than erroring: state 1 (right after the first data char), state 3 (waiting
     * on the 4th byte of a tuple), state 4 (between the two padding '=' characters) and state 5
     * (trailing, after both paddings have been consumed) each have their own "skip" branch in the
     * source. Each case is verified against the same string with the whitespace removed, decoded
     * through the public API.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "T Wu",   // whitespace in state 1
        "TWF u",  // whitespace in state 3
        "TQ= =",  // whitespace in state 4
        "TQ== "   // whitespace in state 5
    })
    void testEmbeddedWhitespace_IsSkippedTransparently(String withWhitespace) {
        String withoutWhitespace = withWhitespace.replace(" ", "");
        assertArrayEquals(
            Base64Util.decode(withoutWhitespace, Base64Util.DEFAULT),
            Base64Util.decode(withWhitespace, Base64Util.DEFAULT)
        );
    }

    /**
     * Decoding exactly two valid data characters with no padding leaves the state machine in
     * state 2 at finish time, which emits one output byte. No existing test finishes decoding
     * while still in state 2.
     */
    @Test
    void testFinishInState2_NoPadding_EmitsOneByte() {
        byte[] input = "TW".getBytes();
        assertTrue(decoder.process(input, 0, input.length, true));
        assertEquals(1, decoder.op);
    }

}