package com.example.demo;

/**
 * SAPDC-3648
 * @author nsasi
 *
 */
public class UsernameTokenConfig {

	private String mUsername;
	private String mPassword;

	public UsernameTokenConfig(String username, String password) {
		super();
		this.mUsername = username;
		this.mPassword = password;
	}
	public String getUsername() {
		return mUsername;
	}
	public void setUsername(String mUsername) {
		this.mUsername = mUsername;
	}
	public String getPassword() {
		return mPassword;
	}
	public void setPassword(String mPassword) {
		this.mPassword = mPassword;
	}
}
