# Zero GC FIX New Order Single Parser (JDK 25)

This project provides a zero-allocation parser for FIX New Order Single (35=D)
messages using Java FFM `MemorySegment`. The parser reads from a caller-supplied
segment and stores field offsets/lengths so no Strings are created in the hot
path.

## Notes on zero allocation

- `FixNewOrderSingleParser.parse` performs no allocations; reuse the parser and
  output object between messages.
- The sample `App` class allocates a String and byte array to build a demo
  message. In a production setup, feed the parser a pre-filled `MemorySegment`
  backed by off-heap I/O buffers.

## Usage

```
mvn -q -DskipTests package
java -cp target/zero-gc-parser-1.0-SNAPSHOT.jar com.example.fix.App
```

## Key classes

- `com.example.fix.FixNewOrderSingleParser`
- `com.example.fix.FixNewOrderSingle`
