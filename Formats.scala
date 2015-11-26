package com.FacebookApp

import scala.collection.mutable.ArrayBuffer

import spray.json.DefaultJsonProtocol
import spray.json.DeserializationException
import spray.json.JsArray
import spray.json.JsNumber
import spray.json.JsObject
import spray.json.JsString
import spray.json.JsValue
import spray.json.JsonFormat
import spray.json.pimpAny

trait JsonFormats extends DefaultJsonProtocol {
  case class SendPost(userId: Int, time: Long, msg: String)
  case class UserProfile(uname: String, dob: String, email: String)
  case class UserInfo(uid: Int, uname: String, dob: String, email: String, pass: String, postCount: Int, friendsCount: Int)
  case class SendMsg(senderId: Int, time: Long, msg: String, recepientId: Int)

  implicit val postFormat = jsonFormat3(SendPost)
  implicit val userProfileFormat = jsonFormat3(UserProfile)
  implicit val userInfoFormat = jsonFormat7(UserInfo)
  implicit val msgFormat = jsonFormat4(SendMsg)

  implicit object TimelineJsonFormat extends JsonFormat[FacebookServer.Posts] {
    def write(c: FacebookServer.Posts) = JsObject(
      "authorId" -> JsNumber(c.authorId),
      "message" -> JsString(c.message),
      "timeStamp" -> JsString(c.timeStamp.toString),
      "postId" -> JsString(c.postId),
      "tags" -> JsArray(c.tags.map(_.toJson).toVector),
      "hashtags" -> JsArray(c.hashtags.map(_.toJson).toVector))
    def read(value: JsValue) = {
      value.asJsObject.getFields("postId", "authorId", "message", "timeStamp", "tags", "hashtags") match {
        case Seq(JsString(postId), JsNumber(authorId), JsString(message), JsString(timeStamp), JsArray(tags), JsArray(hashtags)) =>
          new FacebookServer.Posts(postId, authorId.toInt, message, timeStamp.toLong, tags.map(_.convertTo[String]).to[ArrayBuffer], hashtags.map(_.convertTo[String]).to[ArrayBuffer])
        case _ => throw new DeserializationException("Posts expected")
      }
    }
  }

  implicit object MessagesJsonFormat extends JsonFormat[FacebookServer.Messages] {
    def write(c: FacebookServer.Messages) = JsObject(
      "senderId" -> JsNumber(c.senderId),
      "message" -> JsString(c.message),
      "timeStamp" -> JsString(c.timeStamp.toString),
      "msgId" -> JsString(c.msgId),
      "recepientId" -> JsNumber(c.recepientId))
    def read(value: JsValue) = {
      value.asJsObject.getFields("msgId", "senderId", "message", "timeStamp", "recepientId") match {
        case Seq(JsString(msgId), JsNumber(senderId), JsString(message), JsString(timeStamp), JsNumber(recepientId)) =>
          new FacebookServer.Messages(senderId.toInt, recepientId.toInt, msgId, message, timeStamp.toLong)
        case _ => throw new DeserializationException("Messages expected")
      }
    }
  }
}