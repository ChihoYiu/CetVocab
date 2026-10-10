package com.cetvocab.app.core.result

/**
 * Small result wrapper used by repositories and importers.
 *
 * The app deliberately keeps domain failures as *values* (instead of exceptions) so that
 * every screen can render a real Loading / Empty / Error state, and so a single bad row in
 * an imported file can never crash the process.
 */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>

    fun getOrNull(): T? = (this as? Success)?.value
    val isSuccess: Boolean get() = this is Success
}

inline fun <T> Outcome<T>.onSuccess(block: (T) -> Unit): Outcome<T> {
    if (this is Outcome.Success) block(value)
    return this
}

inline fun <T> Outcome<T>.onFailure(block: (AppError) -> Unit): Outcome<T> {
    if (this is Outcome.Failure) block(error)
    return this
}

/** A user-facing failure: [message] is shown in Chinese, [debugDetail] never contains user data. */
data class AppError(
    val code: ErrorCode,
    val message: String,
    val debugDetail: String? = null,
) {
    companion object {
        fun io(detail: String? = null) = AppError(ErrorCode.IO, "读写失败，请重试或检查存储空间。", detail)
        fun parse(detail: String? = null) = AppError(ErrorCode.PARSE, "文件格式不正确，请检查后重新导入。", detail)
        fun tooLarge() = AppError(ErrorCode.TOO_LARGE, "文件过大，请拆分后再导入。")
        fun empty() = AppError(ErrorCode.EMPTY, "文件中没有可用的单词记录。")
        fun database(detail: String? = null) = AppError(ErrorCode.DATABASE, "本地数据操作失败，请重试。", detail)
        fun unknown(detail: String? = null) = AppError(ErrorCode.UNKNOWN, "操作失败，请稍后再试。", detail)
    }
}

enum class ErrorCode { IO, PARSE, TOO_LARGE, EMPTY, DATABASE, UNKNOWN }
