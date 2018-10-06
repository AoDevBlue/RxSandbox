package aodev.blue.rxsandbox.model.operator.observable.create

import aodev.blue.rxsandbox.model.Config
import aodev.blue.rxsandbox.model.ObservableT
import aodev.blue.rxsandbox.model.Timeline
import aodev.blue.rxsandbox.model.operator.Input
import aodev.blue.rxsandbox.model.operator.Operator


class ObservableThrow<T> : Operator<T, T> {

    override fun apply(input: List<Timeline<T>>): Timeline<T>? {
        return Input.None.from(input) {
            apply()
        }
    }

    fun apply(): ObservableT<T> {
        return ObservableT(
                emptyList(),
                ObservableT.Termination.Error(0f)
        )
    }

    override val expression: String = "throw"

    override val docUrl: String? = "${Config.operatorDocUrlPrefix}empty-never-throw.html"
}
