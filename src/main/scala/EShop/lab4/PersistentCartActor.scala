package EShop.lab4

import EShop.lab2.{Cart, TypedCheckout, TypedCartActor}
import EShop.lab3.OrderManager
import akka.actor.Cancellable
import akka.actor.typed.Behavior
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import scala.concurrent.duration._

object PersistentCartActor {

  import EShop.lab2.TypedCartActor.Command
  import EShop.lab2.TypedCartActor.AddItem
  import EShop.lab2.TypedCartActor.RemoveItem
  import EShop.lab2.TypedCartActor.GetItems
  import EShop.lab2.TypedCartActor.StartCheckout
  import EShop.lab2.TypedCartActor.ConfirmCheckoutClosed
  import EShop.lab2.TypedCartActor.ConfirmCheckoutCancelled
  import EShop.lab2.TypedCartActor.ExpireCart

  sealed trait Event
  final case class ItemAdded(item: Any) extends Event
  final case class ItemRemoved(item: Any) extends Event
  case object CheckoutStarted extends Event
  case object CartEmptied extends Event
  case object CartExpired extends Event
  case object CheckoutClosed extends Event
  case object CheckoutCancelled extends Event

  sealed trait State
  case object Empty extends State
  final case class NonEmpty(cart: Cart, timer: Cancellable) extends State
  final case class InCheckout(cart: Cart) extends State

  private val cartTimerDuration: FiniteDuration = 1.seconds

  private def scheduleTimer(context: ActorContext[Command]): Cancellable =
    context.scheduleOnce(cartTimerDuration, context.self, ExpireCart)

  def apply(persistenceId: PersistenceId): Behavior[Command] = Behaviors.setup { context =>
    EventSourcedBehavior[Command, Event, State](
      persistenceId,
      Empty,
      commandHandler(context),
      eventHandler(context)
    )
  }

  def commandHandler(context: ActorContext[Command]): (State, Command) => Effect[Event, State] = (state, command) => {
    state match {
      case Empty =>
        command match {
          case AddItem(item) =>
            Effect.persist(ItemAdded(item))
          case GetItems(replyTo) =>
            replyTo ! Cart.empty
            Effect.none
          case _ => Effect.none
        }

      case NonEmpty(cart, _) =>
        command match {
          case GetItems(replyTo) =>
            replyTo ! cart
            Effect.none
          case AddItem(item) =>
            Effect.persist(ItemAdded(item))
          case RemoveItem(item) if cart.contains(item) =>
            Effect.persist(ItemRemoved(item))
          case StartCheckout(replyTo) =>
            val checkout = context.spawn(TypedCheckout(context.self), "checkout")
            replyTo ! TypedCartActor.CheckoutStarted(checkout)
            Effect.persist(CheckoutStarted)
          case ExpireCart =>
            Effect.persist(CartExpired)
          case _ => Effect.none
        }

      case InCheckout(cart) =>
        command match {
          case GetItems(replyTo) =>
            replyTo ! cart
            Effect.none
          case ConfirmCheckoutClosed =>
            Effect.persist(CheckoutClosed)
          case ConfirmCheckoutCancelled =>
            Effect.persist(CheckoutCancelled)
          case _ => Effect.none
        }
    }
  }

  def eventHandler(context: ActorContext[Command]): (State, Event) => State = (state, event) => {
    state match {
      case Empty =>
        event match {
          case ItemAdded(item) =>
            NonEmpty(Cart(Seq(item)), scheduleTimer(context))
          case _ => Empty
        }

      case NonEmpty(cart, timer) =>
        event match {
          case ItemAdded(item) =>
            timer.cancel()
            NonEmpty(cart.addItem(item), scheduleTimer(context))
          case ItemRemoved(item) if cart.contains(item) && cart.size == 1 =>
            timer.cancel()
            Empty
          case ItemRemoved(item) if cart.contains(item) =>
            timer.cancel()
            NonEmpty(cart.removeItem(item), scheduleTimer(context))
          case CheckoutStarted =>
            timer.cancel()
            InCheckout(cart)
          case CartExpired =>
            timer.cancel()
            Empty
          case _ => NonEmpty(cart, timer)
        }

      case InCheckout(cart) =>
        event match {
          case CheckoutClosed =>
            Empty
          case CheckoutCancelled =>
            NonEmpty(cart, scheduleTimer(context))
          case _ => InCheckout(cart)
        }
    }
  }

}
