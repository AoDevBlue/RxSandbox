package aodev.blue.rxsandbox.ui.widget.timeline

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import aodev.blue.rxsandbox.R
import aodev.blue.rxsandbox.model.Config
import aodev.blue.rxsandbox.model.observable.ObservableEvent
import aodev.blue.rxsandbox.model.observable.ObservableTermination
import aodev.blue.rxsandbox.model.observable.ObservableTimeline
import aodev.blue.rxsandbox.ui.utils.basicMeasure
import aodev.blue.rxsandbox.ui.utils.extension.colorCompat
import aodev.blue.rxsandbox.ui.utils.extension.isLtr
import aodev.blue.rxsandbox.utils.clamp
import aodev.blue.rxsandbox.utils.exhaustive
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject


class ObservableTimelineView : View {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
            super(context, attrs, defStyleAttr, defStyleRes)

    companion object {
        private val initialTimeline = ObservableTimeline<Int>(emptyList(), ObservableTermination.None)

        private const val EVENT_INDEX_NONE = -2
        private const val EVENT_INDEX_TERMINATION = -1
    }

    // Data
    private var _timeline: ObservableTimeline<Int> = initialTimeline
        set(value) {
            field = value
            timelineSubject.onNext(value)
            invalidate()
        }

    /**
     * Exposed timeline for external use.
     * Updating the timeline resets the current gestures.
     */
    var timeline: ObservableTimeline<Int>
        set(value) {
            if (_timeline != value) {
                _timeline = value
                resetCurrentGesture()
            }
        }
        get() = _timeline

    private val timelineSubject: Subject<ObservableTimeline<Int>> = BehaviorSubject.createDefault(initialTimeline)
    val timelineFlowable: Flowable<ObservableTimeline<Int>>
        get() = timelineSubject.toFlowable(BackpressureStrategy.LATEST)

    var readOnly: Boolean = false


    // Gestures
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var lastTouchX: Float = 0f
    private var movingEventIndex = EVENT_INDEX_NONE
    private var eventsToMove: MutableList<ObservableEvent<Int>> = mutableListOf()


    // Resources
    private val strokeWidth = context.resources.getDimension(R.dimen.timeline_stroke_width)
    private val padding = context.resources.getDimension(R.dimen.timeline_padding)
    private val innerPaddingStart = context.resources.getDimension(R.dimen.timeline_padding_inner_start)
    private val innerPaddingEnd = context.resources.getDimension(R.dimen.timeline_padding_inner_end)
    private val eventSize = context.resources.getDimension(R.dimen.timeline_event_size)
    private val eventTextSize = context.resources.getDimension(R.dimen.timeline_event_text_size)
    private val completeHeight = context.resources.getDimension(R.dimen.timeline_complete_height)
    private val errorSize = context.resources.getDimension(R.dimen.timeline_error_size)
    private val errorStrokeWidth = context.resources.getDimension(R.dimen.timeline_error_stroke_width)
    private val touchTargetSize = context.resources.getDimension(R.dimen.timeline_touch_target_size)

    private val strokeColor = context.colorCompat(R.color.timeline_stroke_color)
    private val eventFillColor = context.colorCompat(R.color.timeline_event_fill_color)
    private val eventTextColor = context.colorCompat(R.color.timeline_event_text_color)
    private val errorColor = context.colorCompat(R.color.timeline_error_color)


    // Paint
    private val strokePaint = Paint().apply {
        flags = Paint.ANTI_ALIAS_FLAG
        color = strokeColor
        strokeWidth = this@ObservableTimelineView.strokeWidth
        style = Paint.Style.STROKE
    }
    private val eventFillPaint = Paint().apply {
        color = eventFillColor
        style = Paint.Style.FILL
    }
    private val eventTextPaint = Paint().apply {
        flags = Paint.ANTI_ALIAS_FLAG
        color = eventTextColor
        textSize = eventTextSize
    }
    private val errorPaint = Paint().apply {
        color = errorColor
        strokeWidth = errorStrokeWidth
        style = Paint.Style.STROKE
    }

    // Draw
    private val textBoundsRect = Rect()
    private val lineDrawer = TimelineLineDrawer(context, TimelineViewTypeText.OBSERVABLE)

    //region Measurement

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = (2 * padding + innerPaddingStart + innerPaddingEnd + 10 * eventSize).toInt()
        val desiredHeight = (2 * padding + touchTargetSize).toInt()

        val (width, height) = basicMeasure(widthMeasureSpec, heightMeasureSpec, desiredWidth, desiredHeight)
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        lineDrawer.isLtr = isLtr
        lineDrawer.onSizeChanged(w, h)
    }

    //endregion

    //region Drawing

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        lineDrawer.draw(canvas)
        drawTerminationEvent(canvas, _timeline.termination)
        drawEvents(canvas, _timeline.events)
    }

    private fun drawTerminationEvent(canvas: Canvas, termination: ObservableTermination) {
        val centerHeight = height.toFloat() / 2

        when (termination) {
            is ObservableTermination.None -> Unit
            is ObservableTermination.Complete -> {
                val position = eventPosition(termination.time)
                canvas.drawLine(
                        position,
                        centerHeight - completeHeight / 2,
                        position,
                        centerHeight + completeHeight / 2,
                        strokePaint
                )
            }
            is ObservableTermination.Error -> {
                val position = eventPosition(termination.time)
                canvas.drawLine(
                        position - errorSize / 2,
                        centerHeight - errorSize / 2,
                        position + errorSize / 2,
                        centerHeight + errorSize / 2,
                        errorPaint
                )
                canvas.drawLine(
                        position - errorSize / 2,
                        centerHeight + errorSize / 2,
                        position + errorSize / 2,
                        centerHeight - errorSize / 2,
                        errorPaint
                )
            }
        }.exhaustive
    }

    private fun drawEvents(canvas: Canvas, sortedEvents: List<ObservableEvent<Int>>) {
        val centerHeight = height.toFloat() / 2
        sortedEvents.reversed().forEach { event ->
            val position = eventPosition(event.time)

            canvas.drawCircle(position, centerHeight, eventSize / 2, eventFillPaint)
            canvas.drawCircle(position, centerHeight, eventSize / 2, strokePaint)

            val eventText = event.value.toString()
            eventTextPaint.getTextBounds(eventText, 0, eventText.length, textBoundsRect)
            val textX = position - textBoundsRect.width().toFloat() / 2 - textBoundsRect.left
            val textY = centerHeight + textBoundsRect.height().toFloat() / 2 - textBoundsRect.bottom

            canvas.drawText(eventText, textX, textY, eventTextPaint)
        }
    }

    //endregion

    //region Event position

    private val availableWidth: Float
        get() = width - 2 * padding - innerPaddingStart - innerPaddingEnd

    private fun eventPosition(time: Float): Float {
        val timeFactor = time / Config.timelineDuration
        return if (isLtr) {
            timeFactor * availableWidth + padding + innerPaddingStart
        } else {
            (1 - timeFactor) * availableWidth + padding + innerPaddingEnd
        }
    }

    //endregion

    //region Gestures

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (readOnly) return false

        val action = ev.actionMasked

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                val pointerIndex = ev.actionIndex
                val x = ev.getX(pointerIndex)
                val y = ev.getY(pointerIndex)

                eventsToMove = _timeline.events.toMutableList()
                movingEventIndex = getEventIndexForPosition(x, y)

                lastTouchX = x
                activePointerId = ev.getPointerId(0)
            }

            MotionEvent.ACTION_MOVE -> {
                // Find the index of the active pointer and fetch its position
                val pointerIndex = ev.findPointerIndex(activePointerId)

                val x = ev.getX(pointerIndex)

                // Calculate the distance moved
                val dx = if (isLtr) x - lastTouchX else lastTouchX - x
                lastTouchX = x

                val timeDiff = dx / availableWidth * Config.timelineDuration

                val movingEventIndex = movingEventIndex
                when (movingEventIndex) {
                    EVENT_INDEX_NONE -> Unit
                    EVENT_INDEX_TERMINATION -> moveTerminationEvent(timeDiff)
                    else -> moveEvent(movingEventIndex, timeDiff)
                }
            }

            MotionEvent.ACTION_UP -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
            }

            MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = ev.actionIndex
                val pointerId = ev.getPointerId(pointerIndex)

                if (pointerId == activePointerId) {
                    // This was our active pointer going up. Choose a new
                    // active pointer and adjust accordingly.
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                    lastTouchX = ev.getX(newPointerIndex)
                    activePointerId = ev.getPointerId(newPointerIndex)
                }
            }
        }
        return true
    }

    private fun getEventIndexForPosition(x: Float, y: Float): Int {
        val eventBoundingBoxes = eventsToMove.map { getEventBoundingBox(it.time) }

        val termination = _timeline.termination
        val terminationBoundingBox = when (termination) {
            ObservableTermination.None -> null
            is ObservableTermination.Complete -> getEventBoundingBox(termination.time)
            is ObservableTermination.Error -> getEventBoundingBox(termination.time)
        }

        val boundingBoxes = if (terminationBoundingBox != null) {
            eventBoundingBoxes + terminationBoundingBox
        } else {
            eventBoundingBoxes
        }

        return boundingBoxes.mapIndexed { index, boundingBox -> index to boundingBox }
                .filter { it.second.contains(x, y) }
                .sortedBy { Math.abs(it.second.centerX() - x) }
                .firstOrNull()
                ?.first
                ?.let { if (it == _timeline.events.size) EVENT_INDEX_TERMINATION else it }
                ?: EVENT_INDEX_NONE
    }

    private fun getEventBoundingBox(eventTime: Float): RectF {
        val centerHeight = height.toFloat() / 2
        val eventPosition = eventPosition(eventTime)
        return RectF(
                eventPosition - touchTargetSize / 2,
                centerHeight - touchTargetSize / 2,
                eventPosition + touchTargetSize / 2,
                centerHeight + touchTargetSize / 2
        )
    }

    private fun moveTerminationEvent(timeDiff: Float) {
        val termination = _timeline.termination

        when (termination) {
            ObservableTermination.None -> Unit
            is ObservableTermination.Complete -> {
                moveTerminationEvent(timeDiff, termination.time) {
                    ObservableTermination.Complete(it)
                }
            }
            is ObservableTermination.Error -> {
                moveTerminationEvent(timeDiff, termination.time) {
                    ObservableTermination.Error(it)
                }
            }
        }.exhaustive
    }

    private fun moveTerminationEvent(
            timeDiff: Float,
            time: Float,
            makeWithTime: (Float) -> ObservableTermination
    ) {

        val newTime = (time + timeDiff).clamp(0f, Config.timelineDuration.toFloat())
        val termEvent = makeWithTime(newTime)

        // Move the events that might be after the termination
        val events = _timeline.events.map {
            if (it.time > newTime) {
                it.moveTo(newTime)
            } else {
                it
            }
        }

        this._timeline = _timeline.copy(
                events = events,
                termination = termEvent
        )
    }

    private fun moveEvent(eventIndex: Int, timeDiff: Float) {
        val oldEvent = eventsToMove.getOrNull(eventIndex) ?: return
        val newTime = (oldEvent.time + timeDiff).clamp(0f, Config.timelineDuration.toFloat())

        eventsToMove[eventIndex] = oldEvent.moveTo(newTime)

        val termination = _timeline.termination
        val newTermination = when (termination) {
            ObservableTermination.None -> termination
            is ObservableTermination.Complete -> {
                if (termination.time < newTime) {
                    ObservableTermination.Complete(newTime)
                } else {
                    termination
                }
            }
            is ObservableTermination.Error -> {
                if (termination.time < newTime) {
                    ObservableTermination.Error(newTime)
                } else {
                    termination
                }
            }
        }

        _timeline = _timeline.copy(
                events = eventsToMove.sortedBy { it.time },
                termination = newTermination
        )
    }

    private fun resetCurrentGesture() {
        activePointerId = MotionEvent.INVALID_POINTER_ID
        lastTouchX = 0f
        movingEventIndex = EVENT_INDEX_NONE
        eventsToMove = mutableListOf()
    }

    //endregion
}