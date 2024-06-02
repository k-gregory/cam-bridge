const path = require('path');
const HtmlWebpackPlugin = require('html-webpack-plugin');

module.exports = {
  entry: './src/index.tsx',
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
      template: "./src/index.html"
    }),

  ],

  resolve: {
    extensions: ['.ts', '.tsx', '.js']
  },
  
  module: {
    rules: [
      { test: /\.tsx?$/, use: 'ts-loader', exclude: /node_modules/ },
    ]
  },

  

  output: {
    path: path.resolve(__dirname, 'dist'),
    filename: '[name].bundle.js',
  }
};
