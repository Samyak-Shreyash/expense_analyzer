const path = require('path')
const { getDefaultConfig } = require('expo/metro-config')

const config = getDefaultConfig(__dirname)

// The shared package lives outside this app and has no node_modules of its own.
config.watchFolders = [path.resolve(__dirname, '../packages/api-client')]
config.resolver.nodeModulesPaths = [path.resolve(__dirname, 'node_modules')]

module.exports = config
