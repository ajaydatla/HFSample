package hf.fabcar;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.hyperledger.fabric.sdk.ChaincodeEndorsementPolicy;
import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.Enrollment;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.InstallProposalRequest;
import org.hyperledger.fabric.sdk.InstantiateProposalRequest;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.ProposalResponse;
import org.hyperledger.fabric.sdk.QueryByChaincodeRequest;
import org.hyperledger.fabric.sdk.TransactionProposalRequest;
import org.hyperledger.fabric.sdk.User;
import org.hyperledger.fabric.sdk.TransactionRequest.Type;
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

			Channel mychannel = client.newChannel("mychannel5");

			Properties ordererProperties = getOrdererProperties();

			// example of setting keepAlive to avoid timeouts on inactive http2 connections.
			// Under 5 minutes would require changes to server side to accept faster ping
			// rates.
			ordererProperties.put("grpc.NettyChannelBuilderOption.keepAliveTime",
					new Object[] { 5L, TimeUnit.MINUTES });
			ordererProperties.put("grpc.NettyChannelBuilderOption.keepAliveTimeout",
					new Object[] { 8L, TimeUnit.SECONDS });
			ordererProperties.put("grpc.NettyChannelBuilderOption.keepAliveWithoutCalls", new Object[] { true });

			mychannel.addOrderer(client.newOrderer("orderer.example.com", "grpc://localhost:7050", ordererProperties));
			
			Properties peerProperties = getEndPointProperties(); //test properties for peer.. if any.

            //Example of setting specific options on grpc's NettyChannelBuilder
            peerProperties.put("grpc.NettyChannelBuilderOption.maxInboundMessageSize", 9000000);
			mychannel.addPeer(client.newPeer("peer0.org1.example.com", "grpc://localhost:7051", peerProperties));

			System.out.println(mychannel.getPeers().size());
			String version = "1.2";

			mychannel.initialize();
			String ccName = "fabcar1";
			ChaincodeID chaincodeID = ChaincodeID.newBuilder().setName(ccName).setPath("/exchaincode")
					.setVersion(version).build();

			InstallProposalRequest installProposalRequest = client.newInstallProposalRequest();
//            installProposalRequest.setChaincodeID(chaincodeID);
//            installProposalRequest.setChaincodeMetaInfLocation(new File("/home/user/workspace/github/metadata"));
			installProposalRequest.setChaincodeSourceLocation(Paths.get("/home/user").toFile());
			installProposalRequest.setChaincodeVersion(version);
			installProposalRequest.setChaincodeLanguage(Type.GO_LANG);
			installProposalRequest.setChaincodeName(ccName);
			installProposalRequest.setProposalWaitTime(100000);
			installProposalRequest.setChaincodePath("/exchaincode");
			installProposalRequest.setUserContext(user);

			Collection<ProposalResponse> installPR = client.sendInstallProposal(installProposalRequest,
					mychannel.getPeers());
			for (ProposalResponse proposalResponse : installPR) {
				System.out.println("install status is " + proposalResponse.getStatus());
				System.out.println("message " + proposalResponse.getMessage());
			}

			InstantiateProposalRequest instantiateProposalRequest = client.newInstantiationProposalRequest();
			instantiateProposalRequest.setProposalWaitTime(100000);
			instantiateProposalRequest.setChaincodeID(chaincodeID);
			instantiateProposalRequest.setChaincodeLanguage(Type.GO_LANG);

			ChaincodeEndorsementPolicy chaincodeEndorsementPolicy = new ChaincodeEndorsementPolicy();
			chaincodeEndorsementPolicy.fromYamlFile(new File("/home/user/workspace/policy.yaml"));
			instantiateProposalRequest.setChaincodeEndorsementPolicy(chaincodeEndorsementPolicy);
			mychannel.sendInstantiationProposal(instantiateProposalRequest, mychannel.getPeers());

//            instantiateProposalRequest.setFcn("init");
//            instantiateProposalRequest.setArgs(new String[] {"a", "500", "b", "" + (200 + delta)});

//			TransactionProposalRequest proposalRequest = client.newTransactionProposalRequest();
//			proposalRequest.setChaincodeID(chaincodeID);
//			proposalRequest.setFcn("queryAllCars");
//			proposalRequest.setProposalWaitTime(TimeUnit.SECONDS.toMillis(10));
//			proposalRequest.setChaincodeLanguage(Type.GO_LANG);

//			Collection<ProposalResponse> transactionPropResp = mychannel.sendTransactionProposal(transactionProposalRequest, channel.getPeers());
//            for (ProposalResponse response : transactionPropResp) {
//                if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
//                   System.out.println("successfull");
//                } else {
//                    failed.add(response);
//                }
//            }

			QueryByChaincodeRequest queryByChaincodeRequest = client.newQueryProposalRequest();
//          queryByChaincodeRequest.setArgs(new String[] {"b"});
			queryByChaincodeRequest.setFcn("queryAllCars");
			queryByChaincodeRequest.setChaincodeID(chaincodeID);

			final Collection<ProposalResponse> responses = mychannel.queryByChaincode(queryByChaincodeRequest);
			for (Iterator iterator = responses.iterator(); iterator.hasNext();) {
				System.out.println("inside");
				ProposalResponse proposalResponse = (ProposalResponse) iterator.next();
				System.out.println("transaction id is: " + proposalResponse.getTransactionID());
				System.out.println(proposalResponse.getMessage());
			}

			System.out.println(mychannel.isInitialized());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws IOException, ClassNotFoundException {
//		UserContext user = registerUser();

//		getHFClient(getUser("gold"));
//		enrollAdmin();
//		registerUser();
//		getHFClient(getAdmin());
		getHFClient(getUser(""));
	}

	public static UserContext getUser(String name) {

		FileInputStream is;
		try {
			is = new FileInputStream("/home/user/workspace/github/fabric-samples/basic-network/crypto-config/"
					+ "peerOrganizations/org1.example.com/users/" + name + "User1@org1.example.com/" + name
					+ "user1.ser");

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
			String name = "user1";
			RegistrationRequest rr = new RegistrationRequest(name, "org1");
			UserContext admin = getAdmin();
			String registered = ca.register(rr, admin);
			System.out.println(registered);
			enrollment = ca.enroll(name, registered);
			UserContext userContext = new UserContext();
			userContext.setMspId("Org1MSP");

			userContext.setAffiliation(name + "@org1.example.com");
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

	public static Properties getOrdererProperties() {

		File cert = Paths
				.get("/home/user/workspace/github/fabric-samples/basic-network/"
						+ "crypto-config/ordererOrganizations/example.com/orderers/orderer.example.com/tls/server.crt")
				.toFile();

		File clientCert = Paths.get("/home/user/workspace/github/fabric-samples/basic-network/crypto-config/"
				+ "ordererOrganizations/example.com/users/Admin@example.com/tls/server.crt").toFile();

		File clientKey = Paths.get("/home/user/workspace/github/fabric-samples/basic-network/crypto-config/"
				+ "ordererOrganizations/example.com/users/Admin@example.com/tls/server.key").toFile();

		Properties ret = new Properties();

//		ret.setProperty("clientCertFile", clientCert.getAbsolutePath());
//		ret.setProperty("clientKeyFile", clientKey.getAbsolutePath());
		ret.setProperty("pemFile", cert.getAbsolutePath());

		ret.setProperty("hostnameOverride", "orderer.example.com");
		ret.setProperty("sslProvider", "openSSL");
		ret.setProperty("negotiationType", "TLS");
		
		return ret;

	}

	public static Properties getEndPointProperties() {

		File cert = Paths.get(
				"/home/user/workspace/github/fabric-samples/basic-network/crypto-config/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/server.crt")
				.toFile();

		File clientCert = Paths.get("/home/user/workspace/github/fabric-samples/basic-network/crypto-config/"
				+ "peerOrganizations/org1.example.com/users/User1@org1.example.com/tls/server.crt").toFile();

		File clientKey = Paths.get("/home/user/workspace/github/fabric-samples/basic-network/crypto-config/"
				+ "peerOrganizations/org1.example.com/users/User1@org1.example.com/tls/server.key").toFile();

		Properties ret = new Properties();

//		ret.setProperty("clientCertFile", clientCert.getAbsolutePath());
//		ret.setProperty("clientKeyFile", clientKey.getAbsolutePath());
		ret.setProperty("pemFile", cert.getAbsolutePath());

		ret.setProperty("hostnameOverride", "peer0.org1.example.com");
		ret.setProperty("sslProvider", "openSSL");
		ret.setProperty("negotiationType", "TLS");
		return ret;

	}
	/*
	 * public static Properties getEndPointProperties(final String type, final
	 * String name) { Properties ret = new Properties();
	 * 
	 * // final String domainName = getDomainName(name); final String domainName =
	 * "";
	 * 
	 * 
	 * File cert = Paths.get(getTestChannelPath(),
	 * "crypto-config/ordererOrganizations".replace("orderer", type), domainName,
	 * type + "s", name, "tls/server.crt").toFile();
	 * 
	 * File cert = Paths.get(getTestChannelPath(),
	 * "crypto-config/ordererOrganizations".replace("orderer", type), domainName,
	 * type + "s", name, "tls/server.crt").toFile(); if (!cert.exists()) { throw new
	 * RuntimeException(String.
	 * format("Missing cert file for: %s. Could not find at location: %s", name,
	 * cert.getAbsolutePath())); }
	 * 
	 * if (!isRunningAgainstFabric10()) { File clientCert; File clientKey; if
	 * ("orderer".equals(type)) { clientCert = Paths .get(getTestChannelPath(),
	 * "crypto-config/ordererOrganizations/example.com/users/Admin@example.com/tls/client.crt")
	 * .toFile();
	 * 
	 * clientKey = Paths .get(getTestChannelPath(),
	 * "crypto-config/ordererOrganizations/example.com/users/Admin@example.com/tls/client.key")
	 * .toFile(); } else { clientCert = Paths.get(getTestChannelPath(),
	 * "crypto-config/peerOrganizations/", domainName, "users/User1@" + domainName,
	 * "tls/client.crt").toFile(); clientKey = Paths.get(getTestChannelPath(),
	 * "crypto-config/peerOrganizations/", domainName, "users/User1@" + domainName,
	 * "tls/client.key").toFile(); }
	 * 
	 * if (!clientCert.exists()) { throw new RuntimeException( String.
	 * format("Missing  client cert file for: %s. Could not find at location: %s",
	 * name, clientCert.getAbsolutePath())); }
	 * 
	 * if (!clientKey.exists()) { throw new RuntimeException( String.
	 * format("Missing  client key file for: %s. Could not find at location: %s",
	 * name, clientKey.getAbsolutePath())); } ret.setProperty("clientCertFile",
	 * clientCert.getAbsolutePath()); ret.setProperty("clientKeyFile",
	 * clientKey.getAbsolutePath()); }
	 * 
	 * ret.setProperty("pemFile", cert.getAbsolutePath());
	 * 
	 * ret.setProperty("hostnameOverride", name); ret.setProperty("sslProvider",
	 * "openSSL"); ret.setProperty("negotiationType", "TLS");
	 * 
	 * return ret; }
	 */
}
