# Client Stubs Integration Test Project
## Overview
This project is dedicated to running integration tests for TypeScript client-stubs generated from Rell code. 
The integration tests focus on verifying that the client-stubs can correctly interact with a running node, ensuring 
both proper input handling and return type translation.

### General Info
* **Goals of the Integration Tests**
  * Type Translation Verification: Ensure that the input and return types are correctly translated from Rell to Typescript.
  *  Execution Validation: Verify that the generated stubs are executable and that they correctly query a running node,
handling real-world scenarios.


* **Queries vs. Operations**: This project specifically tests queries, as they involve both input arguments and return types. 
Unlike operations, which do not return values, queries allow to validate type translation on both input arguments
and return type.


* **Stub Management**: The TypeScript client-stubs used in the tests are currently checked into the repository. 
While this works for now, a potential improvement is to automate the generation of these stubs as part of the test 
setup. This would ensure that the most up-to-date stubs are always used during testing.


### Execution
#### Kotlin Context
The tests are executed in a Kotlin context using Docker containers, which spin up a Postgres database, a Chromia node,
and a Node environment. See `TypescriptCodegenITTest.kt`.

#### TypeScript Context
To run the tests manually:

1. Start the node with `chr node start` in the rell folder (ensure Postgres is running).
2. In the frontend folder, run `npm run test` (after installing dependencies).

##### Make sure to:
1. Remove the database.host property from chromia.yml.
2. Update the client to target localhost instead of the Docker network alias:
