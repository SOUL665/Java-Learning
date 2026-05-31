import java.util.Scanner;

public class Switch {
    public static void main(String[] args){
        int total = sum2();
        System.out.print(total);
    }
    static int sum2(){
        Scanner sc = new Scanner(System.in);
        System.out.print("Enter the first number: ");
        int a = sc.nextInt();
        System.out.print("Enter the second number: ");
        int b = sc.nextInt();
        int sum = a + b ;
        System.out.print("The sum of two numbers is ");
        return sum;
    }
}
