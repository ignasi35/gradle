/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.instantexecution.serialization.codecs

import org.gradle.api.internal.GradleInternal
import org.gradle.execution.plan.Node
import org.gradle.execution.plan.TaskNode
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.IsolateOwner
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.instantexecution.serialization.readNonNull
import org.gradle.instantexecution.serialization.withIsolate


internal
class WorkNodeCodec(
    private val owner: GradleInternal,
    private val internalTypesCodec: Codec<Any?>
) {

    suspend fun WriteContext.writeWork(nodes: List<Node>) {
        // Share bean instances across all nodes (except tasks, which have their own isolate)
        withIsolate(IsolateOwner.OwnerGradle(owner), internalTypesCodec) {
            writeNodes(nodes)
        }
    }

    suspend fun ReadContext.readWork(): List<Node> =
        withIsolate(IsolateOwner.OwnerGradle(owner), internalTypesCodec) {
            readNodes()
        }

    private
    suspend fun WriteContext.writeNodes(nodes: List<Node>) {
        val scheduledNodeIds = HashMap<Node, Int>(nodes.size)
        writeSmallInt(nodes.size)
        nodes.forEachIndexed { nodeId, node ->
            scheduledNodeIds[node] = nodeId
            writeNode(node, scheduledNodeIds)
        }
    }

    private
    suspend fun ReadContext.readNodes(): List<Node> {
        val count = readSmallInt()
        val nodesById = HashMap<Int, Node>(count)
        val nodes = ArrayList<Node>(count)
        for (nodeId in 0 until count) {
            val node = readNode(nodesById)
            nodes.add(node)
            nodesById[nodeId] = node
        }
        return nodes
    }

    private
    suspend fun WriteContext.writeNode(
        node: Node,
        scheduledNodeIds: Map<Node, Int>
    ) {
        write(node)
        writeSuccessorReferencesOf(node, scheduledNodeIds)
        writeExecutionStateOf(node)
    }

    private
    suspend fun ReadContext.readNode(nodesById: Map<Int, Node>): Node {
        val node = readNonNull<Node>()
        readSuccessorReferencesOf(node, nodesById)
        readExecutionStateOf(node)
        return node
    }

    private
    fun WriteContext.writeExecutionStateOf(node: Node) {
        // entry nodes are required, finalizer nodes and their dependencies are not
        writeBoolean(node.isRequired)
    }

    private
    fun ReadContext.readExecutionStateOf(node: Node) {
        val isRequired = readBoolean()
        when {
            isRequired -> node.require()
            else -> node.mustNotRun() // finalizer nodes and their dependencies
        }
        node.dependenciesProcessed()
    }

    private
    fun WriteContext.writeSuccessorReferencesOf(node: Node, scheduledNodeIds: Map<Node, Int>) {
        writeSuccessorReferences(node.dependencySuccessors, scheduledNodeIds)
        when (node) {
            is TaskNode -> {
                writeSuccessorReferences(node.shouldSuccessors, scheduledNodeIds)
                writeSuccessorReferences(node.mustSuccessors, scheduledNodeIds)
                writeSuccessorReferences(node.finalizingSuccessors, scheduledNodeIds)
            }
        }
    }

    private
    fun ReadContext.readSuccessorReferencesOf(node: Node, nodesById: Map<Int, Node>) {
        readSuccessorReferences(nodesById) {
            node.addDependencySuccessor(it)
        }
        when (node) {
            is TaskNode -> {
                readSuccessorReferences(nodesById) {
                    node.addShouldSuccessor(it)
                }
                readSuccessorReferences(nodesById) {
                    require(it is TaskNode)
                    node.addMustSuccessor(it)
                }
                readSuccessorReferences(nodesById) {
                    require(it is TaskNode)
                    node.addFinalizingSuccessor(it)
                }
            }
        }
    }

    private
    fun WriteContext.writeSuccessorReferences(
        successors: Collection<Node>,
        scheduledNodeIds: Map<Node, Int>
    ) {
        for (successor in successors) {
            // Discard should/must run after relationships to nodes that are not scheduled to run
            scheduledNodeIds[successor]?.let { successorId ->
                writeSmallInt(successorId)
            }
        }
        writeSmallInt(-1)
    }

    private
    fun ReadContext.readSuccessorReferences(nodesById: Map<Int, Node>, onSuccessor: (Node) -> Unit) {
        while (true) {
            val successorId = readSmallInt()
            if (successorId == -1) break
            val successor = nodesById.getValue(successorId)
            onSuccessor(successor)
        }
    }
}
