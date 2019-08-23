import java.util.*
import time.*
import kotlinx.coroutines.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.Math.abs


val BLOCK_TIME = 1.seconds
val F = 1
val N = 3 * F + 1
var ID = 0
val BUFFER_SIZE = 1


enum class ClientType {
    PEER, CLIENT
}

val logger = LoggerFactory.getLogger("Main")

data class Transaction(val hash: Int, val valid: Boolean = true)

data class Context(val clientType: ClientType, val clientId: Int)

data class Block(val height: Int, val hash: Long, val txs: List<Transaction>)

data class Peer(val id: Int, val bufferSize : Int, val malicious: Boolean = false) {
    private val logger = LoggerFactory.getLogger("Peer$id")


    var setA = listOf<Peer>()
    var setB = listOf<Peer>()
    var leader: Peer? = null
    var tail: Peer? = null

    val txHistory = mutableSetOf<Transaction>()
    var txs = mutableListOf<Transaction>()
    var peers = listOf<Peer>()
    val blocks = mutableListOf(Block(1, 0, listOf()))
    val votes = mutableListOf<Block>()


    fun init() {
        orderPeers()
    }

    fun validateBlock(block: Block): Boolean {
        return block.txs.all { it.valid }
    }

    fun orderPeers() {
        val rng = Random(blocks.last().hash)
        peers = peers.sortedBy { it.id }.shuffled(rng)
        val (a, b) = split(peers)
        setA = a
        setB = b

        leader = setA.first()
        tail = setA.last()
        logger.info("Ordered: Leader ${leader!!.id}, Tail: ${tail!!.id} ")
        println(setA)
        println(setB)


    }

    fun createBlock(): Block {
        val hash = txs.map { it.hash }.sum().toLong()
        return Block(blocks.last().height + 1, hash, txs.toList())

    }

    fun propagateTx(tx: Transaction) {
        logger.info("Propagate $tx ")
        val thisContext = Context(ClientType.PEER, this.id)

        peers.forEach {
            it.onTransaction(thisContext, tx)
        }
    }

    fun onTransaction(context: Context, tx: Transaction) {
        logger.info("Got transaction $tx from context $context")

        if (!txHistory.contains(tx)) {
            txHistory.add(tx)
            txs.add(tx.copy())
            propagateTx(tx)
        }
    }

    fun onCommit(context: Context, block: Block) {
        logger.info("onCommit from $context, block: $block")
        if (context.clientId == tail!!.id) {
            blocks.add(block)
            logger.info("Block added: $block")
            orderPeers()
            txs.clear()
        }
    }

    fun onBlock(context: Context, block: Block) {
        if (setB.contains(this))
            return

        logger.info("onBlock from $context, block $block")
        val ctx = Context(ClientType.PEER, this.id)
        val isValid = validateBlock(block)

        propagateBlock(block, setB)
        if (tail == this) {
            votes.add(block)
            if (votes.size == 2 * F) {
                // Check validity
                if (votes.distinct().size == 1 && isValid) {
                    //Commit
                    peers.forEach { peer ->
                        logger.info("Commit $block")
                        peer.onCommit(ctx, block)
                    }

                }
            }

        } else {
            if (context.clientId == leader!!.id && isValid) {
                tail!!.onBlock(ctx, block)
            }
        }
    }

    fun propagateBlock(block: Block, peers: List<Peer>) {
        val ctx = Context(ClientType.PEER, this.id)
        peers.forEach {
            if (it != this)
                it.onBlock(ctx, block)
        }
    }

    fun round() {
        if (txs.size == bufferSize) {
            // Create block if leader
            if (leader == this) {
                val block = createBlock()
                propagateBlock(block, setA)
            }

        }
    }

    suspend fun run() {
        while (true) {
            if (txs.size == BUFFER_SIZE) {
                // Create block if leader
                if (leader == this) {
                    val block = createBlock()
                    propagateBlock(block, setA)
                }

            }
            delay(10)
        }
    }
}


fun main() {
    logger.error("Start")

    val peers = createPeers(N, BUFFER_SIZE)

    val ctx = Context(ClientType.CLIENT, 1)

    repeat(2) {
        println("___________________")
        peers.first().onTransaction(ctx, Transaction(Random().nextInt()))
        peers.forEach {
            it.round()
        }
    }

}