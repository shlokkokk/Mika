package com.wangzan101.mika.calls

/**
 * 通话怎么结束的固定说法:写进通话记录的 `result`,记录页按它换成界面语言(见 `localizedCallResult`)。
 * 写的地方和认的地方共用这些常量,改字不会让记录页悄悄认不出来;已存的旧记录也是这些原文,所以文字不要随意改。
 */
internal object CallEndText {
    const val ENDED = "通话已结束"
    const val BUSY = "对方占线，通话已结束"
    const val FAILED = "Android 报告通话失败"
    const val MISSED = "来电未接"
    const val USER_HANGUP = "用户挂断"
    const val ANDROID_CONFIRMED = "Android 已确认通话结束"
    const val FAREWELL_ENDED = "通话已收尾，Android 已挂断"
    const val MAX_DURATION_ENDED = "达到时长上限，Android 已挂断"
}
