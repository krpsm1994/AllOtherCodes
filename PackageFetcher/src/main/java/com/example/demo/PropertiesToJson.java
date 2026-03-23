package com.example.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.FileInputStream;
import java.util.Properties;

public class PropertiesToJson {

	public static void main(String[] args) {
		String filePath = "C:/Drive/GitRepo/sap.damintegration/pkg/SAP_DAMIntegration/res/otmm_config/data/sap/damlinkPWConfig.properties";
		try {
            // Load properties from file
            Properties properties = new Properties();
            FileInputStream fis = new FileInputStream(filePath);
            properties.load(fis);

            // Create an ObjectMapper
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();

            // Iterate through properties and build JSON structure
            for (String key : properties.stringPropertyNames()) {
                String[] parts = key.split("\\.");
                ObjectNode currentNode = rootNode;
                for (int i = 0; i < parts.length; i++) {
                    if (!currentNode.has(parts[i])) {
                        if (i == parts.length - 1) {
                            currentNode.put(parts[i], properties.getProperty(key));
                        } else {
                            ObjectNode newNode = mapper.createObjectNode();
                            currentNode.set(parts[i], newNode);
                            currentNode = newNode;
                        }
                    } else {
                        currentNode = (ObjectNode) currentNode.get(parts[i]);
                    }
                }
            }

            // Convert ObjectNode to JSON string
            String json = mapper.writeValueAsString(rootNode);
            System.out.println(json);

            fis.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
	}

}
