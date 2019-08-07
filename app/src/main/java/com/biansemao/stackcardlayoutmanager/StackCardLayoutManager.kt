package com.thai.thishop.weight.layoutmanager

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.support.annotation.FloatRange
import android.support.annotation.IntRange
import android.support.v7.widget.RecyclerView
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import java.lang.reflect.Method

/**
 * 自定义RecyclerView.LayoutManager，卡片式层叠效果
 */
class StackCardLayoutManager : RecyclerView.LayoutManager {

    /* 每个item宽高 */
    private var mItemWidth: Int = 0
    private var mItemHeight: Int = 0

    private var mTotalOffset: Int = 0
    private var initialOffset: Int = 0

    private var animator: ObjectAnimator? = null
    private var animateValue: Int = 0
    private var lastAnimateValue: Int = 0
    private val duration = 300

    private var mRecyclerView: RecyclerView? = null
    private var mRecycler: RecyclerView.Recycler? = null

    private var initialFlag: Boolean = false

    private var mMinVelocity: Int = 0
    private val mVelocityTracker = VelocityTracker.obtain()
    private var pointerId: Int = 0

    private var sSetScrollState: Method? = null
    private var mPendingScrollPosition = RecyclerView.NO_POSITION

    private var mOnPositionChangeListener: OnPositionChangeListener? = null

    private val mTouchListener = View.OnTouchListener { v, event ->
        mVelocityTracker.addMovement(event)
        if (event.action == MotionEvent.ACTION_DOWN) {
            animator?.let {
                if (it.isRunning) {
                    it.cancel()
                }
            }
            pointerId = event.getPointerId(0)

            stopAutoCycle()
        }
        if (event.action == MotionEvent.ACTION_UP) {
            if (v.isPressed) {
                v.performClick()
            }
            mVelocityTracker.computeCurrentVelocity(1000, 14000f)
            when (stackConfig.direction) {
                StackDirection.LEFT, StackDirection.RIGHT -> {
                    if (mItemHeight > 0) {
                        val o = mTotalOffset % mItemWidth
                        val scrollX: Int
                        if (Math.abs(mVelocityTracker.getXVelocity(pointerId)) < mMinVelocity && o != 0) {
                            scrollX = if (mTotalOffset >= 0) {
                                if (o >= mItemWidth / 2) {
                                    mItemWidth - o
                                } else {
                                    -o
                                }
                            } else {
                                if (o <= -mItemWidth / 2) {
                                    -mItemWidth - o
                                } else {
                                    -o
                                }
                            }
                            val dur = (Math.abs((scrollX + 0f) / mItemWidth) * duration).toInt()
                            brewAndStartAnimator(dur, scrollX)
                        }
                    }
                }
                StackDirection.TOP, StackDirection.BOTTOM -> {
                    if (mItemHeight > 0) {
                        val o = mTotalOffset % mItemHeight
                        val scrollY: Int
                        if (Math.abs(mVelocityTracker.getYVelocity(pointerId)) < mMinVelocity && o != 0) {
                            scrollY = if (mTotalOffset >= 0) {
                                if (o >= mItemHeight / 2) {
                                    mItemHeight - o
                                } else {
                                    -o
                                }
                            } else {
                                if (o <= -mItemHeight / 2) {
                                    -mItemHeight - o
                                } else {
                                    -o
                                }
                            }
                            val dur = (Math.abs((scrollY + 0f) / mItemHeight) * duration).toInt()
                            brewAndStartAnimator(dur, scrollY)
                        }
                    }
                }
            }

            startAutoCycle()
        }
        false
    }

    private val mOnFlingListener = object : RecyclerView.OnFlingListener() {
        override fun onFling(velocityX: Int, velocityY: Int): Boolean {
            stopAutoCycle()

            val vel = absMax(velocityX, velocityY)
            when (stackConfig.direction) {
                StackDirection.LEFT, StackDirection.RIGHT -> {
                    if (mItemWidth > 0) {
                        val o = mTotalOffset % mItemWidth
                        val scroll = if (mTotalOffset >= 0) {
                            val s = mItemWidth - o
                            if (vel * stackConfig.direction.layoutDirection > 0) {
                                s
                            } else {
                                -o
                            }
                        } else {
                            val s = -mItemWidth - o
                            if (vel * stackConfig.direction.layoutDirection < 0) {
                                s
                            } else {
                                -o
                            }
                        }
                        val dur = computeHorizontalSettleDuration(Math.abs(scroll), Math.abs(vel).toFloat())
                        brewAndStartAnimator(dur, scroll)
                    }
                }
                StackDirection.TOP, StackDirection.BOTTOM -> {
                    if (mItemHeight > 0) {
                        val o = mTotalOffset % mItemHeight
                        val scroll = if (mTotalOffset >= 0) {
                            val s = mItemHeight - o
                            if (vel * stackConfig.direction.layoutDirection > 0) {
                                s
                            } else {
                                -o
                            }
                        } else {
                            val s = -mItemHeight - o
                            if (vel * stackConfig.direction.layoutDirection < 0) {
                                s
                            } else {
                                -o
                            }
                        }
                        val dur = computeVerticalSettleDuration(Math.abs(scroll), Math.abs(vel).toFloat())
                        brewAndStartAnimator(dur, scroll)
                    }
                }
            }
            setScrollStateIdle()

            startAutoCycle()
            return true
        }
    }

    private val mAutoCycleRunnable = Runnable {
        when (stackConfig.direction) {
            StackDirection.LEFT, StackDirection.RIGHT -> {
                val dur = computeHorizontalSettleDuration(Math.abs(mItemWidth), 0f)
                brewAndStartAnimator(dur, mItemWidth)
            }
            StackDirection.TOP, StackDirection.BOTTOM -> {
                val dur = computeVerticalSettleDuration(Math.abs(mItemHeight), 0f)
                brewAndStartAnimator(dur, mItemHeight)
            }
        }

        startAutoCycle()
    }

    private var stackConfig: StackConfig = StackConfig()

    constructor() {

    }

    constructor(config: StackConfig) {
        stackConfig = config
    }

    /**
     * 必须为true，否则RecyclerView为warp_content时，无法显示画面
     */
    override fun isAutoMeasureEnabled(): Boolean {
        return true
    }

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        if (itemCount <= 0) {
            return
        }
        this.mRecycler = recycler
        detachAndScrapAttachedViews(recycler)

        /* 获取第一个item，所有item具有相同尺寸 */
        val anchorView = recycler.getViewForPosition(0)
        measureChildWithMargins(anchorView, 0, 0)
        mItemWidth = anchorView.measuredWidth
        mItemHeight = anchorView.measuredHeight

        initialOffset = resolveInitialOffset()
        mMinVelocity = ViewConfiguration.get(anchorView.context).scaledMinimumFlingVelocity
        fillItemView(recycler, 0)
    }

    private fun resolveInitialOffset(): Int {
        var position = stackConfig.stackPosition
        if (position >= itemCount) {
            position = itemCount - 1
        }
        var offset = when (stackConfig.direction) {
            StackDirection.LEFT, StackDirection.RIGHT -> {
                position * mItemWidth
            }
            StackDirection.TOP, StackDirection.BOTTOM -> {
                position * mItemHeight
            }
        }
        if (mPendingScrollPosition != RecyclerView.NO_POSITION) {
            offset = when (stackConfig.direction) {
                StackDirection.LEFT, StackDirection.RIGHT -> {
                    mPendingScrollPosition * mItemWidth
                }
                StackDirection.TOP, StackDirection.BOTTOM -> {
                    mPendingScrollPosition * mItemHeight
                }
            }
            mPendingScrollPosition = RecyclerView.NO_POSITION
        }

        return stackConfig.direction.layoutDirection * offset
    }

    override fun onLayoutCompleted(state: RecyclerView.State?) {
        super.onLayoutCompleted(state)
        if (itemCount <= 0) {
            return
        }
        if (!initialFlag) {
            mRecycler?.let {
                fillItemView(it, initialOffset, false)
            }
            initialFlag = true
            startAutoCycle()
        }
    }

    override fun onAdapterChanged(oldAdapter: RecyclerView.Adapter<*>?, newAdapter: RecyclerView.Adapter<*>?) {
        initialFlag = false
        mTotalOffset = 0
    }

    /**
     * 填充控件
     */
    private fun fillItemView(recycler: RecyclerView.Recycler, dy: Int): Int {
        return fillItemView(recycler, dy, true)
    }

    /**
     * 填充控件
     */
    private fun fillItemView(recycler: RecyclerView.Recycler, dy: Int, apply: Boolean): Int {
        var delta = stackConfig.direction.layoutDirection * dy
        if (apply) {
            delta = (delta * stackConfig.parallex).toInt()
        }
        return if (stackConfig.isCycle && itemCount > 1) {
            when (stackConfig.direction) {
                StackDirection.LEFT -> {
                    fillHorizontalCycleItemView(recycler, delta, true)
                }
                StackDirection.TOP -> {
                    fillVerticalCycleItemView(recycler, delta, true)
                }
                StackDirection.RIGHT -> {
                    fillHorizontalCycleItemView(recycler, delta, false)
                }
                StackDirection.BOTTOM -> {
                    fillVerticalCycleItemView(recycler, delta, false)
                }
            }
        } else {
            when (stackConfig.direction) {
                StackDirection.LEFT -> {
                    fillHorizontalItemView(recycler, delta, true)
                }
                StackDirection.TOP -> {
                    fillVerticalItemView(recycler, delta, true)
                }
                StackDirection.RIGHT -> {
                    fillHorizontalItemView(recycler, delta, false)
                }
                StackDirection.BOTTOM -> {
                    fillVerticalItemView(recycler, delta, false)
                }
            }
        }
    }

    /**
     * 填充垂直方向的控件
     */
    private fun fillVerticalItemView(recycler: RecyclerView.Recycler, dy: Int, isTopFlag: Boolean): Int {
        if (mTotalOffset + dy < 0 || (mTotalOffset.toFloat() + dy.toFloat() + 0f) / mItemHeight > itemCount - 1) {
            return 0
        }
        detachAndScrapAttachedViews(recycler)
        mTotalOffset += dy

        for (i in 0 until childCount) {
            getChildAt(i)?.let {
                if (recycleVertically(it, dy)) {
                    removeAndRecycleView(it, recycler)
                }
            }
        }

        if (mItemHeight <= 0) {
            return dy
        }

        var curPosition = mTotalOffset / mItemHeight
        curPosition = when {
            curPosition > itemCount - 1 -> itemCount - 1
            curPosition < 0 -> 0
            else -> curPosition
        }

        val start = if (curPosition + stackConfig.stackCount < itemCount - 1) {
            curPosition + stackConfig.stackCount
        } else {
            itemCount - 1
        }
        val end = if (curPosition > 0) {
            curPosition - 1
        } else {
            0
        }

        val firstScale = getVerticalFirstScale()
        for (i in start downTo end) {
            fillVerticalBaseItemView(recycler.getViewForPosition(i), firstScale, curPosition, i, isTopFlag)
        }

        return dy
    }

    /**
     * 填充垂直方向的控件--循环
     */
    private fun fillVerticalCycleItemView(recycler: RecyclerView.Recycler, dy: Int, isTopFlag: Boolean): Int {
        detachAndScrapAttachedViews(recycler)
        mTotalOffset += dy

        for (i in 0 until childCount) {
            getChildAt(i)?.let {
                if (recycleVertically(it, dy)) {
                    removeAndRecycleView(it, recycler)
                }
            }
        }

        if (mItemHeight <= 0) {
            return dy
        }
        when {
            mTotalOffset >= itemCount * mItemHeight -> {
                mTotalOffset -= itemCount * mItemHeight
            }
            mTotalOffset <= -itemCount * mItemHeight -> {
                mTotalOffset += itemCount * mItemHeight
            }
        }

        val curPosition = mTotalOffset / mItemHeight
        val start = curPosition - 1
        val end = curPosition + stackConfig.stackCount
        val tempList = ArrayList<View>()
        for (i in start..end) {
            when {
                i < -itemCount -> {
                    tempList.add(recycler.getViewForPosition(2 * itemCount + i))
                }
                i < 0 -> {
                    tempList.add(recycler.getViewForPosition(itemCount + i))
                }
                i >= itemCount -> {
                    tempList.add(recycler.getViewForPosition(i - itemCount))
                }
                else -> {
                    tempList.add(recycler.getViewForPosition(i))
                }
            }
        }

        val firstScale = getVerticalFirstScale()
        for (i in tempList.size - 1 downTo 0) {
            fillVerticalBaseItemView(tempList[i], firstScale, 1, i, isTopFlag)
        }

        return dy
    }

    /**
     * 填充垂直方向的控件
     */
    private fun fillVerticalBaseItemView(view: View, firstScale: Float, position: Int, index: Int, isTopFlag: Boolean) {
        // 通知测量view的margin值
        measureChildWithMargins(view, 0, 0)
        val scale = if (mTotalOffset >= 0) {
            calculateVerticalScale(firstScale, position, index)
        } else {
            calculateVerticalCycleScale(firstScale, position, index)
        }
        if (scale > 0f) {
            // 因为刚刚进行了detach操作，所以现在可以重新添加
            addView(view)
            // 调用这句我们指定了该View的显示区域，并将View显示上去，此时所有区域都用于显示View
            layoutDecoratedWithMargins(view, 0, 0, mItemWidth, mItemHeight)
            view.scaleX = scale
            view.scaleY = scale
            val offset = if (mTotalOffset >= 0) {
                calculateVerticalOffset(scale, position, index)
            } else {
                calculateVerticalCycleOffset(scale, position, index)
            }
            if (isTopFlag) {
                view.translationY = -offset
            } else {
                view.translationY = offset
            }
        }
    }

    /**
     * 填充水平方向的控件
     */
    private fun fillHorizontalItemView(recycler: RecyclerView.Recycler, dx: Int, isLeftFlag: Boolean): Int {
        if (mTotalOffset + dx < 0 || (mTotalOffset.toFloat() + dx.toFloat() + 0f) / mItemWidth > itemCount - 1) {
            return 0
        }
        detachAndScrapAttachedViews(recycler)
        mTotalOffset += dx

        for (i in 0 until childCount) {
            getChildAt(i)?.let {
                if (recycleHorizontally(it, dx)) {
                    removeAndRecycleView(it, recycler)
                }
            }
        }

        if (mItemWidth <= 0) {
            return dx
        }

        var curPosition = mTotalOffset / mItemWidth
        curPosition = when {
            curPosition > itemCount - 1 -> itemCount - 1
            curPosition < 0 -> 0
            else -> curPosition
        }

        val start = if (curPosition + stackConfig.stackCount < itemCount - 1) {
            curPosition + stackConfig.stackCount
        } else {
            itemCount - 1
        }
        val end = if (curPosition > 0) {
            curPosition - 1
        } else {
            0
        }

        val firstScale = getHorizontalFirstScale()
        for (i in start downTo end) {
            fillHorizontalBaseItemView(recycler.getViewForPosition(i), firstScale, curPosition, i, isLeftFlag)
        }

        return dx
    }

    /**
     * 填充水平方向的控件--循环
     */
    private fun fillHorizontalCycleItemView(recycler: RecyclerView.Recycler, dx: Int, isLeftFlag: Boolean): Int {
        detachAndScrapAttachedViews(recycler)
        mTotalOffset += dx

        for (i in 0 until childCount) {
            getChildAt(i)?.let {
                if (recycleHorizontally(it, dx)) {
                    removeAndRecycleView(it, recycler)
                }
            }
        }

        if (mItemWidth <= 0) {
            return dx
        }
        when {
            mTotalOffset >= itemCount * mItemWidth -> {
                mTotalOffset -= itemCount * mItemWidth
            }
            mTotalOffset <= -itemCount * mItemWidth -> {
                mTotalOffset += itemCount * mItemWidth
            }
        }

        val curPosition = mTotalOffset / mItemWidth
        val start = curPosition - 1
        val end = curPosition + stackConfig.stackCount
        val tempList = ArrayList<View>()
        for (i in start..end) {
            when {
                i < -itemCount -> {
                    tempList.add(recycler.getViewForPosition(2 * itemCount + i))
                }
                i < 0 -> {
                    tempList.add(recycler.getViewForPosition(itemCount + i))
                }
                i >= itemCount -> {
                    tempList.add(recycler.getViewForPosition(i - itemCount))
                }
                else -> {
                    tempList.add(recycler.getViewForPosition(i))
                }
            }
        }

        val firstScale = getHorizontalFirstScale()
        for (i in tempList.size - 1 downTo 0) {
            fillHorizontalBaseItemView(tempList[i], firstScale, 1, i, isLeftFlag)
        }

        return dx
    }

    /**
     * 填充水平方向的控件
     */
    private fun fillHorizontalBaseItemView(view: View, firstScale: Float, position: Int, index: Int, isLeftFlag: Boolean) {
        // 通知测量view的margin值
        measureChildWithMargins(view, 0, 0)
        val scale = if (mTotalOffset >= 0) {
            calculateHorizontalScale(firstScale, position, index)
        } else {
            calculateHorizontalCycleScale(firstScale, position, index)
        }
        if (scale > 0f) {
            // 因为刚刚进行了detach操作，所以现在可以重新添加
            addView(view)
            // 调用这句我们指定了该View的显示区域，并将View显示上去，此时所有区域都用于显示View
            layoutDecoratedWithMargins(view, 0, 0, mItemWidth, mItemHeight)
            view.scaleX = scale
            view.scaleY = scale
            val offset = if (mTotalOffset >= 0) {
                calculateHorizontalOffset(scale, position, index)
            } else {
                calculateHorizontalCycleOffset(scale, position, index)
            }
            if (isLeftFlag) {
                view.translationX = -offset
            } else {
                view.translationX = offset
            }
        }
    }

    private fun absMax(a: Int, b: Int): Int {
        return if (Math.abs(a) > Math.abs(b)) {
            a
        } else {
            b
        }
    }

    override fun onAttachedToWindow(view: RecyclerView?) {
        super.onAttachedToWindow(view)
        mRecyclerView = view
        //check when raise finger and settle to the appropriate item
        view?.setOnTouchListener(mTouchListener)

        view?.onFlingListener = mOnFlingListener
    }

    override fun onDetachedFromWindow(view: RecyclerView?, recycler: RecyclerView.Recycler?) {
        super.onDetachedFromWindow(view, recycler)
        stopAutoCycle()
    }

    private fun brewAndStartAnimator(dur: Int, finalXorY: Int) {
        animator = ObjectAnimator.ofInt(this@StackCardLayoutManager, "animateValue", 0, finalXorY)
        animator?.duration = dur.toLong()
        animator?.start()
        animator?.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                lastAnimateValue = 0
                positionChange()
            }

            override fun onAnimationCancel(animation: Animator) {
                lastAnimateValue = 0
                positionChange()
            }
        })
    }

    private fun positionChange() {
        mOnPositionChangeListener?.let {
            when (stackConfig.direction) {
                StackDirection.LEFT, StackDirection.RIGHT -> {
                    if (mItemWidth > 0) {
                        it.onPositionChange(Math.abs(mTotalOffset) / mItemWidth)
                    }
                }
                StackDirection.TOP, StackDirection.BOTTOM -> {
                    if (mItemHeight > 0) {
                        it.onPositionChange(Math.abs(mTotalOffset) / mItemHeight)
                    }
                }
            }
        }
    }

    @SuppressLint("AnimatorKeep")
    fun setAnimateValue(animateValue: Int) {
        this.animateValue = animateValue
        val distance = this.animateValue - lastAnimateValue
        mRecycler?.let {
            fillItemView(it, stackConfig.direction.layoutDirection * distance, false)
        }
        lastAnimateValue = animateValue
    }

    fun getAnimateValue(): Int {
        return animateValue
    }

    /**
     * 获取垂直方向第一个item的缩放比
     */
    private fun getVerticalFirstScale(): Float {
        return (mItemHeight - (stackConfig.stackCount - 1) * stackConfig.space) * 1f / mItemHeight
    }

    /**
     * 计算垂直方向缩放量，StackDirection.TOP，StackDirection.BOTTOM
     * @param firstScale 首个item的缩放比
     * @param position 当前位置
     * @param index 序号
     */
    private fun calculateVerticalScale(firstScale: Float, position: Int, index: Int): Float {
        return when {
            index > position -> {
                calculateVerticalBaseScale(firstScale, position, index)
            }
            index == position -> { // 第一个item，慢慢移除屏幕
                firstScale
            }
            else -> { // 已完全移出屏幕，不需要显示
                0f
            }
        }
    }

    /**
     * 计算垂直方向缩放量(循环)，StackDirection.TOP，StackDirection.BOTTOM
     * @param firstScale 首个item的缩放比
     * @param position 当前位置
     * @param index 序号
     */
    private fun calculateVerticalCycleScale(firstScale: Float, position: Int, index: Int): Float {
        return when {
            index - position >= stackConfig.stackCount -> {
                0f
            }
            index >= position -> {
                calculateVerticalBaseScale(firstScale, position, index)
            }
            else -> { // 第一个item，慢慢移除屏幕
                firstScale
            }
        }
    }

    /**
     * 计算垂直方向缩放量，StackDirection.TOP，StackDirection.BOTTOM
     * @param firstScale 首个item的缩放比
     * @param position 当前位置
     * @param index 序号
     */
    private fun calculateVerticalBaseScale(firstScale: Float, position: Int, index: Int): Float {
        // 当前移动的比例
        val offsetRatio = mTotalOffset * 1f / mItemHeight - mTotalOffset / mItemHeight
        /* 计算当前item的缩放比 */
        var scale = firstScale
        for (t in 0 until (index - position)) {
            scale *= stackConfig.stackScale
        }
        /* 计算下一个item的缩放比 */
        var nextScale = firstScale
        for (t in 0 until (index - position + 1)) {
            nextScale *= stackConfig.stackScale
        }
        // 返回当前item的缩放比
        return scale + (scale - nextScale) * offsetRatio
    }

    /**
     * 计算垂直方向位置偏移，StackDirection.LEFT，StackDirection.RIGHT
     * @param scale 当前序号item缩放比
     * @param position 当前位置
     * @param index 序号
     */
    private fun calculateVerticalOffset(scale: Float, position: Int, index: Int): Float {
        return when {
            index > position -> {
                calculateVerticalBaseOffset(scale, position, index)
            }
            else -> { // 第一个item
                (mItemHeight - mItemHeight * scale) / 2 - (stackConfig.stackCount - 1) * stackConfig.space * 1f - (mTotalOffset * 1f / mItemHeight - mTotalOffset / mItemHeight) * mItemHeight
            }
        }
    }

    /**
     * 计算垂直方向位置偏移(循环)，StackDirection.LEFT，StackDirection.RIGHT
     * @param scale 当前序号item缩放比
     * @param position 当前位置
     * @param index 序号
     */
    private fun calculateVerticalCycleOffset(scale: Float, position: Int, index: Int): Float {
        return when {
            index >= position -> {
                calculateVerticalBaseOffset(scale, position, index)
            }
            else -> { // 第一个item
                (mItemHeight - mItemHeight * scale) / 2 - (stackConfig.stackCount - 1) * stackConfig.space * 1f - mItemHeight - (mTotalOffset * 1f / mItemHeight - mTotalOffset / mItemHeight) * mItemHeight
            }
        }
    }

    /**
     * 计算垂直方向位置偏移，StackDirection.LEFT，StackDirection.RIGHT
     * @param scale 当前序号item缩放比
     * @param position 当前位置
     * @param index 序号
     */
    private fun calculateVerticalBaseOffset(scale: Float, position: Int, index: Int): Float {
        return if (mTotalOffset % mItemHeight == 0) {
            if (stackConfig.stackCount - index + position - 1 >= 0) {
                (mItemHeight - mItemHeight * scale) / 2 - (stackConfig.stackCount - index + position - 1) * stackConfig.space * 1f
            } else {
                (mItemHeight - mItemHeight * scale) / 2
            }
        } else {
            val offset = ((mTotalOffset) * 1f / mItemHeight - mTotalOffset / mItemHeight) * stackConfig.space
            (mItemHeight - mItemHeight * scale) / 2 - (stackConfig.stackCount - index + position - 1) * stackConfig.space * 1f - offset
        }
    }

    /**
     * 计算时间垂直方向动画时间
     */
    private fun computeVerticalSettleDuration(distance: Int, yVel: Float): Int {
        val sWeight = 0.5f * distance / mItemHeight
        val velWeight = if (yVel > 0) {
            0.5f * mMinVelocity / yVel
        } else {
            0f
        }

        return ((sWeight + velWeight) * duration).toInt()
    }

    /**
     * 获取水平方向第一个item的缩放比
     */
    private fun getHorizontalFirstScale(): Float {
        return (mItemWidth - (stackConfig.stackCount - 1) * stackConfig.space) * 1f / mItemWidth
    }

    /**
     * 计算水平方向缩放量，StackDirection.LEFT，StackDirection.RIGHT
     * @param firstScale 首个item的缩放比
     * @param position 当前位置
     * @param index 序号
     */
    private fun calculateHorizontalScale(firstScale: Float, position: Int, index: Int): Float {
        return when {
            index > position -> {
                calculateHorizontalBaseScale(firstScale, position, index)
            }
            index == position -> { // 第一个item，慢慢移出/移入屏幕
                firstScale
            }
            else -> {
                0f
            }
        }
    }

    /**
     * 计算水平方向缩放量(循环)，StackDirection.LEFT，StackDirection.RIGHT
     * @param firstScale 首个item的缩放比
     * @param position 当前位置
     * @param index 序号
     */
    private fun calculateHorizontalCycleScale(firstScale: Float, position: Int, index: Int): Float {
        return when {
            index - position >= stackConfig.stackCount -> {
                0f
            }
            index >= position -> {
                calculateHorizontalBaseScale(firstScale, position, index)
            }
            else -> { // 第一个item，慢慢移出/移入屏幕
                firstScale
            }
        }
    }

    /**
     * 计算水平方向缩放量，StackDirection.LEFT，StackDirection.RIGHT
     * @param firstScale 首个item的缩放比
     * @param position 当前位置
     * @param index 序号
     */
    private fun calculateHorizontalBaseScale(firstScale: Float, position: Int, index: Int): Float {
        // 当前移动的比例
        val offsetRatio = mTotalOffset * 1f / mItemWidth - mTotalOffset / mItemWidth
        /* 计算当前item的缩放比 */
        var scale = firstScale
        for (t in 0 until (index - position)) {
            scale *= stackConfig.stackScale
        }
        /* 计算下一个item的缩放比 */
        var nextScale = firstScale
        for (t in 0 until (index - position + 1)) {
            nextScale *= stackConfig.stackScale
        }
        // 返回当前item的缩放比
        return scale + (scale - nextScale) * offsetRatio
    }

    /**
     * 计算水平方向偏移，StackDirection.LEFT，StackDirection.RIGHT
     * @param scale 当前序号item缩放比
     * @param position 当前位置
     * @param index 序号
     */
    private fun calculateHorizontalOffset(scale: Float, position: Int, index: Int): Float {
        return when {
            index > position -> {
                calculateHorizontalBaseOffset(scale, position, index)
            }
            else -> { // 第一个item
                (mItemWidth - mItemWidth * scale) / 2 - (stackConfig.stackCount - 1) * stackConfig.space * 1f - (mTotalOffset * 1f / mItemWidth - mTotalOffset / mItemWidth) * mItemWidth
            }
        }
    }

    /**
     * 计算水平方向偏移(循环)，StackDirection.LEFT，StackDirection.RIGHT
     * @param scale 当前序号item缩放比
     * @param position 当前位置
     * @param index 序号
     */
    private fun calculateHorizontalCycleOffset(scale: Float, position: Int, index: Int): Float {
        return when {
            index >= position -> {
                calculateHorizontalBaseOffset(scale, position, index)
            }
            else -> { // 第一个item
                (mItemWidth - mItemWidth * scale) / 2 - (stackConfig.stackCount - 1) * stackConfig.space * 1f - mItemWidth - (mTotalOffset * 1f / mItemWidth - mTotalOffset / mItemWidth) * mItemWidth
            }
        }
    }

    /**
     * 计算水平方向偏移(循环)，StackDirection.LEFT，StackDirection.RIGHT
     * @param scale 当前序号item缩放比
     * @param position 当前位置
     * @param index 序号
     */
    private fun calculateHorizontalBaseOffset(scale: Float, position: Int, index: Int): Float {
        return if (mTotalOffset % mItemWidth == 0) {
            if (stackConfig.stackCount - index + position - 1 >= 0) {
                (mItemWidth - mItemWidth * scale) / 2 - (stackConfig.stackCount - index + position - 1) * stackConfig.space * 1f
            } else {
                (mItemWidth - mItemWidth * scale) / 2
            }
        } else {
            val offset = (mTotalOffset * 1f / mItemWidth - mTotalOffset / mItemWidth) * stackConfig.space
            (mItemWidth - mItemWidth * scale) / 2 - (stackConfig.stackCount - index + position - 1) * stackConfig.space * 1f - offset
        }
    }

    /**
     * 计算时间水平方向动画时间
     */
    private fun computeHorizontalSettleDuration(distance: Int, xVel: Float): Int {
        val sWeight = 0.5f * distance / mItemWidth
        val velWeight = if (xVel > 0) {
            0.5f * mMinVelocity / xVel
        } else {
            0f
        }

        return ((sWeight + velWeight) * duration).toInt()
    }

    private fun recycleHorizontally(view: View?, dx: Int): Boolean {
        return view != null && (view.left - dx < 0 || view.right - dx > width)
    }

    private fun recycleVertically(view: View?, dy: Int): Boolean {
        return view != null && (view.top - dy < 0 || view.bottom - dy > height)
    }

    override fun scrollHorizontallyBy(dx: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
        return fillItemView(recycler, dx)
    }

    override fun scrollVerticallyBy(dy: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
        return fillItemView(recycler, dy)
    }

    override fun canScrollHorizontally(): Boolean {
        return stackConfig.direction == StackDirection.LEFT || stackConfig.direction == StackDirection.RIGHT
    }

    override fun canScrollVertically(): Boolean {
        return stackConfig.direction == StackDirection.TOP || stackConfig.direction == StackDirection.BOTTOM
    }

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
        return RecyclerView.LayoutParams(RecyclerView.LayoutParams.WRAP_CONTENT, RecyclerView.LayoutParams.WRAP_CONTENT)
    }

    /**
     * we need to set scrollstate to [RecyclerView.SCROLL_STATE_IDLE] idle
     * stop RecyclerView from intercepting the touch event which block the item click
     */
    private fun setScrollStateIdle() {
        try {
            if (sSetScrollState == null) {
                sSetScrollState = RecyclerView::class.java.getDeclaredMethod("setScrollState", Int::class.javaPrimitiveType!!)
            }
            sSetScrollState?.isAccessible = true
            sSetScrollState?.invoke(mRecyclerView, RecyclerView.SCROLL_STATE_IDLE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun scrollToPosition(position: Int) {
        if (position > itemCount - 1) {
            return
        }
        when (stackConfig.direction) {
            StackDirection.LEFT, StackDirection.RIGHT -> {
                if (mItemWidth > 0) {
                    val currPosition = mTotalOffset / mItemWidth
                    val distance = (position - currPosition) * mItemWidth
                    val dur = computeHorizontalSettleDuration(Math.abs(distance), 0f)
                    brewAndStartAnimator(dur, distance)
                }
            }
            StackDirection.TOP, StackDirection.BOTTOM -> {
                if (mItemHeight > 0) {
                    val currPosition = mTotalOffset / mItemHeight
                    val distance = (position - currPosition) * mItemHeight
                    val dur = computeVerticalSettleDuration(Math.abs(distance), 0f)
                    brewAndStartAnimator(dur, distance)
                }
            }
        }
    }

    override fun requestLayout() {
        super.requestLayout()
        initialFlag = false
    }

    /**
     * 开始自动循环
     */
    private fun startAutoCycle() {
        if (stackConfig.isCycle && itemCount > 1 && stackConfig.isAutoCycle) {
            mRecyclerView?.postDelayed(mAutoCycleRunnable, (stackConfig.autoCycleTime + duration) * 1L)
        }
    }

    /**
     * 开始暂停自动循环
     */
    private fun stopAutoCycle() {
        if (stackConfig.isCycle && itemCount > 1 && stackConfig.isAutoCycle) {
            mRecyclerView?.removeCallbacks(mAutoCycleRunnable)
        }
    }

    /**
     * 设置位置改变监听
     */
    fun setOnPositionChangeListener(action: (position: Int) -> Unit) {
        this.mOnPositionChangeListener = object : OnPositionChangeListener {
            override fun onPositionChange(position: Int) {
                action(position)
            }
        }
    }

    /**
     * 位置改变监听
     */
    interface OnPositionChangeListener {
        fun onPositionChange(position: Int)
    }

    class StackConfig {

        @IntRange(from = 0)
        var space = 60 // 间距

        @IntRange(from = 1)
        var stackCount = 3 // 可见数

        @IntRange(from = 0)
        var stackPosition = 0 // 初始可见的位置

        @FloatRange(from = 0.0, to = 1.0)
        var stackScale: Float = 0.9f // 缩放比例

        @FloatRange(from = 1.0, to = 2.0)
        var parallex = 1f // 视差因子

        var isCycle = false // 是否能无限循环，若列表数为1不允许无限循环

        var isAutoCycle = false // 若能无限循环，是否自动开始循环

        @IntRange(from = 1000)
        var autoCycleTime = 3000 // 自动循环时间间隔，毫秒

        var direction: StackDirection = StackDirection.RIGHT // 方向

    }

    enum class StackDirection(val layoutDirection: Int = 0) {
        LEFT(-1),
        RIGHT(1),
        TOP(-1),
        BOTTOM(1)
    }

}
