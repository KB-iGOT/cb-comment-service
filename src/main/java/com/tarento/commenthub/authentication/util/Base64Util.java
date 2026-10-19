package com.tarento.commenthub.authentication.util;

import java.nio.charset.StandardCharsets;

public class Base64Util {

  public static final int DEFAULT = 0;

  /**
   * Encoder flag bit to omit the padding '=' characters at the end of the output (if any).
   */
  public static final int NO_PADDING = 1;

  /**
   * Encoder flag bit to omit all line terminators (i.e., the output will be on one long line).
   */
  public static final int NO_WRAP = 2;

  /**
   * Encoder flag bit to indicate lines should be terminated with a CRLF pair instead of just an LF.
   * Has no effect if {@code NO_WRAP} is specified as well.
   */
  public static final int CRLF = 4;

  /**
   * Encoder/decoder flag bit to indicate using the "URL and filename safe" variant of Base64 (see
   * RFC 3548 section 4) where {@code -} and {@code _} are used in place of {@code +} and
   * {@code /}.
   */
  public static final int URL_SAFE = 8;

  /**
   * Flag to pass to {Base64OutputStream} to indicate that it should not close the output stream it
   * is wrapping when it itself is closed.
   */
  public static final int NO_CLOSE = 16;

  //  --------------------------------------------------------
  //  shared code
  //  --------------------------------------------------------

  private Base64Util() {
  }   // don't instantiate

  //  --------------------------------------------------------
  //  decoding
  //  --------------------------------------------------------

  /**
   * Decode the Base64-encoded data in input and return the data in a new byte array.
   * <p>
   * <p>The padding '=' characters at the end are considered optional, but
   * if any are present, there must be the correct number of them.
   *
   * @param str   the input String to decode, which is converted to bytes using the default charset
   * @param flags controls certain features of the decoded output. Pass {@code DEFAULT} to decode
   *              standard Base64.
   * @throws IllegalArgumentException if the input contains incorrect padding
   */
  public static byte[] decode(String str, int flags) {
    return decode(str.getBytes(), flags);
  }

  /**
   * Decode the Base64-encoded data in input and return the data in a new byte array.
   * <p>
   * <p>The padding '=' characters at the end are considered optional, but
   * if any are present, there must be the correct number of them.
   *
   * @param input the input array to decode
   * @param flags controls certain features of the decoded output. Pass {@code DEFAULT} to decode
   *              standard Base64.
   * @throws IllegalArgumentException if the input contains incorrect padding
   */
  public static byte[] decode(byte[] input, int flags) {
    return decode(input, 0, input.length, flags);
  }

  /**
   * Decode the Base64-encoded data in input and return the data in a new byte array.
   * <p>
   * <p>The padding '=' characters at the end are considered optional, but
   * if any are present, there must be the correct number of them.
   *
   * @param input  the data to decode
   * @param offset the position within the input array at which to start
   * @param len    the number of bytes of input to decode
   * @param flags  controls certain features of the decoded output. Pass {@code DEFAULT} to decode
   *               standard Base64.
   * @throws IllegalArgumentException if the input contains incorrect padding
   */
  public static byte[] decode(byte[] input, int offset, int len, int flags) {
    // Allocate space for the most data the input could represent.
    // (It could contain less if it contains whitespace, etc.)
    Decoder decoder = new Decoder(flags, new byte[len * 3 / 4]);

    if (!decoder.process(input, offset, len, true)) {
      throw new IllegalArgumentException("bad base-64");
    }

    // Maybe we got lucky and allocated exactly enough output space.
    if (decoder.op == decoder.output.length) {
      return decoder.output;
    }

    // Need to shorten the array, so allocate a new one of the
    // right size and copy.
    byte[] temp = new byte[decoder.op];
    System.arraycopy(decoder.output, 0, temp, 0, decoder.op);
    return temp;
  }

  /**
   * Base64-encode the given data and return a newly allocated String with the result.
   *
   * @param input the data to encode
   * @param flags controls certain features of the encoded output. Passing {@code DEFAULT} results
   *              in output that adheres to RFC 2045.
   */
  public static String encodeToString(byte[] input, int flags) {
    return new String(encode(input, flags), StandardCharsets.US_ASCII);
  }

  //  --------------------------------------------------------
  //  encoding
  //  --------------------------------------------------------

  /**
   * Base64-encode the given data and return a newly allocated String with the result.
   *
   * @param input  the data to encode
   * @param offset the position within the input array at which to start
   * @param len    the number of bytes of input to encode
   * @param flags  controls certain features of the encoded output. Passing {@code DEFAULT} results
   *               in output that adheres to RFC 2045.
   */
  public static String encodeToString(byte[] input, int offset, int len, int flags) {
    return new String(encode(input, offset, len, flags), StandardCharsets.US_ASCII);
  }

  /**
   * Base64-encode the given data and return a newly allocated byte[] with the result.
   *
   * @param input the data to encode
   * @param flags controls certain features of the encoded output. Passing {@code DEFAULT} results
   *              in output that adheres to RFC 2045.
   */
  public static byte[] encode(byte[] input, int flags) {
    return encode(input, 0, input.length, flags);
  }

  /**
   * Base64-encode the given data and return a newly allocated byte[] with the result.
   *
   * @param input  the data to encode
   * @param offset the position within the input array at which to start
   * @param len    the number of bytes of input to encode
   * @param flags  controls certain features of the encoded output. Passing {@code DEFAULT} results
   *               in output that adheres to RFC 2045.
   */
  public static byte[] encode(byte[] input, int offset, int len, int flags) {
    Encoder encoder = new Encoder(flags, null);

    // Compute the exact length of the array we will produce.
    int outputLen = len / 3 * 4;

    // Account for the tail of the data and the padding bytes, if any.
    if (encoder.doPadding) {
      if (len % 3 > 0) {
        outputLen += 4;
      }
    } else {
      switch (len % 3) {
        case 0:
          break;
        case 1:
          outputLen += 2;
          break;
        case 2:
          outputLen += 3;
          break;
        default:
          break;
      }
    }

    // Account for the newlines, if any.
    if (encoder.doNewline && len > 0) {
      outputLen += (((len - 1) / (3 * Encoder.LINE_GROUPS)) + 1) *
          (encoder.doCr ? 2 : 1);
    }

    encoder.output = new byte[outputLen];
    encoder.process(input, offset, len, true);

    assert encoder.op == outputLen;

    return encoder.output;
  }

  /* package */ abstract static class Coder {

    byte[] output;
    int op;

    /**
     * Encode/decode another block of input data.  this.output is provided by the caller, and must
     * be big enough to hold all the coded data.  On exit, this.opwill be set to the length of the
     * coded data.
     *
     * @param finish true if this is the final call to process for this object.  Will finalize the
     *               coder state and include any final bytes in the output.
     * @return true if the input so far is good; false if some error has been detected in the input
     * stream..
     */
    public abstract boolean process(byte[] input, int offset, int len, boolean finish);

    /**
     * @return the maximum number of bytes a call to process() could produce for the given number of
     * input bytes.  This may be an overestimate.
     */
    public abstract int maxOutputSize(int len);
  }

  /* package */ static class Decoder extends Coder {

    /**
     * Lookup table for turning bytes into their position in the Base64 alphabet.
     */
    private static final int[] DECODE = {
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 62, -1, -1, -1, 63,
        52, 53, 54, 55, 56, 57, 58, 59, 60, 61, -1, -1, -1, -2, -1, -1,
        -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14,
        15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, -1, -1, -1, -1, -1,
        -1, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40,
        41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    };

    /**
     * Decode lookup table for the "web safe" variant (RFC 3548 sec. 4) where - and _ replace + and
     * /.
     */
    private static final int[] DECODE_WEBSAFE = {
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 62, -1, -1,
        52, 53, 54, 55, 56, 57, 58, 59, 60, 61, -1, -1, -1, -2, -1, -1,
        -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14,
        15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, -1, -1, -1, -1, 63,
        -1, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40,
        41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    };

    /**
     * Non-data values in the DECODE arrays.
     */
    private static final int SKIP = -1;
    private static final int EQUALS = -2;
    private final int[] alphabet;
    /**
     * States 0-3 are reading through the next input tuple. State 4 is having read one '=' and
     * expecting exactly one more. State 5 is expecting no more data or padding characters in the
     * input. State 6 is the error state; an error has been detected in the input and no future
     * input can "fix" it.
     */
    private int state;   // state number (0 to 6)
    private int value;

    public Decoder(int flags, byte[] output) {
      this.output = output;

      alphabet = ((flags & URL_SAFE) == 0) ? DECODE : DECODE_WEBSAFE;
      state = 0;
      value = 0;
    }

    /**
     * @return an overestimate for the number of bytes {@code len} bytes could decode to.
     */
    public int maxOutputSize(int len) {
      return len * 3 / 4 + 10;
    }

    /**
     * Decode another block of input data.
     *
     * @return true if the state machine is still healthy.  false if bad base-64 data has been
     * detected in the input stream.
     */
    public boolean process(byte[] input, int offset, int len, boolean finish) {
      if (this.state == 6) {
        return false;
      }

      int p = offset;
      len += offset;
      op = 0;

      while (p < len) {
        // Try the fast path:  we're starting a new tuple and the
        // next four bytes of the input stream are all data
        // bytes.  This corresponds to going through states
        // 0-1-2-3-0.  We expect to use this method for most of
        // the data.
        if (state == 0) {
          p = decodeFastPath(input, p, len);
          if (p >= len) {
            break;
          }
        }

        // The fast path isn't available -- either we've read a
        // partial tuple, or the next four input bytes aren't all
        // data, or whatever.  Fall back to the slower state
        // machine implementation.
        int d = alphabet[input[p++] & 0xff];
        if (!decodeStep(d)) {
          return false;
        }
      }

      if (!finish) {
        // We're out of input, but a future call could provide
        // more.
        return true;
      }

      // Done reading input.  Now figure out where we are left in
      // the state machine and finish up.
      return finishDecoding();
    }

    /**
     * Reads consecutive full tuples of data bytes straight into the output while state stays 0.
     * If any of the next four bytes of input are non-data (whitespace, etc.), the combined value
     * ends up negative and this returns so the caller can fall back to the state machine.  (All
     * the non-data values in decode are small negative numbers, so shifting any of them up and
     * or'ing them together will result in a value with its top bit set.)
     *
     * @return the input position after the last tuple consumed this way.
     */
    private int decodeFastPath(byte[] input, int p, int len) {
      int localValue;
      while (p + 4 <= len &&
          (localValue = ((alphabet[input[p] & 0xff] << 18) |
              (alphabet[input[p + 1] & 0xff] << 12) |
              (alphabet[input[p + 2] & 0xff] << 6) |
              (alphabet[input[p + 3] & 0xff]))) >= 0) {
        output[op + 2] = (byte) localValue;
        output[op + 1] = (byte) (localValue >> 8);
        output[op] = (byte) (localValue >> 16);
        op += 3;
        p += 4;
      }
      return p;
    }

    /**
     * Advances the state machine by one input byte already looked up in the alphabet.
     *
     * @return false if {@code d} puts the stream in an error state.
     */
    private boolean decodeStep(int d) {
      switch (state) {
        case 0:
          return readFirstByteOfTuple(d);
        case 1:
          return readSecondByteOfTuple(d);
        case 2:
          return readThirdByteOfTuple(d);
        case 3:
          return readFourthByteOfTuple(d);
        case 4:
          return awaitSecondPaddingByte(d);
        case 5:
          return expectNoMoreInput(d);
        default:
          return true;
      }
    }

    private boolean readFirstByteOfTuple(int d) {
      if (d >= 0) {
        value = d;
        state = 1;
      } else if (d != SKIP) {
        state = 6;
        return false;
      }
      return true;
    }

    private boolean readSecondByteOfTuple(int d) {
      if (d >= 0) {
        value = (value << 6) | d;
        state = 2;
      } else if (d != SKIP) {
        state = 6;
        return false;
      }
      return true;
    }

    private boolean readThirdByteOfTuple(int d) {
      if (d >= 0) {
        value = (value << 6) | d;
        state = 3;
      } else if (d == EQUALS) {
        // Emit the last (partial) output tuple and expect exactly one more padding character.
        output[op++] = (byte) (value >> 4);
        state = 4;
      } else if (d != SKIP) {
        state = 6;
        return false;
      }
      return true;
    }

    private boolean readFourthByteOfTuple(int d) {
      if (d >= 0) {
        // Emit the output triple and return to state 0.
        value = (value << 6) | d;
        output[op + 2] = (byte) value;
        output[op + 1] = (byte) (value >> 8);
        output[op] = (byte) (value >> 16);
        op += 3;
        state = 0;
      } else if (d == EQUALS) {
        // Emit the last (partial) output tuple and expect no further data or padding characters.
        output[op + 1] = (byte) (value >> 2);
        output[op] = (byte) (value >> 10);
        op += 2;
        state = 5;
      } else if (d != SKIP) {
        state = 6;
        return false;
      }
      return true;
    }

    private boolean awaitSecondPaddingByte(int d) {
      if (d == EQUALS) {
        state = 5;
      } else if (d != SKIP) {
        state = 6;
        return false;
      }
      return true;
    }

    private boolean expectNoMoreInput(int d) {
      if (d != SKIP) {
        state = 6;
        return false;
      }
      return true;
    }

    /**
     * Finalizes the state machine once the caller has signalled there is no more input.
     *
     * @return false if the stream was left in an incomplete, illegal state.
     */
    private boolean finishDecoding() {
      switch (state) {
        case 1, 4:
          // case 1: Read one extra input byte, which isn't enough to make another output byte. Illegal.
          // case 4: Read one padding '=' when we expected 2. Illegal.
          state = 6;
          return false;
        case 2:
          // Read two extra input bytes, enough to emit 1 more
          // output byte.  Fine.
          output[op++] = (byte) (value >> 4);
          break;
        case 3:
          // Read three extra input bytes, enough to emit 2 more
          // output bytes.  Fine.
          output[op++] = (byte) (value >> 10);
          output[op++] = (byte) (value >> 2);
          break;
        default:
          // state 0: output length is a multiple of three.  state 5: read all the padding '='s
          // we expected and no more.  Both fine.
          break;
      }
      return true;
    }
  }

  /* package */ static class Encoder extends Coder {

    /**
     * Emit a new line every this many output tuples.  Corresponds to a 76-character line length
     * (the maximum allowable according to
     * <a href="http://www.ietf.org/rfc/rfc2045.txt">RFC 2045</a>).
     */
    public static final int LINE_GROUPS = 19;

    /**
     * Lookup table for turning Base64 alphabet positions (6 bits) into output bytes.
     */
    private static final byte[] ENCODE = {
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',
        'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f',
        'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v',
        'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '/',
    };

    /**
     * Lookup table for turning Base64 alphabet positions (6 bits) into output bytes.
     */
    private static final byte[] ENCODE_WEBSAFE = {
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',
        'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f',
        'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v',
        'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '_',
    };
    public final boolean doPadding;
    public final boolean doNewline;
    public final boolean doCr;
    private final byte[] tail;
    private final byte[] alphabet;
    /* package */ int tailLen;
    private int count;

    public Encoder(int flags, byte[] output) {
      this.output = output;

      doPadding = (flags & NO_PADDING) == 0;
      doNewline = (flags & NO_WRAP) == 0;
      doCr = (flags & CRLF) != 0;
      alphabet = ((flags & URL_SAFE) == 0) ? ENCODE : ENCODE_WEBSAFE;

      tail = new byte[2];
      tailLen = 0;

      count = doNewline ? LINE_GROUPS : -1;
    }

    /**
     * @return an overestimate for the number of bytes {@code len} bytes could encode to.
     */
    public int maxOutputSize(int len) {
      return len * 8 / 5 + 10;
    }

    public boolean process(byte[] input, int offset, int len, boolean finish) {
      op = 0;

      int p = offset;
      len += offset;

      // First we need to concatenate the tail of the previous call
      // with any input bytes available now and see if we can empty
      // the tail.
      p = flushTail(input, p, len);

      // At this point either there is no tail, or there are fewer
      // than 3 bytes of input available.

      // The main loop, turning 3 input bytes into 4 output bytes on
      // each iteration.
      p = encodeFullTriples(input, p, len);

      if (finish) {
        // Finish up the tail of the input.  Note that we need to
        // consume any bytes in tail before any bytes
        // remaining in input; there should be at most two bytes
        // total.
        p = finishEncoding(input, p, len);

        if (tailLen != 0) {
          throw new IllegalStateException("tailLen must be 0 after finishEncoding");
        }
        if (p != len) {
          throw new IllegalStateException("p must equal len after finishEncoding");
        }
      } else {
        // Save the leftovers in tail to be consumed on the next
        // call to encodeInternal.
        saveTail(input, p, len);
      }

      return true;
    }

    /**
     * Combines a tail saved from a previous call with fresh input bytes and, once a full 3-byte
     * group is available, encodes it.
     *
     * @return the input position after any bytes this consumed.
     */
    private int flushTail(byte[] input, int p, int len) {
      int v = -1;
      switch (tailLen) {
        case 1:
          if (p + 2 <= len) {
            // A 1-byte tail with at least 2 bytes of
            // input available now.
            v = ((tail[0] & 0xff) << 16) |
                ((input[p++] & 0xff) << 8) |
                (input[p++] & 0xff);
            tailLen = 0;
          }
          break;

        case 2:
          if (p + 1 <= len) {
            // A 2-byte tail with at least 1 byte of input.
            v = ((tail[0] & 0xff) << 16) |
                ((tail[1] & 0xff) << 8) |
                (input[p++] & 0xff);
            tailLen = 0;
          }
          break;

        default:
          // There was no tail.
          break;
      }

      if (v != -1) {
        emitTriple(v);
      }
      return p;
    }

    private int encodeFullTriples(byte[] input, int p, int len) {
      while (p + 3 <= len) {
        int v = ((input[p] & 0xff) << 16) |
            ((input[p + 1] & 0xff) << 8) |
            (input[p + 2] & 0xff);
        emitTriple(v);
        p += 3;
      }
      return p;
    }

    private void emitTriple(int v) {
      output[op++] = alphabet[(v >> 18) & 0x3f];
      output[op++] = alphabet[(v >> 12) & 0x3f];
      output[op++] = alphabet[(v >> 6) & 0x3f];
      output[op++] = alphabet[v & 0x3f];
      if (--count == 0) {
        emitNewline();
        count = LINE_GROUPS;
      }
    }

    private void emitNewline() {
      if (doCr) {
        output[op++] = '\r';
      }
      output[op++] = '\n';
    }

    private int finishEncoding(byte[] input, int p, int len) {
      if (p - tailLen == len - 1) {
        return finishOneByteRemainder(input, p);
      } else if (p - tailLen == len - 2) {
        return finishTwoByteRemainder(input, p);
      } else if (doNewline && op > 0 && count != LINE_GROUPS) {
        emitNewline();
      }
      return p;
    }

    private int finishOneByteRemainder(byte[] input, int p) {
      int t = 0;
      int v = ((tailLen > 0 ? tail[t++] : input[p++]) & 0xff) << 4;
      tailLen -= t;
      output[op++] = alphabet[(v >> 6) & 0x3f];
      output[op++] = alphabet[v & 0x3f];
      if (doPadding) {
        output[op++] = '=';
        output[op++] = '=';
      }
      if (doNewline) {
        emitNewline();
      }
      return p;
    }

    private int finishTwoByteRemainder(byte[] input, int p) {
      int t = 0;
      int v = (((tailLen > 1 ? tail[t++] : input[p++]) & 0xff) << 10) |
          (((tailLen > 0 ? tail[t++] : input[p++]) & 0xff) << 2);
      tailLen -= t;
      output[op++] = alphabet[(v >> 12) & 0x3f];
      output[op++] = alphabet[(v >> 6) & 0x3f];
      output[op++] = alphabet[v & 0x3f];
      if (doPadding) {
        output[op++] = '=';
      }
      if (doNewline) {
        emitNewline();
      }
      return p;
    }

    private void saveTail(byte[] input, int p, int len) {
      if (p == len - 1) {
        tail[tailLen++] = input[p];
      } else if (p == len - 2) {
        tail[tailLen++] = input[p];
        tail[tailLen++] = input[p + 1];
      }
    }
  }

}
