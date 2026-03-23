package com.example.demo;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.ReloadingFileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.event.EventListener;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.reloading.PeriodicReloadingTrigger;
import org.apache.commons.configuration2.reloading.ReloadingController;
import org.apache.commons.configuration2.reloading.ReloadingControllerSupport;
import org.apache.commons.configuration2.reloading.ReloadingEvent;


public class ConfigurationBeanManager {
	static boolean configChanged = false;
	static XMLConfiguration config = null;
	static ReloadingFileBasedConfigurationBuilder<XMLConfiguration> builder = null;
	static PeriodicReloadingTrigger trigger = null;
	static ReloadingController controller = null;
	 static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

	public static void main(String[] args) throws URISyntaxException, ConfigurationException, InterruptedException {

		String filePath = "C:\\Drive\\Git Eclipse Workspace\\PackageFetcher\\src\\main\\resources\\damlink_config.xml";
		
		File file = new File(filePath);

		Parameters params = new Parameters();
		
		// Use ReloadingFileBasedConfigurationBuilder for both reloading and saving
		builder = new ReloadingFileBasedConfigurationBuilder<>(XMLConfiguration.class)
				.configure(params.xml().setFile(file).setThrowExceptionOnMissing(true)
	                    .setValidating(false));
		//builder.setAutoSave(true);
		// Create reloading detector
		//controller = ((ReloadingControllerSupport) builder).getReloadingController();
		controller = builder.getReloadingController();
		controller.addEventListener(ReloadingEvent.ANY, new EventListener<ReloadingEvent>() {
			@Override
			public void onEvent(ReloadingEvent event) {
				System.out.println("Detected File change. Inside event logic.");
				configChanged = true;
				try {
					config = builder.getConfiguration();
					System.out.println("Config Updated.");
				} catch (ConfigurationException e) {
					System.out.println("Error retrieving configuaration");
				}
				//scheduler.schedule(task, 10, TimeUnit.SECONDS);
			}
		});

		// Create trigger: check for changes every 5 seconds
		trigger = new PeriodicReloadingTrigger(controller, null, 10, TimeUnit.SECONDS);
		trigger.start();
		
		
		/*Runnable task = () -> {
			try {
				config = builder.getConfiguration();
				List<OTDCConnectionConfig> connections = getAllOTDCServiceConnections();
			} catch (ConfigurationException e) {
				e.printStackTrace();
			}
		};
		
		scheduler.schedule(task, 10, TimeUnit.SECONDS);*/
		/*
		
		try {
			config = builder.getConfiguration();
			
			//System.out.println("Getting all otdc connections.");
			
			List<OTDCConnectionConfig> connections = getAllOTDCServiceConnections();

			//System.out.println("Connections: " + connections);

			//System.out.println("Task completed.");
			
		} catch(ConfigurationException e) {
			e.printStackTrace();
		}*/
		config = builder.getConfiguration();
		
		while(true) {
			List<OTDCConnectionConfig> connections = getAllOTDCServiceConnections();
			Thread.sleep(10000);
		}
	}

	@SuppressWarnings({ "rawtypes" })
	public static List<OTDCConnectionConfig> getAllOTDCServiceConnections() {
		//System.out.println("getALlOTDCConnections started");
		final String PROP_KEY_PREFIX = "otdcservices.connection";
		AbstractConfigurationList<OTDCConnectionConfig> confs = new AbstractConfigurationList<OTDCConnectionConfig>(
				config, PROP_KEY_PREFIX) {
			@Override
            OTDCConnectionConfig buildElement(HierarchicalConfiguration conf, int index) {
                String prefix = PROP_KEY_PREFIX + "(" + index + ")";
                String id = (String) getProperty(conf, "id");
                String active = (String) getProperty(conf, "active");
                String url = (String) getProperty(conf, "url");
                String handler = (String) getProperty(conf, "handler");
                String handlerSystemId = (String) getProperty(conf, "handlersystemid");
                Boolean activeClass = Boolean.valueOf(active);

                UsernameTokenConfig userpassConfig = getAuthorizationConfig(conf, id, prefix);
                TrustedCertificate cert = extractTrustedCertificate(conf);
                String events = conf.getString(OTDCConnectionConfig.ELEM_EVENTS, null);
                List<String> eventList = (events != null) ? Arrays.asList(events.split(",")) : Collections.emptyList();

                return new OTDCConnectionConfig(id, activeClass, url, handler, handlerSystemId, cert, eventList, userpassConfig);
            }
            
            @Override
            OTDCConnectionConfig buildElement(HierarchicalConfiguration conf) {
                // Not used here, but required to satisfy abstract class
                return null;
            }
			/*@Override
			OTDCConnectionConfig buildElement(HierarchicalConfiguration conf, int index) {
				//System.out.println("getALlOTDCConnections buildElement started.");
				
				
				String id = (String) getProperty(conf, "id");
				String active = (String) getProperty(conf, "active");
				String url = (String) getProperty(conf, "url");
				String handler = (String) getProperty(conf, "handler");
				String handlerSystemId = (String) getProperty(conf, "handlersystemid");
				Boolean activeClass = Boolean.valueOf(active);
				//System.out.println("Connection Details: " + id + " : " + active + " : " + url + " : " + handler + " : "+ handlerSystemId);
				// SAPDC-3648
				UsernameTokenConfig userpassConfig = getAuthorizationConfig(conf, id);
				//System.out.println("fetched authorization config");
				TrustedCertificate cert = extractTrustedCertificate(conf);
				//System.out.println("extracted trusted certificates.");
				String events = conf.getString(OTDCConnectionConfig.ELEM_EVENTS, null);
				//System.out.println("getALlOTDCConnections events: " + events);
				List<String> eventList = (events != null) ? Arrays.asList(events.split(","))
						: Collections.<String>emptyList();
				//System.out.println("getALlOTDCConnections buildElement Completed. returning connection");
				return new OTDCConnectionConfig(id, activeClass, url, handler, handlerSystemId, cert, eventList,
						userpassConfig);
			}

			@Override
			OTDCConnectionConfig buildElement(HierarchicalConfiguration conf) {
				return null;
			}*/
		};
		//System.out.println("getALlOTDCConnections completed");
		return confs.getList(buildPropertyNotation("id"));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected static TrustedCertificate extractTrustedCertificate(HierarchicalConfiguration conf) {
		// SAPDC-3648
		// checks if first security configurations is certificate
		List<HierarchicalConfiguration> securityNode = conf.configurationsAt("security",true);
		if (securityNode != null && securityNode.size() > 0) {
			List<HierarchicalConfiguration> certificateNode = securityNode.get(0).configurationsAt("certificate",true);
			if (certificateNode != null && certificateNode.size() > 0) {
				Configuration certif = conf.subset("security.certificate");
				String keyStore = certif.getString("keystore");
				String keyStorePassword = certif.getString("keystorepassword");
				String keyStoreType = certif.getString("keystoretype");
				String keyStoreAlgorithm = certif.getString("keystorealgorithm");
				String trustStore = certif.getString("truststore");
				String trustStorePassword = certif.getString("truststorepassword");
				String trustStoreType = certif.getString("truststoretype");
				String trustStoreAlg = certif.getString("truststorealgorithm");

				return new TrustedCertificate(keyStore, keyStorePassword, keyStoreType, keyStoreAlgorithm, trustStore,
						trustStorePassword, trustStoreType, trustStoreAlg);
			}
		}

		return null;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected static UsernameTokenConfig getAuthorizationConfig(HierarchicalConfiguration conf, String id, String baseKey) {
		//System.out.println("getAuthorizationConfig started.?>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
		// SAPDC-3648
		// checks if first security configurations is usernametoken
		List<HierarchicalConfiguration> securityNode = conf.configurationsAt("security");
        if (securityNode != null && !securityNode.isEmpty()) {
            List<HierarchicalConfiguration> usernameTokenNode = securityNode.get(0).configurationsAt("usernametoken");
            if (usernameTokenNode != null && !usernameTokenNode.isEmpty()) {
                Configuration token = conf.subset("security.usernametoken");
                String username = token.getString("username");
                String password = token.getString("password");
                
                System.out.println("Username : "+username);
    			System.out.println("Password : "+password);

                //SAPDC-3684
    			if(username == null || username == "" || password == null || password == "") {
    				System.out.println("username or password config property doesn't exist for usernametoken for id: " + id);
                    return null;
                }

    			String encryptPwd = "Sai : " + Math.random();
    			
    			String fullKey = baseKey + ".security.usernametoken.password";
                savePropertyValue(fullKey, encryptPwd);
                
              //Encrypt the sensitive information using DAMLink encryption key
                return new UsernameTokenConfig(username, encryptPwd);
            }
        }
		
		
		/*List<HierarchicalConfiguration> securityNodes = conf.configurationsAt("otdcservices.connection.security", true);
		for(HierarchicalConfiguration securityNode : securityNodes) {
			
			String username		= securityNode.getString("usernametoken.username");
			String password		= securityNode.getString("usernametoken.password");

			System.out.println("Username : "+username);
			System.out.println("Password : "+password);
			
			if(username == null || username == "" || password == null || password == "") {
				System.out.println("username or password config property doesn't exist for usernametoken for id: " + id);
				return null;
			}
			
			String encryptPwd = "Sai : " + Math.random();
			securityNode.setProperty("usernametoken.password", encryptPwd);
			
			savePropertyValue(securityNode, "usernametoken.password", encryptPwd);
			
			//System.out.println("getAuthorizationConfig completed. returning UserNameTokenConfig.");
			return new UsernameTokenConfig(username, encryptPwd);
			
		}*/
		//System.out.println("getAuthorizationConfig completed. returning null.");
		return null;
	}

	@SuppressWarnings({ "unused" })
	private static void savePropertyValue(String fullKeyPath, Object value) {
		//System.out.println("savePropertyValue started for propertyName: " + propertyName + ", propertyValue: " + propertyValue);
		
		
		try {
			//System.out.println("Saving property using builder.");
			//config.setProperty(fullKeyPath, value);
            //builder.save();
			System.out.println("Password sent for saving is "+config.getProperty("otdcservices.connection.security.usernametoken.password"));
			
			//builder.save();
		} catch (Exception e) {
			e.printStackTrace();
		}
		//System.out.println("savePropertyValue Completed for propertyName: " + propertyName + ", propertyValue: " + propertyValue);
	}

	@SuppressWarnings({ "rawtypes" })
	static private Object getProperty(HierarchicalConfiguration conf, String propId) {
		return conf.getProperty(buildPropertyNotation(propId));
	}

	private static String buildPropertyNotation(String propId) {
		return "[@" + propId + "]";
	}

	@SuppressWarnings({ "rawtypes" })
	private static abstract class AbstractConfigurationList<T> {
		private String mPropertyKeyPrefix;
		private HierarchicalConfiguration mConf;

		public AbstractConfigurationList(HierarchicalConfiguration conf, String prefix) {
			mConf = conf;
			mPropertyKeyPrefix = prefix;
		}

		ArrayList<T> getList(String subTagOrPropertyName) {
			ArrayList<T> ret = new ArrayList<T>();
			Object subPropObject = mConf.getProperty(mPropertyKeyPrefix + subTagOrPropertyName);

			if (subPropObject instanceof ArrayList) {
				ArrayList<?> coll = (ArrayList<?>) subPropObject;

				for (int pointer = 0; pointer < coll.size(); pointer++) {
					HierarchicalConfiguration sub = mConf.configurationAt(mPropertyKeyPrefix + "(" + pointer + ")");
					T lc = buildElement(sub, pointer);
					ret.add(lc);
				}
			} else if (subPropObject instanceof String) {
				HierarchicalConfiguration sub = mConf.configurationAt(mPropertyKeyPrefix);
				T lc = buildElement(sub);
				ret.add(lc);
			}

			return ret;
		}

		abstract T buildElement(HierarchicalConfiguration conf);
		
		T buildElement(HierarchicalConfiguration conf, int index) {
			return buildElement(conf);
		};
	}

}