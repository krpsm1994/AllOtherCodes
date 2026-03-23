package com.example.demo;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;

public class RenameFiles {

	public static void main(String[] args) {
		/*File oldFile = new File("C:\\Users\\skanimetta\\Downloads\\WhatsApp Video 2023-06-05 at 8.47.04 AM.mp4");
        
        // Specify the new file name and path
        File newFile = new File("C:\\Users\\skanimetta\\Downloads\\DoubleEggDosa.mp4");
        
        // Check if the file exists
        if (oldFile.exists()) {
            // Rename the file
            boolean renamed = oldFile.renameTo(newFile);
            
            // Check if the renaming was successful
            if (renamed) {
                System.out.println("File renamed successfully!");
            } else {
                System.out.println("Failed to rename the file.");
            }
        } else {
            System.out.println("The file does not exist.");
        }*/
        listFiles(new File("D:\\"));

	}
	
	 public static void listFiles(File dir) {
	        // Get all files and subdirectories in the specified directory
	        File[] files = dir.listFiles();

	        if (files != null) {
	        	Arrays.sort(files);
	            for (File file : files) {
	            	if(file.getName().contains("mp31")) {
	            		File newFile = new File("D:\\"+file.getName().replace(".mp31", "(1).mp3"));
	            		file.renameTo(newFile);
	            	}
	                // If it's a directory, recurse into it
	                if (file.isDirectory()) {
	                    System.out.println("Directory: " + file.getAbsolutePath());
	                    listFiles(file); // Recursion for subdirectories
	                } else {
	                    // If it's a file, print its details
	                    System.out.println(file.getName());
	                    //System.out.println("Path: " + file.getAbsolutePath());
	                   // System.out.println("Size: " + file.length() + " bytes");
	                    
	                    // Format and display last modified date
	                    SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
	                   // System.out.println("Last Modified: " + sdf.format(file.lastModified()));
	                    
	                   // System.out.println("---------------------------------------");
	                }
	            }
	        }
	    }

}
