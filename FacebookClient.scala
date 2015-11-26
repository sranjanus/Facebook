package com.FacebookApp

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
	var Ip: String = ""
	var port = 8080

	def main(args: Array[String]) {
		// exti if arguments not passed as command line param
		if(args.length < 2){
			println("Invalid no. of arguments")
			System.exit(1)
		} else {
			Ip = args(0)
			port = args(1).toInt

			println("Ip = " + Ip)
			println("port = " + port)

			// create actor system
			val system = ActorSystem("FacebookClient", ConfigFactory.load(ConfigFactory.parseString("""{ "akka" : { "actor" : { "provider" : "akka.remote.RemoteActorRefProvider" }, "remote" : { "enabled-transports" : [ "akka.remote.netty.tcp" ], "netty" : { "tcp" : { "port" : 13000 , "maximum-frame-size" : 12800000b } } } } } """)))

			var node = system.actorOf(Props(new Client()), name = "1")

			// Intialize the client with info 
			node ! Client.Init(port)
			node ! Client.StartClient

		}
	}

	object Client {
		case class Init(portNo: Int)
		case object AccountCreated
		case object LoggedIn
		case object StartClient
		case object ContinueClient
		case object Message
		case object SearchUser
		case object AddFriend
		case object Post
		case object GetMessages
		case object GetNewsfeed
		case object GetTimeline
		case object GetFriends
		case object GetUserProfile
		case object Stop
	}

	class Client() extends Actor {
		import context._
		import Client._

		var myInfo: UserInfo = new UserInfo(-1, "", "", "", "", -1, -1)
		var port = 0

		def CreateAccount(){
			println("Enter Information to create an account------------------------------------------------")
			println("Email : ")
			var email = readLine()
			println("Date of Birth : (MM/DD/YYYY)")
			var dob = readLine()
			println("Username : ")
			var uName = readLine()
			println("Password : ")
			var pass = readLine()

			val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
          	val request = HttpRequest(method = POST, uri = "http://" + Ip + ":" + port + "/createAccount", entity = HttpEntity(ContentTypes.`application/json`, UserInfo(-1, uName, dob, email, pass, -1, -1).toJson.toString))
          	val responseFuture: Future[HttpResponse] = pipeline(request)
          	responseFuture onComplete {
            	case Success(result) =>
            		myInfo = result.entity.data.asString.parseJson.convertTo[UserInfo]
            		self ! AccountCreated
            	case Failure(error) =>
            		println("Error: CreateAccount: Account Not Created!")
          	}
		}

		def Login(){
			println("Log in-------------------------------------------------------------------------------")
			println("Username : ")
			var uName = readLine()
			println("Password : ")
			var pass = readLine()

			val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
          	val request = HttpRequest(method = POST, uri = "http://" + Ip + ":" + port + "/login", entity = HttpEntity(ContentTypes.`application/json`, UserInfo(-1, uName, "", "", pass, -1, -1).toJson.toString))
          	val responseFuture: Future[HttpResponse] = pipeline(request)
          	responseFuture onComplete {
            	case Success(result) =>
            		myInfo = result.entity.data.asString.parseJson.convertTo[UserInfo]
            		self ! LoggedIn
            	case Failure(error) =>
            		println("Error: Log In: Failed!")
          	} 
		}

		def FetchNewsfeed(){
			val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
        	val request = HttpRequest(method = GET, uri = "http://" + Ip + ":" + port + "/newsfeed/" + myInfo.uid)
        	val responseFuture: Future[HttpResponse] = pipeline(request)
        	responseFuture onComplete {
          		case Success(result) =>
            		val posts = result.entity.data.asString.parseJson.convertTo[List[FacebookServer.Posts]]

            		// To do: Show the posts	

          		case Failure(error) =>
			}
		}

		def PostToTimeline(){
			println("Post to Timeline----------------------------------------------------------------------")
			println("Enter a message to post")
			var postMsg = readLine()
			
			val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
			val request = HttpRequest(method = POST, uri = "http://" + Ip + ":" + port + "/login", entity = HttpEntity(ContentTypes.`application/json`, SendPost(myInfo.uid, System.currentTimeMillis(), postMsg).toJson.toString))
			val responseFuture: Future[HttpResponse] = pipeline(request)
			responseFuture onComplete {
				case Success(result) =>
					val posts = result.entity.data.asString.parseJson.convertTo[List[FacebookServer.Posts]]

					// To do: Show the newsfeed

				case Failure(error) => 
			}
		}

		def Message(){
			println("Send a message------------------------------------------------------------------------")
			println("Enter the Id of the Recepient")
			var recepId = readInt()
			println("Enter a message")
			var message = readLine()

			val pipeline: HttpRequest => Future[String] = sendReceive ~> unmarshal[String]
			val request = HttpRequest(method = POST, uri = "http://" + Ip + ":" + port + "/message", entity = HttpEntity(ContentTypes.`application/json`, SendMsg(myInfo.uid, System.currentTimeMillis(), message, recepId).toJson.toString))
			val responseFuture: Future[String] = pipeline(request)
			responseFuture onComplete {
				case Success(result) =>
					println("Notificaiton : Client : Message Sent!");
				case Failure(error) =>
			}
		}

		def SearchUser(){

		}

		def FetchMessages(){

		}

		def ViewTimeline(){

		}

		def ViewAlbums(){

		}

		def CreateAlbum(){

		}

		def AddPhotos(){

		}

		final def receive = LoggingReceive {
			case Init(portNo) =>
				port = portNo

			case StartClient =>
				println("Options : ------------------------------------------------------")
				println("1. Create a new account")
				println("2. Login")
				println("Please enter one of the two options to continue.")

				var opt = readInt()
				if(opt == 1){
					CreateAccount()
				} else if(opt == 2){
					Login()
				} else {
					println("Error: Client: Invalid User Request!")
				}

			case ContinueClient =>
				println("Options : ---------------------------------------------------")
				println("1. View Newsfeed")
				println("2. Search User")
				println("3. Get Messages")
				println("4. View Timeline")
				println("5. View Albums")
				println("6. Create New Album")
				println("7. Add Photos")
				println("8. Post to Timeline")

				var opt = readInt()
				if(opt == 1){
						FetchNewsfeed()
					} else if(opt == 2){
							SearchUser()
						} else if(opt == 3){
								FetchMessages()
							} else if(opt == 4){
									ViewTimeline()
								} else if(opt == 5){
										ViewAlbums()
									} else if(opt == 6){
											CreateAlbum()
										} else if(opt == 7){
												AddPhotos()
											} else if(opt == 8) {
													PostToTimeline()
												} else {
													println("Error: Client: Invalid User Request!")
													}

			case AccountCreated =>
				println("Welcome " + myInfo.uname + "!")
				println()
				self ! ContinueClient

			case LoggedIn =>
				println("Welcome back " + myInfo.uname + "!")
				println()
				self ! ContinueClient

			case _ =>
				println("Error: Client : Invalid Message Received!") 
				
		} 
	}
}