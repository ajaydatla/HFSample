package hf.fabcar;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.Enrollment;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.User;
import org.hyperledger.fabric.sdk.exception.CryptoException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.hyperledger.fabric_ca.sdk.RegistrationRequest;
import org.hyperledger.fabric_ca.sdk.exception.EnrollmentException;
import org.hyperledger.fabric_ca.sdk.exception.InvalidArgumentException;


import hf.org.sdk.UserContext;
import hf.org.sdk.Util;


public class EnrollAdmin {
	
	static Map<String, User> userMap = new HashMap<>();
	
	public static HFCAClient getHFCAClient() {
		String cert = "/home/user/workspace/github/fabric-samples/basic-network/crypto-config/peerOrganizations/org1.example.com/ca/ca.org1.example.com-cert.pem";
//		String cert = "/home/user/workspace/github/fabric-samples/basic-network/crypto-config/ordererOrganizations/example.com/ca/ca.example.com-cert.pem";
        File cf = new File(cert);
		
		Properties properties = new Properties();
        properties.setProperty("pemFile", cf.getAbsolutePath());

        properties.setProperty("allowAllHostNames", "true");
        try {
        	
        	HFCAClient ca = HFCAClient.createNewInstance("ca.example.com", "http://localhost:7054", properties);
        	ca.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
			return ca;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public static UserContext enrollAdmin() {
		
		try {
			HFCAClient ca = getHFCAClient();
			ca.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
			System.out.println(ca.getCAName());
			Enrollment enrollment = ca.enroll("admin", "adminpw");
			UserContext adminContext = new UserContext();
	        adminContext.setName("admin");
	        adminContext.setEnrollment(enrollment);
	        adminContext.setAffiliation("Admin@org1.example.com");
	        adminContext.setMspId("Org1MSP");

	        Util.writeUserContext(adminContext);
	        return adminContext;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public static void getHFClient(UserContext user) {
		HFClient client = HFClient.createNewInstance();
		
		try {
			client.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
			client.setUserContext(user);
			
			Channel mychannel = client.newChannel("mychannel2");
			mychannel.initialize();
			
			System.out.println(mychannel.isInitialized());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	public static void main(String[] args) throws IOException, ClassNotFoundException {
//		UserContext user = registerUser();
		
		getHFClient(getUser("gold"));
		
	}
	public static UserContext getUser(String name) {

		FileInputStream is;
		try {
			is = new FileInputStream("/home/user/workspace/github/fabric-samples/basic-network/crypto-config/"
					+ "peerOrganizations/org1.example.com/users/"+name+"@org1.example.com/"+name+".ser");
			
			ObjectInputStream obj = new ObjectInputStream(is);
			UserContext user = (UserContext) obj.readObject();
			obj.close();
			is.close();
			return user;
			
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		
		
	
	}
	
	public static UserContext getAdmin() {
		FileInputStream is;
		try {
			is = new FileInputStream("/home/user/workspace/github/fabric-samples/basic-network/crypto-config/"
					+ "peerOrganizations/org1.example.com/users/Admin@org1.example.com/admin.ser");
			
			ObjectInputStream obj = new ObjectInputStream(is);
			UserContext admin = (UserContext) obj.readObject();
			obj.close();
			is.close();
			return admin;
			
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		
		
	}
	
	public static UserContext registerUser() {


        Enrollment enrollment;
		try {
			HFCAClient ca = getHFCAClient();
			String name = "gold";
			RegistrationRequest rr = new RegistrationRequest(name, "org1");
			UserContext admin = getAdmin();
			String registered = ca.register(rr, admin );
			System.out.println(registered);
			enrollment = ca.enroll(name, registered);
			UserContext userContext = new UserContext();
	        userContext.setMspId("Org1MSP");
	        
	        userContext.setAffiliation(name+"@org1.example.com");
	        userContext.setEnrollment(enrollment);
	        userContext.setName(name);
	        userMap.put(name, userContext);
	        Util.writeUserContext(userContext);
			
	        return userContext;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}

        
    
	}

}
