const { defineConfig } = require('eslint/config')
const expo = require('eslint-config-expo/flat')
const prettier = require('eslint-config-prettier')

module.exports = defineConfig([
  expo,
  prettier,
  { ignores: ['.expo', 'dist', 'node_modules'] },
])
