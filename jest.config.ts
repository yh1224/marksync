export default {
    roots: [
        "<rootDir>/tests"
    ],
    moduleNameMapper: {
        "^#/(.+)": "<rootDir>/src/$1"
    },
    testMatch: [
        "**/tests/**/*.ts"
    ],
    transform: {
        "^.+\\.ts$": "ts-jest"
    }
};
