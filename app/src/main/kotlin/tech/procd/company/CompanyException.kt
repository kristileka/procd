package tech.procd.company


sealed class CompanyException(message: String?) : Exception(message) {
    data object CompanyMismatch : CompanyException("Company mismatch")
    class NotFound(id: Company.Id) : CompanyException("Company `$id` was not found")
}
