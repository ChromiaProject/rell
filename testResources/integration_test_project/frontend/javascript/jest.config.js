
export default {
    testEnvironment: 'jest-environment-node',
    transform: {}, // Explicitly state no transformation
    testTimeout: 30 * 1000,
    testMatch: ['**/tests/**/*.test.js'],
    moduleFileExtensions: ['js'],
};