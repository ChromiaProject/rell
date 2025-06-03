#!/usr/bin/env python3
#  Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.

import hashlib
import json

import ecdsa            # pip3 install ecdsa
import eth_keys         # pip3 install eth-hash pysha3
import eth_hash.auto    # pip3 install eth-keys

def make_test_case(priv_key_bytes):
    pub_key = eth_keys.keys.PrivateKey(priv_key_bytes).public_key
    point = ecdsa.ellipticcurve.Point.from_bytes(ecdsa.SECP256k1.curve, pub_key.to_bytes())
    return {
        'sk': priv_key_bytes.hex(),
        'pk1': pub_key.to_bytes().hex(),
        'pk2': pub_key.to_compressed_bytes().hex(),
        'addr': pub_key.to_canonical_address().hex(),
        'x': str(point.x()),
        'y': str(point.y()),
    }

def main():
    res = []

    priv_key_bytes = b'\x00' * 32
    for i in range(256):
        priv_key_bytes = hashlib.sha256(priv_key_bytes).digest()
        tc = make_test_case(priv_key_bytes)
        res.append(tc)

    print(json.dumps(res, indent = 4))

main()
