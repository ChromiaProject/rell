/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

/*
 * Copyright (c) 2021 EmeraldPay Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Source:
// https://github.com/emeraldpay/etherjar/blob/68e46c8fc3a996bdb5a0ff384770fe6108201f37/etherjar-tx/src/main/java/io/emeraldpay/etherjar/tx/PrivateKey.java

package net.postchain.rell.base.utils.etherjar;

import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.ec.FixedPointCombMultiplier;

import java.math.BigInteger;

public class PrivateKey {

    private final byte[] raw;

    private PrivateKey(byte[] raw) {
        this.raw = raw;
    }

    public static PrivateKey create(byte[] raw) {
        if (raw.length != 32) {
            throw new IllegalArgumentException("Invalid PK length: " + raw.length);
        }
        return new PrivateKey(raw.clone());
    }

    public byte[] getRaw() {
        //do not give reference to the original key, otherwise caller may modify the key
        return raw.clone();
    }

    public byte[] getPublicKey() {
        return PrivateKey.getPublicKey(new BigInteger(1, raw));
    }

    public static byte[] getPublicKey(BigInteger pk) {
        FixedPointCombMultiplier mul = new FixedPointCombMultiplier();
        ECPoint point = mul.multiply(Signer.CURVE_PARAMS.getG(), pk);
        byte[] full = point.getEncoded(false);
        byte[] ethereum = new byte[full.length - 1];
        System.arraycopy(full, 1, ethereum, 0, ethereum.length);
        return ethereum;
    }

    public ECPrivateKeyParameters getECKey() {
        return new ECPrivateKeyParameters(
                new BigInteger(1, raw),
                Signer.CURVE_PARAMS
        );
    }
}
