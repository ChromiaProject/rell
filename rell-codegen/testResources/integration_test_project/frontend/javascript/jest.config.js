/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

/** @type {import('jest').Config} */
module.exports = {
	testEnvironment: 'node',
    testTimeout: 30 * 1000,
	testMatch: ['**/tests/**/*.test.js'],
	moduleFileExtensions: ['js'],
  };
