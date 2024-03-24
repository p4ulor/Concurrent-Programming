import java.util.*

object dramaquestion2 {
    val numberArray = intArrayOf(1,2,3)
    //val numberArray = arrayListOf(1,2,3)
    @JvmStatic
    fun main(args: Array<String>) {
        for (x in numberArray) {
            //x = 5 //Val cannot be reassigned...
        }
        numberArray[0] = 0
        //System.out.println("HELLO");
        println(Arrays.toString(numberArray))
    }
}