package com.academicflow.config

/**
 * Verified Clerk identity for the current request (WHO), separate from AcademicFlow UserContext (WHAT).
 */
object ClerkContext {
    data class Identity(
        val clerkUserId: String,
        val email: String,
        val emailVerified: Boolean,
        val fullName: String?
    )

    private val holder = ThreadLocal<Identity?>()

    fun set(identity: Identity) = holder.set(identity)
    fun get(): Identity? = holder.get()
    fun clear() = holder.remove()
    fun require(): Identity = get() ?: throw IllegalStateException("Not authenticated with Clerk")
}
