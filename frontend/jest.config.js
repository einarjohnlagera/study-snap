/* eslint-disable @typescript-eslint/no-require-imports */
const nextJest = require("next/jest");

const createJestConfig = nextJest({
  dir: "./",
});

// ⚠️ Enumerating the ESM chain package-by-package does NOT converge — react-markdown pulls in
// unified, remark, micromark, mdast/hast utils and a long tail of single-purpose ESM modules
// (trim-lines, html-url-attributes, …), and each fix reveals the next. Allow-list the two large
// CommonJS dependencies that must NOT be transformed for speed, and transpile the rest.
const ESM_PACKAGES_TO_TRANSPILE = [
  "/node_modules/(?:react|react-dom|next|@testing-library|jest-.*)/",
];

const customJestConfig = {
  testEnvironment: "jsdom",
  setupFilesAfterEnv: ["<rootDir>/jest.setup.ts"],
  moduleNameMapper: {
    "^@/(.*)$": "<rootDir>/$1",
  },
  testPathIgnorePatterns: ["<rootDir>/node_modules/", "<rootDir>/.next/"],
  // react-markdown and the whole remark/micromark/unified chain ship pure ESM. next/jest excludes
  // node_modules from transformation, so importing them threw and SummaryMarkdown had to be mocked
  // globally in jest.setup.ts — which left the component rendering summaries on nine surfaces, some
  // of them SEO-indexed, with no real coverage anywhere. Transpiling that chain lets tests render it.
};

// ⚠️ transformIgnorePatterns must be applied AFTER createJestConfig, because next/jest overwrites
// whatever the passed-in config sets. Setting it inside customJestConfig silently does nothing.
module.exports = async () => {
  const config = await createJestConfig(customJestConfig)();
  config.transformIgnorePatterns = ESM_PACKAGES_TO_TRANSPILE;
  return config;
};
