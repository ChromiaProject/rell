/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

import { getClient } from "../src/client";
import {
	inputParameterAnyMapQueryObject,
	inputParameterBigIntegerQueryObject,
	inputParameterBlockchainRidQueryObject,
	inputParameterBooleanQueryObject,
	inputParameterEntityQueryObject,
	inputParameterEnumMapQueryObject,
	inputParameterEnumQueryObject,
	inputParameterGtvQueryObject,
	inputParameterIntegerQueryObject,
	inputParameterListInputQueryObject,
	inputParameterMapInputQueryObject,
	inputParameterMultipleQueryObject,
	inputParameterNargsQueryObject,
	inputParameterNullableGtvQueryObject,
	inputParameterNullableListInputQueryObject,
	inputParameterNullableQueryObject,
	inputParameterPubkeyQueryObject,
	inputParameterRowidQueryObject,
	inputParameterSetInputQueryObject,
	inputParameterStructQueryObject,
	inputParameterTextQueryObject,
	myNs1MyNs2Q2InNamespaceQueryObject,
	myNs1MyNs2Q3InNamespaceQueryObject,
	myNs1Q10ReturnTypeAnyMapQueryObject,
	myNs1Q1InNamespaceQueryObject,
	myNs1Q2InNamespaceQueryObject,
	myNs1Q3aReturnTypeEnumQueryObject,
	myNs1Q3bReturnTypeEnumQueryObject,
	myNs1Q4ReturnTypeListStructQueryObject,
	myNs1Q5ReturnTypeListStructQueryObject,
	myNs1Q6ReturnTypeListStructQueryObject,
	myNs1Q7ReturnTypeEnumMapQueryObject,
	myNs1Q8ReturnTypeEnumMapQueryObject,
	myNs1Q9ReturnTypeAnyMapQueryObject,
	returnTypeAnyMapQueryObject,
	returnTypeBigIntegerQueryObject,
	returnTypeBooleanQueryObject,
	returnTypeByteArrayQueryObject,
	returnTypeDecimalQueryObject,
	returnTypeEntityQueryObject,
	returnTypeEnumMapQueryObject,
	returnTypeEnumQueryObject,
	returnTypeGtvQueryObject,
	returnTypeIntegerQueryObject,
	returnTypeListBooleanQueryObject,
	returnTypeListByteArrayQueryObject,
	returnTypeListEntityQueryObject,
	returnTypeListGtvQueryObject,
	returnTypeListIntegerQueryObject,
	returnTypeListListListQueryObject,
	returnTypeListStructQueryObject,
	returnTypeMapQueryObject,
	returnTypeNamedTupleListQueryObject,
	returnTypeNamedTupleQueryObject,
	returnTypeNullableEntityQueryObject,
	returnTypeNullableEnumMapQueryObject,
	returnTypeNullableGtvQueryObject,
	returnTypeNullableListEntityQueryObject,
	returnTypeNullableListStructQueryObject,
	returnTypeNullableMapQueryObject,
	returnTypeNullableNamedTupleQueryObject,
	returnTypeNullableRowidQueryObject,
	returnTypeProposalsSinceQueryObject,
	returnTypePubkeyQueryObject,
	returnTypeRowidQueryObject,
	returnTypeSetGtvQueryObject,
	returnTypeSetIntegerQueryObject,
	returnTypeStructQueryObject,
	returnTypeTextQueryObject,
	returnTypeUnnamedTupleQueryObject,
} from "../src/queries/queries";
import { isMapWithTestStructAsKey, isRawGtv } from "./util/type_check";

let client;

beforeAll(async () => {
	client = await getClient();
	const operationAddOneEntity = { name: "before_query_test" };
	await client.sendTransaction(operationAddOneEntity);
});


describe('Return Type Query Object Functions', () => {

	it('should generate the correct QueryObject for returnTypeEnumQueryObject', async () => {
		const queryObject = returnTypeEnumQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_enum",
		});

		expect(typeof response).toBe("string");
	});

	it('should generate the correct QueryObject for returnTypeBooleanQueryObject', async () => {
		const queryObject = returnTypeBooleanQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_boolean",
		});

		expect(typeof response).toBe("number");
	});

	it('should generate the correct QueryObject for returnTypeIntegerQueryObject', async () => {
		const queryObject = returnTypeIntegerQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_integer",
		});

		expect(typeof response).toBe("number");
	});

	it('should generate the correct QueryObject for returnTypeBigIntegerQueryObject', async () => {
		const queryObject = returnTypeBigIntegerQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_big_integer",
		});

		expect(typeof response).toBe("bigint");
	});

	it('should generate the correct QueryObject for returnTypeTextQueryObject', async () => {
		const queryObject = returnTypeTextQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_text",
		});

		expect(typeof response).toBe("string");
	});

	it('should generate the correct QueryObject for returnTypeDecimalQueryObject', async () => {
		const queryObject = returnTypeDecimalQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_decimal",
		});

		expect(typeof response).toBe("string");
	});

	it('should generate the correct QueryObject for returnTypeByteArrayQueryObject', async () => {
		const queryObject = returnTypeByteArrayQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_byte_array",
		});


		expect(response).toBeInstanceOf(Buffer);
	});

	it('should generate the correct QueryObject for returnTypePubkeyQueryObject', async () => {
		const queryObject = returnTypePubkeyQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_pubkey",
		});

		expect(response).toBeInstanceOf(Buffer);
	});

	it('should generate the correct QueryObject for returnTypeEntityQueryObject', async () => {
		const queryObject = returnTypeEntityQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_entity",
		});

		expect(typeof response).toBe("number");
	});

	it('should generate the correct QueryObject for returnTypeNullableEntityQueryObject', async () => {
		const queryObject = returnTypeNullableEntityQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_nullable_entity",
		});

		expect(response === null).toBe(true);
	});

	it('should generate the correct QueryObject for returnTypeStructQueryObject', async () => {
		const queryObject = returnTypeStructQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_struct",
		});


		const expectedReturn = {
			a: 1
		}
		expect(typeof response).toBe("object");
		expect(JSON.stringify(response)).toBe(JSON.stringify(expectedReturn))
	});

	it('should generate the correct QueryObject for returnTypeRowidQueryObject', async () => {
		const queryObject = returnTypeRowidQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_rowid",
		});

		expect(typeof response).toBe("number");
		expect(response).toBe(1);
	});

	it('should generate the correct QueryObject for returnTypeNullableRowidQueryObject', async () => {
		const queryObject = returnTypeNullableRowidQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_nullable_rowid",
		});

		expect(response).toBe(null);
	});

	it('should generate the correct QueryObject for returnTypeGtvQueryObject', async () => {
		const queryObject = returnTypeGtvQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_gtv",
		});

		expect(isRawGtv(response)).toBe(true);
	});

	it('should generate the correct QueryObject for returnTypeNullableGtvQueryObject', async () => {
		const queryObject = returnTypeNullableGtvQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_nullable_gtv",
		});

		expect(isRawGtv(response)).toBe(true);
	});

	it('should generate the correct QueryObject for returnTypeListIntegerQueryObject', async () => {
		const queryObject = returnTypeListIntegerQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_list_integer",
		});

		expect(Array.isArray(response)).toBeTruthy();
		expect(response.every(item => typeof item === 'number')).toBeTruthy();
	});

	it('should generate the correct QueryObject for returnTypeListBooleanQueryObject', async () => {
		const queryObject = returnTypeListBooleanQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_list_boolean",
		});

		expect(Array.isArray(response)).toBeTruthy();
		expect(response.every(item => typeof item === 'number')).toBeTruthy();
	});

	it('should generate the correct QueryObject for returnTypeListByteArrayQueryObject', async () => {
		const queryObject = returnTypeListByteArrayQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_list_byte_array",
		});

		expect(Array.isArray(response)).toBeTruthy();
		expect(response.every(item => item instanceof Buffer)).toBeTruthy();
	});

	it('should generate the correct QueryObject for returnTypeSetIntegerQueryObject', async () => {
		const queryObject = returnTypeSetIntegerQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_set_integer",
		});

		expect(Array.isArray(response)).toBeTruthy();
		expect(response.every(item => typeof item === 'number')).toBeTruthy();
	});

	it('should generate the correct QueryObject for returnTypeListStructQueryObject', async () => {
		const queryObject = returnTypeListStructQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_list_struct",
		});

		expect(Array.isArray(response)).toBeTruthy();
		expect(response.every(item => typeof item === 'object' && typeof item.a === "number")).toBeTruthy();
	});

	it('should generate the correct QueryObject for returnTypeNullableListStructQueryObject', async () => {
		const queryObject = returnTypeNullableListStructQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_nullable_list_struct",
		});

		expect(response === null || (Array.isArray(response) && response.every(item => typeof item === 'object' && item !== null))).toBeTruthy();
	});

	it('should generate the correct QueryObject for returnTypeListEntityQueryObject', async () => {
		const queryObject = returnTypeListEntityQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_list_entity",
		});

		expect(Array.isArray(response)).toBeTruthy();
		expect(response.every(item => typeof item === 'number')).toBeTruthy();
	});

	it('should generate the correct QueryObject for returnTypeNullableListEntityQueryObject', async () => {
		const queryObject = returnTypeNullableListEntityQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_nullable_list_entity",
		});

		expect(response === null || (Array.isArray(response) && response.every(item => typeof item === 'number'))).toBeTruthy();
	});

	it('should generate the correct QueryObject for returnTypeListGtvQueryObject', async () => {
		const queryObject = returnTypeListGtvQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_list_gtv",
		});

		expect(Array.isArray(response)).toBeTruthy();
		expect(response.every(item => typeof item === 'object' && isRawGtv(item))).toBeTruthy();
	});

	it('should generate the correct QueryObject for returnTypeListListListQueryObject', async () => {
		const queryObject = returnTypeListListListQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_list_list_list",
		});

		expect(Array.isArray(response)).toBeTruthy();
		expect(response.every(subList => Array.isArray(subList) &&
			subList.every(subSubList => Array.isArray(subSubList) &&
				subSubList.every(item => typeof item === 'object' && item !== null)
			)
		)).toBeTruthy();
	});

	it('should generate the correct QueryObject for returnTypeSetGtvQueryObject', async () => {
		const queryObject = returnTypeSetGtvQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_set_gtv",
		});

		expect(Array.isArray(response)).toBeTruthy();
		expect(response.every(item => typeof item === 'object' && isRawGtv(item))).toBeTruthy();
	});

	it('should generate the correct QueryObject for returnTypeMapQueryObject', async () => {
		const queryObject = returnTypeMapQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_map",
		});

		expect(typeof response === 'object').toBeTruthy();
		expect(Object.values(response).every(value => typeof value === 'string')).toBeTruthy();
	});

	it('should generate the correct QueryObject for returnTypeNullableMapQueryObject', async () => {
		const queryObject = returnTypeNullableMapQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_nullable_map",
		});

		expect(response === null || (typeof response === 'object' && response !== null)).toBeTruthy();
	});

	it('should generate the correct QueryObject for returnTypeEnumMapQueryObject', async () => {
		const queryObject = returnTypeEnumMapQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_enum_map",
		});

		expect(Array.isArray(response)).toBeTruthy();
		expect(response.every(item => Array.isArray(item) && item.length === 2)).toBeTruthy();
		expect(response.every(item =>
			Array.isArray(item) &&
			item.length === 2 &&
			typeof item[0] == 'string' &&
			typeof item[1] === 'string'
		)).toBe(true);
	});

	it('should generate the correct QueryObject for returnTypeNullableEnumMapQueryObject', async () => {
		const queryObject = returnTypeNullableEnumMapQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_nullable_enum_map",
		});

		expect(response === null || (Array.isArray(response) &&
			response.every(item => Array.isArray(item) && item.length === 2))).toBeTruthy();
	});

	it('should generate the correct QueryObject for returnTypeAnyMapQueryObject', async () => {
		const queryObject = returnTypeAnyMapQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_any_map",
		});

		expect(Array.isArray(response)).toBeTruthy();
		expect(isMapWithTestStructAsKey(response)).toBe(true)
		expect(response.every(item => Array.isArray(item) && item.length === 2 &&
			typeof item[1] === 'string')).toBeTruthy();
	});


	it('should generate the correct QueryObject for returnTypeNamedTupleQueryObject', async () => {
		const queryObject = returnTypeNamedTupleQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_named_tuple",
		});

		expect(typeof response).toBe("object");
		expect(response).toHaveProperty('foo');
		expect(typeof response.foo).toBe("number");
	});

	it('should generate the correct QueryObject for returnTypeNullableNamedTupleQueryObject', async () => {
		const queryObject = returnTypeNullableNamedTupleQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_nullable_named_tuple",
		});

		expect(response === null || (typeof response === "object" && response.hasOwnProperty('foo'))).toBe(true);
	});

	it('should generate the correct QueryObject for returnTypeNamedTupleListQueryObject', async () => {
		const since = 10;
		const queryObject = returnTypeNamedTupleListQueryObject(since);
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_named_tuple_list",
			args: { since: since },
		});

		expect(Array.isArray(response)).toBe(true);
		if (response.length > 0) {
			expect(typeof response[0].rowid).toBe("number");
			expect(typeof response[0].a).toBe("number");
		}
	});

	it('should generate the correct QueryObject for returnTypeUnnamedTupleQueryObject', async () => {
		const queryObject = returnTypeUnnamedTupleQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_unnamed_tuple",
		});

		expect(Array.isArray(response)).toBe(true);
		if (response.length > 0) {
			expect(typeof response[0]).toBe("number");
		}
	});


	it('should generate the correct QueryObject for returnTypeProposalsSinceQueryObject', async () => {
		const since = 0;
		const queryObject = returnTypeProposalsSinceQueryObject(since);
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "return_type_proposals_since",
			args: { since: since },
		});

		expect(Array.isArray(response)).toBe(true);
		expect(response.length).toBe(1);
		expect(typeof response[0].rowid).toBe("number");
		expect(typeof response[0].a).toBe("number");
	});
})


describe('Input argument Query Object Functions', () => {

	it('should generate the correct QueryObject for inputParameterNargsQueryObject', async () => {
		const queryObject = inputParameterNargsQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "input_parameter_nargs",
		});

		expect(typeof response).toBe("number");
	});

	it('should generate the correct QueryObject for inputParameterTextQueryObject', async () => {
		const text = "test";
		const queryObject = inputParameterTextQueryObject(text);
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "input_parameter_text",
			args: { t: text },
		});

		expect(typeof response).toBe("number");
	});

	it('should generate the correct QueryObject for inputParameterNullableQueryObject', async () => {
		const nullableValue = "nullableTest";
		const queryObject = inputParameterNullableQueryObject(nullableValue);
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "input_parameter_nullable",
			args: { t: nullableValue },
		});

		expect(typeof response).toBe("number");
	});

	it('should generate the correct QueryObject for inputParameterEnumQueryObject', async () => {
		const enumValue = 0;
		const queryObject = inputParameterEnumQueryObject(enumValue);
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "input_parameter_enum",
			args: { e: enumValue },
		});

		expect(typeof response).toBe("number");
	});

	it('should generate the correct QueryObject for inputParameterIntegerQueryObject', async () => {
		const integerValue = 123;
		const queryObject = inputParameterIntegerQueryObject(integerValue);
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "input_parameter_integer",
			args: { i: integerValue },
		});

		expect(typeof response).toBe("number");
	});

	it('should generate the correct QueryObject for inputParameterBigIntegerQueryObject', async () => {
		const bigIntegerValue = BigInt(1234567890);
		const queryObject = inputParameterBigIntegerQueryObject(bigIntegerValue);
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "input_parameter_big_integer",
			args: { i: bigIntegerValue },
		});

		expect(typeof response).toBe("number");
	});


	it('should generate the correct QueryObject for inputParameterBooleanQueryObject', async () => {
		const b = 1;
		const queryObject = inputParameterBooleanQueryObject(b);
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "input_parameter_boolean",
			args: { b: b },
		});

		expect(typeof response).toBe("number");
	});

	it('should generate the correct QueryObject for inputParameterRowidQueryObject', async () => {
		const r = 1234;
		const queryObject = inputParameterRowidQueryObject(r);
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "input_parameter_rowid",
			args: { r: r },
		});

		expect(typeof response).toBe("number");
	});

	it('should generate the correct QueryObject for inputParameterPubkeyQueryObject', async () => {
		const pubkey = Buffer.from("abcd", "hex");
		const queryObject = inputParameterPubkeyQueryObject(pubkey);
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "input_parameter_pubkey",
			args: { pubkey: pubkey },
		});

		expect(typeof response).toBe("number");
	});

	it('should generate the correct QueryObject for inputParameterBlockchainRidQueryObject', async () => {
		const blockchainRid = Buffer.from("abcd", "hex");
		const queryObject = inputParameterBlockchainRidQueryObject(blockchainRid);
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "input_parameter_blockchain_rid",
			args: { blockchain_rid: blockchainRid },
		});

		expect(typeof response).toBe("number");
	});


	it('should generate the correct QueryObject for inputParameterEntityQueryObject', async () => {
		const e = 1;
		const queryObject = inputParameterEntityQueryObject(e);
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "input_parameter_entity",
			args: { e: e },
		});

		expect(typeof response).toBe("number");
	});

	it('should generate the correct QueryObject for inputParameterStructQueryObject', async () => {
		const s = { a: 1 };
		const queryObject = inputParameterStructQueryObject(s);
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "input_parameter_struct",
			args: { s: Object.values(s) },
		});

		expect(typeof response).toBe("number");
	});

	it('should generate the correct QueryObject for inputParameterListInputQueryObject', async () => {
		const v = [Buffer.from("abc", "hex"), Buffer.from("def", "hex")];
		const queryObject = inputParameterListInputQueryObject(v);
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "input_parameter_list_input",
			args: { v: v },
		});

		expect(typeof response).toBe("number");
	});

	it('should generate the correct QueryObject for inputParameterNullableListInputQueryObject', async () => {
		const v = null;
		const queryObject = inputParameterNullableListInputQueryObject(v);
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "input_parameter_nullable_list_input",
			args: { v: v },
		});

		expect(typeof response).toBe("number");
	});

	it('should generate the correct QueryObject for inputParameterSetInputQueryObject', async () => {
		const v = new Set([Buffer.from("abc", "hex"), Buffer.from("def", "hex")]);
		const queryObject = inputParameterSetInputQueryObject(v);
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "input_parameter_set_input",
			args: { v: Array.from(v) },
		});

		expect(typeof response).toBe("number");
	});

	it('should generate the correct QueryObject for inputParameterMapInputQueryObject', async () => {
		const v = { key1: Buffer.from("abc", "hex"), key2: Buffer.from("def", "hex") };
		const queryObject = inputParameterMapInputQueryObject(v);
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "input_parameter_map_input",
			args: { v: v },
		});

		expect(typeof response).toBe("number");
	});

	it('should generate the correct QueryObject for inputParameterEnumMapQueryObject', async () => {
		const m = [
			["a", Buffer.from("abc", "hex")],
		];
		const queryObject = inputParameterEnumMapQueryObject(m);
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "input_parameter_enum_map",
			args: { m: m },
		});

		expect(typeof response).toBe("number");
	});

	it('should generate the correct QueryObject for inputParameterAnyMapQueryObject', async () => {
		const s = { a: 1 };
		const m = [
			[s, Buffer.from("abc", "hex")]
		];
		const queryObject = inputParameterAnyMapQueryObject(m);
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "input_parameter_any_map",
			args: { m: m },
		});

		expect(typeof response).toBe("number");
	});

	it('should generate the correct QueryObject for inputParameterMultipleQueryObject', async () => {
		const s = "first string";
		const s2 = "second string";
		const queryObject = inputParameterMultipleQueryObject(s, s2);
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "input_parameter_multiple",
			args: { s: s, s2: s2 },
		});

		expect(typeof response).toBe("number");
	});

	it('should generate the correct QueryObject for inputParameterGtvQueryObject', async () => {
		const g = { "key": "value" };
		const queryObject = inputParameterGtvQueryObject(g);
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "input_parameter_gtv",
			args: { g: g },
		});

		expect(typeof response).toBe("number");
	});

	it('should generate the correct QueryObject for inputParameterNullableGtvQueryObject', async () => {
		const g = null;
		const queryObject = inputParameterNullableGtvQueryObject(g);
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "input_parameter_nullable_gtv",
			args: { g: g },
		});

		expect(typeof response).toBe("number");
	});

})

describe('Namespace Query Object Functions', () => {

	it('should generate the correct QueryObject for myNs1Q1InNamespaceQueryObject', async () => {
		const e = 0;
		const queryObject = myNs1Q1InNamespaceQueryObject(e);
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "my_ns1.q1_in_namespace",
			args: { e: e },
		});

		expect(typeof response).toBe("object");
	});

	it('should generate the correct QueryObject for myNs1Q2InNamespaceQueryObject', async () => {
		const s = { name: "name" }
		const queryObject = myNs1Q2InNamespaceQueryObject(s);
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "my_ns1.q2_in_namespace",
			args: { s: Object.values(s) },
		});

		expect(response).toEqual(s);
	});

	it('should generate the correct QueryObject for myNs1Q3aReturnTypeEnumQueryObject', async () => {
		const e = 0;
		const queryObject = myNs1Q3aReturnTypeEnumQueryObject(e);
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "my_ns1.q3a_return_type_enum",
			args: { e: e },
		});

		expect(typeof response).toBe("string");
	});

	it('should generate the correct QueryObject for myNs1Q3bReturnTypeEnumQueryObject', async () => {
		const m = [
			[0, Buffer.from("abc", "hex")]
		];
		const queryObject = myNs1Q3bReturnTypeEnumQueryObject(m);
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "my_ns1.q3b_return_type_enum",
			args: { m: m },
		});

		expect(typeof response).toBe("string");
	});

	it('should generate the correct QueryObject for myNs1Q4ReturnTypeListStructQueryObject', async () => {
		const s = { a: 1 }
		const m = [
			[Buffer.from("abc", "hex"), s]
		];
		const queryObject = myNs1Q4ReturnTypeListStructQueryObject(m);
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "my_ns1.q4_return_type_list_struct",
			args: { m: m },
		});

		expect(Array.isArray(response)).toBe(true);
	});

	it('should generate the correct QueryObject for myNs1Q5ReturnTypeListStructQueryObject', async () => {
		const s = { a: 1 }
		const v = [s];
		const queryObject = myNs1Q5ReturnTypeListStructQueryObject(v);
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "my_ns1.q5_return_type_list_struct",
			args: { v: v },
		});

		expect(response).toEqual([]);
	});

	it('should generate the correct QueryObject for myNs1Q6ReturnTypeListStructQueryObject', async () => {
		const queryObject = myNs1Q6ReturnTypeListStructQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "my_ns1.q6_return_type_list_struct",
		});

		expect(Array.isArray(response)).toBe(true);
	});

	it('should generate the correct QueryObject for myNs1Q7ReturnTypeEnumMapQueryObject', async () => {
		const queryObject = myNs1Q7ReturnTypeEnumMapQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "my_ns1.q7_return_type_enum_map",
		});

		expect(Array.isArray(response)).toBe(true);
	});

	it('should generate the correct QueryObject for myNs1Q8ReturnTypeEnumMapQueryObject', async () => {
		const queryObject = myNs1Q8ReturnTypeEnumMapQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "my_ns1.q8_return_type_enum_map",
		});

		expect(Array.isArray(response)).toBe(true);
	});

	it('should generate the correct QueryObject for myNs1Q9ReturnTypeAnyMapQueryObject', async () => {
		const queryObject = myNs1Q9ReturnTypeAnyMapQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "my_ns1.q9_return_type_any_map",
		});

		expect(Array.isArray(response)).toBe(true);
	});

	it('should generate the correct QueryObject for myNs1Q10ReturnTypeAnyMapQueryObject', async () => {
		const queryObject = myNs1Q10ReturnTypeAnyMapQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "my_ns1.q10_return_type_any_map",
		});

		expect(Array.isArray(response)).toBe(true);
	});

	it('should generate the correct QueryObject for myNs1MyNs2Q2InNamespaceQueryObject', async () => {
		const queryObject = myNs1MyNs2Q2InNamespaceQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "my_ns1.my_ns2.q2_in_namespace",
		});

		expect(typeof response).toBe("string");
	});

	it('should generate the correct QueryObject for myNs1MyNs2Q3InNamespaceQueryObject', async () => {
		const queryObject = myNs1MyNs2Q3InNamespaceQueryObject();
		const response = await client.query(queryObject);

		expect(queryObject).toEqual({
			name: "my_ns1.my_ns2.q_3_in_namespace",
		});

		expect(response).toEqual(expect.objectContaining({ foo: expect.any(Number) }));
	});
});
