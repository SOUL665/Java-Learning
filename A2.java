// Date - 24-06-26

import java.util.Arrays;
import java.util.Scanner;

public class LibraryInventorySystem {

    public static void main(String[] args) {
        String[] bookTitles = {
            "The Great Gatsby", "To Kill a Mockingbird", "1984", 
            "The Catcher in the Rye", "Moby Dick", "Brave New World", 
            "The Hobbit", "War and Peace", "Crime and Punishment", "The Odyssey"
        };

        int[] bookIds = {101, 102, 103, 104, 105, 106, 107, 108, 109, 110};

        int[][] shelfLocation = {
            {1, 101}, {1, 102}, {1, 103},
            {2, 104}, {2, 105}, {2, 106},
            {3, 107}, {3, 108}, {3, 109}, {3, 110}
        };

        double[] bookPrices = {15.99, 12.50, 14.25, 10.99, 18.75, 13.40, 16.50, 24.99, 19.95, 11.20};
        boolean[] availability = {true, false, true, true, false, true, true, false, true, true};
        int[] borrowCounts = {45, 82, 110, 23, 9, 67, 125, 14, 53, 39};

        System.out.println("=== INITIALIZING LIBRARY MANAGEMENT SYSTEM ===");
        displayInventory(bookIds, bookTitles, bookPrices, availability);

        System.out.println("\n=== TESTING SEARCH ALGORITHMS ===");
        int searchId = 106;
        int index = linearSearchById(bookIds, searchId);
        if (index != -1) {
            System.out.println("Linear Search: Found " + bookTitles[index] + " at index " + index);
        }

        int[] sortedIds = Arrays.copyOf(bookIds, bookIds.length);
        Arrays.sort(sortedIds);
        int binarySearchId = 108;
        int binaryIndex = binarySearchById(sortedIds, binarySearchId);
        System.out.println("Binary Search: Target ID " + binarySearchId + " position in sorted array: " + binaryIndex);

        System.out.println("\n=== ANALYZING INVENTORY DATA ===");
        double totalValue = calculateTotalValue(bookPrices);
        double avgPrice = calculateAveragePrice(bookPrices);
        int mostBorrowedIndex = findMostBorrowedBook(borrowCounts);
        int leastBorrowedIndex = findLeastBorrowedBook(borrowCounts);

        System.out.printf("Total Inventory Value: $%.2f%n", totalValue);
        System.out.printf("Average Book Price: $%.2f%n", avgPrice);
        System.out.println("Most Popular Book: " + bookTitles[mostBorrowedIndex] + " (" + borrowCounts[mostBorrowedIndex] + " borrows)");
        System.out.println("Least Popular Book: " + bookTitles[leastBorrowedIndex] + " (" + borrowCounts[leastBorrowedIndex] + " borrows)");

        System.out.println("\n=== MATRIX OPERATIONS (SHELF MAP) ===");
        printShelfMatrix(shelfLocation);

        System.out.println("\n=== SORTING INVENTORY BY PRICE (BUBBLE SORT) ===");
        bubbleSortByPrice(bookPrices, bookTitles, bookIds);
        displayInventory(bookIds, bookTitles, bookPrices, availability);

        System.out.println("\n=== REVERSING INVENTORY ORDER ===");
        reverseAllArrays(bookIds, bookTitles, bookPrices, availability);
        displayInventory(bookIds, bookTitles, bookPrices, availability);

        System.out.println("\n=== FILTERING AVAILABLE BOOKS ===");
        int[] availableIndices = filterAvailableBooks(availability);
        System.out.println("Indices of currently available books: " + Arrays.toString(availableIndices));

        System.out.println("\n=== SIMULATING BULK PRICE UPDATE (+10% Inflation) ===");
        applyInflation(bookPrices, 1.10);
        displayInventory(bookIds, bookTitles, bookPrices, availability);

        System.out.println("\n=== CLONING AND TRUNCATING ARCHIVE ===");
        String[] shortArchive = truncateInventory(bookTitles, 5);
        System.out.println("Short Archive Checklist: " + Arrays.toString(shortArchive));

        System.out.println("\n=== GENERATING STATISTICAL REPORT ===");
        generateReport(bookIds, bookTitles, borrowCounts);
    }

    public static void displayInventory(int[] ids, String[] titles, double[] prices, boolean[] available) {
        System.out.println("-----------------------------------------------------------------------------");
        System.printf("%-6s | %-25s | %-8s | %-12s%n", "ID", "Title", "Price", "Status");
        System.out.println("-----------------------------------------------------------------------------");
        for (int i = 0; i < ids.length; i++) {
            String status = available[i] ? "Available" : "Checked Out";
            System.out.printf("%-6d | %-25s | $%-7.2f | %-12s%n", ids[i], titles[i], prices[i], status);
        }
        System.out.println("-----------------------------------------------------------------------------");
    }

    public static int linearSearchById(int[] arr, int target) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == target) {
                return i;
            }
        }
        return -1;
    }

    public static int binarySearchById(int[] arr, int target) {
        int low = 0;
        int high = arr.length - 1;
        while (low <= high) {
            int mid = low + (high - low) / 2;
            if (arr[mid] == target) {
                return mid;
            }
            if (arr[mid] < target) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return -1;
    }

    public static double calculateTotalValue(double[] prices) {
        double sum = 0;
        for (double price : prices) {
            sum += price;
        }
        return sum;
    }

    public static double calculateAveragePrice(double[] prices) {
        if (prices.length == 0) return 0;
        return calculateTotalValue(prices) / prices.length;
    }

    public static int findMostBorrowedBook(int[] counts) {
        int maxIdx = 0;
        for (int i = 1; i < counts.length; i++) {
            if (counts[i] > counts[maxIdx]) {
                maxIdx = i;
            }
        }
        return maxIdx;
    }

    public static int findLeastBorrowedBook(int[] counts) {
        int minIdx = 0;
        for (int i = 1; i < counts.length; i++) {
            if (counts[i] < counts[minIdx]) {
                minIdx = i;
            }
        }
        return minIdx;
    }

    public static void printShelfMatrix(int[][] matrix) {
        System.out.println("Shelf Allocation Map Matrix [Row][Book ID]:");
        for (int i = 0; i < matrix.length; i++) {
            System.out.println(" Location Entry " + i + " -> Shelf Row: " + matrix[i][0] + ", Book ID Target: " + matrix[i][1]);
        }
    }

    public static void bubbleSortByPrice(double[] prices, String[] titles, int[] ids) {
        int n = prices.length;
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - i - 1; j++) {
                if (prices[j] > prices[j + 1]) {
                    double tempPrice = prices[j];
                    prices[j] = prices[j + 1];
                    prices[j + 1] = tempPrice;

                    String tempTitle = titles[j];
                    titles[j] = titles[j + 1];
                    titles[j + 1] = tempTitle;

                    int tempId = ids[j];
                    ids[j] = ids[j + 1];
                    ids[j + 1] = tempId;
                }
            }
        }
    }

    public static void reverseAllArrays(int[] ids, String[] titles, double[] prices, boolean[] available) {
        int len = ids.length;
        for (int i = 0; i < len / 2; i++) {
            int targetIdx = len - 1 - i;

            int tempId = ids[i];
            ids[i] = ids[targetIdx];
            ids[targetIdx] = tempId;

            String tempTitle = titles[i];
            titles[i] = titles[targetIdx];
            titles[targetIdx] = tempTitle;

            double tempPrice = prices[i];
            prices[i] = prices[targetIdx];
            prices[targetIdx] = tempPrice;

            boolean tempAvail = available[i];
            available[i] = available[targetIdx];
            available[targetIdx] = tempAvail;
        }
    }

    public static int[] filterAvailableBooks(boolean[] available) {
        int count = 0;
        for (boolean b : available) {
            if (b) count++;
        }
        int[] indices = new int[count];
        int tracker = 0;
        for (int i = 0; i < available.length; i++) {
            if (available[i]) {
                indices[tracker++] = i;
            }
        }
        return indices;
    }

    public static void applyInflation(double[] prices, double rate) {
        for (int i = 0; i < prices.length; i++) {
            prices[i] = prices[i] * rate;
        }
    }

    public static String[] truncateInventory(String[] titles, int size) {
        String[] truncated = new String[size];
        for (int i = 0; i < size; i++) {
            truncated[i] = titles[i];
        }
        return truncated;
    }

    public static void generateReport(int[] ids, String[] titles, int[] borrows) {
        System.out.println("=================================================");
        System.out.println("            SYSTEM DISTRIBUTION REPORT           ");
        System.out.println("=================================================");
        int totalBorrows = 0;
        for (int count : borrows) {
            totalBorrows += count;
        }
        System.out.println("Total System Wide Borrows: " + totalBorrows);
        System.out.println("Individual Distribution Metrics:");
        for (int i = 0; i < ids.length; i++) {
            double percentage = ((double) borrows[i] / totalBorrows) * 100;
            System.out.printf(" Book ID %d (%s) -> Share: %.2f%%%n", ids[i], titles[i], percentage);
        }
        System.out.println("=================================================");
    }
}
