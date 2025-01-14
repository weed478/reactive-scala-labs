package EShop.lab2

import EShop.lab3.{OrderManager, Payment}
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.concurrent.duration._

object TypedCheckout {

  sealed trait Command
  case object StartCheckout                                                                  extends Command
  case class SelectDeliveryMethod(method: String)                                            extends Command
  case object CancelCheckout                                                                 extends Command
  case object ExpireCheckout                                                                 extends Command
  case class SelectPayment(payment: String, orderManagerRef: ActorRef[OrderManager.Command]) extends Command
  case object ExpirePayment                                                                  extends Command
  case object ConfirmPaymentReceived                                                         extends Command
  case object PaymentRejected                                                                extends Command
  case object PaymentRestarted                                                               extends Command

  final case class PaymentStarted(payment: ActorRef[Payment.Command])

  case object ExpireCheckout extends Command
  case object ExpirePayment extends Command

  private val checkoutTimerDuration: FiniteDuration = 1.seconds
  private val paymentTimerDuration: FiniteDuration  = 1.seconds

  def apply(cart: ActorRef[TypedCartActor.Command]): Behavior[TypedCheckout.Command] =
    start(cart)

  private def start(cart: ActorRef[TypedCartActor.Command]): Behavior[TypedCheckout.Command] =
    Behaviors.setup { context =>
      selectingDelivery(cart, context.scheduleOnce(checkoutTimerDuration, context.self, ExpireCheckout))
    }

  private def selectingDelivery(cart: ActorRef[TypedCartActor.Command], timer: Cancellable): Behavior[TypedCheckout.Command] =
    Behaviors.receiveMessage {
      case ExpireCheckout | CancelCheckout =>
        timer.cancel()
        cancelled(cart)
      case SelectDeliveryMethod(method) =>
        selectingPaymentMethod(cart, timer)
      case _ => Behaviors.same
    }

  private def selectingPaymentMethod(cart: ActorRef[TypedCartActor.Command], timer: Cancellable): Behavior[TypedCheckout.Command] =
    Behaviors.setup { context =>
      Behaviors.receiveMessage {
        case SelectPayment(method, replyTo) =>
          timer.cancel()
          val paymentTimer = context.scheduleOnce(paymentTimerDuration, context.self, ExpirePayment)
          val payment = context.spawn(Payment(method, context.self), "payment")
          replyTo ! PaymentStarted(payment)
          processingPayment(cart, payment, paymentTimer)
        case CancelCheckout | ExpireCheckout =>
          timer.cancel()
          cancelled(cart)
        case _ => Behaviors.same
      }
    }

  private def processingPayment(
    cart: ActorRef[TypedCartActor.Command],
    payment: ActorRef[Payment.Command],
    timer: Cancellable
  ): Behavior[TypedCheckout.Command] =
    Behaviors.receiveMessage {
      case ConfirmPaymentReceived =>
        timer.cancel()
        closed(cart)
      case ExpirePayment =>
        cancelled(cart)
      case _ => Behaviors.same
    }

  private def cancelled(cart: ActorRef[TypedCartActor.Command]): Behavior[TypedCheckout.Command] = {
    cart ! TypedCartActor.ConfirmCheckoutCancelled
    Behaviors.stopped
  }

  private def closed(cart: ActorRef[TypedCartActor.Command]): Behavior[TypedCheckout.Command] = {
    cart ! TypedCartActor.ConfirmCheckoutClosed
    Behaviors.stopped
  }

}
