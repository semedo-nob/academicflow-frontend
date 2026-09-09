package com.academicflow.service

import com.academicflow.config.UserContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

class ScopeIsolationTest {

    private val cs = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa5")
    private val maths = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa8")
    private val chairUser = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1")

    private val service = ScopeService(
        membershipRepo = Mockito.mock(com.academicflow.repository.OrganizationMembershipRepository::class.java),
        orgRepo = Mockito.mock(com.academicflow.repository.OrganizationNodeRepository::class.java)
    )

    @AfterEach
    fun clear() {
        UserContext.clear()
        Mockito.framework().clearInlineMocks()
    }

    private fun chairPrincipal(active: UUID = cs) = UserContext.Principal(
        userId = chairUser,
        email = "j.wanjiku@uonbi.ac.ke",
        role = "DEPARTMENT_CHAIR",
        organizationNodeId = cs,
        tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
        memberships = listOf(
            UserContext.Membership(
                id = UUID.randomUUID(),
                organizationNodeId = cs,
                organizationName = "Computer Science",
                organizationType = "Department",
                role = "DEPARTMENT_CHAIR",
                isPrimary = true
            )
        ),
        activeDepartmentId = active,
        activeDepartmentName = "Computer Science",
        activeRole = "DEPARTMENT_CHAIR"
    )

    @Test
    fun `department chair is department scoped not institution wide`() {
        val me = chairPrincipal()
        assertTrue(me.isDepartmentScoped())
        assertFalse(me.isInstitutionWide())
    }

    @Test
    fun `institution admin is institution wide`() {
        val me = chairPrincipal().copy(role = "INSTITUTION_ADMIN", activeRole = "INSTITUTION_ADMIN")
        assertTrue(me.isInstitutionWide())
        assertFalse(me.isDepartmentScoped())
    }

    @Test
    fun `chair cannot access foreign department via requireDepartmentAccess`() {
        UserContext.set(chairPrincipal(cs))
        assertEquals(setOf(cs), service.authorizedDepartmentIds())
        assertThrows(ResponseStatusException::class.java) {
            service.requireDepartmentAccess(maths)
        }
        service.requireDepartmentAccess(cs)
    }

    @Test
    fun `resolveActiveDepartment rejects unauthorized department selection`() {
        val memberships = listOf(
            UserContext.Membership(
                id = UUID.randomUUID(),
                organizationNodeId = cs,
                organizationName = "Computer Science",
                organizationType = "Department",
                role = "DEPARTMENT_CHAIR",
                isPrimary = true
            )
        )
        assertThrows(ResponseStatusException::class.java) {
            service.resolveActiveDepartment("DEPARTMENT_CHAIR", cs, memberships, maths)
        }
        val ok = service.resolveActiveDepartment("DEPARTMENT_CHAIR", cs, memberships, cs)
        assertEquals(cs, ok.first)
    }

    @Test
    fun `cross department lecturer allocation blocked without override flag`() {
        assertThrows(ResponseStatusException::class.java) {
            service.requireLecturerInDepartments(maths, setOf(cs), crossDepartmentAllowed = false)
        }
        service.requireLecturerInDepartments(maths, setOf(cs), crossDepartmentAllowed = true)
        service.requireLecturerInDepartments(cs, setOf(cs), crossDepartmentAllowed = false)
    }
}
