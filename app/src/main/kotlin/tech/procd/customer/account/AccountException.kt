package tech.procd.customer.account

sealed class AccountException(message: String?) : Exception(message) {
    class AlreadyRegistered : AccountException("Account already registered")
    class OperationFailed(error: Exception) : AccountException(error.message)
}
