package com.FacebookApp

import java.util.concurrent.ConcurrentLinkedQueue

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
import spray.client.pipelining.WithTransformerConcatenation
import spray.client.pipelining.sendReceive
import spray.client.pipelining.sendReceive$default$3
import spray.client.pipelining.unmarshal
import spray.http.ContentTypes
import spray.http.HttpEntity
import spray.http.HttpMethods.GET
import spray.http.HttpMethods.POST
import spray.http.HttpRequest
import spray.http.HttpResponse
import spray.http.Uri.apply
import spray.json.pimpAny
import spray.json.pimpString

object FacebookClient extends JsonFormats {
  var ipAddress: String = ""
  var initPort = 8082
  var noOfPorts = 0
  def main(args: Array[String]) {
    // exit if arguments not passed as command line param.
    if (args.length < 4) {
      println("INVALID NO OF ARGS.  USAGE :")
      System.exit(1)
    } else if (args.length == 4) {
      var avgPostsPerSecond = args(0).toInt
      var noOfUsers = args(1).toInt
      ipAddress = args(2)
      noOfPorts = args(3).toInt

      // create actor system and a watcher actor.
      val system = ActorSystem("FacebookClients", ConfigFactory.load(ConfigFactory.parseString("""{ "akka" : { "actor" : { "provider" : "akka.remote.RemoteActorRefProvider" }, "remote" : { "enabled-transports" : [ "akka.remote.netty.tcp" ], "netty" : { "tcp" : { "port" : 8080 , "maximum-frame-size" : 12800000b } } } } } """)))
      
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
      
      var delay = noOfUsers * 1000
      system.scheduler.scheduleOnce(delay milliseconds, self, CreateFriendNetwork)

      nodesArr += node
      context.watch(node)
    }

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
       	for(i <- 0 to noOfUsers - 1){
       		var node = nodesArr(i)
       		for(j <- 0 to noOfUsers - 1){
       			var id = idsArr(j)
       			node ! Client.SendFriendRequest(id + "")
      		}
       	}

        for(i <- 0 to noOfUsers - 1){
          nodesArr(i) ! Client.GetFriendRequests
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
  }

  class ClientInfo(id: String, name: String, dob: String, email: String, key: String){
  	var userId = id
  	var uname = name
  	var udob = dob
  	var uemail = email
  	var ukey = key
  }

  class Client(implicit system: ActorSystem) extends Actor {
    import context._
    import Client._

    /* Constructor Started */
    var events = ArrayBuffer.empty[Event]
    var id = ""
    val rand = new Random()
    var cancellable: Cancellable = null
    var mCancellable: Cancellable = null
    var ctr = 0
    var endTime: Long = 0
    var port = 0
    /* Constructor Ended */

    var clientInfo = new ClientInfo("", "", "", "", "")

    var newsfeedStore = ArrayBuffer.empty[GetPost]

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
        system.scheduler.scheduleOnce(relative milliseconds, self, Post(tmp.noOfPosts))
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
    	var nid = rand.nextString(rand.nextInt(100))
    	var nuserName = "user" + id
    	var ndateOfBirth = System.currentTimeMillis().toString
    	var nemail = rand.nextString(rand.nextInt(250)) + "@" + rand.nextString(rand.nextInt(250)) + ".com"
    	var nkey = rand.nextString(rand.nextInt(300)) 

    	var tmp = new ClientInfo(nid, nuserName, ndateOfBirth, nemail, nkey)

    	return tmp
    }

    def CreateAccount(absTime: Long) = {
    	var clientInfo = generateUserInfo()
    	val pipeline: HttpRequest => Future[String] = sendReceive ~> unmarshal[String]
    	val request = HttpRequest(method = POST, uri = "http://" + ipAddress + ":" + port + "/createAccount", entity = HttpEntity(ContentTypes.`application/json`, UserInfo(clientInfo.uname, clientInfo.udob, clientInfo.uemail, clientInfo.ukey).toJson.toString))
    	val responseFuture: Future[String] = pipeline(request)
    		responseFuture onComplete {
    			case Success(result) =>
    				var resp = result.parseJson.convertTo[Response]
    				println("Account Creation: " + resp.status + ": " + resp.message)
    				if(resp.status == "SUCCESS"){
    					clientInfo.userId = resp.id
    					id = resp.id
    					system.actorSelection("akka.tcp://FacebookClients@" + ipAddress + ":8080/user/Watcher") ! Watcher.AddUserId(resp.id)
    					println("User " + resp.id + " created!!!")

    					var relative = (absTime - System.currentTimeMillis()).toInt
        				cancellable = system.scheduler.schedule(relative milliseconds, 1 second, self, GetNewsfeed)
                mCancellable = system.scheduler.schedule((relative + 10) milliseconds, 1 second, self, LikePost)
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
        CreateAccount(absoluteTime)

      case Post(noOfPosts: Int) =>
        for (j <- 1 to noOfPosts) {
          val pipeline: HttpRequest => Future[String] = sendReceive ~> unmarshal[String]
          val request = HttpRequest(method = POST, uri = "http://" + ipAddress + ":" + port + "/post", entity = HttpEntity(ContentTypes.`application/json`, SendPost(id, System.currentTimeMillis(), generatePost()).toJson.toString))
          val responseFuture: Future[String] = pipeline(request)
          responseFuture onComplete {
            case Success(result) =>
            	var resp = result.parseJson.convertTo[Response]
    				  println("Adding Post: " + resp.status + ": " + resp.message)
    				if(resp.status == "SUCCESS"){
    				  //println("Post " + resp.id + " created by " + id + "!!!")
    				}
            case Failure(error) =>
    				println("Adding Post: " + error)
          }
        }
        runEvent()

      // case Msg(rId: String) =>
      //   val pipeline: HttpRequest => Future[String] = sendReceive ~> unmarshal[String]
      //   val request = HttpRequest(method = POST, uri = "http://" + ipAddress + ":" + port + "/msg", entity = HttpEntity(ContentTypes.`application/json`, SendMsg(id, System.currentTimeMillis(), generatePost(), rId).toJson.toString))
      //   val responseFuture: Future[String] = pipeline(request)
      //   responseFuture onComplete {
      //     case Success(str) =>
      //     case Failure(error) =>
      //   }

      case SendFriendRequest(rId: String) =>
      	val pipeline: HttpRequest => Future[String] = sendReceive ~> unmarshal[String]
      	val request = HttpRequest(method = POST, uri = "http://" + ipAddress + ":" + port + "/sendFriendRequest", entity = HttpEntity(ContentTypes.`application/json`, FriendRequest(id, rId, clientInfo.ukey).toJson.toString))
      	val responseFuture: Future[String] = pipeline(request)
      	responseFuture onComplete {
      		case Success(result) =>
            	var resp = result.parseJson.convertTo[Response]
    				  println("Sending Friend Request: " + resp.status + ": " + resp.message)
    				if(resp.status == "SUCCESS"){
    					 println("Friend request sent!!")
    				}
            case Failure(error) =>
    				println("Sending Friend Request : " + error)
      	}

      case AcceptFriendRequest(sId: String, key: String) =>
      	val pipeline: HttpRequest => Future[String] = sendReceive ~> unmarshal[String]
      	val request = HttpRequest(method = POST, uri="http://" + ipAddress + ":" + port + "/acceptFriendRequest", entity = HttpEntity(ContentTypes.`application/json`, FriendRequest(id, sId, key).toJson.toString))
      	val responseFuture: Future[String] = pipeline(request)
      	responseFuture onComplete {
      		case Success(result) =>
            var resp = result.parseJson.convertTo[Response]
    				  println("Accepting Friend Request: " + resp.status + ": " + resp.message)
    				if(resp.status == "SUCCESS"){
    					 println("Friend request accepted!!")
    				}
            case Failure(error) =>
    				println("Accepting Friend Request: " + error)
      	}

      case LikePost =>
        println("newsfeedStore.size = " + newsfeedStore.size)
        if(newsfeedStore.size > 0){
          var randomIndex = rand.nextInt(newsfeedStore.size)
          var post = newsfeedStore(randomIndex)
          val pipeline: HttpRequest => Future[String] = sendReceive ~> unmarshal[String]
          val request = HttpRequest(method = POST, uri="http://" + ipAddress + ":" + port + "/likePost", entity = HttpEntity(ContentTypes.`application/json`, SendLikePost(id, post.postId, System.currentTimeMillis).toJson.toString))
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
        val request = HttpRequest(method = GET, uri = "http://" + ipAddress + ":" + port + "/msg/" + id)
        val responseFuture: Future[HttpResponse] = pipeline(request)
        responseFuture onComplete {
          case Success(result) =>
            val messages = result.entity.data.asString.parseJson.convertTo[List[FacebookServer.Messages]]
          case Failure(error) =>
          	println(error)
        }

      case GetNewsfeed =>
        val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
        val request = HttpRequest(method = GET, uri = "http://" + ipAddress + ":" + port + "/newsfeed/" + id)
        val responseFuture: Future[HttpResponse] = pipeline(request)
        responseFuture onComplete {
          case Success(result) =>
          	println("Fetching Newsfeed: SUCCESS")
          	println(result)
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
        val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
        val request = HttpRequest(method = GET, uri = "http://" + ipAddress + ":" + port + "/timeline/" + id)
        val responseFuture: Future[HttpResponse] = pipeline(request)
        responseFuture onComplete {
          case Success(result) =>
            val posts = result.entity.data.asString.parseJson.convertTo[List[GetPost]]
          case Failure(error) =>
          	println(error)
        }

      case GetFriends =>
        val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
        val request = HttpRequest(method = GET, uri = "http://" + ipAddress + ":" + port + "/friends/" + id)
        val responseFuture: Future[HttpResponse] = pipeline(request)
        responseFuture onComplete {
          case Success(result) =>
            val friends = result.entity.data.asString.parseJson.convertTo[List[UserProfile]]
          case Failure(error) =>
          	println(error)
        }

      case GetFriendRequests =>
      	val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
      	val request = HttpRequest(method = GET, uri="http://" + ipAddress + ":" + port + "/getFriendRequests/" + id)
      	val responseFuture: Future[HttpResponse] = pipeline(request)
      	responseFuture onComplete {
      		case Success(result) =>
      			println("Fetching Friend Requests: SUCCESS")
      			val requests = result.entity.data.asString.parseJson.convertTo[List[FriendRequest]]
      			var iter = requests.iterator
      			while(iter.hasNext){
      				var request = iter.next()
      				self ! AcceptFriendRequest(request.senderId, request.key)
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