package com.FacebookApp

import java.net.InetAddress

import scala.concurrent.duration.DurationInt

import com.typesafe.config.ConfigFactory

import akka.actor.Actor
import akka.actor.ActorSelection
import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import akka.util.Timeout.durationToTimeout
import spray.can.Http
import spray.http.ContentTypes
import spray.http.HttpEntity
import spray.http.HttpHeader
import spray.http.HttpEntity.apply
import spray.http.HttpMethods.GET
import spray.http.HttpMethods.POST
import spray.http.HttpRequest
import spray.http.HttpResponse
import spray.http.StatusCode.int2StatusCode
import spray.http.Uri
import spray.json.pimpAny
import spray.json.pimpString
import spray.http.ContentTypes
import FacebookServer._

object HttpServer extends JsonFormats {
	def main(args: Array[String]){
		if(args.length < 2){
			println("Wrong number of arguments!!!")
			System.exit(0)
		} else {
			implicit val system = ActorSystem("HttpServer", ConfigFactory.load(ConfigFactory.parseString("""{ "akka" : { "actor" : { "provider" : "akka.remote.RemoteActorRefProvider" }, "remote" : { "enabled-transports" : [ "akka.remote.netty.tcp" ], "netty" : { "tcp" : { "port" : 11000 , "maximum-frame-size" : 12800000b } } } } } """)));
			var Ip = args(0)

			var watcher = system.actorOf(Props(new Watcher()), name = "Watcher")

	    	watcher ! FacebookServer.Watcher.Init
	    	val server = system.actorSelection("user/Watcher/Router")
			// val server = system.actorSelection("akka.tcp://FacebookServer@" + Ip + ":12000/user/Watcher/Router")
			val ipAddress = InetAddress.getLocalHost.getHostAddress()
			implicit val timeout: Timeout = 10.second // for the actor 'asks'

			for(i  <- 0 to args(1).toInt - 1){
				val handler = system.actorOf(Props(new HttpService(server)), name = "requestHandler" + i)
				IO(Http) ? Http.Bind(handler, interface = ipAddress, port = 8080 + i * 4)
			}

		}


	}

	class HttpService(server: ActorSelection) extends Actor {
		implicit val timeout: Timeout = 5.second // for the actor 'asks'
		import context.dispatcher

		def receive = {
			case _: Http.Connected => sender ! Http.Register(self)

			//Working end points
			case HttpRequest(POST, Uri.Path("/addPicture"), header1:List[HttpHeader], entity: HttpEntity.NonEmpty, _) =>

				val info = entity.data.asString.parseJson.convertTo[SendAddPicture]
				var client = sender
				val result = (server ? FacebookServer.Server.AddPicture(info.userId, info.picture)).mapTo[String]
				result onSuccess {
					case result =>
						println(result)
						client ! HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, result.toString))
				}

			case HttpRequest(POST, Uri.Path("/postComment"), header1:List[HttpHeader], entity: HttpEntity.NonEmpty, _) =>

				val info = entity.data.asString.parseJson.convertTo[SendComment]
				var client = sender
				val result = (server ? FacebookServer.Server.PostComment(info.userId, info.message,info.postId)).mapTo[String]
				result onSuccess {
					case result =>
						println(result)
						client ! HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, result.toString))
				}

			case HttpRequest(POST, Uri.Path("/likePage"), header1:List[HttpHeader], entity: HttpEntity.NonEmpty, _) =>

				val info = entity.data.asString.parseJson.convertTo[SendLikePage]
				var client = sender
				val result = (server ? FacebookServer.Server.LikePage(info.userId, info.pageId, info.time)).mapTo[String]
				result onSuccess {
					case result =>
						println(result)
						client ! HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, result.toString))
				}

			case HttpRequest(POST, Uri.Path("/likePost"), header1:List[HttpHeader], entity: HttpEntity.NonEmpty, _) =>

				val info = entity.data.asString.parseJson.convertTo[SendLikePost]
				var client = sender
				val result = (server ? FacebookServer.Server.LikePost(info.userId, info.postId, info.time)).mapTo[String]
				result onSuccess {
					case result =>
						println(result)
						client ! HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, result.toString))
				}
			case HttpRequest(POST, Uri.Path("/createAccount"), header1:List[HttpHeader], entity: HttpEntity.NonEmpty, _) =>

				val info = entity.data.asString.parseJson.convertTo[UserInfo]
				var client = sender
				val result = (server ? FacebookServer.Server.CreateUser(info.uname, info.dob, info.email,info.key)).mapTo[String]
				result onSuccess {
					case result =>
						println(result)
						client ! HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, result))
				}

			case HttpRequest(POST, Uri.Path("/sendFriendRequest"), header1:List[HttpHeader], entity: HttpEntity.NonEmpty, _) =>

				val info = entity.data.asString.parseJson.convertTo[FriendRequest]
				var client = sender
				val result = (server ? FacebookServer.Server.AddFriendRequest(info.senderId, info.recepientId,info.key)).mapTo[String]
				result onSuccess {
					case result =>
						println(result)
						client ! HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, result.toString))
				}

			case HttpRequest(POST, Uri.Path("/acceptFriendRequest"), header1:List[HttpHeader], entity: HttpEntity.NonEmpty, _) =>

				val info = entity.data.asString.parseJson.convertTo[FriendRequest]
				var client = sender
				val result = (server ? FacebookServer.Server.AcceptFriendRequest(info.senderId, info.recepientId,info.key)).mapTo[String]
				result onSuccess {
					case result =>
						println(result)
						client ! HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, result.toString))
				}

			case HttpRequest(POST, Uri.Path("/createPage"), header1:List[HttpHeader], entity: HttpEntity.NonEmpty, _) =>

				val info = entity.data.asString.parseJson.convertTo[CreatePageInfo]
				var client = sender
				val result = (server ? FacebookServer.Server.CreatePage(info.name,info.details,info.createrId)).mapTo[String]
				result onSuccess {
					case result =>
						println(result)
						client ! HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, result.toString))
				}

			case HttpRequest(GET, Uri.Path(path), _, _, _) if path startsWith "/album" =>
				var id = path.split("/").last.toString
				println(id);
				var client = sender
				val result = (server ? FacebookServer.Server.GetAlbum(id)).mapTo[String]
				println("-- "+id);
				result onSuccess {
					case result =>
						println(result);
						val body = HttpEntity(ContentTypes.`application/json`, result)
						client ! HttpResponse(entity = body)
				}

			case HttpRequest(GET, Uri.Path(path), _, _, _) if path startsWith "/postDetails" =>
				var id = path.split("/").last.toString
				println(id);
				var client = sender
				val result = (server ? FacebookServer.Server.GetPostDetails(id)).mapTo[String]
				println("-- "+id);
				result onSuccess {
					case result =>
						println(result);
						val body = HttpEntity(ContentTypes.`application/json`, result)
						client ! HttpResponse(entity = body)
				}


			case HttpRequest(GET, Uri.Path(path), _, _, _) if path startsWith "/pages" =>
				var id = path.split("/").last.toString
				var client = sender
				val result = (server ? FacebookServer.Server.GetPages(id)).mapTo[String]
				result onSuccess {
					case result =>
						println(result);
						val body = HttpEntity(ContentTypes.`application/json`, result)
						client ! HttpResponse(entity = body)
				}

			case HttpRequest(GET, Uri.Path(path), _, _, _) if path startsWith "/user" =>
				var id = path.split("/").last.toString
				println(id);
				var client = sender
				val result = (server ? FacebookServer.Server.SendUserProfile(id)).mapTo[String]
				println("-- "+id);
				result onSuccess {
					case result =>
						println(result);
						val body = HttpEntity(ContentTypes.`application/json`, result)
						client ! HttpResponse(entity = body)
				}

			case HttpRequest(POST, Uri.Path("/post"), _, entity: HttpEntity.NonEmpty, _) =>
				val post = entity.data.asString.parseJson.convertTo[SendPost]
				var client = sender
				val result = (server ? FacebookServer.Server.AddPost(post.userId, post.time, post.msg)).mapTo[String]
				result onSuccess {
					case result =>
						val body = HttpEntity(ContentTypes.`application/json`, result)
						client ! HttpResponse(entity = body)
				}

			case HttpRequest(POST, Uri.Path("/pagePost"), _, entity: HttpEntity.NonEmpty, _) =>
				val post = entity.data.asString.parseJson.convertTo[SendPagePost]
				var client = sender
				val result = (server ? FacebookServer.Server.PagePost(post.pageId, post.time, post.msg)).mapTo[String]
				result onSuccess {
					case result =>
						val body = HttpEntity(ContentTypes.`application/json`, result)
						client ! HttpResponse(entity = body)
				}

			case HttpRequest(GET, Uri.Path(path), _, _, _) if path startsWith "/pagePosts" =>
				var id = path.split("/").last.toString
				var client = sender
				val result = (server ? FacebookServer.Server.GetPagePosts(id.toString)).mapTo[String]
				result onSuccess {
					case result =>
						val body = HttpEntity(ContentTypes.`application/json`, result)
						client ! HttpResponse(entity = body)
				} 	

			case HttpRequest(GET, Uri.Path(path), _, _, _) if path startsWith "/page" =>
				var id = path.split("/").last.toString
				var client = sender
				val result = (server ? FacebookServer.Server.PageInfo(id.toString)).mapTo[String]
				result onSuccess {
					case result =>
						val body = HttpEntity(ContentTypes.`application/json`, result)
						client ! HttpResponse(entity = body)
				} 		

			case HttpRequest(GET, Uri.Path(path), _, _, _) if path startsWith "/newsfeed" =>
				var id = path.split("/").last.toString
				var client = sender
				val result = (server ? FacebookServer.Server.SendNewsfeed(id)).mapTo[String]
				result onSuccess {
					case result =>
						val body = HttpEntity(ContentTypes.`application/json`, result)
						client ! HttpResponse(entity = body)
				} 

			case HttpRequest(GET, Uri.Path(path), _, _, _) if path startsWith "/timeline" =>
				var id = path.split("/").last.toString
				var client = sender
				val result = (server ? FacebookServer.Server.SendTimeline(id)).mapTo[String]
				result onSuccess {
					case result =>
						val body = HttpEntity(ContentTypes.`application/json`, result)
						client ! HttpResponse(entity = body)
				}
				
			case HttpRequest(GET, Uri.Path(path), _, _, _) if path startsWith "/friends" =>
				var id = path.split("/").last.toString
				var client = sender
				val result = (server ? FacebookServer.Server.SendFriends(id)).mapTo[String]
				result onSuccess {
					case result =>
						val body = HttpEntity(ContentTypes.`application/json`, result)
						client ! HttpResponse(entity = body)
				}


			case HttpRequest(GET, Uri.Path("/"), _, _, _) =>
				val body  = HttpEntity(ContentTypes.`application/json`, "OK")
				sender ! HttpResponse(entity = body)

			//------------------------Not working
			case HttpRequest(GET, Uri.Path(path), _, _, _) if path startsWith "/msg" =>
				var id = path.split("/").last.toString
				var client = sender
				val result = (server ? FacebookServer.Server.SendMessages(id)).mapTo[List[FacebookServer.Messages]]
				result onSuccess {
					case result: List[FacebookServer.Messages] =>
						val body = HttpEntity(ContentTypes.`application/json`, result.toJson.toString)
						client ! HttpResponse(entity = body)
				}

			case HttpRequest(POST, Uri.Path("/msg"), _, entity: HttpEntity.NonEmpty, _) =>
				val message = entity.data.asString.parseJson.convertTo[SendMsg]
				var client = sender
				val result = (server ? FacebookServer.Server.AddMsg(message.senderId, message.time, message.msg, message.recepientId)).mapTo[String]
				result onSuccess {
					case result =>
						client ! HttpResponse(entity = result)
				}
			case _: HttpRequest => sender ! HttpResponse(status = 404, entity = "Unknown!")
		}
	}
}