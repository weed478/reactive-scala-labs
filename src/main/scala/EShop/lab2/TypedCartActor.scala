package EShop.lab2

import EShop.lab3.OrderManager
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.concurrent.duration._

object TypedCartActor {

  sealed trait Command
  final case class AddItem(item: Any) extends Command
  final case class RemoveItem(item: Any) extends Command
  final case class GetItems(replyTo: ActorRef[Cart]) extends Command
  final case class StartCheckout(replyTo: ActorRef[CheckoutStarted]) extends Command
  case object ConfirmCheckoutClosed extends Command
  case object ConfirmCheckoutCancelled extends Command

  case object ExpireCart extends Command

  final case class CheckoutStarted(checkout: ActorRef[TypedCheckout.Command])

  private val cartTimerDuration: FiniteDuration = 5.seconds

  private def scheduleTimer(context: ActorContext[Command]): Cancellable =
    context.scheduleOnce(cartTimerDuration, context.self, ExpireCart)

  def apply(): Behavior[Command] = start()

  private def start(): Behavior[Command] = empty()

  private def empty(): Behavior[Command] =
    Behaviors.setup { context =>
      Behaviors.receiveMessage {
        case AddItem(item) =>
          nonEmpty(Cart(Seq(item)), scheduleTimer(context))
        case GetItems(replyTo) =>
          replyTo ! Cart.empty
          Behaviors.same
        case _ => Behaviors.same
      }
    }

  private def nonEmpty(cart: Cart, timer: Cancellable): Behavior[Command] =
    Behaviors.setup { context =>
      Behaviors.receiveMessage {
        case GetItems(replyTo) =>
          replyTo ! cart
          Behaviors.same
        case AddItem(item) =>
          timer.cancel()
          nonEmpty(cart.addItem(item), scheduleTimer(context))
        case RemoveItem(item) if cart.contains(item) && cart.size == 1 =>
          timer.cancel()
          empty
        case RemoveItem(item) if cart.contains(item) =>
          timer.cancel()
          nonEmpty(cart.removeItem(item), scheduleTimer(context))
        case StartCheckout(replyTo) =>
          timer.cancel()
          inCheckout(cart, replyTo)
        case ExpireCart =>
          empty
        case _ => Behaviors.same
      }
    }

  private def inCheckout(cart: Cart, replyTo: ActorRef[CheckoutStarted]): Behavior[Command] =
    Behaviors.setup { context =>
      val checkout = context.spawn(TypedCheckout(context.self), "Checkout")
      replyTo ! CheckoutStarted(checkout)
      Behaviors.receiveMessage {
        case GetItems(replyTo) =>
          replyTo ! cart
          Behaviors.same
        case ConfirmCheckoutCancelled =>
          nonEmpty(cart, scheduleTimer(context))
        case ConfirmCheckoutClosed =>
          empty
        case _ => Behaviors.same
      }
    }
}
