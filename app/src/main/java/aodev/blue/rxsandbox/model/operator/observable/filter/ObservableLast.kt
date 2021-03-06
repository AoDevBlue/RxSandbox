package aodev.blue.rxsandbox.model.operator.observable.filter

import aodev.blue.rxsandbox.model.Config
import aodev.blue.rxsandbox.model.ObservableT
import aodev.blue.rxsandbox.model.SingleT
import aodev.blue.rxsandbox.model.operator.Operator


class ObservableLast<T : Any> : Operator {

    fun apply(input: ObservableT<T>): SingleT<T> {
        return when (input.termination) {
            is ObservableT.Termination.None -> SingleT(SingleT.Result.None())
            is ObservableT.Termination.Complete -> {
                if (input.events.isNotEmpty()) {
                    val event = input.events.last()
                    SingleT(SingleT.Result.Success(input.termination.time, event.value))
                } else {
                    SingleT(SingleT.Result.Error(input.termination.time))
                }
            }
            is ObservableT.Termination.Error -> {
                SingleT(SingleT.Result.Error(input.termination.time))
            }
        }
    }

    override val expression: String = "last"

    override val docUrl: String? = "${Config.operatorDocUrlPrefix}last.html"
}