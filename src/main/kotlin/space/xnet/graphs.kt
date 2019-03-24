package space.xnet

import java.util.*

data class Node<T>(val incoming: Set<Node<T>>, val payload: T) {
    fun toShortString(): String {
        val incomingPayloads = incoming.map { it.payload }
        return "Node(incoming = [$incomingPayloads], payload = $payload)"
    }
}

fun array2dOfBoolean(sizeOuter: Int, sizeInner: Int): Array<BooleanArray>
        = Array(sizeOuter) { BooleanArray(sizeInner) }


data class Graph<T>(val nodes: List<Node<T>>) {

    fun kahn(): List<Node<T>> {

        // graph[i][k] = true iff edge exists from nodes[i] to nodes[k]
        val graph = array2dOfBoolean(nodes.size, nodes.size).apply {
            for (destination in nodes) {
                val i = nodes.indexOf(destination)
                for (source in destination.incoming) {
                    val k = nodes.indexOf(source)
                    this[k][i] = true
                }
            }
        }

        val node2Payload = nodes.map {
            it.payload
        }

        return kahnFromArray(graph, node2Payload)
    }
}


fun<T> kahnFromArray(array: Array<BooleanArray>, idx2Payload: List<T>): List<Node<T>> {
    fun edgeExists(source: Int, destination: Int) = array[source][destination]
    fun deleteEdge(source: Int, destination: Int) {
        array[source][destination] = false
    }

    val originalArray = array2dOfBoolean(array.size, array.size).apply {
        for (i in array.indices) {
            for (k in array.indices) {
                this[i][k] = array[i][k]
            }
        }
    }

    val topologicalSortOrder = mutableListOf<Node<T>>()

    // start with all nodes that do not have any incoming edges
    val currentNodeIndices = Stack<Int>().apply {
        val nodesNoIncomingEdges = array.indices.filter { i ->
            array.indices.none { k -> array[k][i] }
        }
        addAll(nodesNoIncomingEdges)
    }

    while (currentNodeIndices.isNotEmpty()) {
        val nIdx = currentNodeIndices.pop()!!

        val incomingNodesOfNodeToAdd = originalArray.indices.filter { i ->
            originalArray[i][nIdx]
        }.map { idx ->
            topologicalSortOrder.find { node -> node.payload == idx2Payload[idx] }!!
        }.toSet()
        val newNode = Node(incomingNodesOfNodeToAdd, idx2Payload[nIdx])
        topologicalSortOrder.add(newNode)

        val reachableFromN = array.indices.filter { edgeExists(source = nIdx, destination = it) }

        for (destinationIdx in reachableFromN) {
            deleteEdge(source = nIdx, destination = destinationIdx)
            val noIncomingEdges = array.indices.all { !edgeExists(it, destinationIdx) }
            if (noIncomingEdges) {
                // after the edges have been deleted, a new node without any incoming edges exists.
                currentNodeIndices.push(destinationIdx)
            }
        }
    }

    return topologicalSortOrder.toList()
}
