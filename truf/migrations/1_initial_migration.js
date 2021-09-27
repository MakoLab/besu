var HelloWorld = artifacts.require("Test");
module.exports = function(deployer) {
    deployer.deploy(HelloWorld);
    // Additional contracts can be deployed here
};
