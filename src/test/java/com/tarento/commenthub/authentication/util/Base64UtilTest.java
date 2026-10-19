package com.tarento.commenthub.authentication.util;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class Base64UtilTest {

    /**
     * Tests that the decode method throws an IllegalArgumentException when given input with incorrect padding.
     * This tests the explicitly handled edge case in the method's implementation where incorrect padding is detected.
     */
    @Test
    void testDecodeWithIncorrectPadding() {
        String inputWithIncorrectPadding = "SGVsbG8gV29ybGQ====="; // Extra padding

        assertThrows(IllegalArgumentException.class, () -> {
            Base64Util.decode(inputWithIncorrectPadding, Base64Util.DEFAULT);
        });
    }

    /**
     * Tests that decode throws IllegalArgumentException when input contains incorrect padding.
     */
    @Test
    void testDecodeWithIncorrectPadding_2() {
        byte[] input = "Invalid==Padding".getBytes();

        assertThrows(IllegalArgumentException.class, () -> {
            Base64Util.decode(input, Base64Util.DEFAULT);
        });
    }

    /**
     * Tests that the encode method handles an empty input byte array correctly.
     * This is an edge case where the input is valid but contains no data.
     */
    @Test
    void testEncodeEmptyInput() {
        byte[] emptyInput = new byte[0];
        byte[] result = Base64Util.encode(emptyInput, Base64Util.DEFAULT);
        assertNotNull(result);
        assertEquals(0, result.length);
    }


    /**
     * Tests encoding with padding, non-multiple of 3 input length, and newline insertion.
     * 
     * Path constraints: 
     * - encoder.do_padding is true
     * - len % 3 > 0 
     * - encoder.do_newline && len > 0
     */
    @Test
    void testEncodeWithPaddingAndNewline() {
        byte[] input = "Hello, World!".getBytes();
        int offset = 0;
        int len = input.length;
        int flags = Base64Util.DEFAULT;

        byte[] result = Base64Util.encode(input, offset, len, flags);

        String expected = "SGVsbG8sIFdvcmxkIQ==\n";
        assertEquals(expected, new String(result));
    }

    /**
     * Test encoding with zero-length input.
     * This tests the edge case of providing an empty byte array as input.
     */
    @Test
    void testEncodeWithZeroLengthInput() {
        byte[] input = new byte[0];
        byte[] result = Base64Util.encode(input, Base64Util.DEFAULT);
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    /**
     * Tests the decode method with a simple Base64 encoded string.
     * This test verifies that the method correctly decodes a standard Base64 string
     * using the default flags.
     */
    @Test
    void test_decode_1() {
        String input = "SGVsbG8gV29ybGQ=";
        byte[] expected = "Hello World".getBytes();
        byte[] result = Base64Util.decode(input, Base64Util.DEFAULT);
        assertArrayEquals(expected, result);
    }

    /**
     * Test case for decoding a simple Base64 encoded string
     * This test verifies that the decode method correctly decodes a Base64 encoded input
     * using the default flags (Base64Util.DEFAULT)
     */
    @Test
    void test_decode_1_2() {
        String input = "SGVsbG8gV29ybGQ="; // "Hello World" in Base64
        byte[] expectedOutput = "Hello World".getBytes();
        byte[] result = Base64Util.decode(input.getBytes(), Base64Util.DEFAULT);
        assertArrayEquals(expectedOutput, result);
    }

    /**
     * Test case for decoding an empty input array.
     * This test verifies that the decode method correctly handles an empty input
     * and returns an empty byte array without throwing an exception.
     */
    @Test
    void test_decode_emptyInput() {
        byte[] input = new byte[0];
        byte[] result = Base64Util.decode(input, 0, 0, Base64Util.DEFAULT);
        assertArrayEquals(new byte[0], result);
    }

    /**
     * Test case for decoding a Base64 input that results in an output of exact length.
     * This test verifies that the decode method correctly processes the input and
     * returns the output array without needing to create a new array.
     */
    @Test
    void test_decode_exactOutputLength() {
        // Input that will decode to an exact length output
        byte[] input = "SGVsbG8gV29ybGQ=".getBytes();
        int offset = 0;
        int len = input.length;
        int flags = Base64Util.DEFAULT;

        byte[] result = Base64Util.decode(input, offset, len, flags);

        // Expected output: "Hello World"
        byte[] expected = {72, 101, 108, 108, 111, 32, 87, 111, 114, 108, 100};
        assertArrayEquals(expected, result);
    }


    /**
     * Tests the encode method with no padding and newline insertion.
     * This test case covers the path where padding is disabled (!encoder.do_padding)
     * and newline insertion is enabled (encoder.do_newline && len > 0).
     */
    @Test
    void test_encode_3() {
        byte[] input = "Hello, World!".getBytes();
        int offset = 0;
        int len = input.length;
        int flags = Base64Util.NO_PADDING | Base64Util.CRLF;

        byte[] result = Base64Util.encode(input, offset, len, flags);

        String expected = "SGVsbG8sIFdvcmxkIQ==\r\n".replace("==", "");
        String actual = new String(result);

        assertEquals(expected, actual);
    }

    /**
     * Testcase 4 for public static byte[] encode(byte[] input, int offset, int len, int flags)
     * Tests encoding with padding when input length is not a multiple of 3 and newlines are disabled.
     */
    @Test
    void testEncoderMaxOutputSize() {
        Base64Util.Encoder encoder = new Base64Util.Encoder(Base64Util.DEFAULT, null);
        assertEquals(19, encoder.maxOutputSize(6));
    }

    /**
     * Drives the package-private Encoder across two process() calls so a tail byte carried
     * over from the first call completes a tuple that lands exactly on the line-wrap boundary,
     * covering the newline-emission branch inside the tail-flush path (not reachable through the
     * public encode() methods, which always call process() exactly once).
     */
    @Test
    void testEncoderTailFlush_triggersLineWrap() {
        int flags = Base64Util.CRLF; // do_newline=true, do_cr=true, do_padding=true
        byte[] output = new byte[200];
        Base64Util.Encoder encoder = new Base64Util.Encoder(flags, output);

        byte[] firstPart = new byte[55]; // 18 complete triples (54 bytes) + 1 tail byte
        for (int i = 0; i < firstPart.length; i++) {
            firstPart[i] = (byte) i;
        }
        assertTrue(encoder.process(firstPart, 0, firstPart.length, false));
        assertEquals(1, encoder.tailLen);

        byte[] secondPart = new byte[]{99, 100};
        assertTrue(encoder.process(secondPart, 0, secondPart.length, true));

        String result = new String(output, 0, encoder.op);
        assertTrue(result.contains("\r\n"));
    }

    @Test
    void test_encode_4() {
        byte[] input = {1, 2, 3, 4, 5};
        int offset = 0;
        int len = 5;
        int flags = Base64Util.NO_WRAP; // Disable newlines

        byte[] result = Base64Util.encode(input, offset, len, flags);

        // Expected output: "AQIDBAU=" (Base64 encoded with padding)
        byte[] expected = {65, 81, 73, 68, 66, 65, 85, 61};
        assertArrayEquals(expected, result);
    }

    /**
     * Covers encodeToString(byte[], int), which no existing test invoked at all.
     */
    @Test
    void testEncodeToString_TwoArg() {
        byte[] input = "Man".getBytes();
        String result = Base64Util.encodeToString(input, Base64Util.NO_WRAP);
        assertEquals("TWFu", result);
    }

    /**
     * Covers encodeToString(byte[], int, int, int), which no existing test invoked at all.
     */
    @Test
    void testEncodeToString_FourArg() {
        byte[] input = "Man".getBytes();
        String result = Base64Util.encodeToString(input, 0, input.length, Base64Util.NO_WRAP);
        assertEquals("TWFu", result);
    }

    /**
     * Covers the NO_PADDING switch's "len % 3 == 1" case (outputLen += 2), which was the only
     * remainder arm not yet exercised (remainder 0 and 2 were already covered).
     */
    @Test
    void testEncodeNoPadding_LengthRemainderOne() {
        byte[] input = {1, 2, 3, 4}; // length 4, len % 3 == 1
        byte[] result = Base64Util.encode(input, Base64Util.NO_PADDING | Base64Util.NO_WRAP);
        assertEquals(6, result.length);

        byte[] roundTripped = Base64Util.decode(result, Base64Util.NO_WRAP);
        assertArrayEquals(input, roundTripped);
    }

    /**
     * Covers the URL_SAFE ternary in the Encoder constructor (alphabet selection), which every
     * other encode test leaves at its default (standard alphabet) branch.
     */
    @Test
    void testEncodeURLSafe_UsesWebSafeAlphabet() {
        byte[] input = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        int flags = Base64Util.URL_SAFE | Base64Util.NO_WRAP | Base64Util.NO_PADDING;
        String result = Base64Util.encodeToString(input, flags);
        assertEquals("____", result);
    }

    /**
     * Drives the public encode() API with a full 19-group (57-byte) input and CRLF flag so the
     * main encoding loop's own newline trigger (as opposed to the tail-flush one) fires with
     * doCr == true, and confirms the wrap happens mid-stream rather than only at the end.
     */
    @Test
    void testEncode57Bytes_MainLoopNewlineWithCR() {
        byte[] input = new byte[57];
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) i;
        }
        byte[] result = Base64Util.encode(input, Base64Util.CRLF);
        String encoded = new String(result);
        assertTrue(encoded.contains("\r\n"));
        // The wrap should occur after the 76th character (19 groups * 4), not only at the end.
        int firstBreak = encoded.indexOf("\r\n");
        assertEquals(76, firstBreak);
    }

    /**
     * Same 19-group boundary as above, but with default flags (doCr == false) so the main loop's
     * newline trigger is exercised with a plain '\n' instead of "\r\n".
     */
    @Test
    void testEncode57Bytes_MainLoopNewlineWithoutCR() {
        byte[] input = new byte[57];
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) i;
        }
        byte[] result = Base64Util.encode(input, Base64Util.DEFAULT);
        String encoded = new String(result);
        int firstBreak = encoded.indexOf('\n');
        assertEquals(76, firstBreak);
        assertFalse(encoded.contains("\r"));
    }

}
