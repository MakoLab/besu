pragma solidity >=0.5.0;

/**
 * @title Owner
 * @dev Set & change owner
 */

contract Sub {
    function get(string calldata arg) external view returns(string memory) {
        return arg;
    }
}

contract Test {

    Sub sub;
    
    constructor() public {
        sub = new Sub();
    }

    function get(string calldata arg) external view returns(string memory) {
        
        return sub.get(arg);
    }
}
