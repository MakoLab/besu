/*
 * Copyright 2020 Web3 Labs Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.vm.operations;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.DynamicStruct;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.StaticStruct;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.StaticArray1;
import org.web3j.abi.datatypes.generated.StaticArray2;
import org.web3j.abi.datatypes.generated.StaticArray3;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint32;
import org.web3j.abi.datatypes.Bool;

public class AbiDataStruct {

    public static class Triplet extends DynamicStruct {
        public String object;
        public String predicate;
        public String subject;

        public Triplet(final String object, final String predicate, final String subject) {
            super(
                    new org.web3j.abi.datatypes.Utf8String(object),
                    new org.web3j.abi.datatypes.Utf8String(predicate),
                    new org.web3j.abi.datatypes.Utf8String(subject));
            this.object = object;
            this.predicate = predicate;
            this.subject = subject;
        }

        public Triplet( final Utf8String object, final Utf8String predicate, final Utf8String subject) {
            super(object, predicate, subject);
            this.object = object.getValue();
            this.predicate = predicate.getValue();
            this.subject = subject.getValue();
        }
    }

    public static class ReturnDataSelect extends DynamicStruct {
    	public int count;
		public List<Utf8String> array;
		public boolean error;
		public String message;

		public ReturnDataSelect(final int count, final List<Utf8String> array, final boolean error, final String message){
			super(
				new org.web3j.abi.datatypes.generated.Uint256(count),
				new DynamicArray<Utf8String>(array),
				new Bool(error),
				new Utf8String(message)
			);
			this.count = count;
			this.array = array;
			this.error = error;
			this.message = message;
		}
    }

    public static class ReturnDataAsk extends DynamicStruct {
    	public boolean answer;
		public boolean error;
		public String message;

		public ReturnDataAsk(final boolean answer, final boolean error, final String message) {
			super(
					new Bool(answer),
					new Bool(error),
					new Utf8String(message)
				);
			this.answer = answer;
			this.error = error;
			this.message = message;
		}
    }

    public static class ReturnDataConstruct extends DynamicStruct {
        //public String did;
        public List<Triplet> graph;
		public boolean error;
		public String message;

        public ReturnDataConstruct(final List<Triplet> graph, final boolean error, final String message) {
            super(
              //  new org.web3j.abi.datatypes.Utf8String(did),
                new DynamicArray<Triplet>(graph),
				new Bool(error),
				new Utf8String(message)
            );
            //this.did = did;
            this.graph = graph;
	    	this.error = error;
        }
    }

}
