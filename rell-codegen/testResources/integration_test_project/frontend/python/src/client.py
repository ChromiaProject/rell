import asyncio
from postchain_client_py import Operation
from postchain_client_py.blockchain_client import BlockchainClient
from postchain_client_py.blockchain_client.types import NetworkSettings, Transaction, ClientConfig

async def get_client():
    settings = NetworkSettings(
        node_url_pool=["http://chromia-node:7740"],
        blockchain_iid=0,
    )
    return await BlockchainClient.create(settings)
