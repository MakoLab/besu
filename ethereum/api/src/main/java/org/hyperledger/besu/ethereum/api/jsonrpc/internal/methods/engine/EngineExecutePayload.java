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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine;

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.ExecutionStatus.INVALID;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.ExecutionStatus.KNOWN;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.ExecutionStatus.VALID;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.ExecutionEngineNewBlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.encoding.TransactionDecoder;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.rlp.RLPException;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableMap;
import io.vertx.core.Vertx;
import io.vertx.core.json.EncodeException;
import io.vertx.core.json.Json;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class EngineExecutePayload extends ExecutionEngineJsonRpcMethod {

  private static final List<BlockHeader> OMMERS_CONSTANT = Collections.emptyList();
  private static final Hash OMMERS_HASH_CONSTANT = BodyValidation.ommersHash(OMMERS_CONSTANT);
  private static final Logger LOG = LogManager.getLogger();
  private static final BlockHeaderFunctions headerFunctions = new MainnetBlockHeaderFunctions();
  private final ProtocolSchedule protocolSchedule;
  private final ProtocolContext protocolContext;

  public EngineExecutePayload(
      final Vertx vertx,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext) {
    super(vertx);
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
  }

  @Override
  public String getName() {
    return RpcMethod.ENGINE_EXECUTE_PAYLOAD.getMethodName();
  }

  @Override
  public JsonRpcResponse syncResponse(final JsonRpcRequestContext requestContext) {
    final ExecutionEngineNewBlockParameter blockParam =
        requestContext.getRequiredParameter(0, ExecutionEngineNewBlockParameter.class);

    Object reqId = requestContext.getRequest().getId();
    // respond with known if block is already imported
    if (protocolContext.getBlockchain().getBlockByHash(blockParam.getBlockHash()).isPresent()) {
      return respondWith(reqId, blockParam.getBlockHash(), KNOWN);
    }

    ProtocolSpec spec = protocolSchedule.getByBlockNumber(blockParam.getNumber());

    try {
      LOG.trace("blockparam: " + Json.encodePrettily(blockParam));
    } catch (EncodeException e) {
      throw new RuntimeException(e);
    }

    final List<Transaction> transactions;
    try {
      transactions =
          blockParam.getTransactions().stream()
              .map(Bytes::fromHexString)
              .map(TransactionDecoder::decodeOpaqueBytes)
              .collect(Collectors.toList());
    } catch (final RLPException | IllegalArgumentException e) {
      LOG.warn("failed to decode transactions from newBlock RPC");
      return respondWith(reqId, blockParam.getBlockHash(), INVALID);
    }

    final BlockHeader newBlockHeader =
        new BlockHeader(
            blockParam.getParentHash(),
            OMMERS_HASH_CONSTANT,
            blockParam.getCoinbase(),
            blockParam.getStateRoot(),
            BodyValidation.transactionsRoot(transactions),
            blockParam.getReceiptsRoot(),
            blockParam.getLogsBloom(),
            Difficulty.ONE,
            blockParam.getNumber(),
            blockParam.getGasLimit(),
            blockParam.getGasUsed(),
            blockParam.getTimestamp(),
            Bytes.EMPTY,
            blockParam.getBaseFeePerGas(),
            Hash.ZERO,
            0,
            headerFunctions);

    LOG.trace("newBlockHeader " + newBlockHeader);
    boolean blkHashEq = newBlockHeader.getHash().equals(blockParam.getBlockHash());
    LOG.trace("blkHashEq " + blkHashEq);

    // import block
    boolean importSuccess =
        spec.getBlockImporter()
            .importBlock(
                protocolContext,
                new Block(newBlockHeader, new BlockBody(transactions, OMMERS_CONSTANT)),
                HeaderValidationMode.FULL);

    if (!importSuccess) {
      LOG.warn("{} invalid payload, failed block processing", this.getName());
    }

    // return result response
    return respondWith(
        reqId, blockParam.getBlockHash(), blkHashEq && importSuccess ? VALID : INVALID);
  }

  JsonRpcResponse respondWith(
      final Object requestId, final Hash blockHash, final ExecutionStatus status) {
    return new JsonRpcSuccessResponse(
        requestId, ImmutableMap.of("blockHash", blockHash, "status", status.name()));
  }
}
