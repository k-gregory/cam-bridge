const path = require('path');
const HtmlWebpackPlugin = require('html-webpack-plugin');

module.exports = {
  entry: './src/index.ts',
  mode: 'development',
  devtool: 'inline-source-map',
  devServer: {
    static: './dist',
    server: 'https',
    proxy: [
      {
        context: ['/api/signaling'],
        target: 'wss://localhost:8443',
        secure: false,
        ws: true
      },
    ],
  },
  optimization: {
    runtimeChunk: 'single',
  },
  
  plugins: [
    new HtmlWebpackPlugin({
      title: 'CamBridge',
    }),

  ],
  
  module: {
    rules: [
      { test: /\.ts$/, use: 'ts-loader' },
    ]
  },

  

  output: {
    path: path.resolve(__dirname, 'dist'),
    filename: '[name].bundle.js',
  }
};
