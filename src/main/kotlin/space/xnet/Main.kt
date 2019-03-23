package space.xnet

import java.util.*

data class Node<T>(val incoming: List<Node<T>>, val payload: T)

private fun array2dOfBoolean(sizeOuter: Int, sizeInner: Int): Array<BooleanArray>
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

        fun edgeExists(source: Int, destination: Int) = graph[source][destination]
        fun deleteEdge(source: Int, destination: Int) {
            graph[source][destination] = false
        }

        val topologicalSortOrder = mutableListOf<Node<T>>()

        // start with all nodes that do not have any incoming edges
        val currentNodeIndices = Stack<Int>().apply {
            addAll(nodes.indices.filter { nodes[it].incoming.isEmpty() })
        }

        while (currentNodeIndices.isNotEmpty()) {
            val nIdx = currentNodeIndices.pop()!!
            val n = nodes[nIdx]
            topologicalSortOrder.add(n)

            val reachableFromN = nodes.indices.filter { edgeExists(source = nIdx, destination = it) }

            for (destinationIdx in reachableFromN) {
                deleteEdge(source = nIdx, destination = destinationIdx)
                val noIncomingEdges = nodes.indices.all { !edgeExists(it, destinationIdx) }
                if (noIncomingEdges) {
                    // after the edges have been deleted, a new node without any incoming edges exists.
                    currentNodeIndices.push(destinationIdx)
                }
            }
        }

        return topologicalSortOrder.toList()
    }

}
