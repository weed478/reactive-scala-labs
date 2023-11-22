package EShop.lab4

import EShop.lab2.{Cart, TypedCheckout}
import EShop.lab3.OrderManager
import akka.actor.Cancellable
import akka.actor.typed.Behavior
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import scala.concurrent.duration._

object PersistentCartActor {

  import EShop.lab2.TypedCartActor._

  sealed trait Event
  final case class ItemAdded(item: Any) extends Event
  final case class ItemRemoved(item: Any) extends Event
  final case class EnteredCheckout(cart: Cart) extends Event

  sealed trait State
  case object Empty extends State
  final case class NonEmpty(cart: Cart, timer: Cancellable) extends State
  final case class InCheckout(cart: Cart) extends State

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
        }

      case NonEmpty(cart, _) =>
        command match {
          case GetItems(replyTo) =>
            replyTo ! cart
            Effect.none
          case AddItem(item) =>
            Effect.persist(ItemAdded(item))
          case RemoveItem(item) if cart.contains(item) && cart.size == 1 =>
            Effect.persist(ItemRemoved(item))
          case RemoveItem(item) if cart.contains(item) =>
            Effect.persist(ItemRemoved(item))
          case StartCheckout(replyTo) =>
            Effect.persist(EnteredCheckout(cart))
        }

      case InCheckout(_) =>
        command
    }
  }

  def eventHandler(context: ActorContext[Command]): (State, Event) => State = (state, event) => {
    ???
    event match {
      case CheckoutStarted(_)        => ???
      case ItemAdded(item)           => ???
      case ItemRemoved(item)         => ???
      case CartEmptied | CartExpired => ???
      case CheckoutClosed            => ???
      case CheckoutCancelled         => ???
    }
  }

}
