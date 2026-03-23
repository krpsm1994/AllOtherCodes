package com.example.demo;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;

public class PropertiesConfigService {

	public static void main(String[] args) throws URISyntaxException {
		try {
			URL resourceUrl = ConfigurationBeanManager.class.getClassLoader().getResource("pw.properties");

			Path path = Paths.get(resourceUrl.toURI());
			System.out.println("File path: " + path.toString());
			
			File file = new File(path.toString());
			System.out.println(file.exists());
            Parameters params = new Parameters();
            
            FileBasedConfigurationBuilder<PropertiesConfiguration> builder =
                new FileBasedConfigurationBuilder<>(PropertiesConfiguration.class)
                    .configure(params.properties()
                        .setFile(file));

            PropertiesConfiguration config = builder.getConfiguration();

            // Access properties
            String name = config.getString("otdcpw.rootFolder.name");
            String folderType = config.getString("otdcpw.rootFolder.folderTypeId");

            System.out.println("otdcpw.rootFolder.name: " + name);
            System.out.println("otdcpw.rootFolder.folderTypeId: " + folderType);

        } catch (ConfigurationException e) {
            e.printStackTrace();
        }
	}

}
