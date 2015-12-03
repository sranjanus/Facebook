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

object FacebookServer extends JsonFormats{
	case class sample(id: Int, name: String, noOfPosts: Int, friendsCount: Int) extends java.io.Serializable

	class User (id: String, uName: String, dob: String, email: String, key: String, tkn: String) {
		var userId = id
		var userName = uName
		var dateOfBirth = dob
		var emailAdd = email
		var publicKey = key
		var token = tkn
		var newsfeed: ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue()
		var timeline: ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue()
		var messages: ArrayBuffer[String] = ArrayBuffer.empty
		var friends: ConcurrentHashMap[String, String] = new ConcurrentHashMap()
		var friendRequests: ConcurrentHashMap[String, String] = new ConcurrentHashMap()
		var pages: ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue()
		var album: ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue()
	}

	class Page( pid : String,pName:String,pDetails:String,pCreaterId:String){
		var pageId = pid
		var name = pName
		var details = pDetails
		var createrId = pCreaterId
		var posts: ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue()
		var likes: ConcurrentHashMap[String,Long] = new ConcurrentHashMap()
	}

	class Posts(pid: String, id: String, post: String, time: Long, tagList: ArrayBuffer[String] = ArrayBuffer.empty, hashtagList: ArrayBuffer[String] = ArrayBuffer.empty){
		var authorId = id
		var message = post
		var timeStamp: Long = time
		var postId = pid
		var tags = tagList
		var hashtags = hashtagList
		var likes: ConcurrentHashMap[String, Long] = new ConcurrentHashMap()
		var comments: ConcurrentLinkedQueue[Comment] = new ConcurrentLinkedQueue()
	}

	class Comment(uid:String,msg:String){
		var userId = uid
		var message = msg
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
	var userIds: AtomicInteger = new AtomicInteger()
	var postIds: AtomicInteger = new AtomicInteger()
	var pageIds: AtomicInteger = new AtomicInteger()

	var users: ConcurrentHashMap[String, User] = new ConcurrentHashMap()
	var postStore: ConcurrentHashMap[String, Posts] = new ConcurrentHashMap()
	var msgStore: ConcurrentHashMap[String, Messages] = new ConcurrentHashMap()
	var pageStore: ConcurrentHashMap[String, Page] = new ConcurrentHashMap()

	object Watcher {
    	case object Init
    	case object Time
  	}

  class Watcher extends Actor {
    import Watcher._
    import context._

    // scheduler to count no. of tweets every 5 seconds.
    var counter = 0
    var cancellable = system.scheduler.schedule(0 seconds, 1 second, self, Time)
    //system.scheduler.schedule(0 seconds, 200 seconds, self, Terminated)

    // Start a router with 30 Actors in the Server.
    var cores = (Runtime.getRuntime().availableProcessors() * 1.5).toInt
    val router = context.actorOf(Props[Server].withRouter(SmallestMailboxPool(cores)), name = "Router")
    // Watch the router. It calls the terminate sequence when router is terminated.
    context.watch(router)

    def Initialize() {
      println("Server started")
    }

    // Receive block for the Watcher.
    final def receive = LoggingReceive {
      case Init =>
        Initialize()
        System.gc()

      case Time =>
        var tmp1 = wCtr.get() - wTPS.sum
        wTPS += (tmp1)
        var tmp2 = rdCtr.get() - rdTPS.sum
        rdTPS += (tmp2)

        println(counter + ": " + tmp1 + " , " + tmp2)
        counter += 1

      case Terminated(ref) =>
        if (ref == router) {
          println(wTPS)
          println(rdTPS)
          system.shutdown
        }

      case _ => println("FAILED HERE")
    	}
  	}

	object Server {
		case class CreateUser(uName: String, dob: String, email: String,key:String)
		case class LoginUser(uName: String, pass: String)
		case class AddPost(userId: String, time: Long, msg: String)
		case class PagePost(userId: String, time: Long, msg: String)
		case class AddMsg(sId: Int, time: Long, msg: String, rId: Int)
		case class SendMessages(userId: String)
		case class SendNewsfeed(userId: String)
		case class SendTimeline(userId: String)
		case class SendFriends(userId: String)
		case class SendUserProfile(userId: String)
		case class AddFriendRequest(userId:String,friendId: String,key:String)
		case class AcceptFriendRequest(userId:String,friendId: String,key:String)
		case class CreatePage(name:String,details:String,createrId:String)
		case class PageInfo(id:String)
		case class GetPagePosts(id:String)
		case class LikePage(userId:String,pageId:String,time:Long)
		case class LikePost(userId:String,postId:String,time:Long)
		case class AddPicture(userId:String,picture:String)
		case class GetAlbum(userId:String)
		case class GetPages(userId:String)
		case class PostComment(userId:String,message:String,postId:String)
		case class GetPostDetails(postId:String)
		case class GetFriendRequests(userId:String)
		case class VerifyToken(userId:String,token:String)
	}

	class Server extends Actor {
		import Server._
		import context._

		def checkUser(uName: String): Boolean = {
			return users.containsKey(uName);
		}

		def findUser(uName: String): User = {
			return users.get(uName)
		}

		def genToken: String = {
			val r = new scala.util.Random
    		val sb = new StringBuilder
    		for (i <- 1 to 10) {
      			sb.append(r.nextPrintableChar)
    		}
    		sb.toString
		}

		def verifyToekn(user:User,token:String):Boolean = {
			var valid = false
			try{
				var temp = RSA.decrypt(token,user.publicKey)
				valid = (temp == user.token)
				if(!valid){
					println(user.token+" -- "+ temp)
				}
			}catch{
				case e:Exception => println(e)
			}
			valid
		}

		// Receive block for the server.
		final def receive = LoggingReceive {

			case VerifyToken(userId,token) => 

				if(users.containsKey(userId) ){
					if(verifyToekn(users.get(userId),token)){
						sender ! "SUCCESS"
					}
					else{
						sender ! Response("FAILED","","Authentication error for user id: "+userId).toJson.toString					
					}
				}else{
					sender ! Response("FAILED","","Invalid user"+userId).toJson.toString					
				}


			case PostComment(userId,message,postId) =>
				if(postStore.containsKey(postId)){
					var post = postStore.get(postId)
					post.comments.add(new Comment(userId,message))
					sender ! Response("SUCCESS","","Comment Added Successfully").toJson.toString					
				}else{
					sender ! Response("FAILED","","Invalid post").toJson.toString					
				}

			case GetPostDetails(postId) =>
				if(postStore.containsKey(postId)){
					var temp = postStore.get(postId)
					var itr = temp.comments.iterator()
					var comments: ArrayBuffer[GetComment] = ArrayBuffer.empty
					while(itr.hasNext()) {
						var t = itr.next()
						comments += GetComment(t.userId,t.message)
					}
					var post = SendPostDetails(temp.postId,temp.authorId,temp.timeStamp,temp.message,temp.likes.size,comments.toList)
					sender ! post.toJson.toString
				}else{
					sender ! Response("FAILED","","Invalid post").toJson.toString					
				}

			case GetAlbum(userId) => 
				if(users.containsKey(userId)){
					var pictures: ArrayBuffer[String] = ArrayBuffer.empty
					var itr = users.get(userId).album.iterator()
					while(itr.hasNext()) {
						var temp = itr.next()
						pictures += temp
					}
					sender ! SendPicture(pictures.toList).toJson.toString					
				}else{
					sender ! Response("FAILED","","Invalid user").toJson.toString					
				}


			case GetPages(userId) => 
				if(users.containsKey(userId)){
					var pages: ArrayBuffer[SendPageInfo] = ArrayBuffer.empty
					var itr = users.get(userId).pages.iterator()
					while(itr.hasNext()) {
						var obj = pageStore.get(itr.next())
						pages += SendPageInfo(obj.pageId, obj.name, obj.details, obj.createrId,obj.likes.size,obj.posts.size)
					}
					sender ! pages.toList.toJson.toString					
				}else{
					sender ! Response("FAILED","","Invalid user").toJson.toString					
				}

			case AddPicture(userId,picture) => 
				if(users.containsKey(userId)){
					users.get(userId).album.add(picture)
					sender ! Response("SUCCESS","","Picture Added Successfully").toJson.toString					
				}else{
					sender ! Response("FAILED","","Invalid user").toJson.toString					
				}

			case GetFriendRequests(userId) =>
				var user = users.get(userId)
				var requests: ArrayBuffer[FriendRequest] = ArrayBuffer.empty
				var fRequests = user.friendRequests
				var iter = fRequests.keySet().iterator()
				while(iter.hasNext()){
					var fId = iter.next()
					var key = fRequests.get(fId)
					var request = FriendRequest(fId, userId, key)
					requests += request
				}

				sender ! requests.toList.toJson.toString


			case LikePost(userId,postId,time) =>
				if(users.containsKey(userId)&&postStore.containsKey(postId)){
					var obj = postStore.get(postId)
					obj.likes.put(userId,time)
					sender ! Response("SUCCESS","","").toJson.toString					
				}else{
					sender ! Response("FAILED","","Invalid user or post").toJson.toString					
				}

			case LikePage(userId,pageId,time) =>
				if(users.containsKey(userId)&&pageStore.containsKey(pageId)){
					var obj = pageStore.get(pageId)
					obj.likes.put(userId,time)
					sender ! Response("SUCCESS","","").toJson.toString					
				}else{
					sender ! Response("FAILED","","Invalid user or post").toJson.toString					
				}
			//Working cases
			case AddFriendRequest(userId,friendId,key) =>
				var friend = users.get(friendId)
				var user = users.get(userId)
				if(user!=null&&friend!=null){
					friend.friendRequests.put(userId,key)
					sender ! Response("SUCCESS","","").toJson.toString					
				}else{
					sender ! Response("FAILED","","Invalid user").toJson.toString					
				}

			case AcceptFriendRequest(userId,friendId,key) =>
				var friend = users.get(friendId)
				var user = users.get(userId)
				
				if(user!=null&&friend!=null&&user.friendRequests.containsKey(friendId+"")){
					friend.friends.put(userId,key)
					user.friends.put(friendId,user.friendRequests.get(friendId+""))
					user.friendRequests.remove(friendId)
					sender ! Response("SUCCESS","","").toJson.toString					
				}else{
					sender ! Response("FAILED","","Invalid user").toJson.toString					
				}

			case CreateUser(uName, dob, email,key) =>
				wCtr.addAndGet(1)
				var newUserId = userIds.addAndGet(1).toString
				var newUser = new User(newUserId+"", uName, dob, email,key, genToken)
				users.put(newUserId+"",newUser)
				var encryptedToken = RSA.encrypt(newUser.token, newUser.publicKey)
				sender ! Response("SUCCESS",newUserId+"","token:" + encryptedToken).toJson.toString			


			case SendUserProfile(userId) =>
				if(users.containsKey(userId)){
					rdCtr.addAndGet(1)
					var obj = users.get(userId+"")
					var userProfile = UserProfile(userId+"",obj.userName, obj.dateOfBirth, obj.emailAdd)
					var json = userProfile.toJson.toString
					sender ! json
				}else{
					sender ! Response("FAILED","","Invalid user").toJson.toString					
				}

			case SendFriends(userId) =>
				if(users.containsKey(userId)){
					rdCtr.addAndGet(1)
					var idList = users.get(userId+"").friends.keySet()
					var friends: ArrayBuffer[UserProfile] = ArrayBuffer.empty
					var itr = idList.iterator()
					while(itr.hasNext()){
						var obj = users.get(itr.next())
						friends += UserProfile(obj.userId, obj.userName, obj.dateOfBirth,obj.emailAdd)
					}
					sender ! friends.toList.toJson.toString
				}else{
					sender ! Response("FAILED","","Invalid user").toJson.toString					
				}

			case PageInfo(pageId) =>
				if(pageStore.containsKey(pageId)){
					rdCtr.addAndGet(1)
					var obj = pageStore.get(pageId+"")
					var page = SendPageInfo(pageId, obj.name, obj.details, obj.createrId,obj.likes.size,obj.posts.size)
					var json = page.toJson.toString
					sender ! json
				}else{
					sender ! Response("FAILED","","Invalid page").toJson.toString					
				}

			case GetPagePosts(pageId) =>
				if(pageStore.containsKey(pageId)){
					rdCtr.addAndGet(1)
					var obj = pageStore.get(pageId+"")
					var posts: ArrayBuffer[GetPost] = ArrayBuffer.empty
					var itr = obj.posts.iterator()
					while(itr.hasNext()) {
						var temp = postStore.get(itr.next())
						posts += GetPost(temp.postId,temp.authorId,temp.timeStamp,temp.message,temp.likes.size,temp.comments.size)
					}
					sender ! posts.toList.toJson.toString
				}else{
					sender ! Response("FAILED","","Invalid page").toJson.toString					
				}

			case SendNewsfeed(userId) =>
				if(users.containsKey(userId)){
					rdCtr.addAndGet(1)
					var postIds = users.get(userId+"").newsfeed
					var posts: ArrayBuffer[GetPost] = ArrayBuffer.empty
					var itr = postIds.iterator()
					while(itr.hasNext()) {
						var temp = postStore.get(itr.next())
						posts += GetPost(temp.postId,temp.authorId,temp.timeStamp,temp.message,temp.likes.size,temp.comments.size)
					}
					sender ! posts.toList.toJson.toString
				}else{
					sender ! Response("FAILED","","Invalid user").toJson.toString					
				}

			case SendTimeline(userId) =>
				if(users.containsKey(userId)){
					rdCtr.addAndGet(1)
					var postIds = users.get(userId+"").timeline
					var posts: ArrayBuffer[GetPost] = ArrayBuffer.empty
					var itr = postIds.iterator()
					while(itr.hasNext()) {
						var temp = postStore.get(itr.next())
						posts += GetPost(temp.postId,temp.authorId,temp.timeStamp,temp.message,temp.likes.size,temp.comments.size)
					}
					sender ! posts.toList.toJson.toString
				}else{
					sender ! Response("FAILED","","Invalid user").toJson.toString					
				}
			case AddPost(userId, time, msg) =>
				if(users.containsKey(userId)){
					var regexTags = "@[a-zA-Z0-9]+\\s*".r
					var regexHashtags = "#[a-zA-Z0-9]+\\s*".r
					wCtr.addAndGet(1).toString // generate postId
					var postId = postIds.addAndGet(1).toString
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

					var friends = users.get(userId).friends.keySet().iterator()
					while(friends.hasNext()){
						users.get(friends.next()).newsfeed.add(postId)
					}
					users.get(userId).timeline.add(postId)
					sender ! Response("SUCCESS",postId+"","").toJson.toString
				
				}else{
					sender ! Response("FAILED","","Invalid user").toJson.toString					
				}
			case PagePost(pageId, time, msg) =>
				if(pageStore.containsKey(pageId)){

					var regexTags = "@[a-zA-Z0-9]+\\s*".r
					var regexHashtags = "#[a-zA-Z0-9]+\\s*".r
					wCtr.addAndGet(1).toString // generate postId
					var postId = postIds.addAndGet(1).toString
					var newPost = new Posts(postId, pageId, msg, time)

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

					var friends = pageStore.get(pageId).likes.keySet().iterator()
					while(friends.hasNext()){
						users.get(friends.next()).newsfeed.add(postId)
					}
					pageStore.get(pageId).posts.add(postId)
					sender ! Response("SUCCESS",postId+"","").toJson.toString
				}else{
					sender ! Response("FAILED","","Invalid page").toJson.toString					
				}
			case CreatePage(name,details,createrId) =>
				if(users.containsKey(createrId)){
					wCtr.addAndGet(1).toString // generate postId
					var pageId = pageIds.addAndGet(1).toString
					pageStore.put(pageId,new Page(pageId,name,details,createrId))
					users.get(createrId).pages.add(pageId)
					sender ! Response("SUCCESS",pageId+"","").toJson.toString
				}else{
					sender ! Response("FAILED","","Invalid user").toJson.toString					
				}

			//Not working cases
			case AddMsg(sId, time, msg, rId) =>
				var mid = mCtr.addAndGet(1).toString
				var newMsg = new Messages(sId, rId, mid, msg, time)

				msgStore.put(mid, newMsg)
				sender ! mid

			case SendMessages(userId) =>
				rdCtr.addAndGet(1)
				var msgIds = users.get(userId+"").messages
				var msgs: ArrayBuffer[Messages] = ArrayBuffer.empty
				var itr = msgIds.iterator
				while (itr.hasNext) {
					msgs += msgStore.get(itr.next())
				}
				sender ! msgs.toList


			case _ => println("ERROR : Server Receive : Invalid Case")
		}
	}
}