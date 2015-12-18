package com.FacebookApp

import java.security._
import java.security.spec.KeySpec
import javax.crypto.spec.{PBEKeySpec, SecretKeySpec, IvParameterSpec}
import javax.crypto.{SecretKeyFactory, Cipher, KeyGenerator, SecretKey}
import org.apache.commons.codec.binary.Base64
import java.util

import sun.misc.BASE64Encoder

object AES {

  val prng = new SecureRandom()

  def generateKey: String = {
    val keyGen = KeyGenerator.getInstance("AES")
    keyGen.init(128)
    val secretKey = keyGen.generateKey()
    Base64.encodeBase64String(secretKey.getEncoded)
  }

  def encrypt(key: String, strDataToEncrypt: String): (String, String) = {
    val keyByte = Base64.decodeBase64(key)
    val keyLength = 128
    val iv = new Array[Byte](16)
    prng.nextBytes(iv)
    val aesCipherForEncryption = Cipher.getInstance("AES/CBC/PKCS5PADDING")
    val keyGen = KeyGenerator.getInstance("AES")
    keyGen.init(128)
    val secretKey = new SecretKeySpec(keyByte, "AES")
    aesCipherForEncryption.init(Cipher.ENCRYPT_MODE, secretKey,
      new IvParameterSpec(iv))
    val byteDataToEncrypt = strDataToEncrypt.getBytes()
    val byteCipherText = aesCipherForEncryption
      .doFinal(byteDataToEncrypt)
    val strCipherText = Base64.encodeBase64String(byteCipherText)
    (strCipherText, Base64.encodeBase64String(iv))
  }

  def decrypt(key: String, encryptedText: String, iv: String): String = {
    val aesCipherForDecryption = Cipher.getInstance("AES/CBC/PKCS5PADDING")
    val decodedIv = Base64.decodeBase64(iv)
    val decodedKeyByte = Base64.decodeBase64(key)
    val decodecEncText = Base64.decodeBase64(encryptedText)
    val secretKey = new SecretKeySpec(decodedKeyByte, "AES")
    aesCipherForDecryption.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(decodedIv))
    val byteDecryptedText = aesCipherForDecryption
      .doFinal(decodecEncText)
    Base64.encodeBase64String(byteDecryptedText)
  }

}