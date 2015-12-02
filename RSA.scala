package com.FacebookApp

import java.security._
import java.security.spec.X509EncodedKeySpec
import javax.crypto._
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.binary.Hex
import javax.xml.bind.DatatypeConverter

object RSA {
	def generateKey: KeyPair = {
		// Generate a 1024-bit RSA key pair
		var keyGen = KeyPairGenerator.getInstance("RSA");
		keyGen.initialize(1024);
		var keypair = keyGen.genKeyPair();
		// var publicKey = keypair.getPublic().toString
		// var privateKey = keypair.getPrivate().toString

		// Array(publicKey, privateKey)

		var txt = "aaaaa"
		var temp = encryptB64(keypair.getPrivate, txt.getBytes)
		println("Encrypted text = " + temp)
		var txt2 = decrypt(keypair.getPublic, temp)
		println("Decrypted text = " + temp)

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

  	// def decodePublicKey(encodedKey: Array[Byte]): Option[PublicKey] = { 
	  //   scala.util.control.Exception.allCatch.opt {
	  //     val spec = new X509EncodedKeySpec(encodedKey)
	  //     val factory = KeyFactory.getInstance("RSA")
	  //     factory.generatePublic(spec)
   //  	}   
  	// }

  	// def encrypt(key: PrivateKey, originalmsg: String): String = {
   //      //modified by JEON 20130817
   //      var cipher = Cipher.getInstance("RSA");
   //      //encrypt the message using private key
   //      cipher.init(Cipher.ENCRYPT_MODE, key);
   //      var cipherText = cipher.doFinal(originalmsg.getBytes());
   //      new String(Hex.encode(cipherText));
  	// }

	def encrypt(key: PrivateKey, data: Array[Byte]): Array[Byte] = { 
    	val cipher = Cipher.getInstance("RSA")
    	cipher.init(Cipher.ENCRYPT_MODE, key)
    	cipher.doFinal(data)
  	}

	def encryptB64(key: PrivateKey, data: Array[Byte]): String = { 
    	Base64.encodeBase64String(encrypt(key, data))
  	}

	def encrypt(key: PrivateKey, msg: String): String = {
		encryptB64(key, msg.getBytes)
	}

	def decrypt(key: PublicKey, msg: String): String = {
		  	// 	var cipher = Cipher.getInstance("RSA");
    	// cipher.init(Cipher.DECRYPT_MODE, publicKey);
    	// var dectyptedText = cipher.doFinal(DatatypeConverter.parseHexBinary(originalmsg));

    	// new String(dectyptedText, Charset.forName("UTF-8"));

    	val cipher = Cipher.getInstance("RSA")
    	cipher.init(Cipher.DECRYPT_MODE, key)
    	Base64.encodeBase64String(cipher.doFinal(msg.getBytes))
	}

	def decrypt(key: String, originalmsg: String): String = {
		println("1"+key)
		var publicKey = decodePublicKey(key)
				println(publicKey)

		// val cipher = Cipher.getInstance("RSA")
  //   	cipher.init(Cipher.DECRYPT_MODE, publicKey)
  //   	Base64.encodeBase64String(cipher.doFinal(msg.getBytes))
  		decrypt(publicKey, originalmsg)

	} 
}