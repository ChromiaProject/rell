
/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

import * as pcl from "postchain-client"


export async function getClient() {
	return await pcl.createClient({ 
		blockchainIid: 0,
		nodeUrlPool: ["http://chromia-node:7740"]
	})
}