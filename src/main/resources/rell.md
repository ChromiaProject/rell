# Module Rell Standard Library

The Rell Standard Library contains the essential building blocks for writing your rell dapp. It contains:
- Basic data types
- Cryptographic functions
- Chain metadata
- Test library functions

# Package root

Global namespace containing types and functions accessible without any namespace prefix.

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
