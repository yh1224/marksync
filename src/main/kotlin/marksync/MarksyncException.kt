package marksync

class MarksyncException(message: String, val code: ErrorCode) : Exception(message) {
    enum class ErrorCode(val num: Int) {
        ENV(1),
        TARGET(2),
    }
}
