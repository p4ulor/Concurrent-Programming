package main.exams

import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration

/**
[6] Implemente usando a linguagem Java o sincronizador batch exchanger, com os métodos apresentados em
seguida.
    public class BatchExchanger<T> {
        public BatchExchanger(int batchSize);
        public Optional<List<T>> deliverAndWait(T msg, long timeout) throws InterruptedException;
    }
O método deliverAndWait entrega uma mensagem ao sincronizador e espera até que estejam presentes
batchSize mensagens, i.e., até que um lote de mensagens esteja completo, retornando todas as mensagens
deste lote. Uma chamada a deliverAndWait pode terminar com: 1) retorno dum objecto Optional contendo uma
lista com todas as mensagens do lote; 2) um objecto Optional vazio, caso o tempo de espera definido por
timeout seja excedido sem que o lote esteja completo; 3) com o lançamento duma excepção do tipo
InterruptedException, caso a thread seja interrompida enquanto em espera. Assumindo que as chamadas a
deliverAndWait entregam mensagens distintas, o sincronizador deve garantir as seguintes propriedades: 1) caso
a chamada ao método deliverAndWait acabe sem sucesso, então a mensagem entregue nesta chamada não
pode ser retornada em nenhuma outra chamada a esse método; 2) caso a chamada ao método deliverAndWait
acabe com sucesso, então a lista retornada contém a mensagem entregue; 3) sejam C1 e C2 quaisquer duas
chamadas ao método deliverAndWait que terminaram com sucesso, se o retorno de C1 contém a mensagem
entregue por C2 então o retorno de C2 contém a mensagem entregue por C1.
 */

class BatchExchange<T>(val batchSize: Int){
    // The monitor's lock and condition
    private val mLock: Lock = ReentrantLock()

    class Request<T>(val list: MutableList<T> ,val condition: Condition)
    var batchRequest: Request<T>? = null

    fun deliverAndWait(msg: T, timeout: Duration): List<T>?{
        mLock.withLock {
            val currentRequest = batchRequest
            if (currentRequest != null){
                currentRequest.list.add(msg)
                if(currentRequest.list.size == batchSize){
                    currentRequest.condition.signalAll()
                    batchRequest = null
                    return currentRequest.list
                }
            }else {
                batchRequest = Request(mutableListOf(msg), mLock.newCondition())
            }

            val sharedRequest = batchRequest as Request<T>
            var remainingTime = timeout.inWholeNanoseconds
            while (true) {
                try {
                    remainingTime = sharedRequest.condition.awaitNanos(remainingTime)
                }catch (e: InterruptedException){
                    sharedRequest.list.remove(msg)
                    if (sharedRequest.list.isEmpty()){
                        batchRequest = null
                    }
                    throw e
                }

                if(sharedRequest.list.size == batchSize){
                    return sharedRequest.list
                }

                if (remainingTime <= 0){
                    sharedRequest.list.remove(msg)
                    if (sharedRequest.list.isEmpty()){
                        batchRequest = null
                    }
                    return null
                }
            }
        }
    }
}