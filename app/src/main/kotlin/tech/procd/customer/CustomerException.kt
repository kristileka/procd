package tech.procd.customer


sealed class CustomerException(message: String?) : Exception(message) {
    data object CustomerMismatch : CustomerException("Customer mismatch")
    class AccountNotFound(email: String) :
        CustomerException("Account with email `$email` was not found")

    class NotFound(id: Customer.Id) : CustomerException("Customer `$id` was not found")
}
