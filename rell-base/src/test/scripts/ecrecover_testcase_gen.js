/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

// npm -g install web3
// npm link web3
// node ecrecover_testcase_gen.js

const util = require('util');
const Web3 = require('web3');
const web3 = new Web3();

function random_bytes() {
    var s = "0x";
    for (var i = 0; i < 32; ++i) {
        var v = Math.floor(Math.random() * 256);
        var t = v.toString(16);
        if (t.length < 2) t = "0" + t;
        s += t;
    }
    return s;
}

function process_input(r, s, h, result) {
    var vs = ["0x1b", "0x1c"];

    for (i in vs) {
        var v = vs[i];
        var input = {
            r: r,
            s: s,
            messageHash: h,
            v: v
        };

        var res;
        try {
            res = web3.eth.accounts.recover(input);
        } catch (e) {
            res = "error";
        }

        result.push({
            r: r,
            s: s,
            h: h,
            v: v,
            res: res
        });
    }
}

function main() {
    result = [];

    process_input(
        "0xb91467e570a6466aa9e9876cbcd013baba02900b8979d43fe208a4a4f339f5fd",
        "0x6007e74cd82e037b800186422fc2da167c747ef045e5d18a5f5d4300f8e1a029",
        "0x1da44b586eb0729ff70a73c326926f6ed5a25f5b056e7f47fbc6e58d86871655",
        result
    );

    for (var i = 0, n = (256 - result.length) / 2; i < n; ++i) {
        var r = random_bytes();
        var s = random_bytes();
        var h = random_bytes();
        process_input(r, s, h, result);
    }

    console.log(JSON.stringify(result, null, 4));
}

main();
