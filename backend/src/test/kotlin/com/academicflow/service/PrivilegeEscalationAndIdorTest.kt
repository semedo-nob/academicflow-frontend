package com.academicflow.service

import com.academicflow.config.UserContext
import com.academicflow.service.permission.PermissionCodes
import com.academicflow.service.permission.PermissionService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

class PrivilegeEscalationAndIdorTest {

    private val cs = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa5")
    private val maths = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa8")
    private val chairUser = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1")
    private val lecturerUser = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2")
    private val tenant = UUID.fromString("11111111-1111-1111-1111-111111111111")

    private val scopeService = ScopeService(
        membershipRepo = Mockito.mock(com.academicflow.repository.OrganizationMembershipRepository::class.java),
        orgRepo = Mockito.mock(com.academicflow.repository.OrganizationNodeRepository::class.java)
    )

    private val permissionService = PermissionService(
        userPermissionRepo = Mockito.mock(com.academicflow.repository.UserPermissionRepository::class.java),
        auditRepo = Mockito.mock(com.academicflow.repository.AuditLogRepository::class.java)
    )

    @AfterEach
    fun clear() {
        UserContext.clear()
        Mockito.framework().clearInlineMocks()
    }

    private fun principal(
        userId: UUID,
        role: String,
        dept: UUID,
        email: String
    ) = UserContext.Principal(
        userId = userId,
        email = email,
        role = role,
        organizationNodeId = dept,
        tenantId = tenant,
        memberships = listOf(
            UserContext.Membership(
                id = UUID.randomUUID(),
                organizationNodeId = dept,
                organizationName = "Dept",
                organizationType = "Department",
                role = role,
                isPrimary = true
            )
        ),
        activeDepartmentId = dept,
        activeDepartmentName = "Dept",
        activeRole = role
    )

    @Test
    fun `lecturer effective permissions exclude approve reject and user manage`() {
        val perms = PermissionCodes.defaultsForRole("LECTURER")
        assertTrue(perms.contains(PermissionCodes.ALLOCATIONS_VIEW))
        assertTrue(perms.contains(PermissionCodes.ALLOCATIONS_COMMENT))
        assertFalse(perms.contains(PermissionCodes.ALLOCATIONS_APPROVE))
        assertFalse(perms.contains(PermissionCodes.ALLOCATIONS_REJECT))
        assertFalse(perms.contains(PermissionCodes.USERS_MANAGE))
        assertFalse(perms.contains(PermissionCodes.USERS_INVITE))
        assertFalse(perms.contains(PermissionCodes.DEPARTMENT_MANAGE))
    }

    @Test
    fun `lecturer cannot escalate via permission require approve`() {
        UserContext.set(principal(lecturerUser, "LECTURER", cs, "lecturer@example.com"))
        // No explicit grants in mocked repo → defaults only
        assertThrows(ResponseStatusException::class.java) {
            permissionService.require(PermissionCodes.ALLOCATIONS_APPROVE)
        }
        assertThrows(ResponseStatusException::class.java) {
            permissionService.require(PermissionCodes.USERS_MANAGE)
        }
        permissionService.require(PermissionCodes.ALLOCATIONS_VIEW)
    }

    @Test
    fun `chair cannot access foreign department via requireDepartmentAccess`() {
        UserContext.set(principal(chairUser, "DEPARTMENT_CHAIR", cs, "chair-a@example.com"))
        assertThrows(ResponseStatusException::class.java) {
            scopeService.requireDepartmentAccess(maths)
        }
        scopeService.requireDepartmentAccess(cs)
    }

    @Test
    fun `chair cannot delegate institution manage or approve`() {
        assertThrows(IllegalArgumentException::class.java) {
            permissionService.assertCanDelegate(
                "DEPARTMENT_CHAIR",
                listOf(PermissionCodes.INSTITUTION_MANAGE, PermissionCodes.ALLOCATIONS_APPROVE)
            )
        }
    }

    @Test
    fun `invitation token hash is not reversible plaintext`() {
        val raw = InvitationTokens.generateRawToken()
        val hash = InvitationTokens.hash(raw)
        assertTrue(hash.length == 64)
        assertFalse(hash.equals(raw, ignoreCase = true))
        assertTrue(InvitationTokens.hash(raw) == hash)
        assertFalse(InvitationTokens.hash(raw + "x") == hash)
    }
}
