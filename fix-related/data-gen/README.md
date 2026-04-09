# FIX Data Generator

This directory contains a small FIX 4.2 message generator intended for parser
benchmarking. Each run writes outputs into an auto-incremented folder named
`data-vN` (for example: `data-v1`, `data-v2`, ...).

## Usage

Basic run (readable `|`-delimited file only):

```bash
python3 gen.py
```

Generate 100 messages, also write the SOH-delimited wire file, and set a seed:

```bash
python3 gen.py --wire -n 100 --seed 42
```

Generate with 5% intentionally invalid messages:

```bash
python3 gen.py --invalid-percent 5
```

Generate with invalid messages limited to checksum only and excluding logon/logout:

```bash
python3 gen.py --invalid-percent 5 --invalid-types bad_checksum --exclude-logon-logout
```

## Outputs

Each run creates a new folder:

- `data-vN/fix_messages.txt` (readable `|` delimiter)
- `data-vN/fix_messages_wire.txt` (SOH delimiter, only when `--wire` is set)
- `data-vN/meta.json` (summary and validation stats)

When `--wire` is enabled, the meta analysis uses the wire messages; otherwise it
uses the readable file.

## CLI options

- `-n`, `--count`: number of messages to generate (default: 10)
- `--wire`: also write SOH-delimited messages
- `--seed`: random seed for reproducible output
- `--invalid-percent`: percent of messages to intentionally corrupt (0-100).
  The target invalid count is `floor(count * percent / 100)`.
- `--invalid-types`: comma-separated invalidation types:
  `bad_checksum`, `bad_body_length`, `missing_tag`, `out_of_sequence`.
- `--exclude-logon-logout`: do not corrupt Logon (35=A) or Logout (35=5).
- `--no-sequence-check`: disable out-of-sequence validation in meta analysis.

## Invalid message types

When `--invalid-percent` is set, the generator randomly corrupts messages using
these strategies:

- bad checksum (Tag 10 does not match computed checksum)
- bad body length (Tag 9 does not match actual body length, checksum is correct)
- missing required tag (a required header or message-type tag is removed)
- out of sequence (Tag 34 is modified to break sequence ordering)

Invalid messages are selected randomly across the full batch (including logon
and logout).

## Meta file (`meta.json`)

`meta.json` provides quick sanity/smoke-test stats:

- expected vs actual message counts
- valid vs invalid message counts and invalid reasons
- message type counts (Tag 35)
- buy vs sell counts for NewOrderSingle (Tag 54)
- per-tag counts with valid/invalid splits
- sequence number range and body length range
- output file paths, seed, invalid percent, and invalid target counts
