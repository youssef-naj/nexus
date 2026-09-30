import js from "@eslint/js"
import globals from "globals"
import reactHooks from "eslint-plugin-react-hooks"
import reactRefresh from "eslint-plugin-react-refresh"
import tseslint from "typescript-eslint"
import prettier from "eslint-config-prettier"
import { defineConfig, globalIgnores } from "eslint/config"

export default defineConfig([
  globalIgnores(["dist", "coverage"]),
  {
    files: ["**/*.{ts,tsx}"],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      ecmaVersion: 2023,
      globals: globals.browser,
    },
  },
  {
    // Node-side config files
    files: ["vite.config.ts"],
    languageOptions: { globals: globals.node },
  },
  {
    // shadcn/ui components export variants next to components by design
    files: ["src/components/ui/**"],
    rules: { "react-refresh/only-export-components": "off" },
  },
  // Must stay last: disables stylistic rules that conflict with Prettier
  prettier,
])
