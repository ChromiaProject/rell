


export function isRawGtv(value) {
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

export function isMapWithTestStructAsKey(response) {
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