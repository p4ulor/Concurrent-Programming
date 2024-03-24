import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.pow

fun currThread() = "Thread - ${Thread.currentThread().name}"

fun nanosToSeconds(remainingTime: Long) = "${remainingTime * 10.0.pow(-9)}s"

class Lazy<T>(private val initializer: () -> T) {
    private val isValueInited = AtomicBoolean(false)

    @Volatile
    var value: T? = null
        get() {
            val observedIsValueInited = isValueInited.get()
            return if (!observedIsValueInited && isValueInited.compareAndSet(observedIsValueInited, true)) {
                initializer()
            } else {
                while (field == null);
                field
            }
        }
}
