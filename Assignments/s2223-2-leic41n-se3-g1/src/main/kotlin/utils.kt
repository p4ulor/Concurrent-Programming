import kotlin.math.pow

fun currThread() = "Thread - ${Thread.currentThread().name}"

fun nanosToSeconds(remainingTime: Long) = "${remainingTime * 10.0.pow(-9)}s"