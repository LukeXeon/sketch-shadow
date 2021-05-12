const path = require('path');
module.exports = {
    entry: './src/main/js/index.js',
    output: {
        path: path.resolve(__dirname, 'build', 'js'),
        filename: 'index.bundle.js',
    },
    module: {
        rules: [
            {
                test: /\.m?js$/,
                exclude: /node_modules/,
                use: {
                    loader: 'babel-loader',
                    options: {
                        presets: [
                            [
                                '@babel/preset-env'
                            ]
                        ],
                        plugins: [
                            '@babel/plugin-transform-runtime'
                        ]
                    }
                }
            }
        ]
    },
};
