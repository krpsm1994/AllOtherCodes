package com.example.demo;

import java.io.File;

public class JarFileHirearichal {
	public static void main(String[] args) {
        // Replace this path with your target folder path
        String folderPath = "C:/Drive/GIT/SAP.DAMIntegration/main/media/ot-damlink-25.2.0";
        File root = new File(folderPath);

        if (!root.exists() || !root.isDirectory()) {
            System.out.println("Invalid folder path.");
            return;
        }

        System.out.println("Folder structure with JAR files:");
        listJarFiles(root, 0);
    }

	private static boolean listJarFiles(File folder, int level) {
        File[] files = folder.listFiles();
        if (files == null) return false;

        boolean hasJar = false;
        StringBuilder output = new StringBuilder();

        for (File file : files) {
            if (file.isDirectory()) {
                boolean subHasJar = listJarFiles(file, level + 1);
                if (subHasJar) {
                    output.append(indent(level)).append("[Folder] ").append(file.getName()).append("\n");
                    hasJar = true;
                }
            } else if (file.isFile() && file.getName().toLowerCase().endsWith(".jar")) {
                output.append(indent(level)).append("- ").append(file.getName()).append("\n");
                hasJar = true;
            }
        }

        // Print only if current folder or any subfolder has .jar
        if (hasJar && level > 0) {
            System.out.print(indent(level - 1) + "[Folder] " + folder.getName() + "\n");
        }
        if (hasJar) {
            System.out.print(output);
        }

        return hasJar;
    }

    private static String indent(int level) {
        return "    ".repeat(level); // Java 11+
    }
}
