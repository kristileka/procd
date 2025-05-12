package tech.procd.persistence.postgres.infrabuilder


data class ValueDefinition(
    val type: String,
    val size: Int? = null,
    val primary: Boolean = false,
    val nullable: Boolean = false,
    val unique: Boolean = false
) {
    override fun toString(): String {
        val dataType = if (size != null) "$type($size)" else type
        val unique = if (unique) "UNIQUE" else ""
        val primary = if (primary) "PRIMARY" else ""
        val nonNull = if (!nullable) "NOT NULL" else ""
        return "$dataType $unique $primary $nonNull";
    }
}
