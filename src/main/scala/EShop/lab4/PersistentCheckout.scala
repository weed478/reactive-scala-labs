package EShop.lab4

import EShop.lab2.TypedCartActor
import EShop.lab3.{OrderManager, Payment}
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import scala.concurrent.duration._
import EShop.lab2.TypedCheckout

object PersistentCheckout {

  import EShop.lab2.TypedCheckout.Command
  import EShop.lab2.TypedCheckout.SelectDeliveryMethod
  import EShop.lab2.TypedCheckout.SelectPayment
  import EShop.lab2.TypedCheckout.CancelCheckout
  import EShop.lab2.TypedCheckout.ConfirmPaymentReceived
  import EShop.lab2.TypedCheckout.ExpireCheckout
  import EShop.lab2.TypedCheckout.ExpirePayment

  sealed trait Event
  final case class DeliveryMethodSelected(method: String) extends Event
  final case class PaymentStarted(payment: ActorRef[Payment.DoPayment]) extends Event
  case object CheckOutClosed extends Event
  case object CheckoutCancelled extends Event
  case object CheckoutExpired extends Event
  case object PaymentExpired extends Event

  sealed trait State
  final case class SelectingDelivery(timer: Cancellable) extends State
  final case class SelectingPaymentMethod(timer: Cancellable) extends State
  final case class ProcessingPayment(timer: Cancellable) extends State
  case object Cancelled extends State
  case object Closed extends State

  private val checkoutTimerDuration: FiniteDuration = 1.seconds
  private val paymentTimerDuration: FiniteDuration  = 1.seconds

  def apply(cart: ActorRef[TypedCartActor.Command], persistenceId: PersistenceId): Behavior[Command] =
    Behaviors.setup { context =>
      EventSourcedBehavior(
        persistenceId,
        SelectingDelivery(context.scheduleOnce(checkoutTimerDuration, context.self, ExpireCheckout)),
        commandHandler(context, cart),
        eventHandler(context)
      )
    }

  def commandHandler(
    context: ActorContext[Command],
    cartActor: ActorRef[TypedCartActor.Command]
  )(state: State, command: Command): Effect[Event, State] = {
    state match {
      case SelectingDelivery(timer) =>
        command match {
          case SelectDeliveryMethod(method) =>
            Effect.persist(DeliveryMethodSelected(method))
          case CancelCheckout =>
            Effect.persist(CheckoutCancelled)
          case ExpireCheckout =>
            Effect.persist(CheckoutExpired)
          case _ => Effect.none
        }

      case SelectingPaymentMethod(timer) =>
        command match {
          case SelectPayment(payment, replyTo) =>
            val paymentActor = context.spawn(Payment(payment, context.self), "payment")
            replyTo ! TypedCheckout.PaymentStarted(paymentActor)
            Effect.persist(PaymentStarted(paymentActor))
          case CancelCheckout =>
            Effect.persist(CheckoutCancelled)
          case ExpireCheckout =>
            Effect.persist(CheckoutExpired)
          case _ => Effect.none
        }

      case ProcessingPayment(timer) =>
        command match {
          case ConfirmPaymentReceived =>
            Effect.persist(CheckOutClosed)
          case ExpirePayment =>
            Effect.persist(PaymentExpired)
          case _ => Effect.none
        }
      
        case _ => Effect.none
    }
  }

  def eventHandler(context: ActorContext[Command])(state: State, event: Event): State = {
    state match {
      case SelectingDelivery(timer) =>
        event match {
          case DeliveryMethodSelected(_) =>
            timer.cancel()
            SelectingPaymentMethod(context.scheduleOnce(checkoutTimerDuration, context.self, ExpireCheckout))
          case CheckoutCancelled =>
            timer.cancel()
            Cancelled
          case CheckoutExpired =>
            timer.cancel()
            Cancelled
          case _ => state
        }

      case SelectingPaymentMethod(timer) =>
        event match {
          case PaymentStarted(_) =>
            timer.cancel()
            ProcessingPayment(context.scheduleOnce(paymentTimerDuration, context.self, ExpirePayment))
          case CheckoutCancelled =>
            timer.cancel()
            Cancelled
          case CheckoutExpired =>
            timer.cancel()
            Cancelled
          case _ => state
        }

      case ProcessingPayment(timer) =>
        event match {
          case CheckOutClosed =>
            timer.cancel()
            Closed
          case CheckoutCancelled =>
            timer.cancel()
            Cancelled
          case CheckoutExpired =>
            timer.cancel()
            Cancelled
          case _ => state
        }
      
      case _ => state
    }
  }
}
