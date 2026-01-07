package com.example.fix;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;

public final class App {
  public static void main(String[] args) {
    String fixMessage =
        "8=FIX.4.4\u0001" +
        "9=176\u0001" +
        "35=D\u0001" +
        "49=SENDER\u0001" +
        "56=TARGET\u0001" +
        "34=2\u0001" +
        "52=20240101-12:00:00.000\u0001" +
        "11=ABC123\u0001" +
        "55=IBM\u0001" +
        "54=1\u0001" +
        "38=100\u0001" +
        "40=2\u0001" +
        "44=125.50\u0001" +
        "59=0\u0001" +
        "60=20240101-12:00:00.000\u0001" +
        "10=000\u0001";

    byte[] bytes = fixMessage.getBytes(StandardCharsets.US_ASCII);

    FixNewOrderSingleParser parser = new FixNewOrderSingleParser();
    FixNewOrderSingle order = new FixNewOrderSingle();

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment segment = arena.allocate(bytes.length, 1);
      MemorySegment.copy(bytes, 0, segment, 0, bytes.length);

      boolean ok = parser.parse(segment, bytes.length, order);
      System.out.println("parsed=" + ok + " msgType=" + (char) order.msgType());
      System.out.println("clOrdId length=" + order.clOrdIdLength());
      System.out.println("orderQty=" + order.orderQty());
      if (order.pricePresent()) {
        System.out.println("price=" + order.priceMantissa() + " scale=" + order.priceScale());
      }
    }
  }
}
