# Module Rell Standard Library

The Rell Standard Library contains the essential building blocks for writing your rell dapp. It contains:
- Basic data types
- Cryptographic functions
- Chain metadata
- Test library functions

# Package [root]

Global namespace containing types and functions accessible without any namespace prefix.

# Package chain_context

Blockchain metadata and module arguments.

# Package crypto

Cryptographic functions.

# Package op_context

Functions and properties that are only accessible withing the scope of an operation.

### Example

```rell
operation my_operation() {
    print(op_context.exists()); // Prints true
}

query my_query() {
    return op_context.exists(); // Returns false
}
```

# Package rell

Meta information about rell types.

# Package rell.time

Date/time formatting and parsing utilities. Convert between millisecond timestamps and human-readable text via configurable [rell.time.format] patterns.

# Package rell.test

The [rell.test] namespace is only accessible within test modules

### Example

```rell
@test module;

function test() {
    rell.test.block().run();
}
```

# Package rell.test.keypairs

Predefined keypairs that can be used for testing.

# Package rell.test.privkeys

Predefined private keys that can be used for testing.

# Package rell.test.pubkeys

Predefined public keys that can be used for testing.
