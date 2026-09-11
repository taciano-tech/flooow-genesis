package io.flooow.research.exp0007

internal enum class ExperimentalCrashPoint {
    BEFORE_PROJECTION_WRITE,
    AFTER_PROJECTION_WRITE,
    AFTER_PROJECTION_COMMIT,
    BEFORE_CHECKPOINT_ADVANCE,
    AFTER_CHECKPOINT_ADVANCE,
    BEFORE_COMMIT
}

internal class ExperimentalInjectedCrash(
    val point: ExperimentalCrashPoint
) : RuntimeException("EXP-0007 injected crash at $point")

internal fun interface ExperimentalCrashInjector {
    fun hit(point: ExperimentalCrashPoint)

    companion object {
        val NONE = ExperimentalCrashInjector { }

        fun at(target: ExperimentalCrashPoint): ExperimentalCrashInjector =
            ExperimentalCrashInjector { point ->
                if (point == target) {
                    throw ExperimentalInjectedCrash(point)
                }
            }
    }
}
