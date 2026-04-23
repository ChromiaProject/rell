// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
// Standalone test for the extern "C" native numeric op glue (no JVM, no LLVM).
#include <cstdio>
#include <cstring>

#include "rell_numeric_ops.h"

static int fails = 0;

static void chkdec(const char *a, const char *op, const char *b, const char *expect) {
    rell_num_pool_reset();
    void *x = rell_num_decimal_parse(a);
    void *y = rell_num_decimal_parse(b);
    void *r = nullptr;
    if (!strcmp(op, "+")) r = rell_num_decimal_add(x, y);
    else if (!strcmp(op, "-")) r = rell_num_decimal_sub(x, y);
    else if (!strcmp(op, "*")) r = rell_num_decimal_mul(x, y);
    else if (!strcmp(op, "/")) r = rell_num_decimal_div(x, y);
    else if (!strcmp(op, "%")) r = rell_num_decimal_rem(x, y);
    const char *got = rell_num_decimal_to_string(r);
    if (rell_num_pending() || strcmp(got, expect) != 0) {
        printf("FAIL dec: %s %s %s => '%s' (want '%s') pending=%d code=%s\n", a, op, b, got, expect,
               rell_num_pending(), rell_num_error_code());
        fails++;
    }
}

static void chkint(const char *a, const char *op, const char *b, const char *expect) {
    rell_num_pool_reset();
    void *x = rell_num_bigint_parse(a);
    void *y = rell_num_bigint_parse(b);
    void *r = nullptr;
    if (!strcmp(op, "+")) r = rell_num_bigint_add(x, y);
    else if (!strcmp(op, "-")) r = rell_num_bigint_sub(x, y);
    else if (!strcmp(op, "*")) r = rell_num_bigint_mul(x, y);
    else if (!strcmp(op, "/")) r = rell_num_bigint_div(x, y);
    else if (!strcmp(op, "%")) r = rell_num_bigint_rem(x, y);
    const char *got = rell_num_bigint_to_string(r);
    if (rell_num_pending() || strcmp(got, expect) != 0) {
        printf("FAIL int: %s %s %s => '%s' (want '%s')\n", a, op, b, got, expect);
        fails++;
    }
}

int main() {
    chkdec("2.5", "+", "0.3", "2.8");
    chkdec("2.5", "*", "2", "5");
    chkdec("1", "/", "3", "0.33333333333333333333");  // scale 20, HALF_UP
    chkdec("10", "-", "3.5", "6.5");
    chkdec("7", "%", "3", "1");
    chkdec("-2.5", "+", "0", "-2.5");

    chkint("123456789012345678901234567890", "+", "1", "123456789012345678901234567891");
    chkint("-7", "/", "2", "-3");   // truncate toward zero
    chkint("-7", "%", "2", "-1");   // remainder takes dividend's sign
    chkint("1000000000000", "*", "1000000000000", "1000000000000000000000000");

    // deferred error: decimal / 0 must set pending
    rell_num_pool_reset();
    rell_num_decimal_div(rell_num_decimal_parse("1"), rell_num_decimal_parse("0"));
    if (!rell_num_pending()) { printf("FAIL: decimal /0 did not set pending\n"); fails++; }

    if (fails == 0) printf("rell_numeric_ops: ALL PASS\n");
    else printf("rell_numeric_ops: %d FAILURES\n", fails);
    return fails ? 1 : 0;
}
