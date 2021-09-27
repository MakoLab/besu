var HDWalletProvider = require("truffle-hdwallet-provider");
const MNEMONIC = 'YOUR WALLET KEY';

module.exports = {
  networks: {
    development: {
      host: "127.0.0.1",
      port: 8545,
      network_id: "*"
    },
    ropsten: {
      provider: function() {
        return new HDWalletProvider("zoo small forum dinosaur leg inflict bunker daughter warm capital charge jump", "http://localhost:8545")
      },
      network_id: "*",
      gas: 4000000      //make sure this gas allocation isn't over 4M, which is the max
    }
  }
};
