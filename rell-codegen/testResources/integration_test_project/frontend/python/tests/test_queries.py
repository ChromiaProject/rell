from asyncio.log import logger
from postchain_client_py import Operation
from postchain_client_py.blockchain_client.types import QueryObject
import pytest
import pytest_asyncio
import asyncio
from typing import Any, Dict, List, Optional
from src.client import get_client
from postchain_client_py.blockchain_client import BlockchainClient
from postchain_client_py.blockchain_client.types import Transaction
from src.queries.queries import (
    TestEnum,
    MyNs1LocalTestEnum,
    TestStruct,
    MyNs1TestStruct2,
    MyNs1LocalTestStruct,
    MyNs1MyNs12TestStruct2,
    before_query_test_operation,
    # Return type query functions
    return_type_enum,
    return_type_boolean,
    return_type_integer,
    return_type_big_integer,
    return_type_text,
    return_type_decimal,
    return_type_byte_array,
    return_type_pubkey,
    return_type_entity,
    return_type_nullable_entity,
    return_type_struct,
    return_type_rowid,
    return_type_nullable_rowid,
    return_type_gtv,
    return_type_nullable_gtv,
    return_type_list_integer,
    return_type_list_boolean,
    return_type_list_byte_array,
    return_type_set_integer,
    return_type_list_struct,
    return_type_nullable_list_struct,
    return_type_list_entity,
    return_type_nullable_list_entity,
    return_type_list_gtv,
    return_type_list_list_list,
    return_type_set_gtv,
    return_type_map,
    return_type_nullable_map,
    return_type_enum_map,
    return_type_nullable_enum_map,
    return_type_any_map,
    return_type_named_tuple,
    return_type_nullable_named_tuple,
    return_type_named_tuple_list,
    return_type_unnamed_tuple,
    return_type_proposals_since,
    # Input parameter query functions
    input_parameter_nargs,
    input_parameter_text,
    input_parameter_nullable,
    input_parameter_enum,
    input_parameter_integer,
    input_parameter_big_integer,
    input_parameter_boolean,
    input_parameter_rowid,
    input_parameter_pubkey,
    input_parameter_blockchain_rid,
    input_parameter_entity,
    input_parameter_struct,
    input_parameter_list_input,
    input_parameter_nullable_list_input,
    input_parameter_set_input,
    input_parameter_map_input,
    input_parameter_enum_map,
    input_parameter_any_map,
    input_parameter_multiple,
    input_parameter_gtv,
    input_parameter_nullable_gtv,
    # Namespace query functions
    my_ns1_q1_in_namespace,
    my_ns1_q2_in_namespace,
    my_ns1_q3a_return_type_enum,
    my_ns1_q3b_return_type_enum,
    my_ns1_q4_return_type_list_struct,
    my_ns1_q5_return_type_list_struct,
    my_ns1_q6_return_type_list_struct,
    my_ns1_q7_return_type_enum_map,
    my_ns1_q8_return_type_enum_map,
    my_ns1_q9_return_type_any_map,
    my_ns1_q10_return_type_any_map,
    my_ns1_my_ns2_q2_in_namespace,
    my_ns1_my_ns2_q_3_in_namespace,
)
from tests.util.type_check import (
    is_map_with_test_struct_as_key,
    is_raw_gtv,
    is_test_enum,
)

# Monkey-patch QueryObject to omit empty args in to_dict()
def _custom_to_dict(self) -> Dict[str, Any]:
    query_dict = {"name": self.name}
    if self.args:
        query_dict["args"] = self.args
    return query_dict

QueryObject.to_dict = _custom_to_dict

@pytest_asyncio.fixture(scope="session")
async def client():
    client_instance = await get_client()
    tx = Transaction(
        operations=[before_query_test_operation()]
    )
    await client_instance.send_transaction(tx)
    return client_instance


class TestReturnTypeQueries:
    @pytest.mark.asyncio
    async def test_return_type_enum(self, client):
        query_object = return_type_enum()
        response = await client.query(query_object)
        expected_query_object = {
            "name": "return_type_enum",
        }
        
        assert query_object == QueryObject.from_dict(expected_query_object)
        assert isinstance(response, str)

    @pytest.mark.asyncio
    async def test_return_type_boolean_query_object_creation(self, client):
        query_object = return_type_boolean()
        response = await client.query(query_object)
        expected_query_object = {
            "name": "return_type_boolean",
            "args": {}
        }
        
        assert query_object == QueryObject.from_dict(expected_query_object)
        assert isinstance(response, int) 

    @pytest.mark.asyncio
    async def test_return_type_boolean(self, client):
        query_object = return_type_boolean()
        response = await client.query(query_object)

        expected_query_object = {
            "name": "return_type_boolean"
        }
        
        
        assert query_object == QueryObject.from_dict(expected_query_object)
        assert isinstance(response, int) and response in [0, 1]

    @pytest.mark.asyncio
    async def test_return_type_integer(self, client):
        query_object = return_type_integer()
        response = await client.query(query_object)
        expected_query_object = {
            "name": "return_type_integer"
        }
        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, int)

    @pytest.mark.asyncio
    async def test_return_type_big_integer(self, client):
        query_object = return_type_big_integer()
        response = await client.query(query_object)
        expected_query_object = {
            "name": "return_type_big_integer"
        }

        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, int)

    @pytest.mark.asyncio
    async def test_return_type_text(self, client):
        query_object = return_type_text()
        response = await client.query(query_object)
        expected_query_object = {
            "name": "return_type_text"
        }

        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, str)

    @pytest.mark.asyncio
    async def test_return_type_decimal(self, client):
        query_object = return_type_decimal()
        response = await client.query(query_object)

        expected_query_object = {
            "name": "return_type_decimal"
        }

        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, str)

    @pytest.mark.asyncio
    async def test_return_type_byte_array(self, client):
        query_object = return_type_byte_array()
        response = await client.query(query_object)
        expected_query_object = {
            "name": "return_type_byte_array"
        }

        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, bytes)

    @pytest.mark.asyncio
    async def test_return_type_pubkey(self, client):
        query_object = return_type_pubkey()
        response = await client.query(query_object)
        expected_query_object = {
            "name": "return_type_pubkey"
        }

        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, bytes)

    @pytest.mark.asyncio
    async def test_return_type_entity(self, client):
        query_object = return_type_entity()
        response = await client.query(query_object)
        expected_query_object = {
            "name": "return_type_entity"
        }

        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, int)

    @pytest.mark.asyncio
    async def test_return_type_nullable_entity(self, client):
        query_object = return_type_nullable_entity()
        response = await client.query(query_object)

        expected_query_object = {
            "name": "return_type_nullable_entity"
        }
        assert query_object.to_dict() == expected_query_object
        assert response is None

    @pytest.mark.asyncio
    async def test_return_type_struct(self, client):
        query_object = return_type_struct()
        response = await client.query(query_object)

        expected_query_object = {
            "name": "return_type_struct"
        }
        assert query_object.to_dict() == expected_query_object

        assert isinstance(response, dict)

    @pytest.mark.asyncio
    async def test_return_type_rowid(self, client):
        query_object = return_type_rowid()
        response = await client.query(query_object)

        expected_query_object = {
            "name": "return_type_rowid"
        }
        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, int)
        assert response == 1

    @pytest.mark.asyncio
    async def test_return_type_nullable_rowid(self, client):
        query_object = return_type_nullable_rowid()
        response = await client.query(query_object)
        expected_query_object = {
            "name": "return_type_nullable_rowid"
        }
        assert query_object.to_dict() == expected_query_object
        assert response is None

    @pytest.mark.asyncio
    async def test_return_type_gtv(self, client):
        query_object = return_type_gtv()
        response = await client.query(query_object)
        expected_query_object = {
            "name": "return_type_gtv"
        }
        assert query_object.to_dict() == expected_query_object
        assert is_raw_gtv(response)

    @pytest.mark.asyncio
    async def test_return_type_nullable_gtv(self, client):
        query_object = return_type_nullable_gtv()
        response = await client.query(query_object)
        expected_query_object = {
            "name": "return_type_nullable_gtv"
        }
        assert query_object.to_dict() == expected_query_object
        assert is_raw_gtv(response)

    @pytest.mark.asyncio
    async def test_return_type_list_integer(self, client):
        query_object = return_type_list_integer()
        response = await client.query(query_object)

        expected_query_object = {
            "name": "return_type_list_integer"
        }
        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, list)
        assert all(isinstance(item, int) for item in response)

    @pytest.mark.asyncio
    async def test_return_type_list_boolean(self, client):
        query_object = return_type_list_boolean()
        response = await client.query(query_object)

        expected_query_object = {
            "name": "return_type_list_boolean"
        }
        assert query_object == QueryObject.from_dict(expected_query_object)
        assert isinstance(response, list)
        assert all(isinstance(item, int) for item in response)

    @pytest.mark.asyncio
    async def test_return_type_list_byte_array(self, client):
        query_object = return_type_list_byte_array()
        response = await client.query(query_object)

        expected_query_object = {
            "name": "return_type_list_byte_array"
        }
        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, list)
        assert all(isinstance(item, bytes) for item in response)

    @pytest.mark.asyncio
    async def test_return_type_set_integer(self, client):
        query_object = return_type_set_integer()
        response = await client.query(query_object)

        expected_query_object = {
            "name": "return_type_set_integer"
        }
        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, list)
        assert all(isinstance(item, int) for item in response)

    @pytest.mark.asyncio
    async def test_return_type_list_struct(self, client):
        query_object = return_type_list_struct()
        response = await client.query(query_object)
        expected_query_object = {
            "name": "return_type_list_struct"
        }
        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, list)
        assert all(
            isinstance(item, dict) and isinstance(item.get("a"), int)
            for item in response
        )

    @pytest.mark.asyncio
    async def test_return_type_nullable_list_struct(self, client):
        query_object = return_type_nullable_list_struct()
        response = await client.query(query_object)
        expected_query_object = {
            "name": "return_type_nullable_list_struct"
        }
        assert query_object.to_dict() == expected_query_object
        assert response is None or (
            isinstance(response, list)
            and all(isinstance(item, dict) for item in response)
        )

    @pytest.mark.asyncio
    async def test_return_type_list_entity(self, client):
        query_object = return_type_list_entity()
        response = await client.query(query_object)

        expected_query_object = {
            "name": "return_type_list_entity"
        }
        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, list)
        assert all(isinstance(item, int) for item in response)

    @pytest.mark.asyncio
    async def test_return_type_nullable_list_entity(self, client):
        query_object = return_type_nullable_list_entity()
        response = await client.query(query_object)
        expected_query_object = {
            "name": "return_type_nullable_list_entity"
        }
        assert query_object == QueryObject.from_dict(expected_query_object)
        assert response is None or (
            isinstance(response, list)
            and all(isinstance(item, int) for item in response)
        )

    @pytest.mark.asyncio
    async def test_return_type_list_gtv(self, client):
        query_object = return_type_list_gtv()
        response = await client.query(query_object)

        expected_query_object = {
            "name": "return_type_list_gtv"
        }
        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, list)
        assert all(is_raw_gtv(item) for item in response)

    @pytest.mark.asyncio
    async def test_return_type_list_list_list(self, client):
        query_object = return_type_list_list_list()
        response = await client.query(query_object)
        
        expected_query_object = {
            "name": "return_type_list_list_list"
        }
        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, list)
        assert all(
            isinstance(sub_list, list)
            and all(
                isinstance(sub_sub_list, list)
                and all(isinstance(item, dict) for item in sub_sub_list)
                for sub_sub_list in sub_list
            )
            for sub_list in response
        )

    @pytest.mark.asyncio
    async def test_return_type_set_gtv(self, client):
        query_object = return_type_set_gtv()
        response = await client.query(query_object)
        
        expected_query_object = {
            "name": "return_type_set_gtv"
        }
        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, list)
        assert all(is_raw_gtv(item) for item in response)

    @pytest.mark.asyncio
    async def test_return_type_map(self, client):
        query_object = return_type_map()
        response = await client.query(query_object)

        expected_query_object = {
            "name": "return_type_map"
        }
        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, dict)
        assert all(isinstance(value, str) for value in response.values())

    @pytest.mark.asyncio
    async def test_return_type_nullable_map(self, client):
        query_object = return_type_nullable_map()
        response = await client.query(query_object)
        
        expected_query_object = {
            "name": "return_type_nullable_map"
        }
        assert query_object.to_dict() == expected_query_object
        assert response is None or isinstance(response, dict)

    @pytest.mark.asyncio
    async def test_return_type_enum_map(self, client):
        query_object = return_type_enum_map()
        response = await client.query(query_object)
        
        expected_query_object = {
            "name": "return_type_enum_map"
        }
        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, list)
        assert all(isinstance(item, list) and len(item) == 2 for item in response)
        assert all(
            is_test_enum(item[0]) and isinstance(item[1], str) for item in response
        )

    @pytest.mark.asyncio
    async def test_return_type_nullable_enum_map(self, client):
        query_object = return_type_nullable_enum_map()
        response = await client.query(query_object)
        
        expected_query_object = {
            "name": "return_type_nullable_enum_map"
        }
        assert query_object.to_dict() == expected_query_object
        assert response is None or (
            isinstance(response, list)
            and all(isinstance(item, list) and len(item) == 2 for item in response)
        )

    @pytest.mark.asyncio
    async def test_return_type_any_map(self, client):
        query_object = return_type_any_map()
        response = await client.query(query_object)
        
        expected_query_object = {
            "name": "return_type_any_map"
        }
        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, list)

    @pytest.mark.asyncio
    async def test_return_type_named_tuple(self, client):
        query_object = return_type_named_tuple()
        response = await client.query(query_object)
        
        expected_query_object = {
            "name": "return_type_named_tuple"
        }
        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, dict)
        assert "foo" in response
        assert isinstance(response["foo"], int)

    @pytest.mark.asyncio
    async def test_return_type_nullable_named_tuple(self, client):
        query_object = return_type_nullable_named_tuple()
        response = await client.query(query_object)
        
        expected_query_object = {
            "name": "return_type_nullable_named_tuple"
        }
        assert query_object.to_dict() == expected_query_object
        assert response is None or (isinstance(response, dict) and "foo" in response)

    @pytest.mark.asyncio
    async def test_return_type_named_tuple_list(self, client):
        since = 10
        query_object = return_type_named_tuple_list(since)
        response = await client.query(query_object)
        expected_query_object = {
            "name": "return_type_named_tuple_list",
            "args": {"since": since},
        }
        assert query_object.to_dict() == expected_query_object
        if len(response) > 0:
            assert isinstance(response[0]["rowId"], int)
            assert isinstance(response[0]["a"], int)

    @pytest.mark.asyncio
    async def test_return_type_unnamed_tuple(self, client):
        query_object = return_type_unnamed_tuple()
        response = await client.query(query_object)
        
        expected_query_object = {
            "name": "return_type_unnamed_tuple"
        }
        assert query_object == QueryObject.from_dict(expected_query_object)
        assert isinstance(response, list)
        if len(response) > 0:
            assert isinstance(response[0], int)

    @pytest.mark.asyncio
    async def test_return_type_proposals_since(self, client):
        since = 0
        query_object = return_type_proposals_since(since)
        response = await client.query(query_object)
        
        expected_query_object = {
            "name": "return_type_proposals_since",
            "args": {"since": since},
        }
        assert query_object == QueryObject.from_dict(expected_query_object)
        assert isinstance(response, list)


class TestInputArgumentQueries:

    @pytest.mark.asyncio
    async def test_input_parameter_nargs(self, client):
        query_object = input_parameter_nargs()
        response = await client.query(query_object)
        
        expected_query_object = {
            "name": "input_parameter_nargs"
        }
        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, int)

    @pytest.mark.asyncio
    async def test_input_parameter_text(self, client):
        text = "test"
        query_object = input_parameter_text(text)
        response = await client.query(query_object)

        expected_query_object = {
            "name": "input_parameter_text",
            "args": {"t": text}
        }
        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, int)

    @pytest.mark.asyncio
    async def test_input_parameter_nullable(self, client):
        nullable_value = "nullableTest"
        query_object = input_parameter_nullable(nullable_value)
        response = await client.query(query_object)
        
        expected_query_object = {
            "name": "input_parameter_nullable",
            "args": {"t": nullable_value},
        }

        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, int)

    @pytest.mark.asyncio
    async def test_input_parameter_enum(self, client):
        enum_value  = TestEnum.A
        query_object = input_parameter_enum(enum_value)
        response = await client.query(query_object)
        expected_query_object = {
            "name": "input_parameter_enum",
            "args": {"e": enum_value.value},
        }
        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, int)

    @pytest.mark.asyncio
    async def test_input_parameter_integer(self, client):
        integer_value = 123
        query_object = input_parameter_integer(integer_value)
        response = await client.query(query_object)

        expected_query_object = {
            "name": "input_parameter_integer",
            "args": {"i": integer_value},
        }
        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, int)

    @pytest.mark.asyncio
    async def test_input_parameter_big_integer(self, client):
        big_integer_value = 1234567890
        query_object = input_parameter_big_integer(big_integer_value)
        response = await client.query(query_object)

        expected_query_object = {
            "name": "input_parameter_big_integer",
            "args": {"i": big_integer_value},
        }
        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, int)

    @pytest.mark.asyncio
    async def test_input_parameter_boolean(self, client):
        b = False
        query_object = input_parameter_boolean(b)
        response = await client.query(query_object)
        
        expected_query_object = {
            "name": "input_parameter_boolean",
            "args": {"b": b},
        }
        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, int)

    @pytest.mark.asyncio
    async def test_input_parameter_rowid(self, client):
        r = 1234
        query_object = input_parameter_rowid(r)
        response = await client.query(query_object)
        
        expected_query_object = {
            "name": "input_parameter_rowid",
            "args": {"r": r},
        }
        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, int)

    @pytest.mark.asyncio
    async def test_input_parameter_pubkey(self, client):
        pubkey = bytes.fromhex("abcd")
        query_object = input_parameter_pubkey(pubkey)
        response = await client.query(query_object)

        expected_query_object = {
            "name": "input_parameter_pubkey",
            "args": {"pubkey": pubkey},
        }
        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, int)

    @pytest.mark.asyncio
    async def test_input_parameter_blockchain_rid(self, client):
        blockchain_rid = bytes.fromhex("abcd")
        query_object = input_parameter_blockchain_rid(blockchain_rid)
        response = await client.query(query_object)
        
        expected_query_object = {
            "name": "input_parameter_blockchain_rid",
            "args": {"blockchain_rid": blockchain_rid},
        }
        assert query_object == QueryObject.from_dict(expected_query_object)
        assert isinstance(response, int)

    @pytest.mark.asyncio
    async def test_input_parameter_entity(self, client):
        e = 1
        query_object = input_parameter_entity(e)
        response = await client.query(query_object)
        expected_query_object = {
            "name": "input_parameter_entity",
            "args": {"e": e},
        }
        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, int)

    @pytest.mark.asyncio
    async def test_input_parameter_struct(self, client):
        s = TestStruct(a=1)
        query_object = input_parameter_struct(s)
        response = await client.query(query_object)

        expected_query_object = {
            "name": "input_parameter_struct",
            "args": {"s": s.to_dict()},
        }
        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, int)

    @pytest.mark.asyncio
    async def test_input_parameter_list_input(self, client):
        v = [bytes.fromhex("abcd"), bytes.fromhex("deff")]
        query_object = input_parameter_list_input(v)
        response = await client.query(query_object)
        expected_query_object = {
            "name": "input_parameter_list_input",
            "args": {"v": v},
        }
        assert query_object == QueryObject.from_dict(expected_query_object)
        assert isinstance(response, int)

    @pytest.mark.asyncio
    async def test_input_parameter_nullable_list_input(self, client):
        v = None
        query_object = input_parameter_nullable_list_input(v)
        response = await client.query(query_object)
        expected_query_object = {
            "name": "input_parameter_nullable_list_input",
            "args": {"v": v},
        }
        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, int)

    @pytest.mark.asyncio
    async def test_input_parameter_set_input(self, client):
        v = {bytes.fromhex("abcd"), bytes.fromhex("deff")}
        query_object = input_parameter_set_input(v)
        response = await client.query(query_object)
        expected_query_object = {
            "name": "input_parameter_set_input",
            "args": {"v": list(v)},
        }
        assert query_object == QueryObject.from_dict(expected_query_object)
        assert isinstance(response, int)

    @pytest.mark.asyncio
    async def test_input_parameter_map_input(self, client):
        v = {"key1": bytes.fromhex("abcd"), "key2": bytes.fromhex("deff")}
        query_object = input_parameter_map_input(v)
        response = await client.query(query_object)
        expected_query_object = {
            "name": "input_parameter_map_input",
            "args": {"v": v},
        }
        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, int)

    @pytest.mark.asyncio
    async def test_input_parameter_enum_map(self, client):
        m = {TestEnum.A: bytes.fromhex("abcd")}
        query_object = input_parameter_enum_map(m)
        response = await client.query(query_object)
        expected_query_object = {
            "name": "input_parameter_enum_map",
            "args": {"m": [[k.value, v] for k, v in m.items()]},
        }
        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, int)

    @pytest.mark.asyncio
    async def test_input_parameter_any_map(self, client):
        s = TestStruct(a=1)
        m = {s: bytes.fromhex("abcd")}
        query_object = input_parameter_any_map(m)
        response = await client.query(query_object)
        expected_query_object = {
            "name": "input_parameter_any_map",
            "args": {"m": [[k.to_dict(), v] for k, v in m.items()]},
        }
        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, int)

    @pytest.mark.asyncio
    async def test_input_parameter_multiple(self, client):
        s = "first string"
        s2 = "second string"
        query_object = input_parameter_multiple(s, s2)
        response = await client.query(query_object)
        expected_query_object = {   
            "name": "input_parameter_multiple",
            "args": {"s": s, "s2": s2},
        }
        assert query_object == QueryObject.from_dict(expected_query_object)
        assert isinstance(response, int)

    @pytest.mark.asyncio
    async def test_input_parameter_gtv(self, client):
        g = {"key": "value"}
        query_object = input_parameter_gtv(g)
        response = await client.query(query_object)
        expected_query_object = {
            "name": "input_parameter_gtv",
            "args": {"g": g},
        }
        assert query_object == QueryObject.from_dict(expected_query_object)
        assert isinstance(response, int)

    @pytest.mark.asyncio
    async def test_input_parameter_nullable_gtv(self, client):
        g = None
        query_object = input_parameter_nullable_gtv(g)
        response = await client.query(query_object)
        expected_query_object = {
            "name": "input_parameter_nullable_gtv",
            "args": {"g": g},
        }
        assert query_object == QueryObject.from_dict(expected_query_object)
        assert isinstance(response, int)


class TestNamespaceQueries:

    @pytest.mark.asyncio
    async def test_my_ns1_q1_in_namespace(self, client):
        e = TestEnum.A
        query_object = my_ns1_q1_in_namespace(e)
        response = await client.query(query_object)
        expected_query_object = {
            "name": "my_ns1.q1_in_namespace",
            "args": {"e": e.value},
        }
        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, dict)

    @pytest.mark.asyncio
    async def test_my_ns1_q2_in_namespace(self, client):
        s = MyNs1TestStruct2(name="name")
        query_object = my_ns1_q2_in_namespace(s)
        response = await client.query(query_object)
        expected_query_object = {
            "name": "my_ns1.q2_in_namespace",
            "args": {"s": s.to_dict()},
        }
        assert query_object.to_dict() == expected_query_object
        assert response == {"name": s.name}

    @pytest.mark.asyncio
    async def test_my_ns1_q3a_return_type_enum(self, client):
        e = MyNs1LocalTestEnum.A
        query_object = my_ns1_q3a_return_type_enum(e)
        response = await client.query(query_object)
        expected_query_object = {
            "name": "my_ns1.q3a_return_type_enum",
            "args": {"e": e.value},
        }
        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, str)

    @pytest.mark.asyncio
    async def test_my_ns1_q3b_return_type_enum(self, client):
        m = { TestEnum.A: bytes.fromhex("abcd") }
        query_object = my_ns1_q3b_return_type_enum(m)
        response = await client.query(query_object)
        expected_query_object = {
            "name": "my_ns1.q3b_return_type_enum",
            "args": {"m": [[k.value, v] for k, v in m.items()]},
        }
        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, str)

    @pytest.mark.asyncio
    async def test_my_ns1_q4_return_type_list_struct(self, client):
        s = MyNs1MyNs12TestStruct2(a=1)
        m = {bytes.fromhex("abcd"): s}
        query_object = my_ns1_q4_return_type_list_struct(m)
        response = await client.query(query_object)
        expected_query_object = {
            "name": "my_ns1.q4_return_type_list_struct",
            "args": {"m": [[k, v.to_dict()] for k, v in m.items()]},
        }
        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, list)

    @pytest.mark.asyncio
    async def test_my_ns1_q5_return_type_list_struct(self, client):
        s = MyNs1LocalTestStruct(a=1)
        v = [s]
        query_object = my_ns1_q5_return_type_list_struct(v)
        response = await client.query(query_object)
        expected_query_object = {
            "name": "my_ns1.q5_return_type_list_struct",
            "args": {"v": [s.to_dict()]},
        }
        assert query_object.to_dict() == expected_query_object
        assert response == []

    @pytest.mark.asyncio
    async def test_my_ns1_q6_return_type_list_struct(self, client):
        query_object = my_ns1_q6_return_type_list_struct()
        response = await client.query(query_object)
        expected_query_object = {
            "name": "my_ns1.q6_return_type_list_struct"
        }
        assert query_object == QueryObject.from_dict(expected_query_object)
        assert isinstance(response, list)

    @pytest.mark.asyncio
    async def test_my_ns1_q7_return_type_enum_map(self, client):
        query_object = my_ns1_q7_return_type_enum_map()
        response = await client.query(query_object)
        expected_query_object = {
            "name": "my_ns1.q7_return_type_enum_map"
        }
        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, list)

    @pytest.mark.asyncio
    async def test_my_ns1_q8_return_type_enum_map(self, client):
        query_object = my_ns1_q8_return_type_enum_map()
        response = await client.query(query_object)
        expected_query_object = {
            "name": "my_ns1.q8_return_type_enum_map"
        }
        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, list)

    @pytest.mark.asyncio
    async def test_my_ns1_q9_return_type_any_map(self, client):
        query_object = my_ns1_q9_return_type_any_map()
        response = await client.query(query_object)
        expected_query_object = {
            "name": "my_ns1.q9_return_type_any_map"
        }
        assert query_object == QueryObject.from_dict(expected_query_object)
        assert isinstance(response, list)

    @pytest.mark.asyncio
    async def test_my_ns1_q10_return_type_any_map(self, client):
        query_object = my_ns1_q10_return_type_any_map()
        response = await client.query(query_object)
        expected_query_object = {
            "name": "my_ns1.q10_return_type_any_map"
        }
        assert query_object == QueryObject.from_dict(expected_query_object)
        assert isinstance(response, list)

    @pytest.mark.asyncio
    async def test_my_ns1_my_ns2_q2_in_namespace(self, client):
        query_object = my_ns1_my_ns2_q2_in_namespace()
        response = await client.query(query_object)
        expected_query_object = {
            "name": "my_ns1.my_ns2.q2_in_namespace"
        }
        assert query_object.to_dict() == expected_query_object
        assert isinstance(response, str)

    @pytest.mark.asyncio
    async def test_my_ns1_my_ns2_q3_in_namespace(self, client):
        query_object = my_ns1_my_ns2_q_3_in_namespace()
        response = await client.query(query_object)
        expected_query_object = {
            "name": "my_ns1.my_ns2.q_3_in_namespace"
        }
        assert query_object == QueryObject.from_dict(expected_query_object)
        assert isinstance(response, dict) and "foo" in response and isinstance(
            response["foo"], int
        )
