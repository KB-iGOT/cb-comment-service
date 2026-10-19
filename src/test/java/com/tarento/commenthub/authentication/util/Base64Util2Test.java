package com.tarento.commenthub.authentication.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class Base64Util2Test {

    private Base64Util.Encoder encoder;
    private byte[] output;

    @BeforeEach
    void setUp() {
        output = new byte[1024];
        encoder = new Base64Util.Encoder(Base64Util.DEFAULT, output);
    }

    private void setField(String fieldName, Object value) throws Exception {
        Field field = Base64Util.Encoder.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(encoder, value);
    }

    private byte[] invokeProcess(byte[] input, boolean finish) {
        encoder.process(input, 0, input.length, finish);
        return Arrays.copyOf(output, encoder.op);
    }

    @Test
    void testCompleteBlockFinish() {
        byte[] input = {0x01, 0x02, 0x03};
        byte[] encoded = invokeProcess(input, true);
        assertTrue(new String(encoded).startsWith("AQID"));
    }

    @Test
    void testOneByteTailFinish() throws Exception {
        setField("tailLen", 1);
        byte[] tail = new byte[2];
        tail[0] = 0x01;
        setField("tail", tail);
        byte[] input = {0x02, 0x03};
        byte[] encoded = invokeProcess(input, true);
        assertTrue(new String(encoded).contains("AQID"));
    }

    @Test
    void testTwoByteTailFinish() throws Exception {
        setField("tailLen", 2);
        byte[] tail = new byte[2];
        tail[0] = 0x01;
        tail[1] = 0x02;
        setField("tail", tail);
        byte[] input = {0x03};
        byte[] encoded = invokeProcess(input, true);
        assertTrue(new String(encoded).contains("AQID"));
    }

    @Test
    void testFinishOneRemainingByte() {
        byte[] input = {0x01};
        byte[] encoded = invokeProcess(input, true);
        assertTrue(new String(encoded).contains("AQ=="));
    }

    @Test
    void testFinishTwoRemainingBytes() {
        byte[] input = {0x01, 0x02};
        byte[] encoded = invokeProcess(input, true);
        assertTrue(new String(encoded).contains("AQI="));
    }

    @Test
    void testTailSavedIfFinishFalse_1Byte() {
        byte[] input = {0x11};
        invokeProcess(input, false);
        assertEquals(1, encoder.tailLen);
    }

    @Test
    void testTailSavedIfFinishFalse_2Bytes() {
        byte[] input = {0x11, 0x12};
        invokeProcess(input, false);
        assertEquals(2, encoder.tailLen);
    }

    @Test
    void testWithCRLF() throws Exception {
        encoder = new Base64Util.Encoder(Base64Util.CRLF, output);
        setField("count", 1); // Force newline
        byte[] input = new byte[6]; // Small input to trigger newline
        Arrays.fill(input, (byte) 0x01);
        byte[] encoded = invokeProcess(input, true);
        String str = new String(encoded);
        assertTrue(str.contains("\r\n"));
    }

    @Test
    void testNoNewlineIfDisabled() {
        encoder = new Base64Util.Encoder(Base64Util.NO_WRAP, output);
        byte[] input = {0x01, 0x02, 0x03};
        byte[] encoded = invokeProcess(input, true);
        assertFalse(new String(encoded).contains("\n"));
    }

    /**
     * Forces the tail-flush block's own newline trigger (count hits 0 right as the carried-over
     * tail byte completes a tuple) to fire with doCr == false, so only '\n' is emitted instead of
     * "\r\n". testWithCRLF already covers the doCr == true side of this same check.
     */
    @Test
    void testTailFlushNewline_WithoutCR() throws Exception {
        encoder = new Base64Util.Encoder(Base64Util.DEFAULT, output); // doCr=false, doNewline=true
        setField("tailLen", 1);
        byte[] tail = new byte[2];
        tail[0] = 0x01;
        setField("tail", tail);
        setField("count", 1); // force the flushed tuple to land exactly on the line boundary
        byte[] input = {0x02, 0x03};
        byte[] encoded = invokeProcess(input, true);
        String str = new String(encoded);
        assertTrue(str.contains("\n"));
        assertFalse(str.contains("\r"));
    }

    /**
     * Sets tailLen to a value outside {0, 1, 2} via reflection to exercise the default arm of the
     * tail-handling switch at the top of process(). This state is unreachable through the public
     * API (tailLen is only ever 0, 1 or 2), but is reachable the same way the file's other
     * reflection-based tests (e.g. testOneByteTailFinish) already poke internal fields directly.
     * finish=false is used deliberately so the trailing "assert tailLen == 0" (which this
     * contrived value would violate) is never reached.
     */
    @Test
    void testTailLenInvalidValue_HitsDefaultBranch() throws Exception {
        setField("tailLen", 3);
        byte[] input = {0x01, 0x02, 0x03};
        byte[] encoded = invokeProcess(input, false);
        // The default case leaves v == -1, so the tail-flush block is skipped and the 3 input
        // bytes are still encoded normally via the main loop.
        assertTrue(new String(encoded).startsWith("AQID"));
    }

    /**
     * Case-1 tail (tailLen == 1) with zero further input bytes available: the "p + 2 <= len"
     * guard is false, so the tail is NOT flushed at the top of process() and instead falls
     * through to the finish-section's "one extra byte" finalization, which drains the tail via
     * its tailLen > 0 ternary branch (the input[p++] branch is already covered by
     * testFinishOneRemainingByte).
     */
    @Test
    void testOneByteTailFinish_NoMoreInput() throws Exception {
        setField("tailLen", 1);
        byte[] tail = new byte[2];
        tail[0] = 0x01;
        setField("tail", tail);
        byte[] input = new byte[0];
        byte[] encoded = invokeProcess(input, true);
        assertTrue(new String(encoded).contains("AQ=="));
    }

    /**
     * Case-2 tail (tailLen == 2) with zero further input bytes available: the "p + 1 <= len"
     * guard is false, so the tail is NOT flushed at the top of process() and instead falls
     * through to the finish-section's "two extra bytes" finalization, draining both tail bytes
     * via its tailLen > 1 / tailLen > 0 ternary branches (the input[p++] branches of both
     * ternaries are already covered by testFinishTwoRemainingBytes).
     */
    @Test
    void testTwoByteTailFinish_NoMoreInput() throws Exception {
        setField("tailLen", 2);
        byte[] tail = new byte[2];
        tail[0] = 0x01;
        tail[1] = 0x02;
        setField("tail", tail);
        byte[] input = new byte[0];
        byte[] encoded = invokeProcess(input, true);
        assertTrue(new String(encoded).contains("AQI="));
    }

    /**
     * Covers the "one extra byte" finalization's doNewline == false branch; every other test
     * reaching this path leaves newlines enabled.
     */
    @Test
    void testFinishOneRemainingByte_NoWrap() {
        encoder = new Base64Util.Encoder(Base64Util.NO_WRAP, output);
        byte[] input = {0x01};
        byte[] encoded = invokeProcess(input, true);
        assertEquals("AQ==", new String(encoded));
    }

    /**
     * Covers the "two extra bytes" finalization's doCr == true branch; every other test reaching
     * this path leaves CRLF disabled.
     */
    @Test
    void testFinishTwoRemainingBytes_CRLF() {
        encoder = new Base64Util.Encoder(Base64Util.CRLF, output);
        byte[] input = {0x01, 0x02};
        byte[] encoded = invokeProcess(input, true);
        assertEquals("AQI=\r\n", new String(encoded));
    }

    /**
     * finish=false with an input whose length is an exact multiple of 3: neither "one leftover
     * byte" nor "two leftover bytes" condition holds, covering the false outcome of the
     * "p == len - 2" check (the true outcome is already covered by
     * testTailSavedIfFinishFalse_2Bytes).
     */
    @Test
    void testTailSavedIfFinishFalse_ExactMultipleOfThree() {
        byte[] input = {0x01, 0x02, 0x03};
        invokeProcess(input, false);
        assertEquals(0, encoder.tailLen);
    }

}
