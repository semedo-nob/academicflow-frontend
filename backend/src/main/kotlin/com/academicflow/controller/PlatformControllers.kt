package com.academicflow.controller

import com.academicflow.service.PlatformService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

data class LifecycleRequest(val status: String, val note: String? = null)
data class AccountStatusRequest(val status: String, val note: String? = null)
data class FlagToggleRequest(val enabled: Boolean)
data class SettingUpdateRequest(val value: String)

@RestController
@RequestMapping("/api/platform")
class PlatformControllers(private val platform: PlatformService) {

    @GetMapping("/command-center")
    fun commandCenter() = platform.commandCenter()

    @GetMapping("/health")
    fun health() = mapOf("checks" to platform.healthChecks())

    @GetMapping("/system")
    fun system() = platform.systemInfo()

    @GetMapping("/institutions")
    fun institutions(@RequestParam(required = false) status: String?) = platform.listInstitutions(status)

    @GetMapping("/institutions/{id}")
    fun institution(@PathVariable id: UUID) = try {
        platform.institutionDetail(id)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    }

    @PostMapping("/institutions/{id}/lifecycle")
    fun lifecycle(@PathVariable id: UUID, @RequestBody req: LifecycleRequest) = try {
        platform.setInstitutionLifecycle(id, req.status, req.note)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    } catch (e: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    }

    @GetMapping("/users")
    fun users(@RequestParam(required = false) q: String?) = platform.listUsers(q)

    @PostMapping("/users/{id}/status")
    fun userStatus(@PathVariable id: UUID, @RequestBody req: AccountStatusRequest) = try {
        platform.setUserAccountStatus(id, req.status, req.note)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    } catch (e: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    }

    @GetMapping("/analytics")
    fun analytics() = platform.analytics()

    @GetMapping("/security/events")
    fun security() = platform.securityEvents()

    @GetMapping("/audit")
    fun audit() = platform.platformAuditLogs()

    @GetMapping("/features")
    fun features() = platform.listFlags()

    @PutMapping("/features/{id}")
    fun setFeature(@PathVariable id: UUID, @RequestBody req: FlagToggleRequest) = try {
        platform.setFlag(id, req.enabled)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    }

    @GetMapping("/configuration")
    fun configuration() = platform.listSettings()

    @PutMapping("/configuration/{id}")
    fun updateConfiguration(@PathVariable id: UUID, @RequestBody req: SettingUpdateRequest) = try {
        platform.updateSetting(id, req.value)
    } catch (e: NoSuchElementException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    }

    @GetMapping("/data-quality")
    fun dataQuality() = platform.dataQuality()

    @GetMapping("/support/lookup")
    fun supportLookup(@RequestParam q: String) = platform.supportLookup(q)

    @GetMapping("/search")
    fun search(@RequestParam q: String) = platform.globalSearch(q)
}
