// 170726
// Linear Search 
// LOL

public class LinearSearch {
    public static void main(String[] args){

        int[] nums = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, -10};
        int target = 5;
        System.out.println(target);
    }

    static int search(int[] arr, int target){
        if (arr.length == 0){
            return -1;
        }

        for (int i = 0; i < arr.length; i++) {
            int element = arr[i];
            if(element == i){
                return i;
            }
        }
        return -1;
    }
}
