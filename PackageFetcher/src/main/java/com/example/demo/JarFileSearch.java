package com.example.demo;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JarFileSearch {

	public static void main(String[] args) {
        String directoryPath = "C:/Drive/GIT/SAP.DAMIntegration/main/media/ot-damlink-25.2.0";
        List<String> jarFiles = findJarFiles(directoryPath);
        Map<String,List<String>> jarFilesMap = findMappedJarFiles(directoryPath);
        //Collections.sort(jarFiles);
        // Print the names of all JAR files found
        /*for (String jarFile : jarFiles) {
            System.out.println(jarFile);
        }*/
        
        for (Map.Entry<String, List<String>> entry : jarFilesMap.entrySet()) {
            System.out.println(entry.getKey() + " :");
            List<String> values = entry.getValue();
            if (values != null && !values.isEmpty()) {
                for (String value : values) {
                    System.out.println("    - " + value);
                }
            } else {
                System.out.println("    (no values)");
            }
        }
    }
	
	public static Map<String,List<String>> findMappedJarFiles(String directoryPath) {
		Map<String,List<String>> jarFilesMap = new HashMap<>();
        Path directory = Paths.get(directoryPath);

        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    //if (file.toString().toLowerCase().endsWith(".jar") && !file.getParent().toString().contains("devlib")) {
                	if (file.toString().toLowerCase().endsWith(".jar")) {
                		if(jarFilesMap.keySet().contains(file.getParent().toString())){
                			List<String> jarFiles = jarFilesMap.get(file.getParent().toString());
                			jarFiles.add(file.getFileName().toString());
                			jarFilesMap.put(file.getParent().toString(), jarFiles);
                		} else {
                			List<String> jarFiles = new ArrayList<>();
                			jarFiles.add(file.getFileName().toString());
                			jarFilesMap.put(file.getParent().toString(), jarFiles);
                		}
                	//System.out.println(file.getFileName().toString() + " : " + file.getParent().toString());
                    //jarFiles.add(file.getFileName().toString()+ " : " + file.getParent().toString());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

        return jarFilesMap;
    }

    public static List<String> findJarFiles(String directoryPath) {
        List<String> jarFiles = new ArrayList<>();
        Path directory = Paths.get(directoryPath);

        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    //if (file.toString().toLowerCase().endsWith(".jar") && !file.getParent().toString().contains("devlib")) {
                	if (file.toString().toLowerCase().endsWith(".jar")) {

                	//System.out.println(file.getFileName().toString() + " : " + file.getParent().toString());
                        jarFiles.add(file.getFileName().toString()+ " : " + file.getParent().toString());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

        return jarFiles;
    }

}
