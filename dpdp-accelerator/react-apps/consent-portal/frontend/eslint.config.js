/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import js from '@eslint/js'
import { FlatCompat } from '@eslint/eslintrc'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import { defineConfig, globalIgnores } from 'eslint/config'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const compat = new FlatCompat({
  baseDirectory: __dirname,
})

export default defineConfig([
  globalIgnores([
    'dist',
    // Ignore this flat config file to avoid linting the config through itself.
    'eslint.config.js',
  ]),
  // TODO: Remove FlatCompat once eslint-config-airbnb supports native flat config.
  ...compat.extends('airbnb', 'airbnb/hooks', 'plugin:prettier/recommended'),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
      parserOptions: {
        project: ['./tsconfig.app.json', './tsconfig.node.json', './tsconfig.test.json'],
      },
    },
    settings: {
      'import/resolver': {
        node: {
          extensions: ['.js', '.jsx', '.ts', '.tsx'],
        },
      },
    },
    rules: {
      // TypeScript + Vite patterns used in this project.
      'react/react-in-jsx-scope': 'off',
      'react/jsx-filename-extension': ['error', { extensions: ['.tsx'] }],
      'import/extensions': 'off',
      'import/prefer-default-export': 'off',
      'import/no-extraneous-dependencies': ['error', { packageDir: [__dirname] }],
      'prettier/prettier': 'error',
    },
  },
  {
    files: ['vite.config.ts', 'vitest.setup.ts'],
    rules: {
      'import/no-extraneous-dependencies': 'off',
      'import/no-unresolved': 'off',
    },
  },
  {
    files: ['scripts/**/*.mjs'],
    languageOptions: {
      ecmaVersion: 2023,
      sourceType: 'module',
      globals: globals.node,
    },
    rules: {
      'import/no-extraneous-dependencies': 'off',
      'no-console': 'off',
      'no-restricted-syntax': 'off',
    },
  },
  {
    files: ['**/*.test.{ts,tsx}', '**/__tests__/**/*.{ts,tsx}'],
    rules: {
      'import/no-extraneous-dependencies': 'off',
    },
  },
])
