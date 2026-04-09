import argparse
import datetime
import json
import os
import random
import re

class FIXGenerator:
    def __init__(self, sender_comp_id="CLIENT", target_comp_id="BROKER"):
        self.seq_num = 1
        self.sender_comp_id = sender_comp_id
        self.target_comp_id = target_comp_id
        self.soh = "|" # Using pipe for readability. Change to chr(1) for actual wire protocol
        # Common NASDAQ symbols (2026 active list)
        self.symbols = [
            "NVDA", "AAPL", "MSFT", "AMZN", "GOOGL", 
            "META", "TSLA", "AVGO", "COST", "AMD"
        ]
        
    def _get_timestamp(self):
        # Tag 52: SendingTime in UTC
        now = datetime.datetime.now(datetime.timezone.utc)
        return now.strftime("%Y%m%d-%H:%M:%S.%f")[:-3]

    def _calc_checksum(self, data):
        # Tag 10: Sum of all bytes in message (up to tag 10) modulo 256
        if isinstance(data, str):
            data = data.encode("ascii")
        total = sum(data)
        checksum = total % 256
        return f"{checksum:03d}"

    def _render_message(self, msg_type, body_tags, seq_num, soh):
        # 1. Construct Header Tags (excluding 8 and 9)
        header_tags = [
            (35, msg_type),
            (49, self.sender_comp_id),
            (56, self.target_comp_id),
            (34, seq_num),
            (52, self._get_timestamp())
        ]
        
        # 2. Combine Header (35+) and Body
        # Note: Tag 9 (BodyLength) covers everything from Tag 35 to the end of the body
        all_content_tags = header_tags + body_tags
        
        # Build string starting from Tag 35
        content_fields = []
        for tag, value in all_content_tags:
            content_fields.append(f"{tag}={value}")
        content_str = soh.join(content_fields) + soh
            
        # 3. Calculate BodyLength (Tag 9)
        body_length = len(content_str.encode("ascii"))
        
        # 4. Prepend Tag 8 and Tag 9
        # Tag 8 is BeginString
        prefix_str = f"8=FIX.4.2{soh}9={body_length}{soh}"
        full_msg_minus_checksum = prefix_str + content_str
        
        # 5. Calculate Checksum (Tag 10)
        checksum = self._calc_checksum(full_msg_minus_checksum.encode("ascii"))
        
        # 6. Final Message
        final_msg = f"{full_msg_minus_checksum}10={checksum}{soh}"
        return final_msg

    def _finalize_message(self, msg_type, body_tags):
        msg = self._render_message(msg_type, body_tags, self.seq_num, self.soh)
        self.seq_num += 1
        return msg

    def _build_message(self, msg_type, body_tags, include_wire):
        seq_num = self.seq_num
        readable_msg = self._render_message(msg_type, body_tags, seq_num, self.soh)
        wire_msg = None
        if include_wire:
            wire_msg = self._render_message(msg_type, body_tags, seq_num, "\x01")
        self.seq_num += 1
        return {
            "msg_type": msg_type,
            "body_tags": body_tags,
            "seq_num": seq_num,
            "readable": readable_msg,
            "wire": wire_msg
        }

    def _logon_body(self):
        return [
            (98, "0"),  # EncryptMethod
            (108, "30") # HeartBtInt
        ]

    def _heartbeat_body(self):
        return []

    def _new_order_body(self):
        symbol = random.choice(self.symbols)
        side = random.choice(["1", "2"]) # Buy, Sell
        qty = random.randint(1, 100) * 100
        price = round(random.uniform(100, 1000), 2)
        cl_ord_id = f"ORD{random.randint(10000, 99999)}"
        return [
            (11, cl_ord_id),
            (21, "1"),         # HandlInst (Automated)
            (55, symbol),
            (54, side),
            (60, self._get_timestamp()), # TransactTime
            (38, qty),
            (40, "2"),         # OrdType (Limit)
            (44, price)
        ]

    def _execution_report_body(self):
        symbol = random.choice(self.symbols)
        exec_id = f"EXEC{random.randint(10000,99999)}"
        order_id = f"ORD{random.randint(10000,99999)}"
        return [
            (37, order_id),    # OrderID
            (17, exec_id),     # ExecID
            (150, "2"),        # ExecType (Fill)
            (39, "2"),         # OrdStatus (Filled)
            (55, symbol),
            (54, "1"),         # Side
            (38, 100),         # OrderQty
            (32, 100),         # LastQty
            (31, "150.00"),    # LastPx
            (151, "0"),        # LeavesQty
            (14, 100),         # CumQty
            (6, "150.00")      # AvgPx
        ]

    def _order_status_request_body(self):
        cl_ord_id = f"ORD{random.randint(10000, 99999)}"
        return [
            (11, cl_ord_id),
            (55, random.choice(self.symbols)),
            (54, random.choice(["1", "2"]))
        ]

    def generate_logon(self):
        # MsgType 35=A
        return self._finalize_message("A", self._logon_body())

    def generate_heartbeat(self):
        # MsgType 35=0
        return self._finalize_message("0", self._heartbeat_body())

    def generate_new_order(self):
        # MsgType 35=D
        return self._finalize_message("D", self._new_order_body())

    def generate_execution_report(self):
        # MsgType 35=8
        # Simulating a fill for a random symbol
        return self._finalize_message("8", self._execution_report_body())

    def generate_order_status_request(self):
        # MsgType 35=H (Query)
        # Addresses user's "queries" request
        return self._finalize_message("H", self._order_status_request_body())

    def generate_random_batch(self, count=10):
        batch = self.generate_random_batch_with_meta(count, include_wire=False)
        return [entry["readable"] for entry in batch]

    def generate_random_batch_with_meta(self, count=10, include_wire=False):
        messages = []
        # Always start with Logon
        messages.append(self._build_message("A", self._logon_body(), include_wire))
        
        # Generate mix of Trade/Admin
        ops = [
            ("0", self._heartbeat_body),
            ("D", self._new_order_body),
            ("D", self._new_order_body), # Higher weight for orders
            ("8", self._execution_report_body),
            ("H", self._order_status_request_body)
        ]
        
        for _ in range(count - 2):
            msg_type, body_builder = random.choice(ops)
            messages.append(self._build_message(msg_type, body_builder(), include_wire))
            
        # Always end with Logout (35=5)
        messages.append(self._build_message("5", [], include_wire))
        return messages

HEADER_REQUIRED_TAGS = {"8", "35", "49", "56", "34", "52"}
REQUIRED_TAGS_BY_MSGTYPE = {
    "A": {"98", "108"},
    "0": set(),
    "D": {"11", "21", "55", "54", "60", "38", "40", "44"},
    "8": {"37", "17", "150", "39", "55", "54", "38", "32", "31", "151", "14", "6"},
    "H": {"11", "55", "54"},
    "5": set()
}
MISSING_TAG_BASE = {"8", "9", "10", "35", "49", "56", "34", "52"}
INVALIDATION_TYPES = {"bad_checksum", "bad_body_length", "missing_tag", "out_of_sequence"}
DEFAULT_INVALID_TYPES = ["bad_checksum", "bad_body_length", "missing_tag"]

def _next_data_dir(base_dir, prefix="data-v"):
    max_version = 0
    for entry in os.listdir(base_dir):
        match = re.match(rf"^{re.escape(prefix)}(\d+)$", entry)
        if match:
            max_version = max(max_version, int(match.group(1)))
    return os.path.join(base_dir, f"{prefix}{max_version + 1}")

def _split_fields(message, delimiter):
    fields = message.split(delimiter)
    if fields and fields[-1] == "":
        fields = fields[:-1]
    return fields

def _parse_fields(message, delimiter):
    fields = _split_fields(message, delimiter)
    pairs = []
    for field in fields:
        if "=" not in field:
            return None, fields, "parse_error"
        tag, value = field.split("=", 1)
        pairs.append((tag, value))
    return pairs, fields, None

def _pairs_to_message(pairs, delimiter):
    fields = [f"{tag}={value}" for tag, value in pairs]
    return delimiter.join(fields) + delimiter

def _find_tag_index(pairs, tag_name):
    for i, (tag, _value) in enumerate(pairs):
        if tag == tag_name:
            return i
    return None

def _update_tag_value(pairs, tag_name, new_value):
    idx = _find_tag_index(pairs, tag_name)
    if idx is not None:
        pairs[idx] = (pairs[idx][0], str(new_value))
    return pairs

def _remove_tag_once(pairs, tag_name):
    idx = _find_tag_index(pairs, tag_name)
    if idx is not None:
        pairs.pop(idx)
    return pairs

def _compute_body_length_from_pairs(pairs, delimiter):
    idx_9 = _find_tag_index(pairs, "9")
    if idx_9 is None:
        return None
    idx_10 = _find_tag_index(pairs, "10")
    end_idx = idx_10 if idx_10 is not None else len(pairs)
    body_pairs = pairs[idx_9 + 1:end_idx]
    if not body_pairs:
        return 0
    body_str = delimiter.join([f"{tag}={value}" for tag, value in body_pairs]) + delimiter
    return len(body_str.encode("ascii"))

def _recalculate_body_length(pairs, delimiter):
    body_length = _compute_body_length_from_pairs(pairs, delimiter)
    if body_length is None:
        return pairs
    return _update_tag_value(pairs, "9", body_length)

def _recalculate_checksum(pairs, delimiter):
    idx_10 = _find_tag_index(pairs, "10")
    if idx_10 is None:
        return pairs
    checksum_str = delimiter.join([f"{tag}={value}" for tag, value in pairs[:idx_10]]) + delimiter
    checksum = sum(checksum_str.encode("ascii")) % 256
    return _update_tag_value(pairs, "10", f"{checksum:03d}")

def _choose_missing_tag(message, delimiter, msg_type):
    pairs, _fields, error = _parse_fields(message, delimiter)
    if error or pairs is None:
        return "35"
    tag_set = {tag for tag, _value in pairs}
    required = set(MISSING_TAG_BASE)
    required |= REQUIRED_TAGS_BY_MSGTYPE.get(msg_type, set())
    candidates = [tag for tag in required if tag in tag_set]
    if not candidates:
        return "35" if "35" in tag_set else pairs[0][0]
    return random.choice(candidates)

def _choose_out_of_sequence_value(message, delimiter):
    pairs, _fields, error = _parse_fields(message, delimiter)
    if error or pairs is None:
        return None
    idx_34 = _find_tag_index(pairs, "34")
    if idx_34 is None:
        return None
    try:
        seq = int(pairs[idx_34][1])
    except ValueError:
        return None
    delta = random.choice([-1, 2, 5, 10])
    new_seq = seq + delta
    if new_seq <= 0:
        new_seq = seq + abs(delta) + 1
    return new_seq

def _corrupt_message(message, delimiter, invalid_type, missing_tag=None, seq_override=None):
    pairs, _fields, error = _parse_fields(message, delimiter)
    if error or pairs is None:
        return message
    pairs = list(pairs)

    if invalid_type == "bad_checksum":
        idx_10 = _find_tag_index(pairs, "10")
        if idx_10 is None:
            return message
        current = pairs[idx_10][1]
        try:
            value = int(current)
        except ValueError:
            value = 0
        pairs[idx_10] = ("10", f"{(value + 1) % 256:03d}")
        return _pairs_to_message(pairs, delimiter)

    if invalid_type == "bad_body_length":
        body_length = _compute_body_length_from_pairs(pairs, delimiter)
        if body_length is None:
            return message
        _update_tag_value(pairs, "9", body_length + 1)
        _recalculate_checksum(pairs, delimiter)
        return _pairs_to_message(pairs, delimiter)

    if invalid_type == "missing_tag":
        if missing_tag:
            _remove_tag_once(pairs, missing_tag)
        _recalculate_body_length(pairs, delimiter)
        _recalculate_checksum(pairs, delimiter)
        return _pairs_to_message(pairs, delimiter)

    if invalid_type == "out_of_sequence":
        idx_34 = _find_tag_index(pairs, "34")
        if idx_34 is None:
            return message
        if seq_override is None:
            seq_override = _choose_out_of_sequence_value(message, delimiter)
        if seq_override is None:
            return message
        pairs[idx_34] = ("34", str(seq_override))
        _recalculate_body_length(pairs, delimiter)
        _recalculate_checksum(pairs, delimiter)
        return _pairs_to_message(pairs, delimiter)

    return message

def _validate_message(message, delimiter):
    pairs, fields, error = _parse_fields(message, delimiter)
    if error:
        return False, ["parse_error"], pairs, fields, None

    tag_values = {}
    tag_set = set()
    for tag, value in pairs:
        tag_set.add(tag)
        if tag not in tag_values:
            tag_values[tag] = value

    idx_9 = next((i for i, (tag, _) in enumerate(pairs) if tag == "9"), None)
    idx_10 = next((i for i, (tag, _) in enumerate(pairs) if tag == "10"), None)
    reasons = []

    for required_tag in sorted(HEADER_REQUIRED_TAGS):
        if required_tag not in tag_set:
            reasons.append(f"missing_required_tag_{required_tag}")

    msg_type = tag_values.get("35")
    if msg_type:
        for required_tag in sorted(REQUIRED_TAGS_BY_MSGTYPE.get(msg_type, set())):
            if required_tag not in tag_set:
                reasons.append(f"missing_required_tag_{required_tag}")

    if idx_9 is None:
        reasons.append("missing_tag_9")
    if idx_10 is None:
        reasons.append("missing_tag_10")
    if idx_9 is not None and idx_10 is not None and idx_10 <= idx_9:
        reasons.append("tag_order")
    expected_body_len = None
    if idx_9 is not None:
        try:
            expected_body_len = int(tag_values.get("9", ""))
        except ValueError:
            reasons.append("invalid_tag_9")

    body_len = None
    if expected_body_len is not None and idx_9 is not None and idx_10 is not None and idx_10 > idx_9:
        body_fields = fields[idx_9 + 1:idx_10]
        body_str = delimiter.join(body_fields) + delimiter
        body_len = len(body_str.encode("ascii"))
        if body_len != expected_body_len:
            reasons.append("body_length_mismatch")

    expected_checksum = None
    if idx_10 is not None:
        try:
            expected_checksum = int(tag_values.get("10", ""))
        except ValueError:
            reasons.append("invalid_tag_10")

    if expected_checksum is not None and idx_10 is not None:
        checksum_str = delimiter.join(fields[:idx_10]) + delimiter
        actual_checksum = sum(checksum_str.encode("ascii")) % 256
        if actual_checksum != expected_checksum:
            reasons.append("checksum_mismatch")

    return len(reasons) == 0, reasons, pairs, fields, body_len

def _analyze_messages(messages, delimiter, expected_count, sequence_check=True):
    meta = {
        "expected_message_count": expected_count,
        "actual_message_count": len(messages),
        "message_count_ok": expected_count == len(messages),
        "valid_messages": 0,
        "invalid_messages": 0,
        "invalid_reasons": {},
        "message_types": {},
        "new_order_sides": {"buy": 0, "sell": 0},
        "tags": {},
        "symbols": {},
        "sequence_numbers": {"min": None, "max": None},
        "body_length_bytes": {"min": None, "max": None}
    }

    seq_numbers = []
    body_lengths = []
    prev_seq = None

    for message in messages:
        is_valid, reasons, pairs, _fields, body_len = _validate_message(message, delimiter)
        tag_set = set()
        tag_values = {}
        if pairs:
            for tag, value in pairs:
                tag_set.add(tag)
                if tag not in tag_values:
                    tag_values[tag] = value

        seq_val = tag_values.get("34")
        seq_num = int(seq_val) if seq_val and seq_val.isdigit() else None
        if sequence_check and seq_num is not None and prev_seq is not None:
            if seq_num != prev_seq + 1:
                reasons.append("sequence_out_of_order")
                is_valid = False
        if seq_num is not None:
            prev_seq = seq_num

        if is_valid:
            meta["valid_messages"] += 1
        else:
            meta["invalid_messages"] += 1
            for reason in reasons:
                meta["invalid_reasons"][reason] = meta["invalid_reasons"].get(reason, 0) + 1

        msg_type = tag_values.get("35")
        if msg_type:
            meta["message_types"][msg_type] = meta["message_types"].get(msg_type, 0) + 1

        if msg_type == "D":
            side = tag_values.get("54")
            if side == "1":
                meta["new_order_sides"]["buy"] += 1
            elif side == "2":
                meta["new_order_sides"]["sell"] += 1

        symbol = tag_values.get("55")
        if symbol:
            meta["symbols"][symbol] = meta["symbols"].get(symbol, 0) + 1

        if seq_num is not None:
            seq_numbers.append(seq_num)

        if body_len is not None:
            body_lengths.append(body_len)

        for tag in tag_set:
            if tag not in meta["tags"]:
                meta["tags"][tag] = {"messages": 0, "valid_messages": 0, "invalid_messages": 0}
            meta["tags"][tag]["messages"] += 1
            if is_valid:
                meta["tags"][tag]["valid_messages"] += 1
            else:
                meta["tags"][tag]["invalid_messages"] += 1

    if seq_numbers:
        meta["sequence_numbers"]["min"] = min(seq_numbers)
        meta["sequence_numbers"]["max"] = max(seq_numbers)

    if body_lengths:
        meta["body_length_bytes"]["min"] = min(body_lengths)
        meta["body_length_bytes"]["max"] = max(body_lengths)

    return meta

def _apply_invalidations(batch, invalid_percent, readable_delimiter, include_wire, invalid_types, exclude_logon_logout):
    invalid_type_counts = {invalid_type: 0 for invalid_type in invalid_types}
    if invalid_percent <= 0 or not invalid_types:
        return 0, 0, invalid_type_counts

    percent = max(0.0, min(100.0, invalid_percent))
    invalid_target_count = int(len(batch) * percent / 100)
    if invalid_target_count <= 0:
        return invalid_target_count, 0, invalid_type_counts

    eligible_indices = list(range(len(batch)))
    if exclude_logon_logout:
        eligible_indices = [
            idx for idx in eligible_indices
            if batch[idx]["msg_type"] not in {"A", "5"}
        ]
    invalid_applied_count = min(invalid_target_count, len(eligible_indices))
    if invalid_applied_count <= 0:
        return invalid_target_count, 0, invalid_type_counts

    indices = random.sample(eligible_indices, invalid_applied_count)

    for idx in indices:
        entry = batch[idx]
        invalid_type = random.choice(invalid_types)
        missing_tag = None
        seq_override = None
        if invalid_type == "missing_tag":
            missing_tag = _choose_missing_tag(entry["readable"], readable_delimiter, entry["msg_type"])
        if invalid_type == "out_of_sequence":
            seq_override = _choose_out_of_sequence_value(entry["readable"], readable_delimiter)
        entry["readable"] = _corrupt_message(
            entry["readable"],
            readable_delimiter,
            invalid_type,
            missing_tag=missing_tag,
            seq_override=seq_override
        )
        if include_wire and entry["wire"]:
            entry["wire"] = _corrupt_message(
                entry["wire"],
                "\x01",
                invalid_type,
                missing_tag=missing_tag,
                seq_override=seq_override
            )
        invalid_type_counts[invalid_type] += 1

    return invalid_target_count, invalid_applied_count, invalid_type_counts

def _parse_invalid_types(value):
    items = [item.strip() for item in value.split(",") if item.strip()]
    unknown = [item for item in items if item not in INVALIDATION_TYPES]
    if unknown:
        raise argparse.ArgumentTypeError(f"Unknown invalidation types: {', '.join(unknown)}")
    return items

def main():
    parser = argparse.ArgumentParser(description="Generate FIX 4.2 messages for testing.")
    parser.add_argument("-n", "--count", type=int, default=10, help="Number of messages to generate.")
    parser.add_argument("--wire", action="store_true", help="Also generate SOH-delimited wire file.")
    parser.add_argument("--seed", type=int, help="Random seed for reproducible output.")
    parser.add_argument(
        "--invalid-percent",
        type=float,
        default=0.0,
        help="Percent of messages to intentionally corrupt (0-100)."
    )
    parser.add_argument(
        "--invalid-types",
        type=_parse_invalid_types,
        default=DEFAULT_INVALID_TYPES,
        help="Comma-separated invalidation types: bad_checksum,bad_body_length,missing_tag,out_of_sequence."
    )
    parser.add_argument(
        "--exclude-logon-logout",
        action="store_true",
        help="Do not corrupt Logon (35=A) or Logout (35=5) messages."
    )
    parser.add_argument(
        "--no-sequence-check",
        action="store_true",
        help="Disable out-of-sequence validation in meta analysis."
    )
    args = parser.parse_args()

    if args.seed is not None:
        random.seed(args.seed)

    gen = FIXGenerator()
    batch = gen.generate_random_batch_with_meta(args.count, include_wire=args.wire)
    invalid_target_count, invalid_applied_count, invalid_type_counts = _apply_invalidations(
        batch,
        args.invalid_percent,
        gen.soh,
        include_wire=args.wire,
        invalid_types=args.invalid_types,
        exclude_logon_logout=args.exclude_logon_logout
    )

    readable_msgs = [entry["readable"] for entry in batch]
    wire_msgs = [entry["wire"] for entry in batch if entry["wire"] is not None]

    output_dir = _next_data_dir(os.getcwd())
    os.makedirs(output_dir, exist_ok=False)

    readable_path = os.path.join(output_dir, "fix_messages.txt")
    with open(readable_path, "w", encoding="ascii") as f:
        for msg in readable_msgs:
            f.write(msg + "\n")

    wire_path = None
    if args.wire:
        wire_path = os.path.join(output_dir, "fix_messages_wire.txt")
        with open(wire_path, "w", encoding="ascii") as f:
            for msg in wire_msgs:
                f.write(msg + "\n")

    analysis_messages = wire_msgs if wire_msgs else readable_msgs
    analysis_delimiter = "\x01" if wire_msgs else gen.soh
    sequence_check = not args.no_sequence_check
    meta = _analyze_messages(analysis_messages, analysis_delimiter, args.count, sequence_check=sequence_check)
    meta["output_dir"] = output_dir
    meta["files"] = {
        "readable": readable_path,
        "wire": wire_path,
        "meta": os.path.join(output_dir, "meta.json")
    }
    meta["wire_mode"] = bool(args.wire)
    meta["seed"] = args.seed
    meta["invalid_percent"] = args.invalid_percent
    meta["invalid_target_count"] = invalid_target_count
    meta["invalid_applied_count"] = invalid_applied_count
    meta["invalid_type_distribution"] = invalid_type_counts
    meta["invalid_types"] = args.invalid_types
    meta["exclude_logon_logout"] = args.exclude_logon_logout
    meta["sequence_check"] = sequence_check

    meta_path = os.path.join(output_dir, "meta.json")
    with open(meta_path, "w", encoding="ascii") as f:
        json.dump(meta, f, indent=2, sort_keys=True)

    print(f"--- Generating {len(readable_msgs)} FIX 4.2 Messages (NASDAQ Symbols) ---")
    for msg in readable_msgs:
        print(msg)
    print(f"Wrote {readable_path}")
    if wire_path:
        print(f"Wrote {wire_path}")
    print(f"Wrote {meta_path}")

if __name__ == "__main__":
    main()
