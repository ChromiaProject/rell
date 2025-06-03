#!/usr/bin/env python3
#  Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.

import ecdsa
import hashlib

def main():
    for x in range(8):
        seed = bytes([x])
        privkey = hashlib.sha256(seed).digest()
        message = 'message_%d' % x

        sigkey = ecdsa.SigningKey.from_string(privkey, curve = ecdsa.SECP256k1, hashfunc = hashlib.sha256)
        message_bytes = message.encode()
        sig = sigkey.sign_deterministic(message_bytes, sigencode = ecdsa.util.sigencode_string_canonize)

        message_hash = hashlib.sha256(message_bytes).digest()
        print('chkGetSignatureTV(\n    "%s",\n    "%s",\n    "%s",\n)' % (privkey.hex(), message_hash.hex(), sig.hex()))

main()
