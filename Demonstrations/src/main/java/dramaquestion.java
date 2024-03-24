import java.util.Arrays;

public class dramaquestion {
    static int[] numberArray = new int[5];
    public static void main(String... args){
        for(Integer x: numberArray){
            x = 5;
        }
        //System.out.println("HELLO");
        System.out.println(Arrays.toString(numberArray));
    }
}
