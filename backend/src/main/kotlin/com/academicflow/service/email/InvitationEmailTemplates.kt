package com.academicflow.service.email

object InvitationEmailTemplates {
    fun invitation(
        institutionName: String,
        departmentName: String?,
        roleLabel: String,
        inviteeName: String,
        acceptUrl: String,
        expiresLabel: String,
        invitedByName: String? = null
    ): EmailMessage {
        val deptLine = departmentName?.takeIf { it.isNotBlank() }?.let { " for $it" }.orEmpty()
        val subject = "You're invited to AcademicFlow — $roleLabel$deptLine"
        val invitedByLine = invitedByName?.takeIf { it.isNotBlank() }
        val text = buildString {
            appendLine("Hello $inviteeName,")
            appendLine()
            appendLine("You have been invited to join $institutionName on AcademicFlow.")
            appendLine()
            if (!departmentName.isNullOrBlank()) appendLine("Department: $departmentName")
            appendLine("Role: $roleLabel")
            if (invitedByLine != null) appendLine("Invited by: $invitedByLine")
            appendLine()
            appendLine("Accept your invitation:")
            appendLine(acceptUrl)
            appendLine()
            appendLine("This invitation expires on $expiresLabel.")
            appendLine("If you did not expect this email, you can ignore it — no account will be created.")
            appendLine()
            appendLine("— AcademicFlow")
        }
        val deptHtml = departmentName?.takeIf { it.isNotBlank() }?.let {
            "<p style=\"margin:4px 0;\"><strong>Department:</strong> ${escape(it)}</p>"
        }.orEmpty()
        val invitedByHtml = invitedByLine?.let {
            "<p style=\"margin:4px 0;\"><strong>Invited by:</strong> ${escape(it)}</p>"
        }.orEmpty()
        val html = """
            <div style="font-family:Segoe UI,Roboto,Helvetica,Arial,sans-serif;line-height:1.5;color:#1a1a1a;max-width:560px;margin:0 auto;padding:24px;">
              <p style="font-size:13px;letter-spacing:.08em;text-transform:uppercase;color:#5b6b7c;margin:0 0 8px;">AcademicFlow</p>
              <h1 style="font-size:22px;margin:0 0 16px;">You're invited</h1>
              <p>Hello ${escape(inviteeName)},</p>
              <p>You have been invited to join <strong>${escape(institutionName)}</strong>.</p>
              $deptHtml
              <p style="margin:4px 0;"><strong>Role:</strong> ${escape(roleLabel)}</p>
              $invitedByHtml
              <p style="margin:28px 0;">
                <a href="${escape(acceptUrl)}" style="display:inline-block;background:#4457E8;color:#fff;text-decoration:none;padding:12px 20px;border-radius:8px;font-weight:600;">
                  Accept Invitation
                </a>
              </p>
              <p style="font-size:13px;color:#5b6b7c;">Secure fallback link (paste into your browser):<br/>
                <a href="${escape(acceptUrl)}" style="color:#4457E8;word-break:break-all;">${escape(acceptUrl)}</a>
              </p>
              <p style="font-size:13px;color:#5b6b7c;">Expires on ${escape(expiresLabel)}. If you did not expect this email, ignore it.</p>
            </div>
        """.trimIndent()
        return EmailMessage(
            to = "", // filled by caller
            subject = subject,
            htmlBody = html,
            textBody = text,
            tags = mapOf("type" to "invitation", "role" to roleLabel)
        )
    }

    fun roleLabel(role: String): String = when (role.uppercase()) {
        "DEPARTMENT_CHAIR" -> "Department Chair"
        "INSTITUTION_ADMIN" -> "Institution Admin"
        "SCHOOL_ADMIN", "SCHOOL_DEAN" -> "School Dean"
        "LECTURER" -> "Lecturer"
        "STUDENT" -> "Student"
        "VIEWER" -> "Viewer"
        else -> role.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
    }

    private fun escape(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
