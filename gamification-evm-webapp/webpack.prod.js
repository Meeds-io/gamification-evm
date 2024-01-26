const path = require('path');
const { merge } = require('webpack-merge');
const webpackCommonConfig = require('./webpack.common.js');

const config = merge(webpackCommonConfig, {
  mode: 'production',
  entry: {
  },
  output: {
    path: path.join(__dirname, 'target/gamification-evm/'),
    filename: 'js/[name].bundle.js',
    libraryTarget: 'amd'
  }
});

module.exports = config;
