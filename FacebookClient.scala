package com.FacebookApp

import java.util.concurrent.ConcurrentLinkedQueue
import java.security._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Random
import scala.util.Success

import com.typesafe.config.ConfigFactory

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Cancellable
import akka.actor.Props
import akka.actor.Terminated
import akka.actor.actorRef2Scala
import akka.event.LoggingReceive
import spray.client.pipelining._
import spray.http.ContentTypes
import spray.http.HttpEntity
import spray.http.HttpMethods.GET
import spray.http.HttpMethods.POST
import spray.http.HttpRequest
import spray.http.HttpResponse
import spray.http.Uri.apply
import spray.json.pimpAny
import spray.json.pimpString
import scala.concurrent.Await
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import akka.util.Timeout
import org.apache.commons.codec.binary.Base64

object FacebookClient extends JsonFormats {
  var ipAddress: String = ""
  var initPort = 8082
  var noOfPorts = 0

  def run(args: Array[String]) {
    // exit if arguments not passed as command line param.
    if (args.length < 5) {
      println("INVALID NO OF ARGS.  USAGE :")
      System.exit(1)
    } else if (args.length == 5) {
      var avgPostsPerSecond = args(1).toInt
      var noOfUsers = args(2).toInt
      ipAddress = args(3)
      noOfPorts = args(4).toInt

      // create actor system and a watcher actor.
      val system = ActorSystem("FacebookClients", ConfigFactory.load(ConfigFactory.parseString("""{ "akka" : { "actor" : { "provider" : "akka.remote.RemoteActorRefProvider" }, "remote" : { "enabled-transports" : [ "akka.remote.netty.tcp" ], "netty" : { "tcp" : { "port" : 8090 , "maximum-frame-size" : 12800000b } } } } } """)))
      
      // creates a watcher Actor.
      val watcher = system.actorOf(Props(new Watcher(noOfUsers, avgPostsPerSecond)), name = "Watcher")
    }
  }
  object Watcher {
    case class Terminate(node: ActorRef)
    case class AddUserId(id: String)
    case object CreateFriendNetwork
  }

  class Watcher(noOfUsers: Int, avgPostsPerSecond: Int) extends Actor {
    import context._
    import Watcher._

    val pdf = new PDF()
    val rnd = new Random
    var serverPublicKey:String = null;
    var PostsPerUser = pdf.exponential(1.0 / 307.0).sample(noOfUsers).map(_.toInt)
    PostsPerUser = PostsPerUser.sortBy(a => a)

    // calculate duration required to produce given posts at a given rate.
    var duration = PostsPerUser.sum / avgPostsPerSecond
    //println(duration)

    // get times for 5% of duration. Duration is relative -> 1 to N
    var percent5 = (duration * 0.05).toInt
    var indexes = ArrayBuffer.empty[Int]
    for (i <- 1 to percent5) {
      var tmp = rnd.nextInt(duration)
      while (indexes.contains(tmp)) {
        tmp = rnd.nextInt(duration)
      }
      indexes += tmp
    }
	
    // keep track of Client Actors.
    var nodesArr = ArrayBuffer.empty[ActorRef]
    var idsArr = ArrayBuffer.empty[String]
    // start running after 10 seconds from currentTime.
    var absoluteStartTime = System.currentTimeMillis() + (10 * 1000)
    // create given number of clients and initialize.
    for (i <- 0 to noOfUsers - 1) {
      var port = initPort + rnd.nextInt(noOfPorts)
      var node = actorOf(Props(new Client()), name = "" + i)

      // Initialize Clients with info like number of Posts, duration of posts, start Time, router address. 
      node ! Client.Init(PostsPerUser(i), duration, indexes, absoluteStartTime, port)
      

      nodesArr += node
      context.watch(node)
    }
    var delay = noOfUsers*100
    if(delay<10000)
    	delay = 10000
    system.scheduler.scheduleOnce(delay milliseconds, self, CreateFriendNetwork)
    var startTime = System.currentTimeMillis()
    // end of constructor

    // Receive block for the Watcher.
    final def receive = LoggingReceive {
      case Terminated(node) =>
        nodesArr -= node
        val finalTime = System.currentTimeMillis()
        // when all actors are down, shutdown the system.
        if (nodesArr.isEmpty) {
          println("Final:" + (finalTime - startTime))
          context.system.shutdown
        }
       case AddUserId(id) =>
       	idsArr += id

       case CreateFriendNetwork =>
       	println("called");
       	for(i <- 0 to noOfUsers - 1){
       		var node = nodesArr(i)
       		for(j <- 0 to noOfUsers/2 - 1){
       			var index = new Random().nextInt(noOfUsers-1)
       			if(idsArr.size>index){
	       			var id = idsArr(index)
	       			node ! Client.SendFriendRequest(id + "")
       			}
      		}
       	}

        for(i <- 0 to noOfUsers - 1){
          system.scheduler.scheduleOnce(200 milliseconds,nodesArr(i),Client.GetFriendRequests)
        }

      case _ => println("FAILED HERE")
    }
  }

  class Event(relative: Int = 0, posts: Int = 0) {
    var relativeTime = relative
    var absTime: Long = 0
    var noOfPosts = posts
  }

  object Client {
    case class Init(avgNoOfPosts: Int, duration: Int, indexes: ArrayBuffer[Int], absoluteTime: Long, port: Int)
    case class Post(noOfPosts: Int)
    case class Msg(rId: String)
    case class SendFriendRequest(rId: String)
    case class AcceptFriendRequest(sId: String, key: String)
    case object GetFriendRequests
    case object GetMessages
    case object GetNewsfeed
    case object GetTimeline
    case object GetFriends
    case object LikePost
    case object CommentPost
    case object Stop
    case class StartReadRequests(absoluteTime: Long)
	case class AddPicture(picture:String)
	case class GetAlbum(userId:String)
	case class SendUserProfile(userId:String)
  }

  class ClientInfo(name: String, dob: String, email: String, pubkey: PublicKey, prvkey: PrivateKey, smkey: String){
  	var userId = ""
  	var uname = name
  	var udob = dob
  	var uemail = email
  	var publickey = pubkey
    var privatekey = prvkey
    var symmkey = smkey
    var token = ""
    var encryptedToken = ""
  }

  class Client(implicit system: ActorSystem) extends Actor {
    import context._
    import Client._

    /* Constructor Started */
    var events = ArrayBuffer.empty[Event]
    val rand = new Random()
    var cancellable: Cancellable = null
    var mCancellable: Cancellable = null
    var ctr = 0
    var endTime: Long = 0
    var port = 0
    var serverPublicKey:String = null
    /* Constructor Ended */

    var clientInfo: ClientInfo = null

    var newsfeedStore = ArrayBuffer.empty[GetPost]

    def genName: String = {
      val r = new scala.util.Random
        val sb = new StringBuilder
        for (i <- 1 to 5) {
            sb.append(r.nextPrintableChar)
        }
        sb.toString
    }

    def Initialize(avgNoOfPosts: Int, duration: Int, indexes: ArrayBuffer[Int]) {
      val pdf = new PDF()

      // Generate Timeline for posts for given duration. Std. Deviation = Mean/4 (25%),	Mean = PostsPerUser(i)
      var mean = avgNoOfPosts / duration.toDouble
      var postspersecond = pdf.gaussian.map(_ * (mean / 4) + mean).sample(duration).map(a => Math.round(a).toInt)
      var skewedRate = postspersecond.sortBy(a => a).takeRight(indexes.length).map(_ * 2) // double value of 10% of largest values to simulate peaks.
      for (j <- 0 to indexes.length - 1) {
        postspersecond(indexes(j)) = skewedRate(j)
      }
      for (j <- 0 to duration - 1) {
        events += new Event(j, postspersecond(j))
      }
      events = events.filter(a => a.noOfPosts > 0).sortBy(a => a.relativeTime)

      endTime = System.currentTimeMillis() + (duration * 1000)
    }

    def setAbsoluteTime(baseTime: Long) {
      var tmp = events.size
      for (j <- 0 to tmp - 1) {
        events(j).absTime = baseTime + (events(j).relativeTime * 1000)
      }
    }

    def runEvent() {

      if (!events.isEmpty) {
        var tmp = events.head
        var relative = (tmp.absTime - System.currentTimeMillis()).toInt
        if (relative < 0) {
          relative = 0
        }
        events.trimStart(1)
        var temp = Random.nextInt(5)
        system.scheduler.scheduleOnce(relative milliseconds, self, Post(tmp.noOfPosts))
        system.scheduler.scheduleOnce(relative milliseconds, self, GetTimeline)
        system.scheduler.scheduleOnce(relative milliseconds, self, GetNewsfeed)
        system.scheduler.scheduleOnce(relative milliseconds, self, LikePost)

        runEvent()
      } else {
        var relative = (endTime - System.currentTimeMillis()).toInt
        if (relative < 0) {
          relative = 0
        }
        system.scheduler.scheduleOnce(relative milliseconds, self, Stop)
      }
    }

    def generatePost(): String = {
      return rand.nextString(rand.nextInt(140))
    }

    def generateUserInfo(): ClientInfo = {
    	var nuserName = genName
    	var ndateOfBirth = System.currentTimeMillis().toString
    	var nemail = rand.nextString(rand.nextInt(250)) + "@" + rand.nextString(rand.nextInt(250)) + ".com"
    	var keypair = RSA.generateKey


    	var tmp = new ClientInfo(nuserName, ndateOfBirth, nemail, keypair.getPublic, keypair.getPrivate, "")

    	return tmp
    }

    def CreateAccount(absTime: Long) = {
    	clientInfo = generateUserInfo()
    	val pipeline: HttpRequest => Future[String] = sendReceive ~> unmarshal[String]
    	val request = HttpRequest(method = POST, uri = "http://" + ipAddress + ":" + port + "/createAccount", 
    		entity = HttpEntity(ContentTypes.`application/json`, 
    			UserInfo(clientInfo.uname, clientInfo.udob, clientInfo.uemail, 
    				RSA.encodePublicKey(clientInfo.publickey)).toJson.toString))

    	val responseFuture: Future[String] = pipeline(request)
    		responseFuture onComplete {
    			case Success(result) =>
    				var resp = result.parseJson.convertTo[Response]
    				println("Account Creation: " + resp.status + ": " + resp.message)
    				if(resp.status == "SUCCESS"){
    					clientInfo.userId = resp.id
              			clientInfo.token = RSA.decrypt(resp.message.substring(6),clientInfo.privatekey)
              			clientInfo.encryptedToken = RSA.encrypt(clientInfo.token,clientInfo.privatekey)
              			clientInfo.symmkey = AES.generateKey
    					system.actorSelection("akka.tcp://FacebookClients@" + ipAddress + ":8090/user/Watcher") ! Watcher.AddUserId(resp.id)
    					println("User " + resp.id + " created!!!")
              			self ! AddPicture(genName)
              			self ! AddPicture(genName)
              			self ! GetAlbum(resp.id)
        				runEvent()
    				}

    			case Failure(error) =>
    				println("Account Creation: " + error)
    		}
    }

    // Receive block when in Initializing State before Node is Alive.
    final def receive = LoggingReceive {
      case Init(avgNoOfPosts, duration, indexes, absoluteTime, portNo) =>
        Initialize(avgNoOfPosts, duration, indexes)
        port = portNo
        setAbsoluteTime(absoluteTime)
        val pipeline: HttpRequest => Future[String] = sendReceive ~> unmarshal[String]
		val request = HttpRequest(method = GET, uri = "http://" + ipAddress + ":"+initPort + "/getPublicKey")
		val responseFuture: Future[String] = pipeline(request)
		responseFuture onComplete {
		case Success(result) =>
			serverPublicKey = result
			//println("******  "+serverPublicKey)
			CreateAccount(absoluteTime)
		}

      case AddPicture(oritinalPic) =>
      	  println("Pic added: "+oritinalPic)
          val (encryptedPicture, encodedIv) = AES.encrypt(clientInfo.symmkey, oritinalPic)
      	  val pipeline: HttpRequest => Future[String] = (addHeader("userid", clientInfo.userId) ~> addHeader("token", clientInfo.encryptedToken) ~> sendReceive ~> unmarshal[String])
          val request = HttpRequest(method = POST, uri = "http://" + ipAddress + ":" + port + "/addPicture", entity = HttpEntity(ContentTypes.`application/json`, 
          	SendAddPicture(clientInfo.userId, encryptedPicture,encodedIv).toJson.toString))
          val responseFuture: Future[String] = pipeline(request)
          responseFuture onComplete {
            case Success(result) =>
            	var resp = result.parseJson.convertTo[Response]
    				  println("Adding pic: " + result)
    				if(resp.status == "SUCCESS"){
    					self ! GetAlbum(clientInfo.userId)
    				}
            case Failure(error) =>
    				println("Adding Post: " + error)
          }
      case GetAlbum(userId) => 
        val pipeline: HttpRequest => Future[HttpResponse] = (addHeader("userid", clientInfo.userId) ~> addHeader("token", clientInfo.encryptedToken) ~> sendReceive)
        val request = HttpRequest(method = GET, uri = "http://" + ipAddress + ":" + port + "/album/" + userId)
        val responseFuture: Future[HttpResponse] = pipeline(request)
        responseFuture onComplete {
          case Success(result) =>
            var album = result.entity.data.asString.parseJson.convertTo[SendPicture]
            var iter = album.picture.iterator
            while(iter.hasNext){
              var picture = iter.next()
              println("before: "+picture.pic)
              if(userId == clientInfo.userId){
                println(clientInfo.symmkey)
              	println("After: "+new String(Base64.decodeBase64(AES.decrypt(clientInfo.symmkey, picture.pic, picture.iv))))
              }else{
              	println("After: "+new String(Base64.decodeBase64(AES.decrypt(RSA.decrypt(album.key,clientInfo.privatekey), picture.pic, picture.iv))))
              }
            }
          case Failure(error) =>
          	println("Fetching Newsfeed: " + error)
        }


      case Post(noOfPosts: Int) =>
        for (j <- 1 to noOfPosts) {

          val (encryptedPost, encodedIv) = AES.encrypt(clientInfo.symmkey, generatePost())
          val pipeline: HttpRequest => Future[String] = (addHeader("userid", clientInfo.userId) ~> addHeader("token", clientInfo.encryptedToken) ~> sendReceive ~> unmarshal[String])
          val request = HttpRequest(method = POST, uri = "http://" + ipAddress + ":" + port + "/post", entity = HttpEntity(ContentTypes.`application/json`, SendPost(clientInfo.userId, System.currentTimeMillis(), encryptedPost,encodedIv).toJson.toString))
          val responseFuture: Future[String] = pipeline(request)
          responseFuture onComplete {
            case Success(result) =>
            	var resp = result.parseJson.convertTo[Response]
    				  println("Adding Post: " + result)
    				if(resp.status == "SUCCESS"){
    				  //println("Post " + resp.id + " created by " + id + "!!!")
    				}
            case Failure(error) =>
    				println("Adding Post: " + error)
          }
        }
       
      case SendFriendRequest(rId: String) =>

        val pipeline: HttpRequest => Future[HttpResponse] = (addHeader("userid", clientInfo.userId) ~> addHeader("token", clientInfo.encryptedToken) ~> sendReceive )
        val request = HttpRequest(method = GET, uri = "http://" + ipAddress + ":" + port + "/user/" + rId)
        val responseFuture: Future[HttpResponse] = pipeline(request)
        responseFuture onComplete {
          case Success(result) =>
          	if(!result.entity.data.asString.contains("FAILED")){
	            var user = result.entity.data.asString.parseJson.convertTo[UserProfile]
	            var encryptedSkey = RSA.encrypt(clientInfo.symmkey,user.key)
	            val pipeline1: HttpRequest => Future[String] = (addHeader("userid", clientInfo.userId) ~> addHeader("token", clientInfo.encryptedToken) ~> sendReceive ~> unmarshal[String])
				val request1 = HttpRequest(method = POST, uri = "http://" + ipAddress + ":" + port + "/sendFriendRequest", 
					entity = HttpEntity(ContentTypes.`application/json`, 
						FriendRequest(clientInfo.userId, rId, encryptedSkey).toJson.toString))
				val responseFuture1: Future[String] = pipeline1(request1)
				responseFuture1 onComplete {
					case Success(result1) =>
					var resp = result1.parseJson.convertTo[Response]
					println("Sending Friend Request: " + resp.status + ": " + resp.message)
					if(resp.status == "SUCCESS"){
					 println("Friend request sent!!")
					}
					case Failure(error) =>
					println("Sending Friend Request : " + error)
				}
			}
          case Failure(error) =>
          	println("Fetching Newsfeed: " + error)
        }



      case AcceptFriendRequest(sId: String, key: String) =>
        val pipeline: HttpRequest => Future[String] = (addHeader("userid", clientInfo.userId) ~> addHeader("token", clientInfo.encryptedToken) ~> sendReceive ~> unmarshal[String])
      	val request = HttpRequest(method = POST, uri="http://" + ipAddress + ":" + port + "/acceptFriendRequest", 
      		entity = HttpEntity(ContentTypes.`application/json`, 
      			FriendRequest(clientInfo.userId, sId, RSA.encrypt(clientInfo.symmkey,key)).toJson.toString))
      	val responseFuture: Future[String] = pipeline(request)
      	responseFuture onComplete {
      		case Success(result) =>
            var resp = result.parseJson.convertTo[Response]
    				  println("Accepting Friend Request: " + resp.status + ": " + resp.message)
    				if(resp.status == "SUCCESS"){
    					 println("Friend request accepted!!")
    					 self ! GetAlbum(sId)
    				}
            case Failure(error) =>
    				println("Accepting Friend Request: " + error)
      	}


      case LikePost =>
        println("newsfeedStore.size = " + newsfeedStore.size)
        if(newsfeedStore.size > 0){
          var randomIndex = rand.nextInt(newsfeedStore.size)
          var post = newsfeedStore(randomIndex)
          val pipeline: HttpRequest => Future[String] = (addHeader("userid", clientInfo.userId) ~> addHeader("token", clientInfo.encryptedToken) ~> sendReceive ~> unmarshal[String])
          val request = HttpRequest(method = POST, uri="http://" + ipAddress + ":" + port + "/likePost", entity = HttpEntity(ContentTypes.`application/json`, SendLikePost(clientInfo.userId, post.postId, System.currentTimeMillis).toJson.toString))
          val responseFuture: Future[String] = pipeline(request)
          responseFuture onComplete {
              case Success(result) =>
                var resp = result.parseJson.convertTo[Response]
                println("Liking Post: " + resp.status + ": " + resp.message)
                if(resp.status == "SUCCESS"){
                  println("Post Liked!!")
                }
              case Failure(error) =>
                println("Liking Post: " + error)
            }
        }
     
      case GetMessages =>
        val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
        val request = HttpRequest(method = GET, uri = "http://" + ipAddress + ":" + port + "/msg/" + clientInfo.userId)
        val responseFuture: Future[HttpResponse] = pipeline(request)
        responseFuture onComplete {
          case Success(result) =>
            val messages = result.entity.data.asString.parseJson.convertTo[List[FacebookServer.Messages]]
          case Failure(error) =>
          	println(error)
        }

      case GetNewsfeed =>

        val pipeline: HttpRequest => Future[HttpResponse] = (addHeader("userid", clientInfo.userId) ~> addHeader("token", clientInfo.encryptedToken) ~> sendReceive)

        val request = HttpRequest(method = GET, uri = "http://" + ipAddress + ":" + port + "/newsfeed/" + clientInfo.userId)
        val responseFuture: Future[HttpResponse] = pipeline(request)
        responseFuture onComplete {
          case Success(result) =>
          	println("Fetching Newsfeed: SUCCESS")
            var newsfeedList = result.entity.data.asString.parseJson.convertTo[List[GetPost]]
            var iter = newsfeedList.iterator
            while(iter.hasNext){
              var post = iter.next()
              newsfeedStore += post
            }
            println("newsfeedStore SIZE = " + newsfeedStore.size)
          case Failure(error) =>
          	println("Fetching Newsfeed: " + error)
        }

      case GetTimeline =>
        val pipeline: HttpRequest => Future[HttpResponse] = (addHeader("userid", clientInfo.userId) ~> addHeader("token", clientInfo.encryptedToken) ~> sendReceive)
        val request = HttpRequest(method = GET, uri = "http://" + ipAddress + ":" + port + "/timeline/" + clientInfo.userId)
        val responseFuture: Future[HttpResponse] = pipeline(request)
        responseFuture onComplete {
          case Success(result) =>
            println("Fetching Timelinea: SUCCESS")
            var timelineList = result.entity.data.asString.parseJson.convertTo[List[GetPost]]
            var iter = timelineList.iterator
            while(iter.hasNext){
              var post = iter.next()
              newsfeedStore += post
            }
            println("newsfeedStore SIZE = " + newsfeedStore.size)
          case Failure(error) =>
            println("Fetching Newsfeed: " + error)
        }

      case GetFriends =>
        val pipeline: HttpRequest => Future[HttpResponse] = (addHeader("userid", clientInfo.userId) ~> addHeader("token", clientInfo.encryptedToken) ~> sendReceive)
        val request = HttpRequest(method = GET, uri = "http://" + ipAddress + ":" + port + "/friends/" + clientInfo.userId)
        val responseFuture: Future[HttpResponse] = pipeline(request)
        responseFuture onComplete {
          case Success(result) =>
            val friends = result.entity.data.asString.parseJson.convertTo[List[UserProfile]]
          case Failure(error) =>
          	println(error)
        }

      case GetFriendRequests =>
        val pipeline: HttpRequest => Future[HttpResponse] = (addHeader("userid", clientInfo.userId) ~> addHeader("token", clientInfo.encryptedToken) ~> sendReceive)
      	val request = HttpRequest(method = GET, uri="http://" + ipAddress + ":" + port + "/getFriendRequests/" + clientInfo.userId)
      	val responseFuture: Future[HttpResponse] = pipeline(request)
      	responseFuture onComplete {
      		case Success(result) =>
      			println("Fetching Friend Requests: SUCCESS")
      			if(!result.entity.data.asString.contains("FAILED")){
	      			val requests = result.entity.data.asString.parseJson.convertTo[List[FriendRequest]]
	      			var iter = requests.iterator
	      			while(iter.hasNext){
	      				var request = iter.next()
	      				self ! AcceptFriendRequest(request.senderId, request.key)
	      			}
	      		}
      		case Failure(error) =>
      			println("Fetching Friend Requests: " + error)
      	}

      case Stop =>
        cancellable.cancel
        mCancellable.cancel
        context.stop(self)

      case _ => println("FAILED")

    }
  }
}