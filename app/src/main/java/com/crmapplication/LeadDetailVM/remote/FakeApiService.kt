package com.crmapplication.LeadDetailVM.remote

import kotlinx.coroutines.delay
import java.util.UUID

class FakeApiService : ApiService {

    override suspend fun login(request: LoginRequest): LoginResponse {
        delay(600)
        val displayName = request.username
            .replaceFirstChar { it.uppercase() }
            .replace("_", " ")
            .replace(".", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        return LoginResponse(
            token = "fake-token-${UUID.randomUUID()}",
            agentName = displayName.ifBlank { "Demo Agent" }
        )
    }

    override suspend fun getDashboard(token: String): DashboardDto {
        delay(500)
        return DashboardDto(
            totalTalktime = "14h 32m",
            totalDials    = 187,
            newCalls      = 63,
            repeatedCalls = 124
        )
    }

    private val dummyLeads = listOf(
        LeadDto("1",  "Alice Smith",       "+1 234 567 8900", System.currentTimeMillis() - 86400000L * 1,  null),
        LeadDto("2",  "Bob Johnson",       "+1 987 654 3210", System.currentTimeMillis() - 86400000L * 5,  System.currentTimeMillis() + 86400000L),
        LeadDto("3",  "Charlie Brown",     "+1 555 123 4567", System.currentTimeMillis() - 86400000L * 10, null),
        LeadDto("4",  "Divya Sharma",      "+91 98765 43210", System.currentTimeMillis() - 86400000L * 2,  System.currentTimeMillis() + 86400000L * 3),
        LeadDto("5",  "Ethan Williams",    "+44 7700 900123", System.currentTimeMillis() - 86400000L * 7,  System.currentTimeMillis() - 86400000L),
        LeadDto("6",  "Fatima Al-Hassan",  "+971 50 123 4567", System.currentTimeMillis() - 3600000L * 3,   null),
        LeadDto("7",  "George Martinez",   "+1 305 987 6543", System.currentTimeMillis() - 86400000L * 14, null),
        LeadDto("8",  "Hannah Lee",        "+82 10 1234 5678", System.currentTimeMillis() - 86400000L * 3,  System.currentTimeMillis()),
        LeadDto("9",  "Ivan Petrov",       "+7 916 123 4567", System.currentTimeMillis() - 86400000L * 4,  System.currentTimeMillis() + 86400000L * 7),
        LeadDto("10", "Julia Chen",        "+86 138 0013 8000", System.currentTimeMillis() - 86400000L * 6,  System.currentTimeMillis() + 86400000L * 2),
    )

    override suspend fun getLeads(token: String): List<LeadDto> {
        delay(700)
        return dummyLeads
    }

    override suspend fun getLead(token: String, id: String): LeadDto {
        delay(300)
        return dummyLeads.find { it.id == id } ?: throw Exception("Lead not found")
    }

    override suspend fun addNote(token: String, leadId: String, request: AddNoteRequest): NoteDto {
        delay(300)
        return NoteDto(
            id        = UUID.randomUUID().toString(),
            leadId    = leadId,
            text      = request.text,
            timestamp = System.currentTimeMillis()
        )
    }

    override suspend fun setDueDate(token: String, leadId: String, request: SetDueDateRequest): LeadDto {
        delay(300)
        val lead = getLead(token, leadId)
        return lead.copy(dueDate = request.dueDate)
    }
}
