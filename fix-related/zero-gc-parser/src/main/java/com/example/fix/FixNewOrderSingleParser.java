package com.example.fix;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public final class FixNewOrderSingleParser {
  private static final byte SOH = 1;
  private static final byte EQ = '=';
  private static final byte DOT = '.';

  private static final int F_MSG_TYPE = 1 << 0;
  private static final int F_CL_ORD_ID = 1 << 1;
  private static final int F_SYMBOL = 1 << 2;
  private static final int F_SIDE = 1 << 3;
  private static final int F_ORDER_QTY = 1 << 4;
  private static final int F_ORD_TYPE = 1 << 5;
  private static final int F_TRANSACT_TIME = 1 << 6;

  private static final int REQUIRED_FLAGS =
      F_MSG_TYPE | F_CL_ORD_ID | F_SYMBOL | F_SIDE | F_ORDER_QTY | F_ORD_TYPE;

  private long decimalMantissa;
  private int decimalScale;

  public boolean parse(MemorySegment buffer, int length, FixNewOrderSingle out) {
    out.reset(buffer);

    int index = 0;
    while (index < length) {
      int tag = 0;
      while (index < length) {
        byte b = getByte(buffer, index++);
        if (b == EQ) {
          break;
        }
        int digit = b - '0';
        if (digit < 0 || digit > 9) {
          return false;
        }
        tag = tag * 10 + digit;
      }

      if (index >= length) {
        return false;
      }

      int valueStart = index;
      while (index < length && getByte(buffer, index) != SOH) {
        index++;
      }

      if (index >= length) {
        return false;
      }

      int valueEnd = index;
      int valueLength = valueEnd - valueStart;

      switch (tag) {
        case 35 -> {
          if (valueLength != 1) {
            return false;
          }
          byte msgType = getByte(buffer, valueStart);
          out.msgType(msgType);
          out.markFlag(F_MSG_TYPE);
        }
        case 49 -> out.senderCompId(valueStart, valueLength);
        case 56 -> out.targetCompId(valueStart, valueLength);
        case 34 -> {
          int seqNum = parseInt(buffer, valueStart, valueEnd);
          if (seqNum < 0) {
            return false;
          }
          out.msgSeqNum(seqNum);
        }
        case 52 -> out.sendingTime(valueStart, valueLength);
        case 11 -> {
          out.clOrdId(valueStart, valueLength);
          out.markFlag(F_CL_ORD_ID);
        }
        case 55 -> {
          out.symbol(valueStart, valueLength);
          out.markFlag(F_SYMBOL);
        }
        case 54 -> {
          if (valueLength != 1) {
            return false;
          }
          out.side(getByte(buffer, valueStart));
          out.markFlag(F_SIDE);
        }
        case 38 -> {
          long qty = parseLong(buffer, valueStart, valueEnd);
          if (qty < 0) {
            return false;
          }
          out.orderQty(qty);
          out.markFlag(F_ORDER_QTY);
        }
        case 40 -> {
          if (valueLength != 1) {
            return false;
          }
          out.ordType(getByte(buffer, valueStart));
          out.markFlag(F_ORD_TYPE);
        }
        case 44 -> {
          if (!parseDecimal(buffer, valueStart, valueEnd)) {
            return false;
          }
          out.price(decimalMantissa, decimalScale);
        }
        case 59 -> {
          if (valueLength != 1) {
            return false;
          }
          out.timeInForce(getByte(buffer, valueStart));
        }
        case 60 -> {
          out.transactTime(valueStart, valueLength);
          out.markFlag(F_TRANSACT_TIME);
        }
        case 10 -> {
          int checksum = parseInt(buffer, valueStart, valueEnd);
          if (checksum < 0) {
            return false;
          }
          out.checksum(checksum);
        }
        default -> {
          // Ignore other tags to keep the hot path simple.
        }
      }

      index++;
    }

    if (out.msgType() != 'D') {
      return false;
    }

    int flags = out.flags();
    if ((flags & REQUIRED_FLAGS) != REQUIRED_FLAGS) {
      return false;
    }

    return true;
  }

  private static byte getByte(MemorySegment buffer, long offset) {
    return buffer.get(ValueLayout.JAVA_BYTE, offset);
  }

  private static int parseInt(MemorySegment buffer, int start, int end) {
    int value = 0;
    for (int i = start; i < end; i++) {
      byte b = getByte(buffer, i);
      int digit = b - '0';
      if (digit < 0 || digit > 9) {
        return -1;
      }
      value = value * 10 + digit;
    }
    return value;
  }

  private static long parseLong(MemorySegment buffer, int start, int end) {
    long value = 0;
    for (int i = start; i < end; i++) {
      byte b = getByte(buffer, i);
      int digit = b - '0';
      if (digit < 0 || digit > 9) {
        return -1;
      }
      value = value * 10 + digit;
    }
    return value;
  }

  private boolean parseDecimal(MemorySegment buffer, int start, int end) {
    long value = 0;
    int scale = 0;
    boolean sawDot = false;

    for (int i = start; i < end; i++) {
      byte b = getByte(buffer, i);
      if (b == DOT) {
        if (sawDot) {
          return false;
        }
        sawDot = true;
        continue;
      }
      int digit = b - '0';
      if (digit < 0 || digit > 9) {
        return false;
      }
      value = value * 10 + digit;
      if (sawDot) {
        scale++;
      }
    }

    decimalMantissa = value;
    decimalScale = scale;
    return true;
  }
}
