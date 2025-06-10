from typing import Any


def is_raw_gtv(value: Any) -> bool:
    if value is None:
        return True

    if isinstance(value, (int, str, bytes, bool)):
        return True

    if isinstance(value, list):
        return all(is_raw_gtv(item) for item in value)

    if isinstance(value, dict):
        return all(
            isinstance(key, str) and is_raw_gtv(val) for key, val in value.items()
        )

    return False


def is_test_enum(value: Any) -> bool:
    return isinstance(value, str) and value in ["a"]


def is_map_with_test_struct_as_key(value: Any) -> bool:
    if not isinstance(value, list):
        return False

    for item in value:
        if not isinstance(item, list) or len(item) != 2:
            return False

        key, _ = item
        if not isinstance(key, list) or len(key) != 1:
            return False

        if not isinstance(key[0], bool):
            return False

    return True

