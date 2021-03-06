package aodev.blue.rxsandbox.model.operator.observable.combine

import aodev.blue.rxsandbox.model.Config
import aodev.blue.rxsandbox.model.ObservableT
import aodev.blue.rxsandbox.model.operator.Operator


class ObservableMerge<T : Any> : Operator {

    fun apply(input: List<ObservableT<T>>): ObservableT<T> {
        return if (input.isEmpty()) {
            ObservableT(emptyList(), ObservableT.Termination.None)
        } else {
            val terminations = input.map { it.termination }
            val firstError = terminations
                    .filterIsInstance<ObservableT.Termination.Error>()
                    .minBy { it.time }

            val termination = when {
                firstError != null -> firstError
                terminations.all { it is ObservableT.Termination.Complete } -> {
                    terminations.map { it as ObservableT.Termination.Complete }
                            .sortedBy { it.time }
                            .last()
                }
                else -> ObservableT.Termination.None
            }
            val terminationTime = termination.time

            val events = input.map { it.events }
                    .flatten()
                    .sortedBy { it.time }
                    .let { events ->
                        if (terminationTime != null) {
                            events.filter { it.time <= terminationTime }
                        } else {
                            events
                        }
                    }

            ObservableT(events, termination)
        }
    }

    override val expression: String = "merge"

    override val docUrl: String? = "${Config.operatorDocUrlPrefix}merge.html"
}
