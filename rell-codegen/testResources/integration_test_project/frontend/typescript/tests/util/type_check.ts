/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

import { RawGtv } from "postchain-client";
import { TestEnum, TestStruct } from "../../src/queries/queries";

export function isRawGtv(value: unknown): value is RawGtv {
    return (
        value === null ||
        typeof value === 'boolean' ||
        value instanceof Buffer ||
        typeof value === 'string' ||
        typeof value === 'number' ||
        typeof value === 'bigint' ||
        (Array.isArray(value) && value.every(isRawGtv)) ||
        (value !== null && typeof value === 'object' && 'key' in value && 'value' in value)
    );
}


export function isTestEnum(value: any): value is TestEnum {
    return Object.values(TestEnum).includes(value);
}

export function isMapWithTestStructAsKey(response: unknown): response is Array<[TestStruct, string]> {
    return (
        Array.isArray(response) &&
        response.every(
            item =>
                Array.isArray(item) &&
                item.length === 2 &&
                typeof item[0] === 'object' &&
                'a' in item[0] && // assuming TestStruct has an 'a' property
                typeof item[1] === 'string'
        )
    );
}