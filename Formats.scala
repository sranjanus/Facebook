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
  case class SendPost(userId: String, time: Long, msg: String)
  case class UserProfile(userId:String,uname: String, dob: String, email: String,key:String)
  case class UserInfo(uname: String, dob: String, email: String, key: String)
  case class SendMsg(senderId: Int, time: Long, msg: String, recepientId: Int)
  case class FriendRequest(senderId: String, recepientId: String,key:String)
  case class GetPost(postId:String,userId: String, time: Long, msg: String,likes:Int,comments:Int)
  case class CreatePageInfo(name:String,details:String,createrId:String)
  case class SendPageInfo(id:String,name:String,details:String,createrId:String,likes:Int,post:Int)
  case class SendPagePost(pageId: String, time: Long, msg: String)
  case class SendLikePage(userId:String,pageId:String,time:Long)
  case class SendLikePost(userId:String,postId:String,time:Long)
  case class Response(status:String, id:String,message:String)
  case class SendAddPicture(userId:String,picture:String)
  case class SendPicture(picture:List[String],key:String)
  case class SendComment(userId:String,message:String,postId:String)
  case class GetComment(userId:String,message:String)
  case class SendPostDetails(postId:String,userId: String, time: Long, msg: String,likes:Int,comments:List[GetComment])


  implicit val postFormat = jsonFormat3(SendPost)
  implicit val userProfileFormat = jsonFormat5(UserProfile)
  implicit val userInfoFormat = jsonFormat4(UserInfo)
  implicit val msgFormat = jsonFormat4(SendMsg)
  implicit val friendRequestFormat = jsonFormat3(FriendRequest)
  implicit val getPostFormat = jsonFormat6(GetPost)
  implicit val createPageFormat = jsonFormat3(CreatePageInfo)
  implicit val sendPageInfoFormat = jsonFormat6(SendPageInfo)
  implicit val sendPagePostFormat = jsonFormat3(SendPagePost)
  implicit val sendLikePageFormat = jsonFormat3(SendLikePage)
  implicit val sendLikePostFormat = jsonFormat3(SendLikePost)
  implicit val responseFormat = jsonFormat3(Response)
  implicit val sendAddPictureFormat = jsonFormat2(SendAddPicture)
  implicit val sendPictureFormat = jsonFormat2(SendPicture)
  implicit val sendCommentFormat = jsonFormat3(SendComment)
  implicit val getCommentFormat = jsonFormat2(GetComment)
  implicit val sendPostDetailsFormat = jsonFormat6(SendPostDetails)

  implicit object TimelineJsonFormat extends JsonFormat[FacebookServer.Posts] {
    def write(c: FacebookServer.Posts) = JsObject(
      "authorId" -> JsString(c.authorId),
      "message" -> JsString(c.message),
      "timeStamp" -> JsString(c.timeStamp.toString),
      "postId" -> JsString(c.postId),
      "tags" -> JsArray(c.tags.map(_.toJson).toVector),
      "hashtags" -> JsArray(c.hashtags.map(_.toJson).toVector))
    def read(value: JsValue) = {
      value.asJsObject.getFields("postId", "authorId", "message", "timeStamp", "tags", "hashtags") match {
        case Seq(JsString(postId), JsString(authorId), JsString(message), JsString(timeStamp), JsArray(tags), JsArray(hashtags)) =>
          new FacebookServer.Posts(postId, authorId, message, timeStamp.toLong, tags.map(_.convertTo[String]).to[ArrayBuffer], hashtags.map(_.convertTo[String]).to[ArrayBuffer])
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