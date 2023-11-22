package EShop.lab3

import EShop.lab2.TypedCheckout
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object Payment {

  sealed trait Command
  final case class DoPayment(orderManager: ActorRef[OrderManager.Command]) extends Command

  def apply(
    method: String,
    checkout: ActorRef[TypedCheckout.Command]
  ): Behavior[Command] =
    Behaviors.setup { context =>
      Behaviors.receiveMessage {
        case DoPayment(orderManager) =>
          orderManager ! OrderManager.ConfirmPaymentReceived
          checkout ! TypedCheckout.ConfirmPaymentReceived
          Behaviors.same
      }
    }

}
