package aodev.blue.rxsandbox.model.observable.operators.filtering

import aodev.blue.rxsandbox.model.Operator
import aodev.blue.rxsandbox.model.observable.ObservableTimeline


class ObservableSkip<T>(
        private val count: Int
) : Operator<ObservableTimeline<T>, ObservableTimeline<T>> {

    override fun apply(input: ObservableTimeline<T>): ObservableTimeline<T> {
        return ObservableTimeline(
                input.sortedEvents.drop(count).toSet(),
                input.termination
        )
    }

    override fun expression(): String {
        return "skip($count)"
    }
}