import java.util.Scanner;

public class Main{
    public static void main(String[] args){
        Scanner sc = new Scanner(System.in);
        if(sc.hasNextLine()) {
            String original = sc.nextLine();
            String target = sc.hasNextLine() ? sc.nextLine() : "";
            String replacement = sc.hasNextLine() ? sc.nextLine() : "";
            
            String result = original.replace(target, replacement);
            System.out.println(result);
        }
        sc.close();
    }
}
