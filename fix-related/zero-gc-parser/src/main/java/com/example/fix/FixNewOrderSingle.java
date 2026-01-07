package com.example.fix;

import java.lang.foreign.MemorySegment;

public final class FixNewOrderSingle {
  static final byte SOH = 1;

  private MemorySegment buffer;

  private byte msgType;
  private int msgSeqNum;

  private int senderCompIdOffset = -1;
  private int senderCompIdLength;
  private int targetCompIdOffset = -1;
  private int targetCompIdLength;

  private int clOrdIdOffset = -1;
  private int clOrdIdLength;
  private int symbolOffset = -1;
  private int symbolLength;

  private byte side;
  private long orderQty = -1;
  private byte ordType;

  private long priceMantissa;
  private int priceScale;
  private boolean pricePresent;

  private byte timeInForce;
  private int transactTimeOffset = -1;
  private int transactTimeLength;

  private int sendingTimeOffset = -1;
  private int sendingTimeLength;

  private int checksum = -1;

  private int flags;

  void reset(MemorySegment segment) {
    buffer = segment;
    msgType = 0;
    msgSeqNum = -1;

    senderCompIdOffset = -1;
    senderCompIdLength = 0;
    targetCompIdOffset = -1;
    targetCompIdLength = 0;

    clOrdIdOffset = -1;
    clOrdIdLength = 0;
    symbolOffset = -1;
    symbolLength = 0;

    side = 0;
    orderQty = -1;
    ordType = 0;

    priceMantissa = 0;
    priceScale = 0;
    pricePresent = false;

    timeInForce = 0;
    transactTimeOffset = -1;
    transactTimeLength = 0;

    sendingTimeOffset = -1;
    sendingTimeLength = 0;

    checksum = -1;

    flags = 0;
  }

  void markFlag(int flag) {
    flags |= flag;
  }

  int flags() {
    return flags;
  }

  void msgType(byte value) {
    msgType = value;
  }

  void msgSeqNum(int value) {
    msgSeqNum = value;
  }

  void senderCompId(int offset, int length) {
    senderCompIdOffset = offset;
    senderCompIdLength = length;
  }

  void targetCompId(int offset, int length) {
    targetCompIdOffset = offset;
    targetCompIdLength = length;
  }

  void clOrdId(int offset, int length) {
    clOrdIdOffset = offset;
    clOrdIdLength = length;
  }

  void symbol(int offset, int length) {
    symbolOffset = offset;
    symbolLength = length;
  }

  void side(byte value) {
    side = value;
  }

  void orderQty(long value) {
    orderQty = value;
  }

  void ordType(byte value) {
    ordType = value;
  }

  void price(long mantissa, int scale) {
    priceMantissa = mantissa;
    priceScale = scale;
    pricePresent = true;
  }

  void timeInForce(byte value) {
    timeInForce = value;
  }

  void transactTime(int offset, int length) {
    transactTimeOffset = offset;
    transactTimeLength = length;
  }

  void sendingTime(int offset, int length) {
    sendingTimeOffset = offset;
    sendingTimeLength = length;
  }

  void checksum(int value) {
    checksum = value;
  }

  public MemorySegment buffer() {
    return buffer;
  }

  public byte msgType() {
    return msgType;
  }

  public int msgSeqNum() {
    return msgSeqNum;
  }

  public int senderCompIdOffset() {
    return senderCompIdOffset;
  }

  public int senderCompIdLength() {
    return senderCompIdLength;
  }

  public int targetCompIdOffset() {
    return targetCompIdOffset;
  }

  public int targetCompIdLength() {
    return targetCompIdLength;
  }

  public int clOrdIdOffset() {
    return clOrdIdOffset;
  }

  public int clOrdIdLength() {
    return clOrdIdLength;
  }

  public int symbolOffset() {
    return symbolOffset;
  }

  public int symbolLength() {
    return symbolLength;
  }

  public byte side() {
    return side;
  }

  public long orderQty() {
    return orderQty;
  }

  public byte ordType() {
    return ordType;
  }

  public boolean pricePresent() {
    return pricePresent;
  }

  public long priceMantissa() {
    return priceMantissa;
  }

  public int priceScale() {
    return priceScale;
  }

  public byte timeInForce() {
    return timeInForce;
  }

  public int transactTimeOffset() {
    return transactTimeOffset;
  }

  public int transactTimeLength() {
    return transactTimeLength;
  }

  public int sendingTimeOffset() {
    return sendingTimeOffset;
  }

  public int sendingTimeLength() {
    return sendingTimeLength;
  }

  public int checksum() {
    return checksum;
  }
}
