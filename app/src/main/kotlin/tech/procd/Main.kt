package tech.procd

import tech.procd.application.ApplicationBuilder
import tech.procd.application.applicationModule
import tech.procd.application.mutexes.mutexModule
import tech.procd.company.companyModule
import tech.procd.customer.customerModule
import java.util.Locale
import java.util.TimeZone

fun main() {
    Locale.setDefault(Locale("en", "EN"))
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    ApplicationBuilder
        .withModules(
            applicationModule,
            mutexModule,
            customerModule,
            companyModule
        )
        .run()
}
