/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

/** @type {import('jest').Config} */
module.exports = {
	preset: 'ts-jest', // Use ts-jest to transpile TypeScript files
	testEnvironment: 'node', // Use Node.js environment for tests
    testTimeout: 30 * 1000,
	testMatch: ['**/tests/**/*.test.ts'], // Match test files
	moduleFileExtensions: ['ts', 'js'], // Support TypeScript and JavaScript files
  };