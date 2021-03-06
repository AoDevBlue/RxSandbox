package aodev.blue.rxsandbox.model.operator.observable.combine

import aodev.blue.rxsandbox.model.ObservableT
import aodev.blue.rxsandbox.model.empty
import aodev.blue.rxsandbox.model.functions.functionOf
import aodev.blue.rxsandbox.model.inputOf
import aodev.blue.rxsandbox.model.never
import org.junit.Assert
import org.junit.Test


class ObservableCombineLatestTest {

    private fun <T : Any, R : Any> operator(combiner: (List<T>) -> R): ObservableCombineLatest<T, R> {
        return ObservableCombineLatest(functionOf("combiner", combiner))
    }

    @Test
    fun neverSources() {
        // Given
        val source1 = ObservableT.never<Int>()
        val source2 = ObservableT.never<Int>()
        val inputs = listOf(source1, source2)

        val operator = operator<Int, Int> { it.sum() }

        // When
        val result = operator.apply(inputs)

        // Then
        val expected = ObservableT<Int>(emptyList(), ObservableT.Termination.None)
        Assert.assertEquals(expected, result)
    }

    @Test
    fun noSources() {
        // Given
        val inputs = emptyList<ObservableT<Int>>()

        val operator = operator<Int, Int> { it.sum() }

        // When
        val result = operator.apply(inputs)

        // Then
        val expected = ObservableT<Int>(emptyList(), ObservableT.Termination.None)
        Assert.assertEquals(expected, result)
    }

    @Test
    fun oneSource() {
        // Given
        val source1 = ObservableT.inputOf(
                events = listOf(0f to 1, 2f to 3, 7f to 4),
                termination = ObservableT.Termination.Error(8f)
        )
        val inputs = listOf(source1)

        val operator = operator<Int, Int> { it.sum() }

        // When
        val result = operator.apply(inputs)

        // Then
        Assert.assertEquals(source1, result)
    }

    @Test
    fun multipleSourcesOneEmpty() {
        // Given
        val source1 = ObservableT.inputOf(
                events = listOf(0f to 1, 2f to 3, 7f to 4),
                termination = ObservableT.Termination.Error(8f)
        )
        val source2 = ObservableT.empty<Int>()
        val inputs = listOf(source1, source2)

        val operator = operator<Int, Int> { it.sum() }

        // When
        val result = operator.apply(inputs)

        // Then
        Assert.assertEquals(source2, result)
    }

    @Test
    fun multipleSourcesOneNever() {
        // Given
        val source1 = ObservableT.inputOf(
                events = listOf(0f to 1, 2f to 3, 7f to 4),
                termination = ObservableT.Termination.Error(8f)
        )
        val source2 = ObservableT.never<Int>()
        val inputs = listOf(source1, source2)

        val operator = operator<Int, Int> { it.sum() }

        // When
        val result = operator.apply(inputs)

        // Then
        val expected = ObservableT<Int>(emptyList(), ObservableT.Termination.Error(8f))
        Assert.assertEquals(expected, result)
    }

    @Test
    fun completeBeforeError() {
        // Given
        val source1 = ObservableT<Int>(emptyList(), ObservableT.Termination.Error(1f))
        val source2 = ObservableT<Int>(emptyList(), ObservableT.Termination.Complete(0f))
        val inputs = listOf(source1, source2)

        val operator = operator<Int, Int> { it.sum() }

        // When
        val result = operator.apply(inputs)

        // Then
        Assert.assertEquals(source2, result)
    }

    @Test
    fun combineValues() {
        // Given
        val source1 = ObservableT.inputOf(
                events = listOf(0f to 1, 2f to 3, 4f to 4),
                termination = ObservableT.Termination.Complete(10f)
        )
        val source2 = ObservableT.inputOf(
                events = listOf(1f to 2, 5f to 3, 7f to 4),
                termination = ObservableT.Termination.Complete(7f)
        )
        val inputs = listOf(source1, source2)

        val operator = operator<Int, Int> { it.sum() }

        // When
        val result = operator.apply(inputs)

        // Then
        val expected = ObservableT.inputOf(
                events = listOf(1f to 3, 2f to 5, 4f to 6, 5f to 7, 7f to 8),
                termination = ObservableT.Termination.Complete(10f)
        )
        Assert.assertEquals(expected, result)
    }

    @Test
    fun stopAtError() {
        // Given
        val source1 = ObservableT.inputOf(
                events = listOf(0f to 1, 4f to 4),
                termination = ObservableT.Termination.Error(5f)
        )
        val source2 = ObservableT.inputOf(
                events = listOf(1f to 2, 6f to 3),
                termination = ObservableT.Termination.Complete(7f)
        )
        val inputs = listOf(source1, source2)

        val operator = operator<Int, Int> { it.sum() }

        // When
        val result = operator.apply(inputs)

        // Then
        val expected = ObservableT.inputOf(
                events = listOf(1f to 3, 4f to 6),
                termination = ObservableT.Termination.Error(5f)
        )
        Assert.assertEquals(expected, result)
    }

    @Test
    fun onlyOneValueThenError() {
        // Given
        val source1 = ObservableT.inputOf(
                events = listOf(0f to 1),
                termination = ObservableT.Termination.Error(5f)
        )
        val source2 = ObservableT.inputOf(
                events = listOf(6f to 2),
                termination = ObservableT.Termination.Complete(7f)
        )
        val inputs = listOf(source1, source2)

        val operator = operator<Int, Int> { it.sum() }

        // When
        val result = operator.apply(inputs)

        // Then
        val expected = ObservableT(emptyList(), ObservableT.Termination.Error(5f))
        Assert.assertEquals(expected, result)
    }
}
