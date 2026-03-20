# Client Stubs Integration Test Project
## Overview
This project is dedicated to running integration tests for TypeScript and Javascript client-stubs generated from Rell code. 
The integration tests focus on verifying that the client-stubs can correctly interact with a running node, ensuring 
both proper input handling and return type translation.

### General Info
* **Goals of the Integration Tests**
  * Type Translation Verification: Ensure that the input and return types are correctly translated from Rell to Typescript/Javascript.
  *  Execution Validation: Verify that the generated stubs are executable and that they correctly query a running node,
handling real-world scenarios.


* **Queries vs. Operations**: This project specifically tests queries, as they involve both input arguments and return types. 
Unlike operations, which do not return values, queries allow to validate type translation on both input arguments
and return type.


* **Stub Management**: The TypeScript/Javascript client-stubs used in the tests are currently checked into the repository. 
While this works for now, a potential improvement is to automate the generation of these stubs as part of the test 
setup. This would ensure that the most up-to-date stubs are always used during testing.


### Execution
#### Kotlin Context
The tests are executed in a Kotlin context using Docker containers, which spin up a Postgres database, a Chromia node,
and a Node environment. See `TypescriptCodegenITTest.kt` or `JavascriptCodegenITTest.kt`.

#### TypeScript Context
To run the tests manually:

1. Run chromia node from within rell folder
   1. Remove the database.host property from chromia.yml, to use the default host of localhost. Using 
`database.host: postgres` is to target the network alias when using the test containers.
   2. Start the node with `chr node start` (ensure Postgres is running before).


2. Executing stub test from within frontend folder either javascript, or typescript
   1. Install dependencies by running `npm install`
   2. Update nodeUrlPool in client config to target `"http://localhost:7740"`
   3. Execute test by running `npm run test`
