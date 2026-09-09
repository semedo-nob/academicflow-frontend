package com.academicflow.service.permission

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PermissionCodesTest {

    @Test
    fun `chair defaults include approve but not institution manage`() {
        val chair = PermissionCodes.defaultsForRole("DEPARTMENT_CHAIR")
        assertTrue(chair.contains(PermissionCodes.ALLOCATIONS_APPROVE))
        assertFalse(chair.contains(PermissionCodes.INSTITUTION_MANAGE))
    }

    @Test
    fun `chair cannot delegate approve or institution manage`() {
        val allowed = PermissionCodes.delegatableBy("DEPARTMENT_CHAIR")
        assertTrue(allowed.contains(PermissionCodes.ALLOCATIONS_COMMENT))
        assertFalse(allowed.contains(PermissionCodes.ALLOCATIONS_APPROVE))
        assertFalse(allowed.contains(PermissionCodes.INSTITUTION_MANAGE))
    }

    @Test
    fun `lecturer cannot approve`() {
        val lecturer = PermissionCodes.defaultsForRole("LECTURER")
        assertFalse(lecturer.contains(PermissionCodes.ALLOCATIONS_APPROVE))
        assertTrue(lecturer.contains(PermissionCodes.ALLOCATIONS_VIEW))
    }

    @Test
    fun `workflow gates approve and edit`() {
        assertTrue(AllocationWorkflow.canSubmit(AllocationWorkflow.DRAFT))
        assertFalse(AllocationWorkflow.canApprove(AllocationWorkflow.DRAFT))
        assertTrue(AllocationWorkflow.canApprove(AllocationWorkflow.SUBMITTED))
        assertTrue(AllocationWorkflow.canEdit(AllocationWorkflow.CHANGES_REQUESTED))
        assertFalse(AllocationWorkflow.canEdit(AllocationWorkflow.APPROVED))
    }

    @Test
    fun `normalize drops unknown codes`() {
        val n = PermissionCodes.normalize(listOf("ALLOCATIONS.VIEW", "HACK.ALL", "units.view"))
        assertTrue(n.contains(PermissionCodes.ALLOCATIONS_VIEW))
        assertTrue(n.contains(PermissionCodes.UNITS_VIEW))
        assertFalse(n.contains("HACK.ALL"))
    }
}

class PermissionDelegationTest {
    @Test
    fun `assertCanDelegate rejects illegal grants`() {
        val svc = PermissionService(
            userPermissionRepo = org.mockito.Mockito.mock(com.academicflow.repository.UserPermissionRepository::class.java),
            auditRepo = org.mockito.Mockito.mock(com.academicflow.repository.AuditLogRepository::class.java)
        )
        assertThrows<IllegalArgumentException> {
            svc.assertCanDelegate(
                "DEPARTMENT_CHAIR",
                listOf(PermissionCodes.ALLOCATIONS_APPROVE, PermissionCodes.INSTITUTION_MANAGE)
            )
        }
        svc.assertCanDelegate(
            "DEPARTMENT_CHAIR",
            listOf(PermissionCodes.ALLOCATIONS_VIEW, PermissionCodes.ALLOCATIONS_COMMENT)
        )
    }
}
