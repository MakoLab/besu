/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.vm.operations;

import com.google.common.base.Splitter;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.vm.AbstractCallOperation;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.ethereum.vm.Words;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

public class LoadGraphOperation extends AbstractCallOperation {

  public LoadGraphOperation(final GasCalculator gasCalculator) {
    super(0x21, "LOADGRAPHCALL", 6, 1, false, 1, gasCalculator);
  }

  @Override
  protected Gas gas(final MessageFrame frame) {
    return Gas.of(frame.getStackItem(0));
  }

  @Override
  protected Address to(final MessageFrame frame) {
    return Words.toAddress(frame.getStackItem(1));
  }

  @Override
  protected Wei value(final MessageFrame frame) {
    return Wei.ZERO;
  }

  @Override
  protected Wei apparentValue(final MessageFrame frame) {
    return value(frame);
  }

  @Override
  protected UInt256 inputDataOffset(final MessageFrame frame) {
    return frame.getStackItem(2);
  }

  @Override
  protected UInt256 inputDataLength(final MessageFrame frame) {
    return frame.getStackItem(3);
  }

  @Override
  protected UInt256 outputDataOffset(final MessageFrame frame) {
    return frame.getStackItem(4);
  }

  @Override
  protected UInt256 outputDataLength(final MessageFrame frame) {
    return frame.getStackItem(5);
  }

  @Override
  protected Address address(final MessageFrame frame) {
    return to(frame);
  }

  @Override
  protected Address sender(final MessageFrame frame) {
    return frame.getRecipientAddress();
  }

  @Override
  public Gas gasAvailableForChildCall(final MessageFrame frame) {
    return gasCalculator().gasAvailableForChildCall(frame, gas(frame), !value(frame).isZero());
  }

  @Override
  protected boolean isStatic(final MessageFrame frame) {
    return true;
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    final Gas stipend = gas(frame);
    final UInt256 inputDataOffset = inputDataOffset(frame);
    final UInt256 inputDataLength = inputDataLength(frame);
    final UInt256 outputDataOffset = outputDataOffset(frame);
    final UInt256 outputDataLength = outputDataLength(frame);
    final Account recipient = frame.getWorldState().get(address(frame));

    return gasCalculator()
        .callOperationGasCost(
            frame,
            stipend,
            inputDataOffset,
            inputDataLength,
            outputDataOffset,
            outputDataLength,
            value(frame),
            recipient,
            to(frame));
  }

  @Override
  public void complete(final MessageFrame frame, final MessageFrame childFrame) {
    frame.setState(MessageFrame.State.CODE_EXECUTING);

    frame.getInputData();

    String s = frame.getInputData().toString().substring(138);

    System.out.println("LOADGRAPH INPUT DATA : " + hexToAscii(s));
    System.out.println("LOADGRAPH PC : " + frame.getPC());
    System.out.println("LOADGRAPH OUTPUT frame: " + frame.toString());
    System.out.println("LOADGRAPH OUTPUT childframe: " + childFrame.toString());
    System.out.println("LOADGRAPH OUTPUT childframe.outputdata: " + childFrame.getOutputData());
    System.out.println("LOADGRAPH OUTPUT childframe.outputDataLength: " + outputDataLength(frame));
    final UInt256 outputOffset = outputDataOffset(frame);

    String text;

    try {
      text = getHTML("http://ontoshell:8080/ontonode/rdf-graph-store?graph=" + hexToAscii(s));
    } catch (Exception e) {
      System.out.println("FAILED: " + e);
      text = "error";
      childFrame.setState(MessageFrame.State.REVERT);
    }

    System.out.println("LOADGRAPH TEXT: " + text);

    String textBytes = Bytes.of(text.getBytes(StandardCharsets.UTF_8)).toString();
    String outputStr = "0000000000000000000000000000000000000000000000000000000000000020";
    String sizeSection = padLeftZeros(Bytes.ofUnsignedInt(text.length()).toString().substring(2),64);
    outputStr += sizeSection;

    Iterable<String> splitTextBytes = Splitter.fixedLength(64).split(textBytes);

    String finalTextBytes = "";

    for (String str: splitTextBytes) {
      if(str.length() == 64) {
        finalTextBytes += str;
      } else {
        finalTextBytes += str;
        for (int i = 0; i < 64 - str.length(); i++) {
          //Pad right
          finalTextBytes += "0";
        }
      }
    }

    outputStr += finalTextBytes.substring(2);

    System.out.println("OUTPUT STRING: " + outputStr);

    final Bytes outputData = Bytes.fromHexString(outputStr);
    System.out.println("LOADGRAPH OUTPUT DATA: " + outputData.toString());
    final UInt256 outputSize = UInt256.ZERO;
    System.out.println("LOADGRAPH OUTPUT SIZE: " + outputSize.toString());
    final int outputSizeAsInt = outputSize.intValue();

    if (outputSizeAsInt > outputData.size()) {
      frame.expandMemory(outputOffset, outputSize);
      frame.writeMemory(outputOffset, UInt256.valueOf(outputData.size()), outputData, true);
    } else {
      frame.writeMemory(outputOffset, outputSize, outputData, true);
    }

    frame.setReturnData(outputData);
    frame.addLogs(childFrame.getLogs());
    frame.addSelfDestructs(childFrame.getSelfDestructs());
    frame.incrementGasRefund(childFrame.getGasRefund());

    final Gas gasRemaining = childFrame.getRemainingGas();
    frame.incrementRemainingGas(gasRemaining);

    frame.popStackItems(getStackItemsConsumed());
    if (childFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
      frame.mergeWarmedUpFields(childFrame);
      frame.pushStackItem(UInt256.ONE);
    } else {
      frame.pushStackItem(UInt256.ZERO);
    }

    final int currentPC = frame.getPC();
    frame.setPC(currentPC + 1);


  }

  public String padLeftZeros(final String inputString,final int length) {
    if (inputString.length() >= length) {
      return inputString;
    }
    StringBuilder sb = new StringBuilder();
    while (sb.length() < length - inputString.length()) {
      sb.append('0');
    }
    sb.append(inputString);

    return sb.toString();
  }

  private static String hexToAscii(final String  hexStr) {
    StringBuilder output = new StringBuilder("");

    for (int i = 0; i < hexStr.length(); i += 2) {
      String str = hexStr.substring(i, i + 2);
      output.append((char) Integer.parseInt(str, 16));
    }

    return output.toString();
  }


  public static String getHTML(final String urlToRead) throws Exception {
    StringBuilder result = new StringBuilder();
    URL url = new URL(urlToRead);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestProperty("Content-Type","text/plain");
    conn.setRequestMethod("GET");
    try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(conn.getInputStream(), Charset.defaultCharset()))) {
      for (String line; (line = reader.readLine()) != null; ) {
        result.append(line);
      }
    }
    return result.toString();
  }

}

// /*
//  * Copyright ConsenSys AG.
//  *
//  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
//  * the License. You may obtain a copy of the License at
//  *
//  * http://www.apache.org/licenses/LICENSE-2.0
//  *
//  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
//  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
//  * specific language governing permissions and limitations under the License.
//  *
//  * SPDX-License-Identifier: Apache-2.0
//  */
// package org.hyperledger.besu.ethereum.vm.operations;

// import com.google.common.base.Splitter;
// import org.apache.tuweni.bytes.Bytes;
// import org.apache.tuweni.units.bigints.UInt256;
// import org.hyperledger.besu.ethereum.core.Account;
// import org.hyperledger.besu.ethereum.core.Address;
// import org.hyperledger.besu.ethereum.core.Gas;
// import org.hyperledger.besu.ethereum.core.Wei;
// import org.hyperledger.besu.ethereum.vm.AbstractCallOperation;
// import org.hyperledger.besu.ethereum.vm.GasCalculator;
// import org.hyperledger.besu.ethereum.vm.MessageFrame;
// import org.hyperledger.besu.ethereum.vm.Words;

// import java.io.BufferedReader;
// import java.io.InputStreamReader;
// import java.net.HttpURLConnection;
// import java.net.URL;
// import java.nio.charset.Charset;
// import java.nio.charset.StandardCharsets;
// import java.util.Arrays;
// import java.util.Base64;
// import java.util.Optional;
// import com.google.common.base.Splitter;
// import java.util.List;

// import org.hyperledger.besu.ethereum.vm.operations.AbiV2TestFixture.Triplet;
// import static org.web3j.abi.TypeEncoder.encode;
// import org.web3j.abi.datatypes.Utf8String;
// import org.web3j.abi.datatypes.DynamicArray;
// import java.io.OutputStreamWriter;
// import java.io.OutputStream;
// import java.util.ArrayList;

// public class LoadGraphOperation extends AbstractCallOperation {

//     public LoadGraphOperation(final GasCalculator gasCalculator) {
//         super(0x21, "LOADGRAPHCALL", 6, 1, false, 1, gasCalculator);
//     }

//     @Override
//     protected Gas gas(final MessageFrame frame) {
//         return Gas.of(frame.getStackItem(0));
//     }

//     @Override
//     protected Address to(final MessageFrame frame) {
//         return Words.toAddress(frame.getStackItem(1));
//     }

//     @Override
//     protected Wei value(final MessageFrame frame) {
//         return Wei.ZERO;
//     }

//     @Override
//     protected Wei apparentValue(final MessageFrame frame) {
//         return value(frame);
//     }

//     @Override
//     protected UInt256 inputDataOffset(final MessageFrame frame) {
//         return frame.getStackItem(2);
//     }

//     @Override
//     protected UInt256 inputDataLength(final MessageFrame frame) {
//         return frame.getStackItem(3);
//     }

//     @Override
//     protected UInt256 outputDataOffset(final MessageFrame frame) {
//         return frame.getStackItem(4);
//     }

//     @Override
//     protected UInt256 outputDataLength(final MessageFrame frame) {
//         return frame.getStackItem(5);
//     }

//     @Override
//     protected Address address(final MessageFrame frame) {
//         return to(frame);
//     }

//     @Override
//     protected Address sender(final MessageFrame frame) {
//         return frame.getRecipientAddress();
//     }

//     @Override
//     public Gas gasAvailableForChildCall(final MessageFrame frame) {
//         return gasCalculator().gasAvailableForChildCall(frame, gas(frame), !value(frame).isZero());
//     }

//     @Override
//     protected boolean isStatic(final MessageFrame frame) {
//         return true;
//     }

//     @Override
//     public Gas cost(final MessageFrame frame) {
//         final Gas stipend = gas(frame);
//         final UInt256 inputDataOffset = inputDataOffset(frame);
//         final UInt256 inputDataLength = inputDataLength(frame);
//         final UInt256 outputDataOffset = outputDataOffset(frame);
//         final UInt256 outputDataLength = outputDataLength(frame);
//         final Account recipient = frame.getWorldState().get(address(frame));

//         return gasCalculator()
//                 .callOperationGasCost(
//                         frame,
//                         stipend,
//                         inputDataOffset,
//                         inputDataLength,
//                         outputDataOffset,
//                         outputDataLength,
//                         value(frame),
//                         recipient,
//                         to(frame));
//     }

//     @Override
//     public void complete(final MessageFrame frame, final MessageFrame childFrame) {
        
//         frame.setState(MessageFrame.State.CODE_EXECUTING);

//         frame.getInputData();

//         String s_1 = frame.getInputData().toString().substring(138);

//         // System.out.println("LOADGRAPH INPUT DATA : " + hexToAscii(s_1));
//         // System.out.println("LOADGRAPH PC : " + frame.getPC());
//         // System.out.println("LOADGRAPH OUTPUT frame: " + frame.toString());
//         // System.out.println("LOADGRAPH OUTPUT childframe: " + childFrame.toString());
//         // System.out.println("LOADGRAPH OUTPUT childframe.outputdata: " + childFrame.getOutputData());
//         // System.out.println("LOADGRAPH OUTPUT childframe.outputDataLength: " + outputDataLength(frame));
//         final UInt256 outputOffset = outputDataOffset(frame);
        
        
//         //String text = "<one> <two> <three> .";

//         // try {
//         //     text = getHTML("http://ontonode04.makodev.pl/ontonode/rdf-graph-store?graph=did:ihash:" + hexToAscii(s_1));
//         //     text = "<error> <error> <error> ." + text;
//         // } catch (Exception e) {
//         //     System.out.println("FAILED: " + e);
//         //     text = "error";
//         //     text = "<nie> <przerzlo> <zapytanie> ." + g;
//         //     childFrame.setState(MessageFrame.State.REVERT);
//         // }

//         // System.out.println("LOADGRAPH:" + text);
//         // StringBuilder outputStr = new StringBuilder("");
//         // List<String> parts = Splitter.on(" .").splitToList(text);
//         // outputStr.append(padLeftZeros(Integer.toString(parts.size()-1), 64));
        

//         // for (int i = 0; i < parts.size()-1; i++) {
//         // outputStr.append("0000000000000000000000000000000000000000000000000000000000000000");
//         // //outputStr.append(padLeftZeros("0", 64 * (parts.size() - 1)));
//         // }
//         //     System.out.println("Size graph:" + parts.size());
//         // for (int i = 0; i < parts.size()-1; i++) {
//         //     //parts.set(i, parts.get(i).trim());
//         //     System.out.println(i + ":" + parts.get(i));
//         //     int lengthOfArray = outputStr.length();
//         //     String d = Integer.toHexString(lengthOfArray/2);
//         //     int x = 64 * (i+1);
//         //     int y = x + 64;
//         //     outputStr.replace(x, y, padLeftZeros(d,64));
//         //     String[] words = parts.get(i).split(" ", 3);
//         //     StringBuilder str = new StringBuilder("0000000000000000000000000000000000000000000000000000000000000003");
//         //     //StringBuilder str = new StringBuilder(padLeftZeros("3",64));
//         //     str.append("0000000000000000000000000000000000000000000000000000000000000000");
//         //     str.append("0000000000000000000000000000000000000000000000000000000000000000");
//         //     str.append("0000000000000000000000000000000000000000000000000000000000000000");
//         //     //str.append(padLeftZeros("0", 64*3));

//         //     for (int j = 0; j < words.length; j++) {
//         //         words[j] = words[j].trim();
//         //         System.out.print(words[j] + " ");
//         //         int howManyBytesOfTriplet = str.length() - 64;
//         //         String offsetWord = Integer.toHexString(howManyBytesOfTriplet/2);
//         //         int x1 = 64 * (j + 1);
//         //         int y1 = x1 + 64;
//         //         str.replace(x1, y1, padLeftZeros(offsetWord, 64));
//         //         str.append(padLeftZeros(Integer.toString(words[j].length()),64));
//         //         String finalTextBytes = "";
//         //         String textBytes = Bytes.of(words[j].getBytes(StandardCharsets.UTF_8)).toString().substring(2);
//         //         Iterable<String> splitTextBytes = Splitter.fixedLength(64).split(textBytes);
//         //         for(String s : splitTextBytes) {
//         //             if (str.length() == 64) {
//         //                 finalTextBytes += s;
//         //             } else {
//         //                 finalTextBytes += s;

//         //                 for (int ia = 0; ia < 64 - s.length(); ia++) {
//         //                     //Pad right
//         //                     finalTextBytes += "0";
//         //                 }
//         //             }
//         //         }
//         //         str.append(finalTextBytes);
//         //     }
//         //     System.out.println("|");
//         //     outputStr.append(str);
//         // }
        
//         // String outputStrFinal = "0000000000000000000000000000000000000000000000000000000000000020" + outputStr;

//         //final Bytes outputData = Bytes.fromHexString(outputStrFinal);
//         //System.out.println(hexToAscii(s_1));
//         final Bytes outputData = Bytes.fromHexString(fun(frame, s_1));

//        // System.out.println("LOADGRAPH OUTPUT DATA: " + outputData.toString());
//         final UInt256 outputSize = UInt256.ZERO;
//         //System.out.println("LOADGRAPH OUTPUT SIZE: " + outputSize.toString());
//         final int outputSizeAsInt = outputSize.intValue();

//         if (outputSizeAsInt > outputData.size()) {
//             frame.expandMemory(outputOffset, outputSize);
//             frame.writeMemory(outputOffset, UInt256.valueOf(outputData.size()), outputData, true);
//         } else {
//             frame.writeMemory(outputOffset, outputSize, outputData, true);
//         }

//         frame.setReturnData(outputData);
//         frame.addLogs(childFrame.getLogs());
//         frame.addSelfDestructs(childFrame.getSelfDestructs());
//         frame.incrementGasRefund(childFrame.getGasRefund());

//         final Gas gasRemaining = childFrame.getRemainingGas();
//         frame.incrementRemainingGas(gasRemaining);

//         frame.popStackItems(getStackItemsConsumed());
//         if (childFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
//             frame.mergeWarmedUpFields(childFrame);
//             frame.pushStackItem(UInt256.ONE);
//         } else {
//             frame.pushStackItem(UInt256.ZERO);
//         }

//         final int currentPC = frame.getPC();
//         frame.setPC(currentPC + 1);


//     }

//     public String padLeftZeros(final String inputString,final int length) {
//         if (inputString.length() >= length) {
//             return inputString;
//         }
//         StringBuilder sb = new StringBuilder();
//         while (sb.length() < length - inputString.length()) {
//             sb.append('0');
//         }
//         sb.append(inputString);

//         return sb.toString();
//     }

//     private static String hexToAscii(final String  hexStr) {
//         StringBuilder output = new StringBuilder("");

//         for (int i = 0; i < hexStr.length(); i += 2) {
//             String str = hexStr.substring(i, i + 2);
//             output.append((char) Integer.parseInt(str, 16));
//         }

//         return output.toString();
//     }

//     public String fun(final MessageFrame frame, final String did){
//         DynamicArray<Triplet> triplets;
    
//         try {
    
           
//             triplets=getHTML("http://10.243.0.4/ontonode/sparql",hexToAscii(did));
//           } catch (Exception e) {
//              System.out.println("FAILED: " + e);       
//              System.out.println("TYT GDZIETO PROBLEMA");
//               triplets = new DynamicArray<Triplet>(
//                 new Triplet(
//                   new Utf8String("Error"),
//                   new Utf8String("Grafu o takim did nie istnije"),
//                   new Utf8String(".")
//               )
//             );
           
//              //frame.setState(MessageFrame.State.REVERT);
//          }
    
//         // Doo df = new Doo(
//         //   hexToAscii(did),
//         //   triplets
//         // );
//         //String outputStrFinal = str.substring(2, (Integer.parseInt(str.substring(66, 66+64), 16) * 2) + 2) + encode(triplets).toString();
//         String outputStrFinal = "0000000000000000000000000000000000000000000000000000000000000020" + encode(triplets).toString();
//         //System.out.println(outputStrFinal);
//         //frame.setOutputData(Bytes.fromHexString(outputStrFinal));
//         //writeMemory(); 
//         //Integer.parse(Integer.toString(outputStrFinal.length()/2), 16);
//         // frame.writeMemory(
//         //   from, 
//         //   UInt256.valueOf(Integer.parseInt(Integer.toString(outputStrFinal.length()/2), 16)),
//         //   //Integer.parseInt(Integer.toString(outputStrFinal.length()/2), 16),
//         //   Bytes.fromHexString(outputStrFinal),
//         //   true
//         // );
//         //frame.setOutputData(Bytes.fromHexString(outputStrFinal));
//         //return Bytes.fromHexString(outputStrFinal);
//         return outputStrFinal;
//       }

//       public static  DynamicArray<Triplet> getHTML(final String urlToRead, final String did) throws Exception {
//         URL url = new URL(urlToRead);
//         HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//         conn.setRequestProperty("Accept","text/plain");
//         conn.setRequestMethod("POST");
//         conn.setDoOutput(true);
//         OutputStream os = conn.getOutputStream();
//         OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");
//         String s = "query=CONSTRUCT{?s?p?o}WHERE{GRAPH<"+ did.trim() +">{?s?p?o}}";
//        // System.out.print(s);
//         osw.write(s);
//         //osw.write("query=CONSTRUCT{?s?p?o}WHERE{GRAPH<"+ did +">{?s?p?o}}");
//         //osw.write("query=CONSTRUCT{?s?p?o}WHERE{GRAPH<did:ihash:9af32088ebd159e83588d0a793e65ef7421038904b12b727b808379db2667492:1642605074841>{?s?p?o}}");
//         osw.flush();
//         osw.close();
//         os.close();
//         conn.connect();
      
//         List<Triplet> graf = new ArrayList<>();
//         List<String> l;
//         try (BufferedReader reader = new BufferedReader(
//                 new InputStreamReader(conn.getInputStream(), Charset.defaultCharset()))) {
//             for (String line; (line = reader.readLine()) != null; ) {
//                 l = Splitter.on(" ").splitToList(line);
//                 graf.add(
//                   new Triplet(
//                     l.get(0).toString(), 
//                     l.get(1).toString(),
//                     l.get(2).toString()
//                   )
//                 );
//             }
    
//         }
//         System.out.println("Size: " + graf.size());
//         DynamicArray<Triplet> dynamicarray = new DynamicArray<>(graf);
//         return dynamicarray;
//         //return graf;
//     }

// }



