// ESLint v9 flat config. Mirrors the previous .eslintrc.json rule set.
const tsParser = require("@typescript-eslint/parser");
const tsPlugin = require("@typescript-eslint/eslint-plugin");
const js = require("@eslint/js");

module.exports = [
  { ignores: ["lib/**", "node_modules/**", "coverage/**"] },
  js.configs.recommended,
  {
    files: ["src/**/*.ts"],
    languageOptions: {
      parser: tsParser,
      parserOptions: { project: ["./tsconfig.json"], sourceType: "module" },
      globals: { fetch: "readonly", Response: "readonly", URLSearchParams: "readonly", Buffer: "readonly", console: "readonly", process: "readonly" },
    },
    plugins: { "@typescript-eslint": tsPlugin },
    rules: {
      ...tsPlugin.configs.recommended.rules,
      "no-console": "error",
      "no-implicit-coercion": "error",
      "@typescript-eslint/no-explicit-any": "error",
      // The Node 20 runtime ships fetch via undici. Pulling undici as a direct
      // dependency drifts from what's actually loaded at runtime.
      "no-restricted-imports": [
        "error",
        {
          paths: [
            {
              name: "undici",
              message: "Node 20 ships a global fetch; do not add an explicit undici dep.",
            },
          ],
        },
      ],
    },
  },
  {
    files: ["test/**/*.ts"],
    languageOptions: {
      parser: tsParser,
      parserOptions: { sourceType: "module" },
      globals: { jest: "readonly", describe: "readonly", it: "readonly", expect: "readonly", beforeEach: "readonly", afterEach: "readonly", Response: "readonly", TextEncoder: "readonly", Buffer: "readonly", globalThis: "readonly" },
    },
    plugins: { "@typescript-eslint": tsPlugin },
    rules: {
      ...tsPlugin.configs.recommended.rules,
      "@typescript-eslint/no-explicit-any": "off",
      "no-console": "off",
    },
  },
];
