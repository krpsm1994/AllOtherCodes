package com.example.demo;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.apache.commons.configuration2.PropertiesConfiguration;

public class PropertiesConfigurator {

	public static void main(String[] args) {
		Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream("C:\\Drive\\damlinkPWConfig.properties")) {
            props.load(fis);
            System.out.println("isInitialized: " + props.getProperty("otdcpw.ui.isInitialized"));
            PropertiesConfiguration config = new PropertiesConfiguration();
            config.clear();
            for (String key : props.stringPropertyNames()) {
                config.setProperty(key, props.getProperty(key));
            }
            
            System.out.println("isInitialized: " + config.getBoolean("otdcpw.ui.isInitialized"));
        } catch (IOException e) {
            e.printStackTrace();
        }
	}
}
