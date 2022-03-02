
package org.hyperledger.besu.ethereum.vm.operations;

import static org.eclipse.rdf4j.model.util.Values.iri;

import java.io.BufferedReader;
import java.io.StringReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.regex.Pattern;

import com.google.common.base.Splitter;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryResult;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;

import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.model.Statement;


import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.vm.AbstractOperation;
import org.hyperledger.besu.ethereum.vm.EVM;
import org.hyperledger.besu.ethereum.vm.ExceptionalHaltReason;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import java.io.OutputStreamWriter;
import java.io.OutputStream;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.DynamicArray;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.ethereum.vm.operations.AbiDataStruct.Triplet;
import org.hyperledger.besu.ethereum.vm.operations.AbiDataStruct.ReturnDataConstruct;
import org.hyperledger.besu.ethereum.vm.operations.AbiDataStruct.ReturnDataSelect;
import org.hyperledger.besu.ethereum.vm.operations.AbiDataStruct.ReturnDataAsk;
import static org.web3j.abi.TypeEncoder.encode;
import org.web3j.abi.datatypes.DynamicStruct;
import org.web3j.utils.Numeric;
import org.web3j.abi.datatypes.DynamicBytes;

import org.web3j.abi.datatypes.Bool;

public class ReturnOperation extends AbstractOperation {
  
  public ReturnOperation(final GasCalculator gasCalculator) {
    super(0xF3, "RETURN", 2, 0, false, 1, gasCalculator);
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
  
    final UInt256 offset = frame.popStackItem();
    final UInt256 length = frame.popStackItem();

    final Gas cost = gasCalculator().memoryExpansionGasCost(frame, offset, length);
    final Optional<Gas> optionalCost = Optional.of(cost);
    if (frame.getRemainingGas().compareTo(cost) < 0) {
      return new OperationResult(optionalCost, Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
    }
  
    String inputData = frame.readMemory(offset, length).toString();
    //System.out.println("RETURN GRAPH Input data: " + inputData);


    if(inputData.length() >= 578) {

      System.out.println("RETURN INFO: dlugasc zgadza sie.");

      if(inputData.substring(322, 386).equals("0000000000000000000000000000000000000000000000000000000000000005")){
        System.out.println("RETURN INFO: dlugasc tokenu zgadza sie.");

        if(inputData.substring(386,450).equals("5155455259000000000000000000000000000000000000000000000000000000")){
          System.out.println("RETURN INFO: token zgadza sie.");
          System.out.println("RETURN INFO length query: " + inputData.substring(450,514));
          //int lengthQuery = Integer.parseInt(inputData.substring(450,514), 16) * 2;
          //String query = inputData.substring(514, lengthQuery);
          Integer qwerty = Integer.parseInt(inputData.substring(450,514), 16) * 2;
          System.out.println("RETURN QUERY LENDTH = " + qwerty);
          String query = hexToAscii(inputData.substring(514, 514 + qwerty));
          //Integer.parseInt(inputData.substring(450,514), 16) * 2
          System.out.println("RETURN QUERY: " + query);

          if(query.substring(0,3).equals("ASK")){
            System.out.println("RETURN QUERY: ASK");
            final Bytes outputData = Bytes.fromHexString(getAnswerAsk(frame, query, "ASK"));
            frame.setOutputData(outputData);
			System.out.println("RETURN END");
            //System.out.println("RETURN OUTDATA: " + outputData);
           
          }
          else if(query.substring(0,9).equals("CONSTRUCT")){
            System.out.println("RETURN QUERY: CONSTRUCT");
            final Bytes outputData = Bytes.fromHexString(getAnswerAsk(frame, query, "CONSTRYCT"));
            frame.setOutputData(outputData);
            System.out.println("RETURN END");
	    //System.out.println("RETURN OUTDATA: " + outputData);
           
          }
          else if(query.substring(0,6).equals("SELECT")){
            System.out.println("RETURN QUERY: SELECT");
            final Bytes outputData = Bytes.fromHexString(getAnswerAsk(frame, query, "SELECT"));
            frame.setOutputData(outputData);
			System.out.println("RETURN END");
            //System.out.println("RETURN OUTDATA: " + outputData);
           
          }
          else{
            frame.setOutputData(frame.readMemory(offset, length));
          }
        }
        else{
            System.out.println("RETURN : " + inputData.substring(386,450));
            System.out.println("RETURN INFO: token nie zgadza sie.");
              frame.setOutputData(frame.readMemory(offset, length));
            }

      }
      else{
		  
            frame.setOutputData(frame.readMemory(offset, length));
			System.out.println("RETURN END");
		}

    }  
    else{
      frame.setOutputData(frame.readMemory(offset, length));
    }
    
    frame.setState(MessageFrame.State.CODE_SUCCESS);
    return new OperationResult(optionalCost, Optional.empty());
  }

  private String hexToAscii(final String  hexStr) {
    StringBuilder output = new StringBuilder("");

for (int i = 0; i < hexStr.length(); i += 2) {
String str = hexStr.substring(i, i + 2);
output.append((char) Integer.parseInt(str, 16));
}

return output.toString();
}

public String getAnswerAsk(final MessageFrame frame, final String query, final String typeQuery){
	try{
		URL url = new URL("http://10.243.0.4/ontonode/sparql");
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		if(typeQuery.equals("ASK")){
			conn.setRequestProperty("Accept","text/plain");
		}
		else if(typeQuery.equals("SELECT")){
			conn.setRequestProperty("Accept","text/tab-separated-values");
		} 
		else {
			conn.setRequestProperty("Accept","application/rdf+xml");
		}
		conn.setRequestMethod("POST");
		conn.setDoOutput(true);
		OutputStream os = conn.getOutputStream();
		OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");
		osw.write("query=" + query);
		osw.flush();
		osw.close();
		os.close();
		conn.connect();

		if(typeQuery.equals("ASK")){
			String answer = "";
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), Charset.defaultCharset()))){
				for (String line; (line = reader.readLine()) != null; ) {
    					answer += line;
				}
			}
			System.out.println("RETURN ANSWER: " + answer);
			Pattern patternTrue = Pattern.compile("<boolean>true</boolean>");
			if(patternTrue.matcher(answer).find()){
				ReturnDataAsk returnDataAsk = new ReturnDataAsk(true, false, "");
				String str = "0000000000000000000000000000000000000000000000000000000000000020" + encode(returnDataAsk).toString();
				String sizeSection = padLeftZeros(Bytes.ofUnsignedInt(str.length()/2).toString().substring(2),64);
				return "0000000000000000000000000000000000000000000000000000000000000020" + sizeSection + str;
				//return "0000000000000000000000000000000000000000000000000000000000000020" + "0000000000000000000000000000000000000000000000000000000000000020" + "0000000000000000000000000000000000000000000000000000000000000001";
			}
			else {
				ReturnDataAsk returnDataAsk = new ReturnDataAsk(false, false, "");
				String str = "0000000000000000000000000000000000000000000000000000000000000020" + encode(returnDataAsk).toString();
				String sizeSection = padLeftZeros(Bytes.ofUnsignedInt(str.length()/2).toString().substring(2),64);
				return "0000000000000000000000000000000000000000000000000000000000000020" + sizeSection + str;
				//return "0000000000000000000000000000000000000000000000000000000000000020" + "0000000000000000000000000000000000000000000000000000000000000020" + "0000000000000000000000000000000000000000000000000000000000000000";
			}
		}
		else if(typeQuery.equals("SELECT")){
        	List<Utf8String> strs = new ArrayList<>();
			String[] df;
			int i = 0;
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), Charset.defaultCharset()))){
				for (String line; (line = reader.readLine()) != null; ) {
  					df = line.split("\t");
  					if(i == 0){
        					i = df.length;
  					}
  					//System.out.println("Length: " + df.length);
  					for(String str : df){
        					strs.add(new Utf8String(str));
        				//	System.out.println(str);
  					}
  					//strs.add(new Utf8String(line));
  					//System.out.println(line);
    					//answer += line;

				}
				ReturnDataSelect array = new ReturnDataSelect(i, strs, false, "");
				//DynamicArray<Utf8String> array = new DynamicArray<>(strs);
				String a = "0000000000000000000000000000000000000000000000000000000000000020" + encode(array).toString();
				String sizeSection = padLeftZeros(Bytes.ofUnsignedInt(a.length()/2).toString().substring(2),64);
				return "0000000000000000000000000000000000000000000000000000000000000020" + sizeSection + a;
			}
		}
		else {
        		List<Triplet> graph = new ArrayList<>();
			try {
				final Model model = Rio.parse(conn.getInputStream(), "" ,RDFFormat.RDFXML);
				for (Statement st: model) {
					graph.add(
						new Triplet(
  							new Utf8String(st.getSubject().toString()),
  							new Utf8String(st.getPredicate().toString()),
  							new Utf8String(st.getObject().toString())
						)
					);
					//System.out.println(st.getSubject() + " " + st.getPredicate() + " " + st.getObject());

				}
				ReturnDataConstruct returnData = new ReturnDataConstruct(graph, false, "");
				String g = "0000000000000000000000000000000000000000000000000000000000000020" + encode(returnData).toString();
				String sizeSection = padLeftZeros(Bytes.ofUnsignedInt(g.length()/2).toString().substring(2),64);
			return "0000000000000000000000000000000000000000000000000000000000000020" + sizeSection + g;

			}
			catch(Exception e){
				System.out.println("Rio.parse: " + e);
			}
		}


	}
	catch(Exception e){
		System.out.println("FAILED: " + e); 
		if(typeQuery.equals("ASK")){
			ReturnDataAsk returnDataAsk = new ReturnDataAsk(false, true, e.getMessage());
			String str = "0000000000000000000000000000000000000000000000000000000000000020" + encode(returnDataAsk).toString();
			String sizeSection = padLeftZeros(Bytes.ofUnsignedInt(str.length()/2).toString().substring(2),64);
			return "0000000000000000000000000000000000000000000000000000000000000020" + sizeSection + str;
		}
		else if(typeQuery.equals("SELECT")){
			ReturnDataSelect returnDataSelect = new ReturnDataSelect(0, new ArrayList<Utf8String>(), true, e.getMessage());
			String str = "0000000000000000000000000000000000000000000000000000000000000020" + encode(returnDataSelect).toString();
			String sizeSection = padLeftZeros(Bytes.ofUnsignedInt(str.length()/2).toString().substring(2),64);
			return "0000000000000000000000000000000000000000000000000000000000000020" + sizeSection + str;
		}
		else{
			ReturnDataConstruct returnDataConstruct = new ReturnDataConstruct(new ArrayList<Triplet>(), true, e.getMessage());
			String str = "0000000000000000000000000000000000000000000000000000000000000020" + encode(returnDataConstruct).toString();
			String sizeSection = padLeftZeros(Bytes.ofUnsignedInt(str.length()/2).toString().substring(2),64);
			return "0000000000000000000000000000000000000000000000000000000000000020" + sizeSection + str;
		}
		//frame.setState(MessageFrame.State.REVERT);
		//return "0000000000000000000000000000000000000000000000000000000000000020" + encode(new Utf8String("error")).toString();
	}
	return "kostia";
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

}
