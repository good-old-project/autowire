import scala.annotation.{compileTimeOnly, Annotation}
import scala.concurrent.Future
import language.experimental.macros

package object autowire {
  case class InputError(ex: Throwable) extends Exception(
    "There was a failure de-serializing input", ex
  )

  object Internal{
    val invalidHandler: PartialFunction[Throwable, Nothing] = {
      case e => throw InputError(e)
    }
    class Wrapper[Wrapped, Result, Reader[_]](implicit val r: Reader[Result])
    object Wrapper {
      implicit def future[Result: Reader, Reader[_]] = new Wrapper[Future[Result], Result, Reader]
      implicit def normal[Result: Reader, Reader[_]] = new Wrapper[Result, Result, Reader]
    }
  }

  /**
   * A `PartialFunction` (usually generated by the [[Macros.route]] macro)
   * that takes in [[Request]] objects and spits out serialized
   * `Future[String]`s in response.
   *
   * It is not concerned with how the [[Request]] objects get to it, or
   * how the marshalled `Future[String]` will be transmitted back to the
   * client: it simply calls the function described by the [[Request]]
   * on the object that it was created with.
   *
   * Being a normal `PartialFunction`, they can be manipulated and chained
   * (e.g. via `orElse` or `andThen`) like `PartialFunction`s normally are.
   */
  type Router = PartialFunction[Request, Future[String]]

  /**
   * A marshalled autowire'd function call.
   *
   * @param path A series of path segments which illustrate which method
   *             to call, typically the fully qualified path of the 
   *             enclosing trait followed by the name of the method
   * @param args Serialized arguments for the method that was called. Kept
   *             as a Map of arg-name -> serialized value. Values which 
   *             exactly match the default value are omitted, and are
   *             simply re-constituted by the receiver.
   */
  case class Request(path: Seq[String], args: Map[String, String])
  implicit class Callable[T](t: T){
    def call(): Future[T] = macro Macros.clientMacro[T]
  }
  case class ClientProxy[Trait, ClientType <: Client](self: ClientType)

  @compileTimeOnly("unwrapClientProxy should not exist at runtime!")
  implicit def unwrapClientProxy[Trait, Reader[_], Writer[_], ClientType <: Client](w: ClientProxy[Trait, ClientType]): Trait = ???
  /**
   * A client to make autowire'd function calls to a particular interface. 
   * A single client can only make calls to one interface, but it's not a 
   * huge deal. Just make a few clients (they can all inherit/delegate the 
   * `callRequest` method) if you want multiple targets.
   */
  trait Client{
    /**
     * Actually makes a request
     *
     * @tparam Trait The interface that this autowire client makes its requests
     *               against.
     */
    def apply[Trait]: ClientProxy[Trait, this.type] = ClientProxy[Trait, this.type](this)

    /**
     * A method for you to override, that actually performs the heavy 
     * lifting to transmit the marshalled function call from the [[Client]]
     * all the way to the [[Router]]
     */
    def callRequest(req: Request): Future[String]
  }

  trait Server{

    /**
     * A method for you to override, that actually performs the heavy
     * lifting to transmit the marshalled function call from the [[Client]]
     * all the way to the [[Router]]
     */
    def route[Trait](f: Trait): Router = macro Macros.routeMacro[Trait]
  }
}

