package com.FacebookApp

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.DurationInt

import com.typesafe.config.ConfigFactory

import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.Terminated
import akka.actor.actorRef2Scala
import akka.event.LoggingReceive
import akka.routing.SmallestMailboxPool

object FacebookServer {
	case class sample(id: Int, name: String, noOfPosts: Int, friendsCount: Int) extends java.io.Serializable

	class User (id: Int, uName: String, dob: String, email: String, pass: String) {
		var userId = id
		var userName = uName
		var dateOfBirth = dob
		var emailAdd = email
		var password = pass
		var newsfeed: ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue()
		var timeline: ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue()
		var messages: ArrayBuffer[String] = ArrayBuffer.empty
		var friends: CopyOnWriteArrayList[Int] = new CopyOnWriteArrayList()
	}

	class Posts(pid: String, id: Int, post: String, time: Long, tagList: ArrayBuffer[String] = ArrayBuffer.empty, hashtagList: ArrayBuffer[String] = ArrayBuffer.empty){
		var authorId = id
		var message = post
		var timeStamp: Long = time
		var postId = pid
		var tags = tagList
		var hashtags = hashtagList
	}

	class Messages(sid: Int, rid: Int, mid: String, msg: String, time: Long){
		var senderId = sid
		var recepientId = rid
		var message = msg
		var timeStamp: Long = time
		var msgId = mid
	}

	var wTPS = ArrayBuffer.empty[Int]
	var rdTPS = ArrayBuffer.empty[Int]
	var mTPS = ArrayBuffer.empty[Int]
	var uCtr: AtomicInteger = new AtomicInteger()
	var wCtr: AtomicInteger = new AtomicInteger()
	var rdCtr: AtomicInteger = new AtomicInteger()
	var mCtr: AtomicInteger = new AtomicInteger()

	var users: CopyOnWriteArrayList[User] = new CopyOnWriteArrayList()
	var postStore: ConcurrentHashMap[String, Posts] = new ConcurrentHashMap()
	var msgStore: ConcurrentHashMap[String, Messages] = new ConcurrentHashMap()

	def main(args: Array[String]){
		// create an actor system
		val system = ActorSystem("FacebookServer", ConfigFactory.load(ConfigFactory.parseString("""{ "akka" : { "actor" : { "provider" : "akka.remote.RemoteActorRefProvider" }, "remote" : { "enabled-transports" : [ "akka.remote.netty.tcp" ], "netty" : { "tcp" : { "port" : 12000 , "maximum-frame-size" : 12800000b } } } } } """)))		
	}

	object Server {
		case class CreateUser(uName: String, dob: String, email: String, pass: String)
		case class LoginUser(uName: String, pass: String)
		case class AddPost(userId: Int, time: Long, msg: String)
		case class AddMsg(sId: Int, time: Long, msg: String, rId: Int)
		case class SendMessages(userId: Int)
		case class SendNewsfeed(userId: Int)
		case class SendTimeline(userId: Int)
		case class SendFriends(userId: Int)
		case class SendUserProfile(userId: Int)
	}

	class Server extends Actor {
		import Server._
		import context._

		def checkUser(uName: String): Boolean = {
			var itr = users.iterator
			while(itr.hasNext){
				var tmp = itr.next()
				if(tmp.userName == uName){
					return true
				}
			}

			return false
		}

		def findUser(uName: String): User = {
			var itr = users.iterator
			while(itr.hasNext){
				var tmp = itr.next()
				if(tmp.userName == uName){
					return tmp
				}
			}
			return null
		}

		// Receive block for the server.
		final def receive = LoggingReceive {
			case CreateUser(uName, dob, email, pass) =>
				if(checkUser(uName)){
						sender ! -1
					} else {
						var newUserId = wCtr.addAndGet(1)
						var newUser = new User(newUserId, uName, dob, email, pass)
						users.add(newUser)

						// return the userId back
						sender ! newUser
					}
				

			case LoginUser(uName, pass) =>
				var user = findUser(uName)
				if(user != null){
					if(user.password == pass){
						sender ! user
					} else {
						sender ! -1
					}
				} else {
					sender ! -1
				}
				

			case AddPost(userId, time, msg) =>
				var regexTags = "@[a-zA-Z0-9]+\\s*".r
				var regexHashtags = "#[a-zA-Z0-9]+\\s*".r
				var postId = wCtr.addAndGet(1).toString // generate postId
				var newPost = new Posts(postId, userId, msg, time)

				// extract tags and store in post object
				var itr = regexTags.findAllMatchIn(msg)
				while(itr.hasNext){
					newPost.tags += itr.next().toString.trim
				}

				// extract all hastags and store in post object
				itr = regexHashtags.findAllMatchIn(msg)
				while(itr.hasNext){
					newPost.hashtags  += itr.next().toString.trim
				}

				postStore.put(postId, newPost)

				var friends = users.get(userId).friends.iterator()
				while(friends.hasNext()){
					users.get(friends.next()).newsfeed.add(postId)
				}
				users.get(userId).timeline.add(postId)
				sender ! postId

			case AddMsg(sId, time, msg, rId) =>
				var mid = mCtr.addAndGet(1).toString
				var newMsg = new Messages(sId, rId, mid, msg, time)

				msgStore.put(mid, newMsg)
				sender ! mid

			case SendMessages(userId) =>
				rdCtr.addAndGet(1)
				var msgIds = users.get(userId).messages
				var msgs: ArrayBuffer[Messages] = ArrayBuffer.empty
				var itr = msgIds.iterator
				while (itr.hasNext) {
					msgs += msgStore.get(itr.next())
				}
				sender ! msgs.toList

			case SendNewsfeed(userId) =>
				rdCtr.addAndGet(1)
				var postIds = users.get(userId).newsfeed
				var posts: ArrayBuffer[Posts] = ArrayBuffer.empty
				var itr = postIds.iterator
				while(itr.hasNext()) {
					posts += postStore.get(itr.next())
				}
				sender ! posts.toList

			case SendTimeline(userId) =>
				rdCtr.addAndGet(1)
				var postIds = users.get(userId).timeline
				var posts: ArrayBuffer[Posts] = ArrayBuffer.empty
				var itr = postIds.iterator()
				while(itr.hasNext()) {
					posts += postStore.get(itr.next())
				}
				sender ! posts.toList

			case SendFriends(userId) =>
				rdCtr.addAndGet(1)
				var idList = users.get(userId).friends
				var friends: ArrayBuffer[sample] = ArrayBuffer.empty
				var itr = idList.iterator()
				while(itr.hasNext()){
					var obj = users.get(itr.next())
					friends += sample(obj.userId, obj.userName, obj.timeline.size(), obj.friends.size())
				}
				sender != friends.toList

			case SendUserProfile(userId) =>
				rdCtr.addAndGet(1)
				var obj = users.get(userId)
				var profile: ArrayBuffer[sample] = ArrayBuffer.empty
				profile += sample(obj.userId, obj.userName, obj.timeline.size(), obj.friends.size())
				sender != profile.toList

			case _ => println("ERROR : Server Receive : Invalid Case")
		}
	}

}