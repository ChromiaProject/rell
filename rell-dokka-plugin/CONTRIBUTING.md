# Contributing to Rell Dokka Plugin

## Dokka Developer Guide

- [Plugin development](https://kotlin.github.io/dokka/2.0.0/developer_guide/plugin-development/introduction/)
- [Core extension points](https://kotlin.github.io/dokka/2.0.0/developer_guide/architecture/extension_points/core_extension_points/)

## Extending the Plugin

1. Identify the appropriate pipeline stage to extend
2. Create a custom implementation of the relevant interface
3. Register it in `RellDokkaPlugin` using the `extending` mechanism
4. Test with both regular Rell code and system library documentation

See [Architecture](docs/Architecture.md) for component details.
