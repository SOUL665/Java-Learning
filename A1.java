// Date - 24-06-26

import java.util.Arrays;

public class ArrayOperationsManager {

    public static void main(String[] args) {
        int[] numbers = {42, 15, 88, 23, 74, 5, 61, 99, 32, 50};
        
        System.out.println("Original Array:");
        printArray(numbers);
        
        int min = findMin(numbers);
        int max = findMax(numbers);
        System.out.println("Minimum value: " + min);
        System.out.println("Maximum value: " + max);
        
        double average = calculateAverage(numbers);
        System.out.println("Average of elements: " + average);
        
        int target = 23;
        int index = searchElement(numbers, target);
        System.out.println("Element " + target + " found at index: " + index);
        
        System.out.println("Reversed Array:");
        int[] reversed = reverseArray(numbers);
        printArray(reversed);
        
        System.out.println("Sorted Array:");
        int[] sorted = sortArray(numbers);
        printArray(sorted);
        
        System.out.println("Even numbers only:");
        int[] evens = filterEvens(numbers);
        printArray(evens);
    }

    public static void printArray(int[] arr) {
        for (int num : arr) {
            System.out.print(num + " ");
        }
        System.out.println("\n---------------------------------");
    }

    public static int findMin(int[] arr) {
        int min = arr[0];
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] < min) {
                min = arr[i];
            }
        }
        return min;
    }

    public static int findMax(int[] arr) {
        int max = arr[0];
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > max) {
                max = arr[i];
            }
        }
        return max;
    }

    public static double calculateAverage(int[] arr) {
        int sum = 0;
        for (int num : arr) {
            sum += num;
        }
        return (double) sum / arr.length;
    }

    public static int searchElement(int[] arr, int target) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == target) {
                return i;
            }
        }
        return -1;
    }

    public static int[] reverseArray(int[] arr) {
        int[] result = new int[arr.length];
        for (int i = 0; i < arr.length; i++) {
            result[i] = arr[arr.length - 1 - i];
        }
        return result;
    }

    public static int[] sortArray(int[] arr) {
        int[] copy = Arrays.copyOf(arr, arr.length);
        Arrays.sort(copy);
        return copy;
    }

    public static int[] filterEvens(int[] arr) {
        int count = 0;
        for (int num : arr) {
            if (num % 2 == 0) {
                count++;
            }
        }
        int[] evens = new int[count];
        int index = 0;
        for (int num : arr) {
            if (num % 2 == 0) {
                evens[index++] = num;
            }
        }
        return evens;
    }
}
