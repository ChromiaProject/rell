# Module Rell

Most dapp blockchain platforms use virtual machines of various kinds. But a traditional virtual machine architecture doesn't work very well with the Chromia relational data model, as we need a way to encode queries and operations. For this reason, ChromaWay is taking a more language-centric approach: a newly developed language called Rell (Relational language) that's used for dapp programming. This language allows programmers to describe the data model/schema, queries, and procedural app code.

Rell code gets compiled to an intermediate binary format, which is code for a specialized virtual machine. Chromia nodes then translate queries in this code into SQL (while ensuring this translation is safe) and execute code as needed using an interpreter or compiler.

# Package root

Basic data types.

# Package crypto

Cryptographic functions.

# Package op_context

Functions and properties that are only accessible withing the scope of an operation.

### Example

```rell
operation my_operation() {
    print(op_context.exists()); // Prints true
}

operation my_query() {
    return op_context.exists(); // Returns true
}
```

# Package chain_context

This namespace contains properties describing current blockchain.

# Package rell.test

The rell.test namespace is only accessible withing test modules

### Example

```rell
@test module;

function test() {
    rell.test.block().run();
}
```

# Package rell

Meta information about rell types.

# Package rell.test.keypairs

Predefined keypairs that can be used for testing.

# Package rell.test.privkeys

Predefined private keys that can be used for testing.

# Package rell.test.pubkeys

Predefined public keys that can be used for testing.
