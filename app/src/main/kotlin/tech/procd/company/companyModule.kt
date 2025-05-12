package tech.procd.company

import org.koin.dsl.module

val companyModule = module {
    single {
        CompanyStore(
            sqlClient = get(),
        )
    }

    single {
        CompanyProvider(
            get(),
            get(),
            get()
        )
    }
}
