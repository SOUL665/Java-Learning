// 180726

// Binary Search

public class BinarySearch {
    public static void main(String[] args){
        int[] arr = {-10, -9, -6, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        int target = 9;
        int ans = searchbinary(arr, target);
        System.out.println(ans);
    }
    static int searchbinary(int[] arr, int target){
        int start = 0;
        int end = arr.length - 1;
        while(start <= end){
            int mid = start + (end - start) / 2;
            if (target < arr[mid]){
              // Left side of the array from the middle
                end = mid - 1;
            }
            else if (target > arr[mid]){
              // Right Side of the array from middle 
                start = mid + 1; 
            }
            else{
                return mid; // Mid == target;
            }
        }
        return -1;
    }
}
