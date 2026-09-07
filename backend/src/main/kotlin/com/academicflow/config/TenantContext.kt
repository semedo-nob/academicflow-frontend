package com.academicflow.config

import java.util.UUID

object TenantContext {
    private val holder = ThreadLocal<UUID>()

    fun set(tenantId: UUID) = holder.set(tenantId)
    fun get(): UUID = holder.get()
        ?: UUID.fromString("11111111-1111-1111-1111-111111111111")
    fun clear() = holder.remove()
}
