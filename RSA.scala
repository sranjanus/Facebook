package com.FacebookApp

import java.security._
import java.security.spec.X509EncodedKeySpec
import javax.crypto._
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.binary.Hex
import javax.xml.bind.DatatypeConverter
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.Security;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import javax.crypto.spec.SecretKeySpec;

object RSA {


	def encrypt(text:String,key:PrivateKey):String = {
		var cipher = Cipher.getInstance("RSA");
		cipher.init(Cipher.ENCRYPT_MODE, key);
		var cipherText = cipher.doFinal(text.getBytes());
		var temp = Base64.encodeBase64String(cipherText);
		temp
	}

	def encrypt(text:String,key:PublicKey):String = {
		var cipher = Cipher.getInstance("RSA");
		cipher.init(Cipher.ENCRYPT_MODE, key);
		var cipherText = cipher.doFinal(text.getBytes());
		var temp = Base64.encodeBase64String(cipherText);
		temp
	}

	def printbytes(b : Array[Byte]){
		for(x <- b){
			print(x.toInt+" ");
		}
		println("-----------------")
	}

  	def decrypt(text :String,  key:PrivateKey):String  ={
		var dectyptedText = Base64.decodeBase64(text)
		var cipher = Cipher.getInstance("RSA");

		cipher.init(Cipher.DECRYPT_MODE, key);
		dectyptedText = cipher.doFinal(dectyptedText);

		new String(dectyptedText);
	}

	def decrypt(text :String,  key:PublicKey):String  ={
		var dectyptedText = Base64.decodeBase64(text)
		var cipher = Cipher.getInstance("RSA");

		cipher.init(Cipher.DECRYPT_MODE, key);
		dectyptedText = cipher.doFinal(dectyptedText);

		new String(dectyptedText);
	}



	def generateKey: KeyPair = {
		var keyGen = KeyPairGenerator.getInstance("RSA");
		keyGen.initialize(1024);
		var keypair = keyGen.genKeyPair()
		keypair
	}

	def encodePublicKey(key: PublicKey): String = {
		Base64.encodeBase64String(key.getEncoded())
	}

	def decodePublicKey(encodedKey: String): PublicKey = { 
    	var publicBytes = Base64.decodeBase64(encodedKey);
		var keySpec = new X509EncodedKeySpec(publicBytes);
		var keyFactory = KeyFactory.getInstance("RSA");
		var pubKey = keyFactory.generatePublic(keySpec);
		pubKey   
  	}

  	def encrypt(text :String,  key:String ):String = {
  		var publicKey = decodePublicKey(key)
		encrypt(text,publicKey)			
  	}

  	def decrypt(text :String,  key:String ):String = {
  		var publicKey = decodePublicKey(key)
		decrypt(text,publicKey)			
  	}
  
  	def generateSymetricKey():String = {
  		var generator = KeyGenerator.getInstance("AES");
		generator.init(128);
		var key = generator.generateKey();
		Base64.encodeBase64String(key.getEncoded());
  	}

	def encryptWithAESKey(data:String,key:String):String  = {
		var secKey = new SecretKeySpec(Base64.decodeBase64(key),"AES");
		var cipher = Cipher.getInstance("AES");
		cipher.init(Cipher.ENCRYPT_MODE, secKey);
		var newData = cipher.doFinal(data.getBytes());
		Base64.encodeBase64String(newData);
	}

	def decryptWithAESKey(inputData: String, key:String) = {
		var cipher = Cipher.getInstance("AES");
		var secKey = new SecretKeySpec(Base64.decodeBase64(key), "AES");
		cipher.init(Cipher.DECRYPT_MODE, secKey);
		var newData = cipher.doFinal(Base64.decodeBase64(inputData.getBytes()));
		new String(newData);
	}
}