package tech.procd.customer.account

import tech.procd.persistence.postgres.execute
import tech.procd.persistence.postgres.fetchOne
import tech.procd.persistence.postgres.querybuilder.insert
import tech.procd.persistence.postgres.querybuilder.select
import tech.procd.persistence.postgres.querybuilder.update
import io.vertx.pgclient.data.Inet
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.SqlClient

class AccountStore(
    private val sqlClient: SqlClient,
) {

    suspend fun find(email: String): Account? = sqlClient.fetchOne(
        select("account") {
            where {
                "email" eq email
            }
        }
    )?.map()


    suspend fun load(account: Account.Id): Account? = sqlClient.fetchOne(
        select("account") {
            where {
                "id" eq account.value
            }
        }
    )?.map()

    suspend fun add(account: Account) = sqlClient.execute(
        insert(
            to = "account",
            rows = mapOf(
                "id" to account.id.value,
                "realm" to account.realmId.value,
                "email" to account.email.lowercase(),
                "assigned_groups" to account.assignedGroups
                    .map { it.name.lowercase().replace(" ", "_") }
                    .toTypedArray(),
                "assigned_roles" to account.assignedRoles.toTypedArray(),
                "registered_at" to account.createdAt,
                "last_authorized_at" to account.lastAuthorizedAt,
            ),
        ),
    )

    suspend fun store(account: Account) = sqlClient.execute(
        update(
            on = "account",
            rows = mapOf(
                "assigned_groups" to account.assignedGroups
                    .map { it.name.lowercase().replace(" ", "_") }
                    .toTypedArray(),
                "assigned_roles" to account.assignedRoles.toTypedArray(),
                "last_authorized_at" to account.lastAuthorizedAt,
            ),
        ) {
            where {
                "id" eq account.id.value
            }
        },
    )
}

private fun Row.map(): Account {
    return Account(
        id = Account.Id(getUUID("id")),
        realmId = Account.Realm(getString("realm")),
        email = getString("email").lowercase(),
        assignedGroups = getArrayOfStrings("assigned_groups")
            .map { Account.Group.valueOf(it.uppercase().replace(" ", "_")) },
        assignedRoles = getArrayOfStrings("assigned_roles").toList(),
        createdAt = getLocalDateTime("registered_at"),
        lastAuthorizedAt = getLocalDateTime("last_authorized_at"),
    )
}
